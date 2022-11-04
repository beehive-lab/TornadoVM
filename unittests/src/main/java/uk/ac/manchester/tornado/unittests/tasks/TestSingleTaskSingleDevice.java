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

package uk.ac.manchester.tornado.unittests.tasks;

import static org.junit.Assert.assertEquals;

import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoDriver;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Testing Tornado with one task in the same device. The {@link TaskGraph}
 * contains a single task. This task is executed on either on the default device
 * of the one selected.
 * <p>
 * How to run?
 * </p>
 * <code>
 *     tornado-test -V uk.ac.manchester.tornado.unittests.tasks.TestSingleTaskSingleDevice
 * </code>
 *
 */
public class TestSingleTaskSingleDevice extends TornadoTestBase {

    public static void simpleTask(float[] a, float[] b, float[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    @Test
    public void testSimpleTask() {
        final int numElements = 4096;
        float[] a = new float[numElements];
        float[] b = new float[numElements];
        float[] c = new float[numElements];

        IntStream.range(0, numElements).sequential().forEach(i -> {
            a[i] = (float) Math.random();
            b[i] = (float) Math.random();
        });

        new TaskGraph("s0")//
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)//
                .task("t0", TestSingleTaskSingleDevice::simpleTask, a, b, c)//
                .transferToHost(c)//
                .execute();

        for (int i = 0; i < c.length; i++) {
            assertEquals(a[i] + b[i], c[i], 0.001);
        }
    }

    @Test
    public void testSimpleTaskOnDevice0() {
        final int numElements = 4096;
        float[] a = new float[numElements];
        float[] b = new float[numElements];
        float[] c = new float[numElements];

        IntStream.range(0, numElements).sequential().forEach(i -> {
            a[i] = (float) Math.random();
            b[i] = (float) Math.random();
        });

        TaskGraph taskGraph = new TaskGraph("s0");
        TornadoDriver driver = TornadoRuntime.getTornadoRuntime().getDriver(0);

        int deviceNumber = 0;
        taskGraph.setDevice(driver.getDevice(deviceNumber));

        taskGraph.transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)//
                .task("t0", TestSingleTaskSingleDevice::simpleTask, a, b, c)//
                .transferToHost(c)//
                .execute();

        for (int i = 0; i < c.length; i++) {
            assertEquals(a[i] + b[i], c[i], 0.001);
        }
    }

    @Test
    public void testSimpleTaskOnDevice1() {
        final int numElements = 4096;
        float[] a = new float[numElements];
        float[] b = new float[numElements];
        float[] c = new float[numElements];

        IntStream.range(0, numElements).sequential().forEach(i -> {
            a[i] = (float) Math.random();
            b[i] = (float) Math.random();
        });

        TaskGraph taskGraph = new TaskGraph("s0");
        TornadoDriver driver = TornadoRuntime.getTornadoRuntime().getDriver(0);

        // select device 1 it is available
        int deviceNumber = 0;
        if (driver.getDeviceCount() > 1) {
            deviceNumber = 1;
        }

        taskGraph.setDevice(driver.getDevice(deviceNumber));

        taskGraph.transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)//
                .task("t0", TestSingleTaskSingleDevice::simpleTask, a, b, c)//
                .transferToHost(c)//
                .execute();

        for (int i = 0; i < c.length; i++) {
            assertEquals(a[i] + b[i], c[i], 0.001);
        }
    }

}
