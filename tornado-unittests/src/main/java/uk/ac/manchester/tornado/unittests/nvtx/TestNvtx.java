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
package uk.ac.manchester.tornado.unittests.nvtx;

import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.junit.Before;
import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.cublas.CuBlas;
import uk.ac.manchester.tornado.cublas.enums.CuBlasOperation;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMCUDANotSupported;

/**
 * Tests that the always-on NVTX instrumentation around native library tasks is
 * <b>transparent</b>: it must not alter results and its host-side push/pop must
 * stay balanced across single, repeated, mixed-JIT, and CUDA-graph executions.
 *
 * <p>NVTX ranges themselves are a profiling side effect (a no-op without a
 * profiler attached); their presence on the Nsight Systems timeline is verified
 * out-of-band with {@code nsys profile --trace=cuda,nvtx}. These tests guard the
 * functional invariant that wrapping {@code provider.dispatch(...)} in a
 * push/pop pair (in a try/finally) never changes the computed value.</p>
 *
 * Requires the CUDA backend and a cuBLAS-capable device; skipped otherwise.
 */
public class TestNvtx extends TornadoTestBase {

    private static final int SIZE = 256;
    private static final Random random = new Random(42);

    @Before
    public void cudaMustBeAvailable() {
        TornadoVMBackendType backendType = getTornadoRuntime().getDefaultDevice().getTornadoVMBackend();
        if (backendType != TornadoVMBackendType.CUDA) {
            String message = "NVTX library-task instrumentation requires the CUDA backend (default device is " + backendType + ")";
            switch (backendType) {
                case OPENCL, PTX, SPIRV, METAL -> assertNotBackend(backendType, message);
                default -> throw new TornadoVMCUDANotSupported(message);
            }
        }
        try {
            System.loadLibrary("tornado-cublas");
        } catch (UnsatisfiedLinkError e) {
            throw new TornadoVMCUDANotSupported("libtornado-cublas is not available: " + e.getMessage());
        }
    }

    private static FloatArray randomArray(int size) {
        FloatArray array = new FloatArray(size);
        for (int i = 0; i < size; i++) {
            array.set(i, random.nextFloat() - 0.5f);
        }
        return array;
    }

    /** Row-major matrix-vector reference: output = matrix * vector. */
    private static void matrixVectorJava(FloatArray matrix, FloatArray vector, FloatArray output, int m, int n) {
        for (int i = 0; i < m; i++) {
            float acc = 0.0f;
            for (int j = 0; j < n; j++) {
                acc += matrix.get(i * n + j) * vector.get(j);
            }
            output.set(i, acc);
        }
    }

    public static void addOne(FloatArray array) {
        for (@Parallel int i = 0; i < array.getSize(); i++) {
            array.set(i, array.get(i) + 1.0f);
        }
    }

    private static void assertClose(int n, FloatArray expected, FloatArray actual) {
        for (int i = 0; i < n; i++) {
            assertEquals(expected.get(i), actual.get(i), 0.01f * Math.max(1.0f, Math.abs(expected.get(i))));
        }
    }

    /** A single NVTX-wrapped library task must compute the correct result. */
    @Test
    public void testSingleLibraryTask() throws TornadoExecutionPlanException {
        FloatArray matrix = randomArray(SIZE * SIZE);
        FloatArray vector = randomArray(SIZE);
        FloatArray output = new FloatArray(SIZE);

        TaskGraph g = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrix, vector) //
                .libraryTask("sgemv", CuBlas::cublasSgemv, //
                        CuBlasOperation.CUBLAS_OP_T.operation(), SIZE, SIZE, 1.0f, matrix, SIZE, vector, 1, 0.0f, output, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(g.snapshot())) {
            plan.execute();
        }
        FloatArray expected = new FloatArray(SIZE);
        matrixVectorJava(matrix, vector, expected, SIZE, SIZE);
        assertClose(SIZE, expected, output);
    }

    /** Repeated execution must keep push/pop balanced and results stable. */
    @Test
    public void testRepeatedLibraryTasks() throws TornadoExecutionPlanException {
        FloatArray matrix = randomArray(SIZE * SIZE);
        FloatArray vector = randomArray(SIZE);
        FloatArray output = new FloatArray(SIZE);
        FloatArray expected = new FloatArray(SIZE);
        matrixVectorJava(matrix, vector, expected, SIZE, SIZE);

        TaskGraph g = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrix, vector) //
                .libraryTask("sgemv", CuBlas::cublasSgemv, //
                        CuBlasOperation.CUBLAS_OP_T.operation(), SIZE, SIZE, 1.0f, matrix, SIZE, vector, 1, 0.0f, output, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(g.snapshot())) {
            for (int it = 0; it < 50; it++) {
                plan.execute();
                assertClose(SIZE, expected, output);
            }
        }
    }

    /** JIT task -> NVTX-wrapped library task -> JIT task: nested/adjacent ranges stay balanced. */
    @Test
    public void testMixedJitAndLibraryTask() throws TornadoExecutionPlanException {
        FloatArray matrix = randomArray(SIZE * SIZE);
        FloatArray vector = randomArray(SIZE);
        FloatArray output = new FloatArray(SIZE);

        TaskGraph g = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrix, vector) //
                .task("pre", TestNvtx::addOne, vector) //
                .libraryTask("sgemv", CuBlas::cublasSgemv, //
                        CuBlasOperation.CUBLAS_OP_T.operation(), SIZE, SIZE, 1.0f, matrix, SIZE, vector, 1, 0.0f, output, 1) //
                .task("post", TestNvtx::addOne, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(g.snapshot())) {
            plan.execute();
        }
        FloatArray vecPlusOne = new FloatArray(SIZE);
        for (int i = 0; i < SIZE; i++) {
            vecPlusOne.set(i, vector.get(i) + 1.0f);
        }
        FloatArray expected = new FloatArray(SIZE);
        matrixVectorJava(matrix, vecPlusOne, expected, SIZE, SIZE);
        for (int i = 0; i < SIZE; i++) {
            expected.set(i, expected.get(i) + 1.0f);
        }
        assertClose(SIZE, expected, output);
    }

    /** NVTX push/pop under CUDA-graph capture and replay must not break results. */
    @Test
    public void testLibraryTaskUnderCudaGraph() throws TornadoExecutionPlanException {
        FloatArray matrix = randomArray(SIZE * SIZE);
        FloatArray vector = randomArray(SIZE);
        FloatArray output = new FloatArray(SIZE);
        FloatArray expected = new FloatArray(SIZE);
        matrixVectorJava(matrix, vector, expected, SIZE, SIZE);

        TaskGraph g = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrix, vector) //
                .libraryTask("sgemv", CuBlas::cublasSgemv, //
                        CuBlasOperation.CUBLAS_OP_T.operation(), SIZE, SIZE, 1.0f, matrix, SIZE, vector, 1, 0.0f, output, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(g.snapshot())) {
            plan.withCUDAGraph();
            for (int it = 0; it < 5; it++) {
                plan.execute();
                assertClose(SIZE, expected, output);
            }
        }
    }
}
