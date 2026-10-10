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

import java.util.List;

import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Inst;
import uk.ac.manchester.tornado.tc.ir.IrBuilder;
import uk.ac.manchester.tornado.tc.ir.Opcode;

/**
 * Math optimisations that trade exact IEEE results for speed (optimisation list item O12), applied
 * only when {@code tornado.enable.mathOptimizations} is on, as on the Graal path:
 * {@code 1 / sqrt(x)} becomes {@code rsqrt(x)} (a single approximate instruction on the GPU instead
 * of an IEEE square root followed by an IEEE division).
 */
public final class MathPeepholes implements Pass {

    @Override
    public String name() {
        return "math-peepholes";
    }

    @Override
    public boolean run(Function function) {
        boolean changed = false;
        for (var block : function.blocks()) {
            for (var inst : List.copyOf(block.insts())) {
                if (inst.block().isPresent() && inst.opcode() instanceof Opcode.Binary(var op) && op == Opcode.BinaryOp.FDIV
                        && inst.operand(0) instanceof Inst one && one.opcode() instanceof Opcode.ConstFloat(var v) && v == 1.0
                        && inst.operand(1) instanceof Inst sqrt && sqrt.opcode() instanceof Opcode.Intrinsic(var name) && name.equals("math.sqrt")) {
                    var rsqrt = new IrBuilder(block).before(inst).at(inst.loc()).intrinsic("math.rsqrt", inst.type(), List.of(sqrt.operand(0)));
                    rsqrt.setName(inst.name());
                    inst.replaceAllUsesWith(rsqrt);
                    inst.erase();
                    if (!sqrt.hasUses()) {
                        sqrt.erase();
                    }
                    changed = true;
                }
            }
        }
        return changed;
    }
}
