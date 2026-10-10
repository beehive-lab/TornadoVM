/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.tc.tornado;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import uk.ac.manchester.tornado.tc.ir.Attribute;
import uk.ac.manchester.tornado.tc.ir.Block;
import uk.ac.manchester.tornado.tc.ir.BlockArg;
import uk.ac.manchester.tornado.tc.ir.Diagnostic;
import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Inst;
import uk.ac.manchester.tornado.tc.ir.IrBuilder;
import uk.ac.manchester.tornado.tc.ir.Loc;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Opcode.BinaryOp;
import uk.ac.manchester.tornado.tc.ir.Type;
import uk.ac.manchester.tornado.tc.ir.Value;
import uk.ac.manchester.tornado.tc.ir.analysis.LoopForest;

/**
 * Lowers {@code @Parallel} loops (optimisation list item T2) for GPU targets: every loop whose header
 * carries {@code parallel(iv=k)} becomes a grid-stride loop over dimension {@code d}:
 *
 * <pre>
 *   for (i = init; cond(i); i += step)   ==>   for (i = init + gid(d) * step; cond(i); i += gsize(d) * step)
 * </pre>
 *
 * <p>Dimensions follow TornadoVM's behaviour on GPUs (loop interchange): the innermost parallel loop
 * is dimension 0 (x), the next one out dimension 1 (y), then 2 (z). The step must be loop-invariant.
 * The header gets {@code grid(dim=d)} for diagnostics and reports.</p>
 *
 * <p>Error codes: {@code TC-E3201} the loop is not in counted form ({@code i = i + step}, one entry
 * edge, one back edge); {@code TC-E3202} more than three nested parallel loops.</p>
 */
public final class ParallelLoopLowering {

    private static final String[] DIMENSIONS = { "x", "y", "z" };

    private ParallelLoopLowering() {
    }

    public static List<Diagnostic> run(Function function) {
        var diagnostics = new ArrayList<Diagnostic>();
        var forest = new LoopForest(function);
        record Parallel(LoopForest.Loop loop, int iv) {
        }
        var parallel = new ArrayList<Parallel>();
        for (var block : function.blocks()) {
            for (var attribute : block.attributes()) {
                if (attribute.name().equals("parallel") && attribute.args().startsWith("iv=")) {
                    var loop = forest.loopWithHeader(block);
                    if (loop.isEmpty()) {
                        diagnostics.add(error(block, "block marked parallel is not a loop header"));
                    } else {
                        parallel.add(new Parallel(loop.get(), Integer.parseInt(attribute.args().substring(3))));
                    }
                }
            }
        }
        // Innermost first: deeper loops get lower dimensions.
        parallel.sort(Comparator.comparingInt((Parallel p) -> p.loop().depth()).reversed());
        if (parallel.size() > 3) {
            diagnostics.add(new Diagnostic("TC-E3202", "more than three nested @Parallel loops (" + parallel.size() + ")", "@" + function.name(), Loc.UNKNOWN));
            return diagnostics;
        }
        for (int dim = 0; dim < parallel.size(); dim++) {
            var p = parallel.get(dim);
            lower(p.loop(), p.iv(), dim).ifPresent(diagnostics::add);
        }
        return diagnostics;
    }

    private static java.util.Optional<Diagnostic> lower(LoopForest.Loop loop, int ivIndex, int dim) {
        var header = loop.header();
        var iv = header.args().get(ivIndex);
        var entries = new ArrayList<Inst>();
        var predecessors = header.function().predecessors().get(header);
        for (var pred : predecessors) {
            if (!loop.contains(pred)) {
                entries.add(pred.terminator().orElseThrow());
            }
        }
        if (entries.size() != 1 || loop.latches().size() != 1) {
            return java.util.Optional.of(error(header, "a @Parallel loop needs one entry edge and one back edge"));
        }
        var entry = entries.get(0);
        var latch = loop.latches().get(0).terminator().orElseThrow();
        var next = argumentOnEdge(latch, header, ivIndex);
        if (!(next instanceof Inst add && add.opcode() instanceof Opcode.Binary(var op) && op == BinaryOp.ADD && (add.operand(0) == iv || add.operand(1) == iv))) {
            return java.util.Optional.of(error(header, "the @Parallel index must advance as i = i + step"));
        }
        var step = add.operand(0) == iv ? add.operand(1) : add.operand(0);
        if (!isInvariant(step, loop)) {
            return java.util.Optional.of(error(header, "the step of a @Parallel loop must not change inside the loop"));
        }
        var d = DIMENSIONS[dim];
        recordDomain(header, iv, argumentOnEdge(entry, header, ivIndex), step, dim);

        var before = new IrBuilder(entry.block().orElseThrow()).before(entry).at(entry.loc());
        var gid = before.intrinsic("tornado.thread_id.global." + d, Type.I32, List.of());
        var init = before.add(argumentOnEdge(entry, header, ivIndex), scaled(before, gid, step));
        setArgumentOnEdge(entry, header, ivIndex, init);

        var inLatch = new IrBuilder(latch.block().orElseThrow()).before(latch).at(latch.loc());
        var size = inLatch.intrinsic("tornado.global_size." + d, Type.I32, List.of());
        var advanced = inLatch.add(iv, scaled(inLatch, size, step));
        advanced.setName(add.name());
        setArgumentOnEdge(latch, header, ivIndex, advanced);
        if (!add.hasUses()) {
            add.erase();
        }
        header.addAttribute(new Attribute("grid", "dim=" + dim));
        return java.util.Optional.empty();
    }

