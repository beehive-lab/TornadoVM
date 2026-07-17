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

import java.util.List;
import java.util.stream.IntStream;

import org.junit.BeforeClass;
import org.junit.Test;

import uk.ac.manchester.tornado.api.AccessorParameters;
import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoBackend;
import uk.ac.manchester.tornado.api.TornadoDeviceMap;
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
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMMultiDeviceNotSupported;
import uk.ac.manchester.tornado.unittests.common.TornadoVMPTXNotSupported;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.prebuilt.PrebuiltTests
 * </code>
 */
public class PrebuiltTests extends TornadoTestBase {
    private static final String TORNADOVM_HOME = "TORNADOVM_HOME";
    private static TornadoDevice defaultDevice;
    private static TornadoVMBackendType backendType;
    private static boolean coops;

    @BeforeClass
    public static void init() {
        backendType = TornadoRuntimeProvider.getTornadoRuntime().getBackendType(0);
        defaultDevice = TornadoRuntimeProvider.getTornadoRuntime().getBackend(0).getDevice(0);
        coops = TornadoNativeArray.ARRAY_HEADER == 16;
    }

    private String getPrebuiltKernelPath(String kernelName) {
        String basePath = System.getenv(TORNADOVM_HOME) + "/examples/generated/";
        String fileStem = coops ? kernelName : kernelName + "_uncompressed";

        String extension = switch (backendType) {
            case PTX    -> ".ptx";
            case OPENCL -> ".cl";
            case METAL  -> ".metal";
            case CUDA   -> ".cu";
            default     -> throw new TornadoRuntimeException("Backend not supported");
        };

        return basePath + fileStem + extension;
    }

    private String getPrebuiltKernelPath(String kernelName, String extension) {
        String sdkPath = System.getenv(TORNADOVM_HOME);
        String basePath = sdkPath + "/examples/generated/";
        String fileStem = coops ? kernelName : kernelName + "_uncompressed";

        // Ensure the extension starts with a dot
        String finalExtension = extension.startsWith(".") ? extension : "." + extension;

        return basePath + fileStem + finalExtension;
    }

    @Test
    public void testPrebuilt01() throws TornadoExecutionPlanException {

        final int numElements = 8;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);
        IntArray c = new IntArray(numElements);

        a.init(1);
        b.init(2);

        // Define accessors for each parameter
        AccessorParameters accessorParameters = new AccessorParameters(3);
        accessorParameters.set(0, a, Access.READ_ONLY);
        accessorParameters.set(1, b, Access.READ_ONLY);
        accessorParameters.set(2, c, Access.WRITE_ONLY);

        String kernelFile = getPrebuiltKernelPath("add");

        // Define the Task-Graph
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .prebuiltTask("t0",      //task name
                        "add",              // name of the low-level kernel to invoke
                        kernelFile,          // file name
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
    public void testPrebuilt01MultiIterations() throws TornadoExecutionPlanException {

        final int numElements = 8;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);
        IntArray c = new IntArray(numElements);

        a.init(1);
        b.init(2);

        // Define accessors for each parameter
        AccessorParameters accessorParameters = new AccessorParameters(3);
        accessorParameters.set(0, a, Access.READ_WRITE);
        accessorParameters.set(1, b, Access.READ_WRITE);
        accessorParameters.set(2, c, Access.WRITE_ONLY);

        String kernelFile = getPrebuiltKernelPath("add");

        // Define the Task-Graph
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .prebuiltTask("t0",      //task name
                        "add",              // name of the low-level kernel to invoke
                        kernelFile,          // file name
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

    /**
     * This test is intended to be passed with multiple backends (e.g., OpenCL and PTX).
     * The PTX backend needs to be installed. Otherwise, an exception is thrown.
     *
     * <p> How to run?
     * <code>
     * tornado-test -V uk.ac.manchester.tornado.unittests.prebuilt.PrebuiltTests#testPrebuiltMutiBackend
     * </code>
     * </p>
     *
     * @throws TornadoExecutionPlanException
     */
    @Test
    public void testPrebuiltMutiBackend() throws TornadoExecutionPlanException {

        final int numElements = 8;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);
        IntArray c = new IntArray(numElements);

        a.init(1);
        b.init(2);

        // Force to use the PTX Backend.
        String kernelFile = getPrebuiltKernelPath("add", ".ptx");

        TornadoDeviceMap tornadoDeviceMap = TornadoExecutionPlan.getTornadoDeviceMap();
        if (tornadoDeviceMap.getNumBackends() < 2) {
            throw new TornadoVMMultiDeviceNotSupported("Test designed to run with multiple backends");
        }

        List<TornadoBackend> ptxBackend = tornadoDeviceMap.getBackendsWithPredicate(backend -> backend.getBackendType() == TornadoVMBackendType.PTX);

        if (ptxBackend == null || ptxBackend.isEmpty()) {
            throw new TornadoVMPTXNotSupported("Test designed to run with multiple backends, including a PTX backend");
        }

        // Access the first device within the NVIDIA PTX Backend
        TornadoDevice device = ptxBackend.getFirst().getDevice(0);

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
                        kernelFile,          // file name
                        accessorParameters) // accessors
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        // When using the prebuilt API, we need to define the WorkerGrid, otherwise it will launch 1 thread
        // on the target device
        WorkerGrid workerGrid = new WorkerGrid1D(numElements);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", workerGrid);

        // Launch the application on the target device
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {

            executionPlan.withGridScheduler(gridScheduler) //
                    .withDevice(device) //
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

}
