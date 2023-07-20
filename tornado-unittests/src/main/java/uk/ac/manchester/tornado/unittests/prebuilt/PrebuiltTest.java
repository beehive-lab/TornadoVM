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
package uk.ac.manchester.tornado.unittests.prebuilt;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.stream.IntStream;

import org.junit.BeforeClass;
import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.prebuilt.PrebuiltTest
 * </code>
 */
public class PrebuiltTest extends TornadoTestBase {
    private static final String TORNADO_SDK = "TORNADO_SDK";
    private static TornadoDevice defaultDevice;
    private static String FILE_PATH;
    private static TornadoVMBackendType backendType;

    @BeforeClass
    public static void init() {
        backendType = TornadoRuntime.getTornadoRuntime().getBackendType(0);
        defaultDevice = TornadoRuntime.getTornadoRuntime().getDriver(0).getDevice(0);
        String tornadoSDK = System.getenv(TORNADO_SDK);
        FILE_PATH = tornadoSDK + "/examples/generated/";
    }

    @Test
    public void testPrebuilt01() {

        final int numElements = 8;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        Arrays.fill(a, 1);
        Arrays.fill(b, 2);

        switch (backendType) {
            case PTX:
                FILE_PATH += "add.ptx";
                break;
            case OPENCL:
                FILE_PATH += "add.cl";
                break;
            case SPIRV:
                FILE_PATH += "add.spv";
                break;
            default:
                throw new TornadoRuntimeException("Backend not supported");
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .prebuiltTask("t0", //
                        "add", //
                        FILE_PATH, //
                        new Object[] { a, b, c }, //
                        new Access[] { Access.READ_ONLY, Access.READ_ONLY, Access.WRITE_ONLY }, //
                        defaultDevice, //
                        new int[] { numElements })//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < c.length; i++) {
            assertEquals(a[i] + b[i], c[i]);
        }
    }

    @Test
    public void testPrebuilt02SPIRV() {
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.OPENCL);

        FILE_PATH += "reduce03.spv";

        final int size = 512;
        final int localSize = 256;
        float[] input = new float[size];
        float[] reduce = new float[size / localSize];
        IntStream.range(0, input.length).sequential().forEach(i -> input[i] = 1);

        WorkerGrid worker = new WorkerGrid1D(size);
        worker.setLocalWork(256, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .prebuiltTask("t0", //
                        "floatReductionAddLocalMemory", //
                        FILE_PATH, //
                        new Object[] { context, input, reduce }, //
                        new Access[] { Access.READ_ONLY, Access.READ_ONLY, Access.WRITE_ONLY }, //
                        defaultDevice, //
                        new int[] { size })//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, reduce);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        executionPlan.withGridScheduler(gridScheduler) //
                .execute();

        // Final SUM
        float finalSum = 0;
        for (float v : reduce) {
            finalSum += v;
        }

        assertEquals(512, finalSum, 0.0f);

    }

    /**
     * It tests the functionality of the prebuilt03SPIRV method.
     *
     * This test case verifies that the prebuilt03SPIRV runs correctly though a
     * SPIRV or OpenCL runtime if the device supports SPIRV.
     *
     * Expected outcome: - If the current backend type is PTX, the test should have
     * thrown unsupported exception. - The test should succeed if a SPIR-V supported
     * device is available, or should use OPENCL as the backend with a device that
     * device supports SPIRV; otherwise, the test should fail.
     */
    @Test
    public void testPrebuilt03SPIRV() {
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.OPENCL);

        FILE_PATH += "reduce04.spv";

        final int size = 32;
        final int localSize = 32;
        int[] input = new int[size];
        int[] reduce = new int[size / localSize];
        IntStream.range(0, input.length).sequential().forEach(i -> input[i] = 2);

        WorkerGrid worker = new WorkerGrid1D(size);
        worker.setLocalWork(32, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("a.b", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("a") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .prebuiltTask("b", //
                        "intReductionAddGlobalMemory", //
                        FILE_PATH, //
                        new Object[] { context, input, reduce }, //
                        new Access[] { Access.READ_ONLY, Access.READ_ONLY, Access.WRITE_ONLY }, //
                        defaultDevice, //
                        new int[] { size })//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, reduce);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withGridScheduler(gridScheduler) //
                .execute();

        // Final SUM
        float finalSum = 0;
        for (int v : reduce) {
            finalSum += v;
        }

        assertEquals(64, finalSum, 0.0f);

    }

    @Test
    public void testPrebuilt04SPIRVThroughOpenCLRuntime() {
        assertNotBackend(TornadoVMBackendType.PTX);

        TornadoDevice device = getSPIRVSupportedDevice();

        if (device == null) {
            assertNotBackend(TornadoVMBackendType.OPENCL, "No SPIRV supported device found with the current OpenCL backend. The OpenCL version must be >= 2.1 to support SPIR-V execution.");
        }

        FILE_PATH += "reduce03.spv";

        final int size = 512;
        final int localSize = 256;
        float[] input = new float[size];
        float[] reduce = new float[size / localSize];
        IntStream.range(0, input.length).sequential().forEach(i -> input[i] = 1);

        WorkerGrid worker = new WorkerGrid1D(size);
        worker.setLocalWork(256, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .prebuiltTask("t0", //
                        "floatReductionAddLocalMemory", //
                        FILE_PATH, //
                        new Object[] { context, input, reduce }, //
                        new Access[] { Access.READ_ONLY, Access.READ_ONLY, Access.WRITE_ONLY }, //
                        device, //
                        new int[] { size })//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, reduce);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        executionPlan.withGridScheduler(gridScheduler) //
                .execute();

        // Final SUM
        float finalSum = 0;
        for (float v : reduce) {
            finalSum += v;
        }

        assertEquals(512, finalSum, 0.0f);

    }
}
