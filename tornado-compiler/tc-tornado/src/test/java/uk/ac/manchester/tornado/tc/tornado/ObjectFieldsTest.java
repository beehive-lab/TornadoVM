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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

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
        assertTrue(Verifier.verify(function, Verifier.Stage.POST_ABI).toString(), Verifier.verify(function, Verifier.Stage.POST_ABI).isEmpty());
        var text = new IrPrinter(false).printFunction(function);
        assertTrue(text, text.contains("const 255 : i32"));
        assertTrue(text, text.contains("const 20 : i64"));
        assertTrue(text, text.matches("(?s).*load %\\d+ : i32.*zext.*const 8 : i64.*mul.*ptr.offset %m, .*"));
        assertTrue(text, !text.contains("field.load"));
    }

    @Test
    public void unknownFieldIsReported() {
        var function = IrParser.parseFunction(KERNEL.replace("\"COLUMNS\"", "\"cols\""));
        var problems = AbiLowering.run(function, TargetCaps.CUDA_SM89, List.of(MATRIX));
        assertEquals("TC-E3102", problems.get(0).code());
    }
}
