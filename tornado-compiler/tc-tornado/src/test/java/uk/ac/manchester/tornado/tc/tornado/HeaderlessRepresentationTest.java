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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.tc.ir.IrParser;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps;

/**
 * Which arrays ABI lowering treats as headerless, where the answer is not simply read off the
 * parameter list.
 *
 * <p>
 * Two arrays swapped around a loop reach the loop's block arguments from the loop itself, so
 * deciding their layout means solving a cycle. Asking "is every value reaching this argument already
 * known headerless" is never true inside one -- the backedge is the argument being decided -- and
 * two headerless arrays were therefore reported as a mix of layouts although no headered array
 * existed anywhere in the function.
 * </p>
 */
public class HeaderlessRepresentationTest {

    /** Two arrays carried around a loop and swapped on the backedge. */
    private static final String SWAP = """
            kernel @K.swap(%a: array<f32>, %b: array<f32>, %n: i32) -> void {
            ^entry:
              %zero = const 0 : i32
              br ^loop(%a, %b, %zero)
            ^loop(%x: array<f32>, %y: array<f32>, %i: i32):
              %more = icmp.slt %i, %n : i1
              cond_br %more, ^body, ^done
            ^body:
              %v = array.load %x[%i] : f32
              array.store %y[%i], %v
              %one = const 1 : i32
              %i1 = add %i, %one : i32
              br ^loop(%y, %x, %i1)
            ^done:
              ret
            }
            """;

    @Test
    public void aCycleOfHeaderlessArraysIsNotAMixOfLayouts() {
        var function = IrParser.parseFunction(SWAP);
        var problems = AbiLowering.run(function, TargetCaps.CUDA_SM89, List.of(), Set.of(0, 1));
        assertTrue(problems.isEmpty(), "both arrays entering the loop are headerless, so nothing is mixed: " + problems);
    }

    @Test
    public void aCycleOfOrdinaryArraysIsAlsoAccepted() {
        var function = IrParser.parseFunction(SWAP);
        var problems = AbiLowering.run(function, TargetCaps.CUDA_SM89, List.of(), Set.of());
        assertTrue(problems.isEmpty(), "neither array is a device view, so nothing is mixed: " + problems);
    }

    @Test
    public void aCycleThatReallyMixesLayoutsIsStillRefused() {
        var function = IrParser.parseFunction(SWAP);
        // One headerless, one ordinary, swapped into each other: the same block argument would have
        // to be addressed both with and without a header, which has no single right answer.
        var problems = AbiLowering.run(function, TargetCaps.CUDA_SM89, List.of(), Set.of(0));
        assertFalse(problems.isEmpty(), "a genuine mix of layouts around a loop must still be refused");
        assertTrue(problems.stream().anyMatch(d -> d.code().equals("TC-E3105")),
                "the refusal should be the mixed-layout diagnostic, got: " + problems);
    }
}
