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
