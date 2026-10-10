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
import java.util.List;

import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Inst;
import uk.ac.manchester.tornado.tc.ir.Opcode;

/**
 * Dead-code elimination (optimisation list item O5): removes instructions whose result is unused and
 * that have no side effects (pure operations, loads, array lengths, allocations), and block arguments
 * that no instruction reads, together with the values passed for them.
 */
public final class DeadCodeElimination implements Pass {

    @Override
    public String name() {
        return "dce";
    }

    @Override
    public boolean run(Function function) {
        boolean changed = false;
        boolean progress = true;
        while (progress) {
            progress = false;
            for (var block : function.blocks()) {
                for (var inst : List.copyOf(block.insts())) {
                    if (inst.producesValue() && !inst.hasUses() && removable(inst)) {
                        inst.erase();
                        progress = true;
                    }
                }
            }
            progress |= removeDeadArguments(function);
            changed |= progress;
        }
        return changed;
    }

    static boolean removable(Inst inst) {
        return switch (inst.opcode()) {
            case Opcode.Load l -> true;
            case Opcode.ArrayLoad l -> true;
            case Opcode.FieldLoad l -> true;
            case Opcode.Alloca a -> true;
            // An intrinsic that only reads memory (no write, side effect or convergence) goes like a load.
            case Opcode.Intrinsic(var name) -> uk.ac.manchester.tornado.tc.ir.IntrinsicRegistry.lookup(name)
                    .map(d -> d.effects().stream().allMatch(e -> e == uk.ac.manchester.tornado.tc.ir.IntrinsicRegistry.Effect.READS)).orElse(false);
            default -> inst.opcode().isPure();
        };
    }

    /** Removes unused arguments of non-entry blocks and the matching values on every incoming edge. */
    private static boolean removeDeadArguments(Function function) {
        boolean changed = false;
        for (var block : function.blocks()) {
            if (block == function.entry()) {
                continue;
            }
            for (int index = block.args().size() - 1; index >= 0; index--) {
                var arg = block.args().get(index);
                final int position = index;
                // An argument only fed back into itself on its own edges is dead too.
                if (arg.users().stream().anyMatch(u -> !passesOnlyToItself(u, arg, position))) {
                    continue;
                }
                for (var pred : function.blocks()) {
                    var term = pred.terminator().orElse(null);
                    if (term == null) {
                        continue;
                    }
                    for (int i = 0; i < term.successors().size(); i++) {
                        if (term.successors().get(i).target() == block) {
                            var args = new ArrayList<>(term.successors().get(i).args());
                            args.remove(index);
                            term.setSuccessorArgs(i, args);
                        }
                    }
                }
                block.removeArg(arg);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean passesOnlyToItself(Inst user, uk.ac.manchester.tornado.tc.ir.BlockArg arg, int index) {
        if (!user.operands().isEmpty() && user.operands().contains(arg)) {
            return false;
        }
        for (var s : user.successors()) {
            for (int i = 0; i < s.args().size(); i++) {
                if (s.args().get(i) == arg && !(s.target() == arg.block() && i == index)) {
                    return false;
                }
            }
        }
        return true;
    }
}
