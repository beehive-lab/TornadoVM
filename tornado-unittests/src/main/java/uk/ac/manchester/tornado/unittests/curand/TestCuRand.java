/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.unittests.curand;
import static uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider.getTornadoRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.curand.CuRand;
import uk.ac.manchester.tornado.curand.provider.CuRandLibraryProvider;
import uk.ac.manchester.tornado.unittests.common.LibraryStreamPerThread;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMCUDANotSupported;

/**
 * Random number generation on the device through cuRAND.
 *
 * <p>
 * How to run?
 * <p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.curand.TestCuRand
 * </code>
 * </p>
 */
public class TestCuRand extends TornadoTestBase {

    @BeforeEach
    public void curandMustBeAvailable() {
        TornadoVMBackendType backendType = getTornadoRuntime().getDefaultDevice().getTornadoVMBackend();
        if (backendType != TornadoVMBackendType.CUDA) {
            String message = "cuRAND library tasks require the CUDA backend (default device is " + backendType + ")";
            switch (backendType) {
                case OPENCL, METAL -> assertNotBackend(backendType, message);
                default -> throw new TornadoVMCUDANotSupported(message);
            }
        }
        if (!CuRandLibraryProvider.isAvailable()) {
            throw new TornadoVMCUDANotSupported("cuRAND is not available on this host");
        }
    }

    private static final int NUM_ELEMENTS = 1 << 16;

    private static double mean(FloatArray values, int from, int to) {
        double total = 0.0;
        for (int i = from; i < to; i++) {
            total += values.get(i);
        }
        return total / (to - from);
    }

    private static double stddev(FloatArray values, int from, int to, double mean) {
        double total = 0.0;
        for (int i = from; i < to; i++) {
            double d = values.get(i) - mean;
            total += d * d;
        }
        return Math.sqrt(total / (to - from));
    }

