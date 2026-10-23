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

import java.util.LinkedHashSet;
import java.util.List;

import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Inst;
import uk.ac.manchester.tornado.tc.ir.IrBuilder;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Type;

/**
 * Fused multiply-adds for a target whose compiler contracts {@code a*b+c} by default (NVRTC), where
 * fusion is allowed ({@code tornado.enable.fma}, and not a strict-floating-point plan). A product shared by several adds and subtracts stays a separate multiply in the
 * device code, because the driver's own CSE merges the copies before it contracts: in a Julia-set
 * loop, {@code zx*zx} feeds both {@code zx*zx + zy*zy} and {@code zx*zx - zy*zy}, and NVRTC then keeps
 * one multiply and two plain adds. So the pass recomputes such a product at each use, and turns each
 * {@code a*b + c} into an explicit {@code fma(a, b, c)}, which the driver cannot merge with anything;
 * the remaining {@code a*b - c} have a product of their own and are contracted by the driver.
 *
 * <p>Run it last, after GVN, which would merge the copies again.</p>
 */
public final class FmaRematerialization implements Pass {

    @Override
    public String name() {
        return "fma-remat";
    }

    /** Whether {@code function} has a product this pass would recompute. */
    public static boolean applies(Function function) {
        return function.blocks().stream().flatMap(b -> b.insts().stream())
                .anyMatch(i -> isShared(i) || i.opcode() instanceof Opcode.Binary(var op) && op == Opcode.BinaryOp.FMUL && i.users().stream().anyMatch(u -> u.opcode() instanceof Opcode.Binary(var a) && a == Opcode.BinaryOp.FADD));
    }

    @Override
    public boolean run(Function function) {
        boolean changed = false;
        for (var block : function.blocks()) {
            for (var inst : List.copyOf(block.insts())) {
                if (!isShared(inst)) {
                    continue;
                }
                var users = new LinkedHashSet<>(inst.users());
                boolean first = true;
                for (var user : users) {
                    if (first) {
                        first = false;
                        continue;
                    }
                    var copy = IrBuilder.insertingBefore(user).at(inst.loc()).binary(Opcode.BinaryOp.FMUL, inst.operand(0), inst.operand(1));
                    for (int k = 0; k < user.operandCount(); k++) {
                        if (user.operand(k) == inst) {
                            user.setOperand(k, copy);
                        }
                    }
                    changed = true;
                }
            }
        }
        for (var block : function.blocks()) {
            for (var inst : List.copyOf(block.insts())) {
                if (!(inst.opcode() instanceof Opcode.Binary(var op)) || op != Opcode.BinaryOp.FADD) {
                    continue;
                }
                for (int k = 0; k < 2; k++) {
                    if (inst.operand(k) instanceof Inst product && product.opcode() instanceof Opcode.Binary(var mul) && mul == Opcode.BinaryOp.FMUL
                            && product.users().size() == 1 && product.type().equals(inst.type()) && (inst.type().equals(Type.F32) || inst.type().equals(Type.F64))) {
                        var fma = IrBuilder.insertingBefore(inst).at(inst.loc()).intrinsic("math.fma", inst.type(), List.of(product.operand(0), product.operand(1), inst.operand(1 - k)));
                        fma.setName(inst.name());
                        inst.replaceAllUsesWith(fma);
                        inst.erase();
                        product.erase();
                        changed = true;
                        break;
                    }
                }
            }
        }
        return changed;
    }

    /** A float multiply with two or more users, all of them float adds or subtracts. */
    private static boolean isShared(Inst inst) {
        if (!(inst.opcode() instanceof Opcode.Binary(var op)) || op != Opcode.BinaryOp.FMUL) {
            return false;
        }
        var users = new LinkedHashSet<>(inst.users());
        return users.size() >= 2 && users.stream().allMatch(FmaRematerialization::isAddOrSubtract);
    }

    private static boolean isAddOrSubtract(Inst user) {
        return user.opcode() instanceof Opcode.Binary(var op) && (op == Opcode.BinaryOp.FADD || op == Opcode.BinaryOp.FSUB);
    }
}
