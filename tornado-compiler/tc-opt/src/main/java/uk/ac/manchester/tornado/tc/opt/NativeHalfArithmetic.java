/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.tc.opt;

import java.util.List;

import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Inst;
import uk.ac.manchester.tornado.tc.ir.IrBuilder;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Opcode.BinaryOp;
import uk.ac.manchester.tornado.tc.ir.Opcode.ConvertOp;
import uk.ac.manchester.tornado.tc.ir.Type;
import uk.ac.manchester.tornado.tc.ir.Value;

/**
 * Half-precision arithmetic in half. Java's HalfFloat (and HAT's F16) computes {@code a + b} as
 * {@code (half) ((float) a + (float) b)}: widen, compute, round. For +, - and * of two halves that
 * double rounding is innocuous (float has 24 bits, at least 2 * 11 + 2: Figueroa, "When is double
 * rounding innocuous?", 1995), so a correctly rounded half operation gives the same bits. This pass
 * rewrites {@code ftrunc.f16(fop(fext a, fext b))} into {@code fop a, b : f16}, for targets with half
 * arithmetic. The emitters must not let the device compiler contract the result into an fma (CUDA:
 * the {@code _rn} intrinsics; OpenCL: FP_CONTRACT OFF). Division keeps the float path: native half
 * division is not documented as correctly rounded on every target.
 */
public final class NativeHalfArithmetic implements Pass {

    @Override
    public String name() {
        return "native-half";
    }

    /** Whether {@code function} rounds a float sum, difference or product of two halves to half. */
    public static boolean applies(Function function) {
        return function.blocks().stream().flatMap(b -> b.insts().stream()).anyMatch(i -> match(i) != null);
    }

    @Override
    public boolean run(Function function) {
        boolean changed = false;
        for (var block : function.blocks()) {
            for (var inst : List.copyOf(block.insts())) {
                var op = match(inst);
                if (op == null) {
                    continue;
                }
                var arithmetic = (Inst) inst.operand(0);
                var a = halfSource(arithmetic.operand(0));
                var b = halfSource(arithmetic.operand(1));
                var half = IrBuilder.insertingBefore(inst).at(arithmetic.loc()).binary(op, a, b);
                half.setName(inst.name());
                inst.replaceAllUsesWith(half);
                inst.erase();
                eraseIfDead(arithmetic);
                changed = true;
            }
        }
        // Widenings whose only users were rewritten.
        for (var block : function.blocks()) {
            for (var inst : List.copyOf(block.insts())) {
                if (isWidening(inst)) {
                    eraseIfDead(inst);
                }
            }
        }
        return changed;
    }

    /** The half operation for {@code ftrunc.f16(fop(fext a, fext b))}, or null. */
    private static BinaryOp match(Inst inst) {
        if (!(inst.opcode() instanceof Opcode.Convert(var conv)) || conv != ConvertOp.FTRUNC || !inst.type().equals(Type.F16)) {
            return null;
        }
        if (!(inst.operand(0) instanceof Inst arithmetic) || !(arithmetic.opcode() instanceof Opcode.Binary(var op)) || !arithmetic.type().equals(Type.F32)
                || arithmetic.users().size() != 1) {
            return null;
        }
        if (op != BinaryOp.FADD && op != BinaryOp.FSUB && op != BinaryOp.FMUL) {
            return null;
        }
        return halfSource(arithmetic.operand(0)) != null && halfSource(arithmetic.operand(1)) != null ? op : null;
    }

    /** The half a float operand was widened from, or null. */
    private static Value halfSource(Value value) {
        return value instanceof Inst inst && isWidening(inst) ? inst.operand(0) : null;
    }

    private static boolean isWidening(Inst inst) {
        return inst.opcode() instanceof Opcode.Convert(var conv) && conv == ConvertOp.FEXT && inst.type().equals(Type.F32) && inst.operand(0).type().equals(Type.F16);
    }

    private static void eraseIfDead(Inst inst) {
        if (inst.users().isEmpty()) {
            inst.erase();
        }
    }
}
