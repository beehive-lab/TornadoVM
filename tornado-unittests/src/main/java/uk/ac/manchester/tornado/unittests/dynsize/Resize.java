/*
 * Copyright (c) 2020, 2022, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.unittests.dynsize;

import static org.junit.Assert.assertEquals;

import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * How to run?
 * <p>
 * <code>
 *     tornado-test -V uk.ac.manchester.tornado.unittests.dynsize.Resize
 * </code>
 * </p>
 */
public class Resize extends TornadoTestBase {

    public static void resize01(float[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = 1.0f;
        }
    }

    public static void resize02(float[] a, float[] b) {
        for (@Parallel int i = 0; i < a.length; i++) {
            b[i] = a[i] + 10;
        }
    }

    public float[] createArray(int numElements) {
        float[] a = new float[numElements];
        IntStream.range(0, numElements).sequential().forEach(i -> {
            a[i] = 10.0f;
        });
        return a;
    }

    @Test
    public void testDynamicSize01() {
        float[] a = createArray(256);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", Resize::resize01, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlanPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlanPlan.execute();

        // Resize data
        float[] b = createArray(512);

        // We create a second task-graph with the parameter b instead
        TaskGraph taskGraph2 = new TaskGraph("graph2") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, b) //
                .task("t0", Resize::resize01, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b); //

        ImmutableTaskGraph immutableTaskGraph2 = taskGraph2.snapshot();
        TornadoExecutionPlan executionPlanPlan2 = new TornadoExecutionPlan(immutableTaskGraph2);
        executionPlanPlan2.execute();

        for (float v : b) {
            assertEquals(1.0f, v, 0.001f);
        }
    }

    @Test
    public void testDynamicSize02() {
        float[] a = createArray(256);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", Resize::resize01, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlanPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlanPlan.execute();

        // Resize data
        float[] b = createArray(512);

        // We create a second task-graph with the parameter b instead
        TaskGraph taskGraph2 = new TaskGraph("graph2") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, b) //
                .task("t0", Resize::resize01, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b); //

        ImmutableTaskGraph immutableTaskGraph2 = taskGraph2.snapshot();
        TornadoExecutionPlan executionPlanPlan2 = new TornadoExecutionPlan(immutableTaskGraph2);
        executionPlanPlan2.execute();

        // Update old reference for a new reference
        float[] c = createArray(2048);
        // We create a second task-graph with the parameter b instead
        TaskGraph taskGraph3 = new TaskGraph("graph3") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, c) //
                .task("t0", Resize::resize01, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c); //

        ImmutableTaskGraph immutableTaskGraph3 = taskGraph3.snapshot();
        TornadoExecutionPlan executionPlanPlan3 = new TornadoExecutionPlan(immutableTaskGraph3);
        executionPlanPlan3.execute();

        for (float v : c) {
            assertEquals(1.0f, v, 0.001f);
        }
    }

}
