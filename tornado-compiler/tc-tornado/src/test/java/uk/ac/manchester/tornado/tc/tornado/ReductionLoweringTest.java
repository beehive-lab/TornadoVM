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

import org.junit.Test;

import uk.ac.manchester.tornado.tc.ir.IrParser;
import uk.ac.manchester.tornado.tc.ir.IrPrinter;
import uk.ac.manchester.tornado.tc.ir.Verifier;

/**
 * @Reduce lowering: the parallel loop runs one iteration per thread, the reduction store becomes a
 * work-group reduction and a write of the group's slot by thread 0; the domain covers the loop.
 */
public class ReductionLoweringTest {

    private static final String SUM = """
            kernel @K.sum(%in: array<f32>, %out: array<f32>) -> void {
            ^entry:
              %zero = const 0 : i32
              %fzero = const 0.0 : f32
              array.store %out[%zero], %fzero
              br ^head(%zero)
            ^head(%i: i32) [parallel(iv=0)]:
              %n = const 4096 : i32
              %done = icmp.sge %i, %n : i1
              cond_br %done, ^exit, ^body
            ^body:
              %x = array.load %in[%i] : f32
              %k = const 0 : i32
              %acc = array.load %out[%k] : f32
              %s = fadd %acc, %x : f32
              array.store %out[%k], %s
              %one = const 1 : i32
              %next = add %i, %one : i32
              br ^head(%next)
            ^exit:
              ret
            }
            """;

    @Test
    public void sumBecomesWorkGroupReduction() {
        var function = IrParser.parseFunction(SUM);
        assertTrue(ReductionLowering.run(function, List.of(false, true)).isEmpty());
        assertTrue(Verifier.verify(function).toString(), Verifier.verify(function).isEmpty());
        var text = new IrPrinter(false).printFunction(function);
        assertTrue(text, text.contains("tornado.reduce.add(%x)"));
        assertTrue(text, text.contains("tornado.group_id.x()"));
        assertTrue(text, !text.contains("parallel(iv=0)"));
        assertEquals(List.of(new ParallelLoopLowering.Domain(0, 0, 1, 4096)), ParallelLoopLowering.domain(function));
    }

    @Test
    public void readOnlyReduceArrayIsAnOrdinaryKernel() {
        var function = IrParser.parseFunction(SUM.replace("array.store %out[%k], %s", "array.store %in[%i], %s"));
        assertTrue(ReductionLowering.run(function, List.of(false, true)).isEmpty());
        assertTrue(new IrPrinter(false).printFunction(function).contains("parallel(iv=0)"));
    }

    @Test
    public void conditionalReductionIsRefused() {
        var conditional = SUM.replace("""
                  %x = array.load %in[%i] : f32""", """
                  %x = array.load %in[%i] : f32
                  %neg = fcmp.olt %x, %fzero : i1
                  cond_br %neg, ^add, ^skip
                ^add:""").replace("""
                  %one = const 1 : i32""", """
                  br ^skip
                ^skip:
                  %one = const 1 : i32""");
        var problems = ReductionLowering.run(IrParser.parseFunction(conditional), List.of(false, true));
        assertEquals("TC-E3204", problems.get(0).code());
    }
}
