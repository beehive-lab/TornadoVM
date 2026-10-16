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
import java.util.List;
import java.util.Map;

import uk.ac.manchester.tornado.tc.ir.Attribute;
import uk.ac.manchester.tornado.tc.ir.Block;
import uk.ac.manchester.tornado.tc.ir.Diagnostic;
import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Immediates;
import uk.ac.manchester.tornado.tc.ir.Inst;
import uk.ac.manchester.tornado.tc.ir.IrBuilder;
import uk.ac.manchester.tornado.tc.ir.Loc;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Opcode.BinaryOp;
import uk.ac.manchester.tornado.tc.ir.Opcode.ConvertOp;
import uk.ac.manchester.tornado.tc.ir.Opcode.IntPredicate;
import uk.ac.manchester.tornado.tc.ir.Type;
import uk.ac.manchester.tornado.tc.ir.Value;
import uk.ac.manchester.tornado.tc.ir.analysis.Analyses;
import uk.ac.manchester.tornado.tc.ir.analysis.Constants;
import uk.ac.manchester.tornado.tc.ir.analysis.CountedLoop;
import uk.ac.manchester.tornado.tc.ir.analysis.LoopForest;
import uk.ac.manchester.tornado.tc.ir.analysis.LoopShape;

/**
 * Lowers {@code @Parallel} loops for GPU targets: every loop whose header
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


    private ParallelLoopLowering() {
    }

    public static List<Diagnostic> run(Function function) {
        var diagnostics = new ArrayList<Diagnostic>();
        var forest = Analyses.loops(function);
        record Parallel(LoopForest.Loop loop, int iv) {
        }
        var parallel = new ArrayList<Parallel>();
        for (var block : function.blocks()) {
            for (var attribute : block.attributes()) {
                if (attribute.name().equals("parallel")) {
                    var loop = forest.loopWithHeader(block);
                    if (loop.isEmpty()) {
                        diagnostics.add(error(block, "block marked parallel is not a loop header"));
                    } else {
                        parallel.add(new Parallel(loop.get(), attribute.integer("iv")));
                    }
                }
            }
        }
        // A loop's dimension is the longest chain of @Parallel loops nested inside it: the innermost
        // is x. Sibling loops share a dimension (each grid-strides over its own bound); giving them
        // different dimensions would run each body once per thread of the other.
        var dims = new java.util.IdentityHashMap<LoopForest.Loop, Integer>();
        for (var p : parallel) {
            dimension(p.loop(), parallel.stream().map(Parallel::loop).toList(), dims);
        }
        int nesting = dims.values().stream().mapToInt(d -> d + 1).max().orElse(0);
        if (nesting > 3) {
            diagnostics.add(new Diagnostic("TC-E3202", "@Parallel loops nested " + nesting + " deep (at most three: x, y, z)", "@" + function.name(), Loc.UNKNOWN));
            return diagnostics;
        }
        for (var p : parallel) {
            lower(p.loop(), p.iv(), dims.get(p.loop())).ifPresent(diagnostics::add);
        }
        return diagnostics;
    }

    /** 0 for a @Parallel loop with no @Parallel loop inside it, else one more than the deepest inside. */
    private static int dimension(LoopForest.Loop loop, List<LoopForest.Loop> parallel, Map<LoopForest.Loop, Integer> dims) {
        var known = dims.get(loop);
        if (known != null) {
            return known;
        }
        int d = 0;
        for (var other : parallel) {
            if (other != loop && loop.contains(other.header())) {
                d = Math.max(d, 1 + dimension(other, parallel, dims));
            }
        }
        dims.put(loop, d);
        return d;
    }

    private static java.util.Optional<Diagnostic> lower(LoopForest.Loop loop, int ivIndex, int dim) {
        var header = loop.header();
        var shape = CountedLoop.analyse(loop, ivIndex);
        if (shape instanceof LoopShape.NotCounted(var failure)) {
            return java.util.Optional.of(error(header, switch (failure) {
                case EDGES -> "a @Parallel loop needs one entry edge and one back edge";
                case INCREMENT -> "the @Parallel index must advance as i = i + step";
                case STEP -> "the step of a @Parallel loop must not change inside the loop";
            }));
        }
        var counted = (CountedLoop) shape;
        var iv = counted.iv();
        var step = counted.step();
        recordDomain(counted, dim);

        var entry = counted.entry().terminator();
        var before = IrBuilder.insertingBefore(entry);
        var gid = before.intrinsic("tornado.thread_id.global", Immediates.of("dim", dim), Type.I32, List.of());
        counted.entry().setArgument(ivIndex, gridStride(before, counted.init(), gid, step));

        var latch = counted.backEdge().terminator();
        var inLatch = IrBuilder.insertingBefore(latch);
        var size = inLatch.intrinsic("tornado.global_size", Immediates.of("dim", dim), Type.I32, List.of());
        var advanced = gridStride(inLatch, iv, size, step);
        var add = counted.increment();
        advanced.setName(add.name());
        counted.backEdge().setArgument(ivIndex, advanced);
        if (!add.hasUses()) {
            add.erase();
        }
        header.addAttribute(Attribute.of("grid", "dim", dim));
        return java.util.Optional.empty();
    }

    /** A parallel dimension with constant bounds: {@code for (i = offset; i < upper; i += step)}. */
    public record Domain(int dim, long offset, long step, long upper) {
    }

    /**
     * The launch domain of the lowered loops, read back from the function
     * attributes {@code domain(dim,offset,step,upper)}; empty when a bound is not constant.
     */
    public static List<Domain> domain(Function function) {
        var result = new ArrayList<Domain>();
        for (var attribute : function.attributes()) {
            if (attribute.name().equals("domain")) {
                result.add(new Domain(attribute.integer("dim"), attribute.longValue("offset"), attribute.longValue("step"), attribute.longValue("upper")));
            }
        }
        // Sibling loops share a dimension: the launch covers the widest of them (each loop
        // grid-strides, so any thread count is correct; the widest avoids idle loops).
        var widest = new java.util.TreeMap<Integer, Domain>();
        for (var d : result) {
            widest.merge(d.dim(), d, (a, b) -> threads(b) > threads(a) ? b : a);
        }
        return List.copyOf(widest.values());
    }

    private static long threads(Domain d) {
        return d.step() == 0 ? 0 : (d.upper() - d.offset() + d.step() - 1) / d.step();
    }

    /** Records offset, step and exclusive upper bound when all three are constants. */
    private static void recordDomain(CountedLoop loop, int dim) {
        var offset = loop.constantInit();
        var stride = loop.constantStep();
        var upper = loop.exclusiveUpper();
        if (offset.isPresent() && stride.isPresent() && upper.isPresent()) {
            loop.header().function().addAttribute(Attribute.of("domain", "dim", dim, "offset", offset.getAsLong(), "step", stride.getAsLong(), "upper", upper.getAsLong()));
        }
    }

    /**
     * {@code base + count * step}, the grid-stride arithmetic. For an int index it is evaluated in 64
     * bits and clamped to the int range: with more than 2^30 iterations at step 1, thread {@code g}
     * computes {@code g + gsize > Integer.MAX_VALUE}, and a wrapped (negative) index would pass
     * {@code i < n} and run iterations a second time. A clamped index is past every int bound in the
     * direction of the step, so the thread leaves the loop. The 64-bit arithmetic cannot overflow
     * ({@code |base| <= 2^31}, {@code |count * step| <= 2^62}).
     */
    static Inst gridStride(IrBuilder b, Value base, Value count, Value step) {
        var k = Constants.integer(step);
        var c = Constants.integer(base);
        if (!base.type().equals(Type.I32) || c.isPresent() && k.isPresent() && fitsInt(c.getAsLong(), k.getAsLong())) {
            // A constant start and step with count in [0, Integer.MAX_VALUE] (a thread id or the global size):
            // the int arithmetic cannot overflow.
            return noSignedWrap(b.add(base, scaled(b, count, step)));
        }
        Value product = b.convert(ConvertOp.SEXT, count, Type.I64);
        if (k.isEmpty() || k.getAsLong() != 1) {
            Value wideStep = k.isPresent() ? b.constInt(Type.I64, k.getAsLong()) : b.convert(ConvertOp.SEXT, step, Type.I64);
            product = noSignedWrap(b.binary(BinaryOp.MUL, product, wideStep));
        }
        Value wide = noSignedWrap(b.add(b.convert(ConvertOp.SEXT, base, Type.I64), product));
        if (k.isEmpty() || k.getAsLong() > 0) {
            var max = b.constInt(Type.I64, Integer.MAX_VALUE);
            wide = b.select(b.icmp(IntPredicate.SGT, wide, max), max, wide);
        }
        if (k.isEmpty() || k.getAsLong() < 0) {
            var min = b.constInt(Type.I64, Integer.MIN_VALUE);
            wide = b.select(b.icmp(IntPredicate.SLT, wide, min), min, wide);
        }
        return b.convert(ConvertOp.TRUNC, wide, Type.I32);
    }

    /** Whether {@code base + count * step} stays in the int range for every count in [0, Integer.MAX_VALUE]. */
    private static boolean fitsInt(long base, long step) {
        // base and step are int constants, so the long arithmetic is exact.
        long far = base + (long) Integer.MAX_VALUE * step;
        return Math.min(base, far) >= Integer.MIN_VALUE && Math.max(base, far) <= Integer.MAX_VALUE;
    }

    private static Value scaled(IrBuilder b, Value v, Value step) {
        if (step instanceof Inst c && c.opcode() instanceof Opcode.ConstInt(var k)) {
            // A fresh constant at the use: the original may live in the loop latch.
            return k == 1 ? v : noSignedWrap(b.binary(BinaryOp.MUL, v, b.constInt(step.type(), k)));
        }
        return noSignedWrap(b.binary(BinaryOp.MUL, v, step));
    }

    /** Marks arithmetic TC generated and knows cannot overflow: the emitter prints it as plain C. */
    static Inst noSignedWrap(Inst inst) {
        inst.addAttribute(Attribute.of("nsw"));
        return inst;
    }

    private static Diagnostic error(Block header, String message) {
        return new Diagnostic("TC-E3201", message, "^" + header.label() + " in @" + header.function().name(), Loc.UNKNOWN);
    }
}
