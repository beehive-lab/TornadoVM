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
package uk.ac.manchester.tornado.tc.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.tc.ir.AddressSpace;
import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Inst;
import uk.ac.manchester.tornado.tc.ir.IrBuilder;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Type;
import uk.ac.manchester.tornado.tc.ir.Value;

/**
 * The API that model frontends (code models, ...) use to lower TornadoVM API calls as the bytecode
 * frontend does: value types, vectors, atomics and local arrays.
 */
public class ModelFrontendApiTest {

    private static final String TYPES = "uk.ac.manchester.tornado.api.types.";

    private final Function function = new Function("f", Function.Kind.DEVICE, Type.VOID);
    private final IrBuilder b = new IrBuilder(function.newBlock("entry"));

    private static String intrinsic(Value v) {
        return v instanceof Inst i && i.opcode() instanceof Opcode.Intrinsic(var name) ? name : "";
    }

    @Test
    public void halfFloatArithmeticIsSinglePrecisionRoundedOnce() {
        var x = b.convert(Opcode.ConvertOp.FTRUNC, b.constFloat(Type.F32, 1.5), Type.F16);
        var sum = Frontend.apiCall(b, TYPES + "HalfFloat", "add", "(L" + "uk/ac/manchester/tornado/api/types/HalfFloat;Luk/ac/manchester/tornado/api/types/HalfFloat;)"
                + "Luk/ac/manchester/tornado/api/types/HalfFloat;", true, List.of(x, x)).orElseThrow().value().orElseThrow();
        assertEquals(Type.F16, sum.type());
        assertTrue(sum instanceof Inst i && i.opcode() instanceof Opcode.Convert(var op) && op == Opcode.ConvertOp.FTRUNC, "rounded back to half: " + sum);
        var bits = Frontend.apiCall(b, TYPES + "HalfFloat", "getHalfFloatValue", "()S", false, List.of(sum)).orElseThrow().value().orElseThrow();
        assertEquals(Type.I32, bits.type(), "a short widens to int");
    }

    @Test
    public void newValueTypes() {
        var half = Frontend.apiNew(b, TYPES + "HalfFloat", "(F)V", List.of(b.constFloat(Type.F32, 2.0))).orElseThrow();
        assertEquals(Type.F16, half.type());
        var float4 = Frontend.apiNew(b, TYPES + "vectors.Float4", "(FFFF)V",
                List.of(b.constFloat(Type.F32, 1), b.constFloat(Type.F32, 2), b.constFloat(Type.F32, 3), b.constFloat(Type.F32, 4))).orElseThrow();
        assertEquals(new Type.VectorType(Type.F32, 4), float4.type());
        assertEquals("tornado.vector.make", intrinsic(float4));
        var zero = Frontend.apiNew(b, TYPES + "vectors.Int2", "()V", List.of()).orElseThrow();
        assertEquals(new Type.VectorType(Type.I32, 2), zero.type());
        var collection = Frontend.apiNew(b, TYPES + "collections.VectorFloat4", "(I)V", List.of(b.constInt(Type.I32, 8))).orElseThrow();
        assertEquals("tornado.local_collection", intrinsic(collection));
        assertTrue(Frontend.apiNew(b, "java.lang.Object", "()V", List.of()).isEmpty(), "not a TornadoVM value type");
    }

    @Test
    public void vectorSettersReturnTheReceiversNewValue() {
        var v = Frontend.apiNew(b, TYPES + "vectors.Float4", "()V", List.of()).orElseThrow();
        var set = Frontend.apiCall(b, TYPES + "vectors.Float4", "setX", "(F)V", false, List.of(v, b.constFloat(Type.F32, 7))).orElseThrow();
        assertTrue(set.value().isEmpty());
        assertEquals("tornado.vector.insert", intrinsic(set.receiver().orElseThrow()));
        var x = Frontend.apiCall(b, TYPES + "vectors.Float4", "getX", "()F", false, List.of(set.receiver().get())).orElseThrow().value().orElseThrow();
        assertEquals("tornado.vector.lane", intrinsic(x));
    }

    @Test
    public void localArraysAreAllocatedNowOrPending() {
        var now = Frontend.localArray(b, AddressSpace.PRIVATE, Type.F32, b.constInt(Type.I32, 16));
        assertTrue(now instanceof Inst i && i.opcode() instanceof Opcode.Alloca(var space, var element, var count) && count == 16 && element.equals(Type.F32));
        assertTrue(Frontend.localArrayLength(b, now) instanceof Inst i && i.opcode() instanceof Opcode.ConstInt(var n) && n == 16);
        var n = function.entry().addArg(Type.I32, "n");
        var pending = Frontend.localArray(b, AddressSpace.SHARED, Type.I32, n);
        assertEquals("tornado.local_array", intrinsic(pending));
        assertEquals(n, Frontend.localArrayLength(b, pending), "a pending array's length is its operand");
    }
}
