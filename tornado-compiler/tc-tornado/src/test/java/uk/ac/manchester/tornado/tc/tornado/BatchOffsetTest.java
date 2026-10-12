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

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.tc.ir.IrParser;
import uk.ac.manchester.tornado.tc.ir.IrPrinter;

/** Batched execution: the parallel index is global where it is a value, chunk-local where it indexes memory. */
public class BatchOffsetTest {

    @Test
    public void valuesUseTheGlobalIndexAndAccessesTheLocalOne() {
        var function = IrParser.parseFunction("""
                kernel @K.init(%data: array<f32>) -> void {
                ^entry:
                  %zero = const 0 : i32
                  br ^head(%zero)
                ^head(%i: i32) [parallel<iv = 0>]:
                  %n = const 1000 : i32
                  %done = icmp.sge %i, %n : i1
                  cond_br %done, ^exit, ^body
                ^body:
                  %v = i2f %i : f32
                  array.store %data[%i], %v
                  %one = const 1 : i32
                  %next = add %i, %one : i32
                  br ^head(%next)
                ^exit:
                  ret
                }
                """);
        BatchOffset.run(function, 3000);
        var text = new IrPrinter(false).printFunction(function);
        assertTrue(text.contains("i2f %i_global"), text);
        assertTrue(text.contains("array.store %data[%i], %v"), text);
        assertTrue(text.contains("icmp.sge %i, %n"), text);
    }
}