    /** A parallel dimension with constant bounds: {@code for (i = offset; i < upper; i += step)}. */
    public record Domain(int dim, long offset, long step, long upper) {
    }

    /**
     * The launch domain of the lowered loops (optimisation list item T3), read back from the function
     * attributes {@code domain(dim,offset,step,upper)}; empty when a bound is not constant.
     */
    public static List<Domain> domain(Function function) {
        var result = new ArrayList<Domain>();
        for (var attribute : function.attributes()) {
            if (attribute.name().equals("domain")) {
                var v = attribute.args().split(",");
                result.add(new Domain(Integer.parseInt(v[0].trim()), Long.parseLong(v[1].trim()), Long.parseLong(v[2].trim()), Long.parseLong(v[3].trim())));
            }
        }
        result.sort(Comparator.comparingInt(Domain::dim));
        return result;
    }

    /** Records offset, step and exclusive upper bound when all three are constants and the test is {@code i < upper} or {@code i <= last}. */
    private static void recordDomain(Block header, BlockArg iv, Value init, Value step, int dim) {
        var offset = constant(init);
        var stride = constant(step);
        var term = header.terminator().orElse(null);
        if (offset.isEmpty() || stride.isEmpty() || term == null || !(term.opcode() instanceof Opcode.CondBranch) || !(term.operand(0) instanceof Inst cmp)) {
            return;
        }
        // javac tests the exit condition: `i >= upper` jumps out (`i < upper` stays in); for `i <= last`
        // it is `i > last`, an exclusive upper bound of last + 1.
        if (cmp.opcode() instanceof Opcode.ICmp(var p) && cmp.operand(0) == iv) {
            long inclusive = switch (p) {
                case SGE, SLT -> 0;
                case SGT, SLE -> 1;
                default -> -1;
            };
            if (inclusive >= 0) {
                constant(cmp.operand(1)).ifPresent(upper -> header.function().addAttribute(new Attribute("domain", dim + "," + offset.get() + "," + stride.get() + "," + (upper + inclusive))));
            }
        }
    }

    private static java.util.Optional<Long> constant(Value v) {
        return v instanceof Inst i && i.opcode() instanceof Opcode.ConstInt(var c) ? java.util.Optional.of(c) : java.util.Optional.empty();
    }

    private static Value scaled(IrBuilder b, Value v, Value step) {
        if (step instanceof Inst c && c.opcode() instanceof Opcode.ConstInt(var k)) {
            // A fresh constant at the use: the original may live in the loop latch.
            return k == 1 ? v : b.binary(BinaryOp.MUL, v, b.constInt(step.type(), k));
        }
        return b.binary(BinaryOp.MUL, v, step);
    }

    private static boolean isInvariant(Value v, LoopForest.Loop loop) {
        return switch (v) {
            case Inst i -> i.opcode() instanceof Opcode.ConstInt || i.block().map(b -> !loop.contains(b)).orElse(false);
            case BlockArg a -> !loop.contains(a.block());
        };
    }

    private static Value argumentOnEdge(Inst terminator, Block target, int index) {
        for (var s : terminator.successors()) {
            if (s.target() == target) {
                return s.args().get(index);
            }
        }
        throw new IllegalStateException("no edge to " + target);
    }

    private static void setArgumentOnEdge(Inst terminator, Block target, int index, Value value) {
        for (int i = 0; i < terminator.successors().size(); i++) {
            var s = terminator.successors().get(i);
            if (s.target() == target) {
                var args = new ArrayList<>(s.args());
                args.set(index, value);
                terminator.setSuccessorArgs(i, args);
            }
        }
    }

    private static Diagnostic error(Block header, String message) {
        return new Diagnostic("TC-E3201", message, "^" + header.label() + " in @" + header.function().name(), Loc.UNKNOWN);
    }
}