    @Test
    public void testGenerateNormal() throws TornadoExecutionPlanException {
        FloatArray output = new FloatArray(NUM_ELEMENTS);
        output.init(0.0f);

        TaskGraph taskGraph = new TaskGraph("rng") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, output) //
                .libraryTask("normals", CuRand::generateNormal, output, 0, NUM_ELEMENTS, 0.0f, 1.0f) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }

        double m = mean(output, 0, NUM_ELEMENTS);
        double s = stddev(output, 0, NUM_ELEMENTS, m);
        // 65536 standard normals: the sample mean has standard error 1/256, so 0.05 is ~13 sigma.
        assertEquals(0.0, m, 0.05, "sample mean");
        assertEquals(1.0, s, 0.05, "sample standard deviation");
    }

    @Test
    public void testGenerateNormalWithMeanAndStdDev() throws TornadoExecutionPlanException {
        FloatArray output = new FloatArray(NUM_ELEMENTS);

        TaskGraph taskGraph = new TaskGraph("rng") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, output) //
                .libraryTask("normals", CuRand::generateNormal, output, 0, NUM_ELEMENTS, 5.0f, 2.0f) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }

        double m = mean(output, 0, NUM_ELEMENTS);
        assertEquals(5.0, m, 0.10, "sample mean");
        assertEquals(2.0, stddev(output, 0, NUM_ELEMENTS, m), 0.10, "sample standard deviation");
    }

    /**
     * Writing a slice must leave the rest of the buffer alone: the destination is declared
     * READ_WRITE precisely so TornadoVM keeps the untouched region valid.
     */
    @Test
    public void testGenerateIntoSlice() throws TornadoExecutionPlanException {
        int half = NUM_ELEMENTS / 2;
        FloatArray output = new FloatArray(NUM_ELEMENTS);
        output.init(-7.0f);

        TaskGraph taskGraph = new TaskGraph("rng") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, output) //
                .libraryTask("normals", CuRand::generateNormal, output, half, half, 0.0f, 1.0f) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }

        for (int i = 0; i < half; i++) {
            assertEquals(-7.0f, output.get(i), 0.0f, "element " + i + " outside the slice must be untouched");
        }
        double m = mean(output, half, NUM_ELEMENTS);
        assertEquals(0.0, m, 0.05, "sample mean of the slice");
    }

    /** Successive executions must advance the sequence rather than repeat it. */
    @Test
    public void testSuccessiveExecutionsDiffer() throws TornadoExecutionPlanException {
        FloatArray output = new FloatArray(NUM_ELEMENTS);

        TaskGraph taskGraph = new TaskGraph("rng") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, output) //
                .libraryTask("normals", CuRand::generateNormal, output, 0, NUM_ELEMENTS, 0.0f, 1.0f) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
            float[] first = new float[16];
            for (int i = 0; i < first.length; i++) {
                first[i] = output.get(i);
            }

            executionPlan.execute();
            int identical = 0;
            for (int i = 0; i < first.length; i++) {
                if (first[i] == output.get(i)) {
                    identical++;
                }
            }
            assertNotEquals(first.length, identical, "a second execution returned the same block of numbers");
        }
    }

    @Test
    public void testGenerateUniform() throws TornadoExecutionPlanException {
        FloatArray output = new FloatArray(NUM_ELEMENTS);

        TaskGraph taskGraph = new TaskGraph("rng") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, output) //
                .libraryTask("uniform", CuRand::generateUniform, output, 0, NUM_ELEMENTS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }

        for (int i = 0; i < NUM_ELEMENTS; i++) {
            float v = output.get(i);
            assertTrue(v > 0.0f && v <= 1.0f, "uniform value out of (0,1]: " + v);
        }
        assertEquals(0.5, mean(output, 0, NUM_ELEMENTS), 0.02, "sample mean");
    }

    @Test
    public void testGenerateNormalDouble() throws TornadoExecutionPlanException {
        DoubleArray output = new DoubleArray(NUM_ELEMENTS);

        TaskGraph taskGraph = new TaskGraph("rng") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, output) //
                .libraryTask("normals", CuRand::generateNormal, output, 0, NUM_ELEMENTS, 0.0d, 1.0d) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }

        double total = 0.0;
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            total += output.get(i);
        }
        assertEquals(0.0, total / NUM_ELEMENTS, 0.05, "sample mean");
    }

    /**
     * The same plan run from a second thread: TornadoVM gives each thread its own stream, and the
     * cuRAND call must follow the kernel before it on that thread's stream, not run on the stream
     * of the thread that first ran the plan. The kernel slowly fills the output with -1, so a
     * generation that does not wait for it is overwritten.
     */
    @Test
    public void testGenerateFollowsKernelOnAnotherThread() throws Exception {
        FloatArray output = new FloatArray(NUM_ELEMENTS);
        FloatArray sentinel = new FloatArray(1);
        sentinel.set(0, -1.0f);

        TaskGraph taskGraph = new TaskGraph("streamPerThread") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, output, sentinel) //
                .task("fill", LibraryStreamPerThread::slowFill, new KernelContext(), output, sentinel, LibraryStreamPerThread.SPINS) //
                .libraryTask("uniform", CuRand::generateUniform, output, 0, NUM_ELEMENTS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(LibraryStreamPerThread.gridScheduler("streamPerThread.fill")).execute();
            for (int i = 0; i < NUM_ELEMENTS; i++) {
                float v = output.get(i);
                assertTrue(v > 0.0f && v <= 1.0f, "element " + i + " out of (0,1]: " + v);
            }
            output.init(0.0f);
            LibraryStreamPerThread.executeOnAnotherThread(plan);
            for (int i = 0; i < NUM_ELEMENTS; i++) {
                float v = output.get(i);
                assertTrue(v > 0.0f && v <= 1.0f, "element " + i + " out of (0,1]: " + v);
            }
        }
    }
}
