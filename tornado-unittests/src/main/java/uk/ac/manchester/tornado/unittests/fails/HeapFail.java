/*
 * Copyright (c) 2013-2020, 2022, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package uk.ac.manchester.tornado.unittests.fails;

import java.util.Arrays;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoOutOfMemoryException;

/**
 * <p>
 * How to run.
 * </p>
 * <code>
 *     tornado-test -V uk.ac.manchester.tornado.unittests.fails.HeapFail
 * </code>
 */
public class HeapFail {

    private static void validKernel(float[] a, float[] b) {
        for (@Parallel int i = 0; i < a.length; i++) {
            b[i] = a[i];
        }
    }

    /**
     * How to run.
     *
     * <code>
     * $ tornado-test -V --fast -J"-Dtornado.device.memory=1MB"
     * uk.ac.manchester.tornado.unittests.fails.HeapFail#test03
     * </code>
     *
     */
    @Test(expected = TornadoOutOfMemoryException.class)
    public void test03() throws TornadoOutOfMemoryException {
        // This test simulates small amount of memory on the target device and we
        // allocate more than available. We should get a concrete error message back
        // with the steps to take to increase the device's heap size

        // We allocate 64MB of data on the device
        float[] x = new float[16777216];
        float[] y = new float[16777216];

        Arrays.fill(x, 2.0f);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .task("s0", HeapFail::validKernel, x, y) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, y); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();
    }
}
