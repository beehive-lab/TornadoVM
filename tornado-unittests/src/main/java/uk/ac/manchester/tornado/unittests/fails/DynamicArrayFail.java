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
package uk.ac.manchester.tornado.unittests.fails;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * TornadoVM requires array sizes to be resolvable at JIT/partial-evaluation time (see
 * "No Dynamic Memory Allocation" in docs/source/unsupported.rst). Declaring a kernel-local array
 * whose length comes from a runtime value (not a compile-time constant) is not supported.
 * Confirmed empirically: it fails compilation with {@link TornadoInternalError} ("unimplemented:
 * dynamically sized array declarations are not supported"), which extends {@link Error} rather
 * than {@code RuntimeException}. This pins down the restriction with a regression test.
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.fails.DynamicArrayFail
 * </code>
 */
public class DynamicArrayFail extends TornadoTestBase {

    public static void kernelWithDynamicSizedArray(IntArray sizes, IntArray out) {
        int n = sizes.get(0); // runtime value, not a compile-time constant
        int[] local = new int[n];
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            local[0] = i;
            out.set(i, local[0]);
        }
    }

    @Test(expected = TornadoInternalError.class)
    public void testDynamicSizedArrayAllocation() {
        final int size = 64;
        IntArray sizes = new IntArray(1);
        sizes.set(0, 8);
        IntArray out = new IntArray(size);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, sizes) //
                .task("t0", DynamicArrayFail::kernelWithDynamicSizedArray, sizes, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();
    }

}
