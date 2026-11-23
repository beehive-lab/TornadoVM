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
package uk.ac.manchester.tornado.tc.tornado;

import java.util.ArrayList;
import java.util.List;

import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Immediates;
import uk.ac.manchester.tornado.tc.ir.IrBuilder;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Type;
import uk.ac.manchester.tornado.tc.ir.Value;

/**
 * {@code tornado.vector.load(data, byteOffset)} and {@code tornado.vector.store(data, byteOffset, v)}:
 * a vector's lanes at consecutive elements of an array, at a byte offset from its data. CUDA and
 * OpenCL spell them as one wide access where the address allows it; on other targets this pass
 * splits them into one access per lane.
 */
public final class VectorMemory {

    public static final String LOAD = "tornado.vector.load";
    public static final String STORE = "tornado.vector.store";

    private VectorMemory() {
    }

    /** Splits every vector load and store of {@code function} into lane accesses. */
    public static void split(Function function) {
        for (var block : function.blocks()) {
            for (var inst : List.copyOf(block.insts())) {
                if (!(inst.opcode() instanceof Opcode.Intrinsic(var name)) || !(name.equals(LOAD) || name.equals(STORE))) {
                    continue;
                }
                var b = IrBuilder.insertingBefore(inst);
                var data = inst.operand(0);
                var offset = inst.operand(1);
                if (name.equals(LOAD)) {
                    var type = (Type.VectorType) inst.type();
                    var lanes = new ArrayList<Value>();
                    for (int l = 0; l < type.length(); l++) {
                        lanes.add(b.load(b.pointerOffset(data, lane(b, offset, type, l)), type.element()));
                    }
                    var vector = b.intrinsic("tornado.vector.make", type, lanes);
                    vector.setName(inst.name());
                    inst.replaceAllUsesWith(vector);
                } else {
                    var vector = inst.operand(2);
                    var type = (Type.VectorType) vector.type();
                    for (int l = 0; l < type.length(); l++) {
                        var value = b.intrinsic("tornado.vector.lane", Immediates.of("lane", l), type.element(), List.of(vector));
                        b.store(b.pointerOffset(data, lane(b, offset, type, l)), value);
                    }
                }
                inst.erase();
            }
        }
    }

    private static int bytes(Type t) {
        return t.equals(Type.I64) || t.equals(Type.F64) ? 8 : t.equals(Type.I16) || t.equals(Type.F16) ? 2 : t.equals(Type.I8) ? 1 : 4;
    }

    private static Value lane(IrBuilder b, Value offset, Type.VectorType type, int lane) {
        return lane == 0 ? offset : ParallelLoopLowering.noSignedWrap(b.binary(Opcode.BinaryOp.ADD, offset, b.constInt(offset.type(), (long) lane * bytes(type.element()))));
    }
}
