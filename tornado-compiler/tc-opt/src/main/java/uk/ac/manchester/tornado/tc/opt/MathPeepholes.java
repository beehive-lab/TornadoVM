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

/**
 * Math optimisations that trade exact IEEE results for speed, applied
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
                    var rsqrt = IrBuilder.insertingBefore(inst).intrinsic("math.rsqrt", inst.type(), List.of(sqrt.operand(0)));
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
