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
package uk.ac.manchester.tornado.unittests.prebuilt;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class PrebuiltTest extends TornadoTestBase {

    @Test
    public void testPrebuild01() {

        final int numElements = 8;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        String tornadoSDK = System.getenv("TORNADO_SDK");

        Arrays.fill(a, 1);
        Arrays.fill(b, 2);

        TornadoDevice defaultDevice = TornadoRuntime.getTornadoRuntime().getDriver(0).getDevice(0);
        String filePath = tornadoSDK + "/examples/generated/";

        TornadoVMBackendType backendType = TornadoRuntime.getTornadoRuntime().getBackendType(0);
        switch (backendType) {
            case PTX:
                filePath += "add.ptx";
                break;
            case OPENCL:
                filePath += "add.cl";
                break;
            case SPIRV:
                filePath += "add.spv";
                break;
            default:
                throw new RuntimeException("Backend not supported");
        }

        // @formatter:off
        new TaskSchedule("s0")
            .prebuiltTask("t0",
                        "add",
                        filePath,
                        new Object[] { a, b, c },
                        new Access[] { Access.READ, Access.READ, Access.WRITE },
                        defaultDevice,
                        new int[] { numElements })
            .streamOut(c)
            .execute();
        // @formatter:on

        for (int i = 0; i < c.length; i++) {
            assertEquals(a[i] + b[i], c[i]);
        }
    }

    @Test
    public void testPrebuild02() {

        final int numElements = 8;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        String tornadoSDK = System.getenv("TORNADO_SDK");

        Arrays.fill(a, 1);
        Arrays.fill(b, 2);

        TornadoDevice defaultDevice = TornadoRuntime.getTornadoRuntime().getDriver(0).getDevice(0);
        String filePath = tornadoSDK + "/examples/generated/";

        TornadoVMBackendType backendType = TornadoRuntime.getTornadoRuntime().getBackendType(0);
        switch (backendType) {
            case PTX:
                filePath += "add.ptx";
                break;
            case OPENCL:
                filePath += "add.cl";
                break;
            case SPIRV:
                filePath += "add.spv";
                break;
            default:
                throw new RuntimeException("Backend not supported");
        }

        // @formatter:off
        new TaskSchedule("s0")
                .prebuiltTask("t0",
                        "add",
                        filePath,
                        new Object[] { a, b, c },
                        new Access[] { Access.READ, Access.READ, Access.WRITE },
                        defaultDevice,
                        new int[] { numElements })
                .streamOut(c)
                .execute();
        // @formatter:on

        for (int i = 0; i < c.length; i++) {
            assertEquals(a[i] + b[i], c[i]);
        }
    }

    @Test
    public void testPrebuild03() {
        assertNotBackend(TornadoVMBackendType.PTX);

        TornadoDevice device = checkSPIRVSupport();

        if (device == null) {
            assertNotBackend(TornadoVMBackendType.OPENCL);
        }

        String tornadoSDK = System.getenv("TORNADO_SDK");
        String filePath = tornadoSDK + "/examples/generated/reduce03.spv";

        final int size = 512;
        final int localSize = 256;
        float[] input = new float[size];
        float[] reduce = new float[size / localSize];
        IntStream.range(0, input.length).sequential().forEach(i -> input[i] = 1);

        WorkerGrid worker = new WorkerGrid1D(size);
        worker.setLocalWork(256, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        // @formatter:off
        new TaskSchedule("s0")
                .prebuiltTask("t0",
                        "floatReductionAddLocalMemory",
                        filePath,
                        new Object[]{context, input, reduce},
                        new Access[]{Access.READ, Access.READ, Access.WRITE},
                        device,
                        new int[]{size})
                .streamOut(reduce)
                .execute(gridScheduler);
        // @formatter:on

        // Final SUM
        float finalSum = 0;
        for (float v : reduce) {
            finalSum += v;
        }

        assertEquals(512, finalSum, 0.0f);

    }

    @Test
    public void testPrebuild04() {
        assertNotBackend(TornadoVMBackendType.PTX);

        TornadoDevice device = checkSPIRVSupport();

        if (device == null) {
            assertNotBackend(TornadoVMBackendType.OPENCL);
        }

        String tornadoSDK = System.getenv("TORNADO_SDK");
        String filePath = tornadoSDK + "/examples/generated/reduce04.spv";

        final int size = 32;
        final int localSize = 32;
        int[] input = new int[size];
        int[] reduce = new int[size / localSize];
        IntStream.range(0, input.length).sequential().forEach(i -> input[i] = 2);

        WorkerGrid worker = new WorkerGrid1D(size);
        worker.setLocalWork(32, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("a.b", worker);
        KernelContext context = new KernelContext();

        // @formatter:off
        new TaskSchedule("a")
                .prebuiltTask("b",
                        "intReductionAddGlobalMemory",
                        filePath,
                        new Object[]{context, input, reduce},
                        new Access[]{Access.READ, Access.READ, Access.WRITE},
                        device,
                        new int[]{size})
                .streamOut(reduce)
                .execute(gridScheduler);
        // @formatter:on

        // Final SUM
        float finalSum = 0;
        for (int v : reduce) {
            finalSum += v;
        }

        assertEquals(64, finalSum, 0.0f);

    }
}
