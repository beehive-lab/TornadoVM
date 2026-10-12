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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

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
            ^head(%i: i32) [parallel<iv = 0>]:
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
        assertTrue(ReductionLowering.run(function, List.of(false, true), 256).isEmpty());
        assertTrue(Verifier.verify(function).isEmpty(), Verifier.verify(function).toString());
        var text = new IrPrinter(false).printFunction(function);
        // The tree is IR: a shared array sized by the largest work-group, barriers and a halving fold.
        assertTrue(text.contains("alloca.shared") && text.contains("[256] : ptr<shared>"), text);
        assertTrue(text.contains("tornado.barrier.local()") && text.contains("%half"), text);
        // Each thread accumulates a grid-stride loop from the neutral element.
        assertTrue(text.contains("tornado.global_size<dim = 0>()") && text.contains("%acc: f32"), text);
        assertTrue(!text.contains("array.load %out"), text);
        assertTrue(!text.contains("tornado.reduce"), text);
        assertTrue(text.contains("tornado.group_id<dim = 0>()"), text);
        assertTrue(!text.contains("parallel<iv = 0>"), text);
        assertEquals(List.of(new ParallelLoopLowering.Domain(0, 0, 1, 4096)), ParallelLoopLowering.domain(function));
    }

    @Test
    public void chainOfTheSameOperationIsAReduction() {
        // out[0] = (out[0] + x) + 1: the read of out[0] is anywhere along a chain of additions.
        var chain = SUM.replace("""
                  %s = fadd %acc, %x : f32""", """
                  %y = fadd %acc, %x : f32
                  %c = const 1.0 : f32
                  %s = fadd %y, %c : f32""");
        var function = IrParser.parseFunction(chain);
        assertTrue(ReductionLowering.run(function, List.of(false, true)).isEmpty());
        assertTrue(Verifier.verify(function).isEmpty(), Verifier.verify(function).toString());
        assertTrue(ReductionLowering.info(IrParser.parseFunction(chain), List.of(1)).operations().equals(List.of("add")));
    }

    @Test
    public void mixedOperationsAreRefused() {
        var mixed = SUM.replace("""
                  %s = fadd %acc, %x : f32""", """
                  %y = fmul %acc, %x : f32
                  %s = fadd %y, %x : f32""");
        assertEquals("TC-E3204", ReductionLowering.run(IrParser.parseFunction(mixed), List.of(false, true)).get(0).code());
    }

    @Test
    public void readOnlyReduceArrayIsAnOrdinaryKernel() {
        var function = IrParser.parseFunction(SUM.replace("array.store %out[%k], %s", "array.store %in[%i], %s"));
        assertTrue(ReductionLowering.run(function, List.of(false, true)).isEmpty());
        assertTrue(new IrPrinter(false).printFunction(function).contains("parallel<iv = 0>"));
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
