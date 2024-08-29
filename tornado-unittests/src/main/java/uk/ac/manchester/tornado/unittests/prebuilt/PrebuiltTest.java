/*
 * Copyright (c) 2013-2020, 2022, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

import java.util.stream.IntStream;

import org.junit.BeforeClass;
import org.junit.Test;

import uk.ac.manchester.tornado.api.AccessorParameters;
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
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
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
        backendType = TornadoRuntimeProvider.getTornadoRuntime().getBackendType(0);
        defaultDevice = TornadoRuntimeProvider.getTornadoRuntime().getBackend(0).getDevice(0);
        String tornadoSDK = System.getenv(TORNADO_SDK);
        FILE_PATH = tornadoSDK + "/examples/generated/";
    }

    @Test
    public void testPrebuilt01() throws TornadoExecutionPlanException {

        final int numElements = 8;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);
        IntArray c = new IntArray(numElements);

        a.init(1);
        b.init(2);

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

        // Define accessors for each parameter
        AccessorParameters accessorParameters = new AccessorParameters(3);
        accessorParameters.set(0, a, Access.READ_ONLY);
        accessorParameters.set(1, b, Access.READ_ONLY);
        accessorParameters.set(2, c, Access.WRITE_ONLY);

        // Define the Task-Graph
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .prebuiltTask("t0",      //task name
                        "add",              // name of the low-level kernel to invoke
                        FILE_PATH,          // file name
                        accessorParameters) // accessors
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        // When using the prebuilt API, we need to define the WorkerGrid, otherwise it will launch 1 thread
        // on the target device
        WorkerGrid workerGrid = new WorkerGrid1D(numElements);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", workerGrid);

        // Launch the application on the target device
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.withGridScheduler(gridScheduler) //
                    .withDevice(defaultDevice) //
                    .execute();
        }
        for (int j = 0; j < c.getSize(); j++) {
            assertEquals(a.get(j) + b.get(j), c.get(j));
        }

    }

    @Test
    public void testPrebuilt01Multi() throws TornadoExecutionPlanException {

        final int numElements = 8;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);
        IntArray c = new IntArray(numElements);

        a.init(1);
        b.init(2);

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

        // Define accessors for each parameter
        AccessorParameters accessorParameters = new AccessorParameters(3);
        accessorParameters.set(0, a, Access.READ_WRITE);
        accessorParameters.set(1, b, Access.READ_WRITE);
        accessorParameters.set(2, c, Access.WRITE_ONLY);

        // Define the Task-Graph
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .prebuiltTask("t0",      //task name
                        "add",              // name of the low-level kernel to invoke
                        FILE_PATH,          // file name
                        accessorParameters) // accessors
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        // When using the prebuilt API, we need to define the WorkerGrid, otherwise it will launch 1 thread
        // on the target device
        WorkerGrid workerGrid = new WorkerGrid1D(numElements);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", workerGrid);

        // Launch the application on the target device
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {

            executionPlan.withGridScheduler(gridScheduler) //
                    .withDevice(defaultDevice) //
                    .execute();

            // Run task multiple times
            for (int i = 0; i < 10; i++) {
                executionPlan.execute();
                for (int j = 0; j < c.getSize(); j++) {
                    assertEquals(a.get(j) + b.get(j), c.get(j));
                }
                IntStream.range(0, numElements).forEach(k -> a.set(k, c.get(k)));
            }
        }

    }

    @Test
    public void testPrebuilt02SPIRV() throws TornadoExecutionPlanException {
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

        AccessorParameters accessorParameters = new AccessorParameters(3);
        accessorParameters.set(0, context, Access.READ_ONLY);
        accessorParameters.set(1, input, Access.READ_ONLY);
        accessorParameters.set(2, reduce, Access.WRITE_ONLY);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .prebuiltTask("t0",         //
                        "floatReductionAddLocalMemory", //
                        FILE_PATH,          //
                        accessorParameters) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, reduce);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler) //
                    .withDevice(defaultDevice) //
                    .execute();
        }

        // Final SUM
        float finalSum = 0;
        for (float v : reduce) {
            finalSum += v;
        }

        assertEquals(512, finalSum, 0.0f);

    }

    /**
     * This test case verifies that the {@link PrebuiltTest#testPrebuilt03SPIRV} runs correctly though a
     * SPIR-V or OpenCL runtime if the device supports SPIR-V.
     *
     * <p>Expected outcome: - If the current backend type is PTX, the test should have
     * thrown unsupported exception. - The test should succeed if a SPIR-V supported
     * device is available, or should use OPENCL as the backend with a device that
     * device supports SPIR-V; otherwise, the test should fail.</p>
     */
    @Test
    public void testPrebuilt03SPIRV() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.OPENCL);

        FILE_PATH += "reduce04.spv";

        final int size = 32;
        final int localSize = 32;
        int[] input = new int[size];
        int[] output = new int[size / localSize];
        IntStream.range(0, input.length).sequential().forEach(i -> input[i] = 2);

        WorkerGrid worker = new WorkerGrid1D(size);
        worker.setLocalWork(32, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("a.b", worker);
        KernelContext context = new KernelContext();

        AccessorParameters accessorParameters = new AccessorParameters(3);
        accessorParameters.set(0, context, Access.READ_ONLY);
        accessorParameters.set(1, input, Access.READ_ONLY);
        accessorParameters.set(2, output, Access.WRITE_ONLY);

        TaskGraph taskGraph = new TaskGraph("a") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .prebuiltTask("b", //
                        "intReductionAddGlobalMemory", //
                        FILE_PATH, //
                        accessorParameters) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler) //
                    .withDevice(defaultDevice) //
                    .execute();
        }

        // Final SUM
        float finalSum = 0;
        for (int v : output) {
            finalSum += v;
        }

        assertEquals(64, finalSum, 0.0f);

    }

    @Test
    public void testPrebuilt04SPIRVThroughOpenCLRuntime() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.PTX);

        TornadoDevice device = getSPIRVSupportedDevice();

        if (device == null) {
            assertNotBackend(TornadoVMBackendType.OPENCL, "No SPIRV supported device found with the current OpenCL backend. The OpenCL version must be >= 2.1 to support SPIR-V execution.");
        }

        FILE_PATH += "reduce03.spv";

        final int size = 512;
        final int localSize = 256;
        float[] input = new float[size];
        float[] output = new float[size / localSize];
        IntStream.range(0, input.length).sequential().forEach(i -> input[i] = 1);

        WorkerGrid worker = new WorkerGrid1D(size);
        worker.setLocalWork(256, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        AccessorParameters accessorParameters = new AccessorParameters(3);
        accessorParameters.set(0, context, Access.READ_ONLY);
        accessorParameters.set(1, input, Access.READ_ONLY);
        accessorParameters.set(2, output, Access.WRITE_ONLY);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .prebuiltTask("t0", //
                        "floatReductionAddLocalMemory", //
                        FILE_PATH, //
                        accessorParameters) // 
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler) //
                    .withDevice(device) // 
                    .execute();
        }

        // Final SUM
        float finalSum = 0;
        for (float v : output) {
            finalSum += v;
        }

        assertEquals(512, finalSum, 0.0f);

    }
}
