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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
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
            case OpenCL:
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
            case OpenCL:
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
        assertNotBackend(TornadoVMBackendType.OpenCL);

        TornadoDevice defaultDevice = TornadoRuntime.getTornadoRuntime().getDriver(0).getDevice(0);
        String tornadoSDK = System.getenv("TORNADO_SDK");
        String filePath = tornadoSDK + "/examples/generated/reduce03.spv";

        final int size = 512;
        final int localSize = 256;
        float[] input = new float[size];
        float[] reduce = new float[size / localSize];
        IntStream.range(0, input.length).sequential().forEach(i -> input[i] = 1);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        // @formatter:off
        new TaskSchedule("s0")
                .prebuiltTask("t0",
                        "floatReductionAddLocalMemory",
                        filePath,
                        new Object[]{context, input, reduce},
                        new Access[]{Access.READ, Access.READ, Access.WRITE},
                        defaultDevice,
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
        assertNotBackend(TornadoVMBackendType.OpenCL);

        TornadoDevice defaultDevice = TornadoRuntime.getTornadoRuntime().getDriver(0).getDevice(0);
        String tornadoSDK = System.getenv("TORNADO_SDK");
        String filePath = tornadoSDK + "/examples/generated/reduce04.spv";

        final int size = 32;
        final int localSize = 32;
        int[] input = new int[size];
        int[] reduce = new int[size / localSize];
        IntStream.range(0, input.length).sequential().forEach(i -> input[i] = 2);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("a.b", worker);
        KernelContext context = new KernelContext();

        // @formatter:off
        new TaskSchedule("a")
                .prebuiltTask("b",
                        "intReductionAddGlobalMemory",
                        filePath,
                        new Object[]{context, input, reduce},
                        new Access[]{Access.READ, Access.READ, Access.WRITE},
                        defaultDevice,
                        new int[]{size})
                .streamOut(reduce)
                .execute(gridScheduler);
        // @formatter:on

        System.out.println(Arrays.toString(reduce));

        // Final SUM
        float finalSum = 0;
        for (int v : reduce) {
            finalSum += v;
        }

        assertEquals(64, finalSum, 0.0f);

    }

    private static final float DELTA = 0.005f;
    private static final float ESP_SQR = 500.0f;

    private static void nBody(int numBodies, float[] refPos, float[] refVel) {
        for (@Parallel int i = 0; i < numBodies; i++) {
            int body = 4 * i;

            float[] acc = new float[] { 0.0f, 0.0f, 0.0f };
            for (int j = 0; j < numBodies; j++) {
                float[] r = new float[3];
                int index = 4 * j;

                float distSqr = 0.0f;
                for (int k = 0; k < 3; k++) {
                    r[k] = refPos[index + k] - refPos[body + k];
                    distSqr += r[k] * r[k];
                }

                float invDist = (float) (1.0f / Math.sqrt(distSqr + ESP_SQR));

                float invDistCube = invDist * invDist * invDist;
                float s = refPos[index + 3] * invDistCube;

                for (int k = 0; k < 3; k++) {
                    acc[k] += s * r[k];
                }
            }
            for (int k = 0; k < 3; k++) {
                refPos[body + k] += refVel[body + k] * DELTA + 0.5f * acc[k] * DELTA * DELTA;
                refVel[body + k] += acc[k] * DELTA;
            }
        }
    }

    public static void validate(int numBodies, float[] posTornadoVM, float[] velTornadoVM, float[] posSequential, float[] velSequential) {
        for (int i = 0; i < numBodies * 4; i++) {
            assertEquals(posSequential[i], posTornadoVM[i], 0.1f);
            assertEquals(velSequential[i], velTornadoVM[i], 0.1f);
        }
    }

    // Provisional test
    @Test
    public void testNBody() {

        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.OpenCL);

        TornadoDevice defaultDevice = TornadoRuntime.getTornadoRuntime().getDriver(0).getDevice(0);
        String filePath = "/tmp/nbody.spv";

        final int numBodies = 2048;
        float[] posSeq = new float[numBodies * 4];
        float[] velSeq = new float[numBodies * 4];

        for (int i = 0; i < posSeq.length; i++) {
            posSeq[i] = (float) Math.random();
        }

        Arrays.fill(velSeq, 0.0f);

        float[] posTornadoVM = new float[numBodies * 4];
        float[] velTornadoVM = new float[numBodies * 4];

        System.arraycopy(posSeq, 0, posTornadoVM, 0, posSeq.length);
        System.arraycopy(velSeq, 0, velTornadoVM, 0, velSeq.length);

        // Run Sequential
        nBody(numBodies, posSeq, velSeq);

        WorkerGrid workerGrid = new WorkerGrid1D(numBodies);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", workerGrid);
        workerGrid.setGlobalWork(numBodies, 1, 1);
        workerGrid.setLocalWork(32, 1, 1);

        // @formatter:off
        new TaskSchedule("s0")
                .prebuiltTask("t0",
                        "nBody",
                        filePath,
                        new Object[]{numBodies, posTornadoVM, velTornadoVM},
                        new Access[]{Access.READ, Access.WRITE, Access.WRITE},
                        defaultDevice,
                        new int[]{numBodies})
                .streamOut(posTornadoVM, velTornadoVM)
                .execute(gridScheduler);
        // @formatter:on

        validate(numBodies, posTornadoVM, velTornadoVM, posSeq, velSeq);
    }

    @Test
    public void test05() {
        // Check only for the SPIR-V backend
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.OpenCL);

        final int numElements = 8192 * 16;
        int[] a = new int[numElements];
        int[] b = new int[numElements];

        Arrays.fill(a, 0);
        Arrays.fill(b, 0);
        int[] expectedResultA = new int[numElements];
        int[] expectedResultB = new int[numElements];
        Arrays.fill(expectedResultA, 100);
        Arrays.fill(expectedResultB, 500);

        TornadoDevice defaultDevice = TornadoRuntime.getTornadoRuntime().getDriver(0).getDevice(0);
        String tornadoSDK = System.getenv("TORNADO_SDK");
        String filePath = tornadoSDK + "/examples/generated/init.spv";

        // @formatter:off
        new TaskSchedule("s0")
                .prebuiltTask("t0",
                        "init",
                        filePath,
                        new Object[]{a, b},
                        new Access[]{Access.WRITE, Access.WRITE},
                        defaultDevice,
                        new int[]{numElements})
                .streamOut(a, b)
                .execute();
        // @formatter:on

        assertArrayEquals(expectedResultA, a);
        assertArrayEquals(expectedResultB, b);
    }

}
