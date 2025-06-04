/*
 * Copyright (c) 2013-2020, 2022, 2024, APT Group, Department of Computer Science,
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
import uk.ac.manchester.tornado.api.TornadoBackend;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoFailureException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Test bad uses of the TornadoVM API. It should throw exceptions when possible
 * with the concrete problem.
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.fails.TestFails
 * </code>
 */
public class TestFails extends TornadoTestBase {

    private void reset() {
        for (int backendIndex = 0; backendIndex < TornadoRuntimeProvider.getTornadoRuntime().getNumBackends(); backendIndex++) {
            final TornadoBackend driver = TornadoRuntimeProvider.getTornadoRuntime().getBackend(backendIndex);
            for (int deviceIndex = 0; deviceIndex < driver.getNumDevices(); deviceIndex++) {
                driver.getDevice(deviceIndex).clean();
            }
        }
    }

    @Test(expected = TornadoFailureException.class)
    public void test01() {
        // =============================================================================
        // Call reset after warm-up. This is not legal in TornadoVM. WarmUP will
        // initialize the heap and the code cache. If reset is called, it will clean all
        // state.
        // This is a different case of calling reset and then execute, because it will
        // reset the internal state of variables if needed, meanwhile warmup skip many
        // of those steps.
        // =============================================================================
        FloatArray x = new FloatArray(100);
        FloatArray y = new FloatArray(100);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .task("s0", (a, b) -> {
                    for (int i = 0; i < 100; i++) {

                    }
                }, x, y) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, y);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlanPlan = new TornadoExecutionPlan(immutableTaskGraph);

        // How to provoke the failure
        executionPlanPlan.withPreCompilation().execute();
        reset();
        executionPlanPlan.execute();
    }

    private static void kernel(FloatArray a, FloatArray b) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            b.set(i, a.get(i));
        }
    }

    @Test(expected = TornadoRuntimeException.class)
    public void test02() {
        // This test fails because the Java method's name to be accelerated corresponds
        // to an OpenCL token.

        FloatArray x = new FloatArray(100);
        FloatArray y = new FloatArray(100);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .task("s0", TestFails::kernel, x, y) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, y);

        // How to provoke the failure
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlanPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlanPlan.execute();
    }

}
