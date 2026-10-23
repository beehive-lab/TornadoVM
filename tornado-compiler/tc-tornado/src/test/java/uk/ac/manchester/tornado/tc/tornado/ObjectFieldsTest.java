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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.tc.ir.IrParser;
import uk.ac.manchester.tornado.tc.ir.IrPrinter;
import uk.ac.manchester.tornado.tc.ir.Verifier;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps;

/**
 * Object arguments (Matrix2DFloat.set inlined): a final primitive field folds to its value; a
 * reference field is read as {@code object + 8 * u32 at its offset}; the array behind it keeps its
 * native header.
 */
public class ObjectFieldsTest {

    private static final String KERNEL = """
            kernel @K.set(%m: obj<Matrix2DFloat>, %i: i32, %j: i32, %v: f32) -> void {
            ^entry:
              %storage = field.load %m, "storage" : array<f32>
              %cols = field.load %m, "COLUMNS" : i32
              %row = mul %i, %cols : i32
              %k = add %row, %j : i32
              array.store %storage[%k], %v
              ret
            }
            """;

    private static final ObjectLayout MATRIX = new ObjectLayout("Matrix2DFloat", Map.of("ROWS", 12, "COLUMNS", 16, "storage", 20, "numElements", 24), Map.of());

    @Test
    public void finalFieldsFoldAndReferencesDecompress() {
        var function = IrParser.parseFunction(KERNEL);
        var facts = List.<ValueSpecialisation.Fact>of(new ValueSpecialisation.Fact.Instance(Map.of("COLUMNS", 255, "ROWS", 255), List.of(MATRIX)));
        assertEquals(1, ValueSpecialisation.run(function, facts));
        assertTrue(AbiLowering.run(function, TargetCaps.CUDA_SM89, List.of(MATRIX)).isEmpty());
        assertTrue(Verifier.verify(function, Verifier.Stage.POST_ABI).isEmpty(), Verifier.verify(function, Verifier.Stage.POST_ABI).toString());
        var text = new IrPrinter(false).printFunction(function);
        assertTrue(text.contains("const 255 : i32"), text);
        assertTrue(text.contains("const 20 : i64"), text);
        assertTrue(text.matches("(?s).*load %\\d+ : i32.*zext.*const 8 : i64.*mul.*ptr.offset %m, .*"), text);
        assertTrue(!text.contains("field.load"), text);
    }

    @Test
    public void unknownFieldIsReported() {
        var function = IrParser.parseFunction(KERNEL.replace("\"COLUMNS\"", "\"cols\""));
        var problems = AbiLowering.run(function, TargetCaps.CUDA_SM89, List.of(MATRIX));
        assertEquals("TC-E3102", problems.get(0).code());
    }
}
