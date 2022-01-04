/*
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.profiler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.unittests.TestHello;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestProfiler extends TornadoTestBase {

    private boolean isBackendPTXOrSPIRV(int driverIndex) {
        TornadoVMBackendType type = TornadoRuntime.getTornadoRuntime().getDriver(driverIndex).getBackendType();
        switch (type) {
            case PTX:
            case SPIRV:
                return true;
            case OPENCL:
                return false;
            default:
                return false;
        }
    }

    @Test
    public void testProfilerEnabled() {
        int numElements = 16;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        Arrays.fill(a, 1);
        Arrays.fill(b, 2);

        // testProfilerDisabled might execute first. We must make sure that the code
        // cache is reset.
        // Otherwise, we get 0 compile time.
        TornadoRuntime.getTornadoRuntime().getDefaultDevice().reset();

        // Enable profiler
        System.setProperty("tornado.profiler", "True");

        // @formatter:off
        TaskSchedule ts = new TaskSchedule("s0")
                .task("t0", TestHello::add, a, b, c)
                .streamOut(c);
        // @formatter:on

        ts.execute();

        int driverIndex = TornadoRuntime.getTornadoRuntime().getDefaultDevice().getDriverIndex();

        assertTrue(ts.getTotalTime() > 0);
        assertTrue(ts.getTornadoCompilerTime() > 0);
        assertTrue(ts.getCompileTime() > 0);
        assertTrue(ts.getDataTransfersTime() >= 0);
        assertTrue(ts.getReadTime() >= 0);
        assertTrue(ts.getWriteTime() >= 0);
        // We do not support dispatch timers for the PTX and SPIRV backends
        if (!isBackendPTXOrSPIRV(driverIndex)) {
            assertTrue(ts.getDataTransferDispatchTime() > 0);
            assertTrue(ts.getKernelDispatchTime() > 0);
        }
        assertTrue(ts.getDeviceReadTime() >= 0);
        assertTrue(ts.getDeviceWriteTime() >= 0);
        assertTrue(ts.getDeviceKernelTime() > 0);

        assertEquals(ts.getWriteTime() + ts.getReadTime(), ts.getDataTransfersTime());
        assertEquals(ts.getTornadoCompilerTime() + ts.getDriverInstallTime(), ts.getCompileTime());

        // Will dump profile information about the sync stream outs.
        ts.syncObjects(c);

        // Disable profiler
        System.setProperty("tornado.profiler", "False");
    }

    @Test
    public void testProfilerDisabled() {
        int numElements = 16;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        Arrays.fill(a, 1);
        Arrays.fill(b, 2);

        // Disable profiler
        System.setProperty("tornado.profiler", "False");

        // @formatter:off
        TaskSchedule ts = new TaskSchedule("s0")
                .task("t0", TestHello::add, a, b, c)
                .streamOut(c);
        // @formatter:on

        ts.execute();

        assertEquals(ts.getTotalTime(), 0);
        assertEquals(ts.getTornadoCompilerTime(), 0);
        assertEquals(ts.getCompileTime(), 0);
        assertEquals(ts.getDataTransfersTime(), 0);
        assertEquals(ts.getReadTime(), 0);
        assertEquals(ts.getWriteTime(), 0);
        assertEquals(ts.getDataTransferDispatchTime(), 0);
        assertEquals(ts.getKernelDispatchTime(), 0);
        assertEquals(ts.getDeviceReadTime(), 0);
        assertEquals(ts.getDeviceWriteTime(), 0);
        assertEquals(ts.getDeviceKernelTime(), 0);
        assertEquals(ts.getDeviceKernelTime(), 0);
    }
}
