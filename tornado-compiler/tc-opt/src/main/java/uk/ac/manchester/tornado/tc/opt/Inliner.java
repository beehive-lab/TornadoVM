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
 * <p>Error codes: {@code TC-E3301} callee not in the module, {@code TC-E1009} recursion.</p>
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
            inline(kernel, call, callee.get());
        }
    }

    private static void inline(Function caller, Inst call, Function callee) {
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
    }

    private static String shortName(String name) {
        var dot = name.lastIndexOf('.');
        var base = dot >= 0 ? name.substring(dot + 1) : name;
        var underscore = base.lastIndexOf('_');
        return underscore > 0 ? base.substring(0, underscore) : base;
    }
}
