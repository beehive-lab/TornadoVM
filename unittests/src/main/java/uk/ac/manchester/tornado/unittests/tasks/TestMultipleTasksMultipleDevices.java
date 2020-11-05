/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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
import static org.junit.Assert.assertTrue;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

import org.junit.Ignore;
import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;

public class TestMultipleTasksMultipleDevices {

    @Ignore
    public void testTwoTasksTwoDevicesThreadPool() {
        final int numElements = 2048;
        int[] a = new int[numElements];
        int[] b = new int[numElements];

        IntStream.range(0, numElements).forEach(i -> {
            a[i] = 30;
            b[i] = 10;
        });

        int maxThreadCount = Runtime.getRuntime().availableProcessors();

        Thread[] th = new Thread[maxThreadCount];

        System.setProperty("tornado.debug", "true");
        System.setProperty("s0.t0.device", "0:1");
        System.setProperty("s1.t1.device", "0:0");
        Lock lock = new ReentrantLock();

        th[0] = new Thread(() -> {
            lock.lock();
            TaskSchedule ts = new TaskSchedule("s0");
            lock.unlock();

            ts.streamIn(b)//
                    .task("t0", TestMultipleTasksSingleDevice::task0Initialization, b) //
                    .streamOut(b); //
            ts.execute();
        });

        th[1] = new Thread(() -> {
            lock.lock();
            TaskSchedule ts2 = new TaskSchedule("s1");
            lock.unlock();

            ts2.streamIn(a)//
                    .task("t1", TestMultipleTasksSingleDevice::task1Multiplication, a, 12) //
                    .streamOut(a); //
            ts2.execute();
        });

        th[0].start();
        th[1].start();

        for (Thread t : th) {
            try {
                t.join();
            } catch (InterruptedException e) {
                throw (new RuntimeException(e));
            }
        }

        for (int i = 0; i < a.length; i++) {
            assertEquals(360, a[i]);
            assertEquals(10, b[i]);
        }
    }

    @Test
    public void testTwoTasksTwoDevices() {
        final int numElements = 8192;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int devices = TornadoRuntime.getTornadoRuntime().getDriver(0).getDeviceCount();

        IntStream.range(0, numElements).forEach(i -> {
            a[i] = 30;
            b[i] = 10;
        });

        if (devices == 1) {
            assertTrue("This test needs at least 2 OpenCL-compatible devices.", devices == 1);
        } else {
            System.setProperty("tornado.experimental.pvm", "true");
            System.setProperty("tornado.debug", "true");
            System.setProperty("s0.t0.device", "0:1");
            System.setProperty("s0.t1.device", "0:0");
        }

        TaskSchedule ts = new TaskSchedule("s0")//
                .task("t0", TestMultipleTasksSingleDevice::task0Initialization, b) //
                .task("t1", TestMultipleTasksSingleDevice::task1Multiplication, a, 12) //
                .streamOut(a, b); //

        ts.execute();

        for (int i = 0; i < a.length; i++) {
            assertEquals(360, a[i]);
            assertEquals(10, b[i]);
        }
    }

    @Test
    public void testThreeTasksThreeDevices() {
        final int numElements = 2048;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];
        int[] d = new int[numElements];
        int devices = TornadoRuntime.getTornadoRuntime().getDriver(0).getDeviceCount();

        IntStream.range(0, numElements).forEach(i -> {
            a[i] = 30;
            b[i] = 10;
            c[i] = 120;
        });

        if (devices < 3) {
            assertTrue("This test needs at least 2 OpenCL-compatible devices.", devices < 3);
        } else {
            System.setProperty("tornado.debug", "true");
            System.setProperty("s0.t0.device", "0:1");
            System.setProperty("s0.t1.device", "0:0");
            System.setProperty("s0.t2.device", "0:2");
        }

        TaskSchedule ts = new TaskSchedule("s0")//
                .streamIn(a, b)//
                .task("t0", TestMultipleTasksSingleDevice::task0Initialization, b) //
                .task("t1", TestMultipleTasksSingleDevice::task1Multiplication, a, 12) //
                .task("t2", TestMultipleTasksSingleDevice::task2Saxpy, c, c, d, 12) //
                .streamOut(a, b, d); //

        ts.execute();

        for (int i = 0; i < a.length; i++) {
            assertEquals(360, a[i]);
            assertEquals(10, b[i]);
            assertEquals((12 * 120) + 120, d[i]);
        }
    }

    @Ignore
    public void testThreeTasksSingleContextFallBack() {
        final int numElements = 2048;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];
        int[] d = new int[numElements];
        int devices = TornadoRuntime.getTornadoRuntime().getDriver(0).getDeviceCount();

        IntStream.range(0, numElements).forEach(i -> {
            a[i] = 100;
            b[i] = 100;
            c[i] = 120;
        });

        if (devices < 3) {
            assertTrue("This test needs at least 2 OpenCL-compatible devices.", devices < 3);
        } else {
            System.setProperty("tornado.experimental.pvm", "true");
            System.setProperty("tornado.debug", "true");
            System.setProperty("s0.t0.device", "0:1");
            System.setProperty("s0.t1.device", "0:0");
            System.setProperty("s0.t2.device", "0:2");
        }

        TaskSchedule ts = new TaskSchedule("s0")//
                .streamIn(a, b)//
                .task("t0", TestMultipleTasksSingleDevice::task0Initialization, b) //
                .task("t1", TestMultipleTasksSingleDevice::task1Multiplication, b, 12) //
                .task("t2", TestMultipleTasksSingleDevice::task2Saxpy, c, c, d, 12) //
                .streamOut(b, d); //

        ts.execute();

        for (int i = 0; i < a.length; i++) {
            // assertEquals(120, a[i]);
            assertEquals(1200, b[i]);
            assertEquals((12 * 120) + 120, d[i]);
        }
    }
}
