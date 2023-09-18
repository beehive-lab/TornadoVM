/*
 * Copyright (c) 20212-2022, APT Group, Department of Computer Science,
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
import uk.ac.manchester.tornado.api.exceptions.TornadoTaskRuntimeException;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 *     tornado-test -V uk.ac.manchester.tornado.unittests.fails.RuntimeFail
 * </code>
 */
public class RuntimeFail extends TornadoTestBase {

    public static void vectorAdd(float[] a, float[] b, float[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            c[i] = a[i] * b[i];
        }
    }

    public static void square(float[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = a[i] * a[i];
        }
    }

    /**
     * This test sets the same task-name for two different tasks. This triggers an
     * error and TornadoVM exits execution.
     *
     * How to run?
     *
     * <code>
     *     tornado-test -V -pk --debug uk.ac.manchester.tornado.unittests.fails.RuntimeFail#test01
     * </code>
     */
    @Test(expected = TornadoTaskRuntimeException.class)
    public void test01() {
        float[] x = new float[8192];
        float[] y = new float[8192];
        float[] z = new float[8192];

        Arrays.fill(x, 2.0f);
        Arrays.fill(y, 8.0f);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, x, y) //
                .task("t0", RuntimeFail::vectorAdd, x, y, z) //
                .task("t0", RuntimeFail::square, z) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, z);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlanPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlanPlan.execute();

    }

}
