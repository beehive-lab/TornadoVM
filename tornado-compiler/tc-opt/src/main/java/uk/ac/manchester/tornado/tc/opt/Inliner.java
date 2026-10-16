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
package uk.ac.manchester.tornado.tc.opt;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import uk.ac.manchester.tornado.tc.ir.Block;
import uk.ac.manchester.tornado.tc.ir.Diagnostic;
import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Immediates;
import uk.ac.manchester.tornado.tc.ir.Inst;
import uk.ac.manchester.tornado.tc.ir.IrBuilder;
import uk.ac.manchester.tornado.tc.ir.IrModule;
import uk.ac.manchester.tornado.tc.ir.Loc;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Type;
import uk.ac.manchester.tornado.tc.ir.Value;

/**
 * Inlines every call in a kernel: GPU kernels are compiled as one
 * function, as on the Graal path. At a call, the block is split; the callee's blocks are cloned into
 * the caller with its parameters replaced by the call's operands; each {@code ret v} becomes a branch
 * to the continuation block, whose argument replaces the call's result. Repeats until no call is left.
 *
 * <p>A helper that takes a tensor-core accumulator fragment as {@code float[]} (jitLLM's
 * {@code addTile(X, acc, ...)}) reads it with array loads; once inlined, their array is the
 * caller's fragment, a register vector. Each becomes the lane read the frontend emits for
 * {@code frag[i]} in the kernel itself.</p>
 *
 * <p>Error codes: {@code TC-E3301} callee not in the module, {@code TC-E1009} recursion,
 * {@code TC-E1010} a fragment passed to a helper and read at a non-constant index or written.</p>
 */
public final class Inliner {

    private static final int MAX_INLINES = 1024;

    private Inliner() {
    }

    public static List<Diagnostic> run(IrModule module, Function kernel) {
        int inlined = 0;
        while (true) {
            Inst call = null;
            for (var block : kernel.blocks()) {
                for (var inst : block.insts()) {
                    if (inst.opcode() instanceof Opcode.Call) {
                        call = inst;
                        break;
                    }
                }
                if (call != null) {
                    break;
                }
            }
            if (call == null) {
                return List.of();
            }
            var name = ((Opcode.Call) call.opcode()).callee();
            var callee = module.find(name);
            if (callee.isEmpty()) {
                return List.of(new Diagnostic("TC-E3301", "callee @" + name + " is not available to inline", "", call.loc()));
            }
            if (++inlined > MAX_INLINES) {
                return List.of(new Diagnostic("TC-E1009", "recursion: inlining @" + name + " does not terminate (kernels cannot recurse)", "", call.loc()));
            }
            var problems = inline(kernel, call, callee.get());
            if (!problems.isEmpty()) {
                return problems;
            }
        }
    }

    private static List<Diagnostic> inline(Function caller, Inst call, Function callee) {
        var block = call.block().orElseThrow();
        var prefix = "inl" + caller.blocks().size() + "." + shortName(callee.name()) + ".";
        var continuation = caller.newBlock(prefix + "ret");
        if (call.producesValue()) {
            var result = continuation.addArg(call.type(), call.name());
            call.replaceAllUsesWith(result);
        }
        var insts = block.insts();
        var after = new ArrayList<>(insts.subList(insts.indexOf(call) + 1, insts.size()));
        for (var inst : after) {
            inst.moveToEnd(continuation);
        }

        Map<Value, Value> values = new IdentityHashMap<>();
        Map<Block, Block> blocks = new IdentityHashMap<>();
        for (int i = 0; i < callee.params().size(); i++) {
            values.put(callee.params().get(i), call.operand(i));
        }
        // Pass 1: blocks, block arguments and instruction shells (operands may refer forward).
        Map<Inst, Inst> clones = new IdentityHashMap<>();
        for (var original : callee.blocks()) {
            var copy = caller.newBlock(prefix + original.label());
            blocks.put(original, copy);
            original.attributes().forEach(copy::addAttribute);
            if (original != callee.entry()) {
                for (var arg : original.args()) {
                    values.put(arg, copy.addArg(arg.type(), arg.name()));
                }
            }
            for (var inst : original.insts()) {
                // A return becomes the branch to the continuation; everything else is copied as it is.
                var clone = inst.opcode() instanceof Opcode.Return ? new Inst(new Opcode.Branch(), Type.VOID) : inst.copyShell();
                clone.setLoc(inst.loc().isKnown() ? inst.loc() : call.loc());
                copy.append(clone);
                clones.put(inst, clone);
                values.put(inst, clone);
            }
        }
        // Pass 2: operands and successors.
        for (var entry : clones.entrySet()) {
            var inst = entry.getKey();
            var clone = entry.getValue();
            if (inst.opcode() instanceof Opcode.Return) {
                clone.addSuccessor(continuation, inst.operands().stream().map(values::get).toList());
                continue;
            }
            for (var operand : inst.operands()) {
                clone.addOperand(values.get(operand));
            }
            for (var s : inst.successors()) {
                clone.addSuccessor(blocks.get(s.target()), s.args().stream().map(values::get).toList());
            }
        }
        call.erase();
        new IrBuilder(block).at(Loc.UNKNOWN).br(blocks.get(callee.entry()), List.of());
        return fragmentAccesses(List.copyOf(clones.values()));
    }

    /** The inlined array accesses whose array is now a fragment (a vector): loads become lane reads. */
    private static List<Diagnostic> fragmentAccesses(List<Inst> clones) {
        var problems = new ArrayList<Diagnostic>();
        for (var inst : clones) {
            boolean load = inst.opcode() instanceof Opcode.ArrayLoad;
            boolean store = inst.opcode() instanceof Opcode.ArrayStore;
            if (!(load || store) || !(inst.operand(0).type() instanceof Type.VectorType(var element, var length))) {
                continue;
            }
            if (store) {
                problems.add(new Diagnostic("TC-E1010", "an MMA fragment passed to a helper cannot be written there; return the new fragment from the helper instead", "",
                        inst.loc()));
            } else if (!(inst.operand(1) instanceof Inst c && c.opcode() instanceof Opcode.ConstInt(var lane)) || lane < 0 || lane >= length) {
                problems.add(new Diagnostic("TC-E1010", "fragment elements can only be read at a constant index within the fragment (also in a helper it is passed to)", "",
                        inst.loc()));
            } else {
                var read = IrBuilder.insertingBefore(inst).at(inst.loc()).intrinsic("tornado.vector.lane", Immediates.of("lane", lane), element, List.of(inst.operand(0)));
                inst.replaceAllUsesWith(read);
                inst.erase();
            }
        }
        return problems;
    }

    private static String shortName(String name) {
        var dot = name.lastIndexOf('.');
        var base = dot >= 0 ? name.substring(dot + 1) : name;
        var underscore = base.lastIndexOf('_');
        return underscore > 0 ? base.substring(0, underscore) : base;
    }
}
