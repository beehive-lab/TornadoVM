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
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * The auto-parallelizer (TornadoAutoParalleliser) requires a {@code @Parallel} loop's stride to
 * be a compile-time constant; a stride read from a runtime value fails to parallelize. This pins
 * down that restriction with a regression test.
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.fails.NonConstantStrideFail
 * </code>
 */
public class NonConstantStrideFail extends TornadoTestBase {

    public static void kernelWithNonConstantStride(IntArray stepHolder, IntArray a) {
        int step = stepHolder.get(0); // runtime value, not a compile-time constant
        for (@Parallel int i = 0; i < a.getSize(); i += step) {
            a.set(i, i);
        }
    }

    @Test(expected = TornadoBailoutRuntimeException.class)
    public void testNonConstantLoopStride() {
        final int size = 64;
        IntArray stepHolder = new IntArray(1);
        stepHolder.set(0, 2);
        IntArray a = new IntArray(size);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, stepHolder) //
                .task("t0", NonConstantStrideFail::kernelWithNonConstantStride, stepHolder, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();
    }

}
