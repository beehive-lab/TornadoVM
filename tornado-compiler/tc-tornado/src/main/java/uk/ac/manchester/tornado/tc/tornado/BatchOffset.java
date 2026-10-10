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
 * Batched execution: each device buffer holds one chunk, so memory is indexed chunk-locally, but a
 * kernel that uses its {@code @Parallel} index as a value ({@code data[i] = i}) must see the global
 * index. Every use of a parallel index that is not (only) an array index becomes {@code i + offset}.
 */
public final class BatchOffset {

    private BatchOffset() {
    }

    public static void run(Function function, long offset) {
        if (offset == 0) {
            return;
        }
        for (var header : function.blocks()) {
            if (!header.hasAttribute("parallel")) {
                continue;
            }
            for (var a : header.attributes()) {
                if (!a.name().equals("parallel")) {
                    continue;
                }
                var iv = header.args().get(Integer.parseInt(a.args().substring("iv=".length())));
                Value global = null;
                for (var user : List.copyOf(iv.users())) {
                    if (feedsOnlyIndices(user, iv, 0) || isLoopControl(user, header)) {
                        continue;
                    }
                    if (global == null) {
                        var first = header.insts().get(0);
                        var b = new IrBuilder(header).before(first).at(first.loc());
                        global = b.binary(Opcode.BinaryOp.ADD, iv, b.constInt(iv.type(), offset));
                        global.setName(iv.name() + "_global");
                    }
                    for (int k = 0; k < user.operandCount(); k++) {
                        if (user.operand(k) == iv) {
                            user.setOperand(k, global);
                        }
                    }
                }
            }
        }
    }

    /** The loop's own bound test (in the header) and increment (passed back to the header). */
    private static boolean isLoopControl(Inst user, uk.ac.manchester.tornado.tc.ir.Block header) {
        if (user.opcode() instanceof Opcode.ICmp) {
            return user.block().map(b -> b == header).orElse(false);
        }
        return user.opcode() instanceof Opcode.Binary(var op) && op == Opcode.BinaryOp.ADD
                && user.users().stream().anyMatch(t -> t.isTerminator() && t.successors().stream().anyMatch(s -> s.target() == header));
    }

    /** Whether {@code value} only ends up as an array index (directly or through integer arithmetic). */
    private static boolean feedsOnlyIndices(Inst use, Value value, int depth) {
        if (depth > 6) {
            return false;
        }
        return switch (use.opcode()) {
            case Opcode.ArrayLoad l -> use.operand(1) == value && use.operand(0) != value;
            case Opcode.ArrayStore s -> use.operand(1) == value && use.operand(0) != value && use.operand(2) != value;
            case Opcode.Binary(var op) when use.type().isInteger() && !use.users().isEmpty() && (op == Opcode.BinaryOp.ADD || op == Opcode.BinaryOp.MUL || op == Opcode.BinaryOp.SUB) ->
                use.users().stream().allMatch(u -> feedsOnlyIndices(u, use, depth + 1));
            case Opcode.Convert c when use.type().isInteger() -> use.users().stream().allMatch(u -> feedsOnlyIndices(u, use, depth + 1));
            default -> false;
        };
    }
}
