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

import java.util.List;

import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Inst;
import uk.ac.manchester.tornado.tc.ir.IrBuilder;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Type;
import uk.ac.manchester.tornado.tc.ir.Value;

/**
 * Batched execution: each device buffer holds one chunk of every array, starting at element
 * {@code offset}. In the Java program the {@code @Parallel} index is global, so: every use of the
 * index (other than the loop's own bound test and increment, which count over the chunk) becomes
 * {@code i + offset}, and every array index that depends on it is made chunk-local again by
 * subtracting {@code offset}. An index that is exactly the parallel index reads the chunk-local
 * {@code i} directly. Values and indices are handled per use, so an expression used as both
 * ({@code k = i + 1; a[k - 1] = k}) is right in both roles (design review TP-3).
 */
public final class BatchOffset {

    private BatchOffset() {
    }

    public static void run(Function function, long offset) {
        if (offset == 0) {
            return;
        }
        for (var header : function.blocks()) {
            for (var a : header.attributes()) {
                if (!a.name().equals("parallel")) {
                    continue;
                }
                var iv = header.args().get(a.integer("iv"));
                var first = header.insts().get(0);
                var b = new IrBuilder(header).before(first).at(first.loc());
                var global = b.binary(Opcode.BinaryOp.ADD, iv, b.constInt(iv.type(), offset));
                global.setName(iv.name() + "_global");
                for (var user : List.copyOf(iv.users())) {
                    if (user == global || isLoopControl(user, header)) {
                        continue;
                    }
                    for (int k = 0; k < user.operandCount(); k++) {
                        if (user.operand(k) == iv) {
                            user.setOperand(k, global);
                        }
                    }
                }
                localiseIndices(function, iv, global, offset);
                if (!global.hasUses()) {
                    global.erase();
                }
            }
        }
    }

    /** Array accesses whose index depends on the global index address the chunk: index - offset. */
    private static void localiseIndices(Function function, Value iv, Inst global, long offset) {
        for (var block : function.blocks()) {
            for (var inst : List.copyOf(block.insts())) {
                if (!(inst.opcode() instanceof Opcode.ArrayLoad || inst.opcode() instanceof Opcode.ArrayStore)) {
                    continue;
                }
                var index = inst.operand(1);
                if (index == global) {
                    inst.setOperand(1, iv);
                } else if (dependsOn(index, global, new java.util.HashSet<>())) {
                    var b = new IrBuilder(block).before(inst).at(inst.loc());
                    var local = b.binary(Opcode.BinaryOp.SUB, index, b.constInt(index.type(), offset));
                    inst.setOperand(1, local);
                }
            }
        }
    }

    private static boolean dependsOn(Value v, Value target, java.util.Set<Value> seen) {
        if (v == target) {
            return true;
        }
        if (!(v instanceof Inst inst) || !seen.add(v)) {
            return false;
        }
        for (var op : inst.operands()) {
            if (dependsOn(op, target, seen)) {
                return true;
            }
        }
        return false;
    }

    /** The loop's own bound test (in the header) and increment (passed back to the header). */
    private static boolean isLoopControl(Inst user, uk.ac.manchester.tornado.tc.ir.Block header) {
        if (user.opcode() instanceof Opcode.ICmp) {
            return user.block().map(b -> b == header).orElse(false);
        }
        return user.opcode() instanceof Opcode.Binary(var op) && op == Opcode.BinaryOp.ADD
                && user.users().stream().anyMatch(t -> t.isTerminator() && t.successors().stream().anyMatch(s -> s.target() == header));
    }
}
