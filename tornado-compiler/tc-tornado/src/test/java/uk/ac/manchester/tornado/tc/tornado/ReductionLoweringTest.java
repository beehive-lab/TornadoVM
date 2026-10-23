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

    /**
     * The initial value stored before the loop ({@code out[0] = 0}) is sequential code: run by every thread it was a
     * write-write race on {@code out[0]} (SentryWard: conflicting accesses with no required ordering, on every
     * {@code @Reduce} kernel with such a store). It runs on global thread 0 only.
     */
    @Test
    public void initialValueIsStoredByOneThread() {
        var function = IrParser.parseFunction(SUM);
        assertTrue(ReductionLowering.run(function, List.of(false, true), 256).isEmpty());
        assertTrue(Verifier.verify(function).isEmpty(), Verifier.verify(function).toString());
        var text = new IrPrinter(false).printFunction(function);
        String initBlock = block(text, "^entry.init:");
        assertTrue(initBlock.contains("array.store %out[%zero], %fzero"), text);
        String entry = block(text, "^entry:");
        assertTrue(entry.contains("tornado.thread_id.global<dim = 0>()") && entry.contains("icmp.eq"), entry);
        assertTrue(entry.contains("cond_br") && entry.contains("^entry.init") && entry.contains("^entry.after_init"), entry);
        assertTrue(!entry.contains("array.store %out"), entry);
    }

    /** The printed block whose label line is {@code label}, up to the next label. */
    private static String block(String text, String label) {
        int start = text.indexOf("\n" + label);
        assertTrue(start >= 0, label + " in\n" + text);
        int end = text.indexOf("\n^", start + 1);
        return text.substring(start, end < 0 ? text.length() : end);
    }

    /** Read back outside the loop, the array keeps the store on every thread: one writer would race with the readers. */
    @Test
    public void initialValueReadBackIsNotGuarded() {
        var readBack = SUM.replace("""
                ^exit:
                  ret""", """
                ^exit:
                  %r = array.load %out[%zero] : f32
                  ret""");
        var function = IrParser.parseFunction(readBack);
        assertTrue(ReductionLowering.run(function, List.of(false, true), 256).isEmpty());
        assertTrue(Verifier.verify(function).isEmpty(), Verifier.verify(function).toString());
        assertTrue(!new IrPrinter(false).printFunction(function).contains("^entry.init:"));
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
