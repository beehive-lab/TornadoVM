/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester.
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
package uk.ac.manchester.tornado.unittests.codegen;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * {@code fails.InliningFail} only proves the compiler REJECTS a call graph far over the inlining
 * node-count limit (one giant method, repeated ~14x). Nothing proves the complementary case: a
 * DEEP but non-recursive chain of many small, distinct methods -- the idiomatic way real code
 * ends up with a deep call graph -- inlines and runs correctly. This is the below-the-limit
 * counterpart, not a precise boundary probe (the exact node-count threshold is an internal
 * compiler detail not worth pinning to a fragile assertion).
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.codegen.TestDeepInliningChain
 * </code>
 */
public class TestDeepInliningChain extends TornadoTestBase {

    private static int step01(int x) {
        return x + 1;
    }

    private static int step02(int x) {
        return step01(x) * 2;
    }

    private static int step03(int x) {
        return step02(x) + 3;
    }

    private static int step04(int x) {
        return step03(x) * 2;
    }

    private static int step05(int x) {
        return step04(x) + 5;
    }

    private static int step06(int x) {
        return step05(x) * 2;
    }

    private static int step07(int x) {
        return step06(x) + 7;
    }

    private static int step08(int x) {
        return step07(x) * 2;
    }

    private static int step09(int x) {
        return step08(x) + 9;
    }

    private static int step10(int x) {
        return step09(x) * 2;
    }

    private static int step11(int x) {
        return step10(x) + 11;
    }

    private static int step12(int x) {
        return step11(x) * 2;
    }

    private static int step13(int x) {
        return step12(x) + 13;
    }

    private static int step14(int x) {
        return step13(x) * 2;
    }

    private static int step15(int x) {
        return step14(x) + 15;
    }

    public static void deepChainKernel(IntArray input, IntArray output) {
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            output.set(i, step15(input.get(i)));
        }
    }

    @Test
    public void testDeepNonRecursiveCallChain() throws TornadoExecutionPlanException {
        final int size = 256;
        IntArray input = new IntArray(size);
        for (int i = 0; i < size; i++) {
            input.set(i, i);
        }
        IntArray output = new IntArray(size);
        IntArray expected = new IntArray(size);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .task("t0", TestDeepInliningChain::deepChainKernel, input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        deepChainKernel(input, expected);
        for (int i = 0; i < size; i++) {
            assertEquals(expected.get(i), output.get(i));
        }
    }

}
