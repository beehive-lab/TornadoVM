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

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
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

    public static void vectorAdd(FloatArray a, FloatArray b, FloatArray c) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            c.set(i, a.get(i) * b.get(i));
        }
    }

    public static void square(FloatArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, a.get(i) * a.get(i));
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
        FloatArray x = new FloatArray(8192);
        FloatArray y = new FloatArray(8192);
        FloatArray z = new FloatArray(8192);

        x.init(2.0f);
        y.init(8.0f);

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
