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
package uk.ac.manchester.tornado.unittests.cublas;

import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.cublas.CuBlas;
import uk.ac.manchester.tornado.cublas.CuBlasOptions;
import uk.ac.manchester.tornado.cublas.enums.CuBlasOperation;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Unit tests for cuBLAS library tasks (hybrid API). Skipped (JUnit assumption)
 * unless the default device is on the CUDA backend and libtornado-cublas is
 * loadable.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.cublas.TestCuBlas
 * </code>
 */
public class TestCuBlas extends TornadoTestBase {

    private static final int SIZE = 128;
    private static final Random random = new Random(42);

    @Before
    public void assumeCuBlasIsAvailable() {
        TornadoVMBackendType backendType = getTornadoRuntime().getDefaultDevice().getTornadoVMBackend();
        Assume.assumeTrue("cuBLAS library tasks require the CUDA backend (default device is " + backendType + ")", backendType == TornadoVMBackendType.CUDA);
        try {
            System.loadLibrary("tornado-cublas");
        } catch (UnsatisfiedLinkError e) {
            Assume.assumeTrue("libtornado-cublas is not available: " + e.getMessage(), false);
        }
    }

    private static FloatArray randomArray(int size) {
        FloatArray array = new FloatArray(size);
        for (int i = 0; i < size; i++) {
            array.set(i, random.nextFloat());
        }
        return array;
    }

    private static void matrixVectorJava(FloatArray matrix, FloatArray vector, FloatArray output, int m, int n) {
        for (int i = 0; i < m; i++) {
            float sum = 0.0f;
            for (int j = 0; j < n; j++) {
                sum += matrix.get(i * n + j) * vector.get(j);
            }
            output.set(i, sum);
        }
    }

    private static void matrixMultiplyJava(FloatArray a, FloatArray b, FloatArray c, int size) {
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                float sum = 0.0f;
                for (int p = 0; p < size; p++) {
                    sum += a.get(i * size + p) * b.get(p * size + j);
                }
                c.set(i * size + j, sum);
            }
        }
    }

    public static void addOne(FloatArray array) {
        for (@Parallel int i = 0; i < array.getSize(); i++) {
            array.set(i, array.get(i) + 1.0f);
        }
    }

    @Test
    public void testSgemv() throws TornadoExecutionPlanException {
        FloatArray matrix = randomArray(SIZE * SIZE);
        FloatArray vector = randomArray(SIZE);
        FloatArray output = new FloatArray(SIZE);
        FloatArray expected = new FloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrix, vector) //
                .libraryTask("sgemv", CuBlas::cublasSgemv, //
                        CuBlasOperation.CUBLAS_OP_T.operation(), SIZE, SIZE, 1.0f, matrix, SIZE, vector, 1, 0.0f, output, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        matrixVectorJava(matrix, vector, expected, SIZE, SIZE);
        for (int i = 0; i < SIZE; i++) {
            assertEquals(expected.get(i), output.get(i), 0.01f);
        }
    }

    @Test
    public void testSgemvBeta() throws TornadoExecutionPlanException {
        FloatArray matrix = randomArray(SIZE * SIZE);
        FloatArray vector = randomArray(SIZE);
        FloatArray output = randomArray(SIZE);
        FloatArray expected = new FloatArray(SIZE);
        final float beta = 0.5f;

        FloatArray initialOutput = new FloatArray(SIZE);
        for (int i = 0; i < SIZE; i++) {
            initialOutput.set(i, output.get(i));
        }

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrix, vector, output) //
                .libraryTask("sgemv", CuBlas::cublasSgemv, //
                        CuBlasOperation.CUBLAS_OP_T.operation(), SIZE, SIZE, 1.0f, matrix, SIZE, vector, 1, beta, output, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        matrixVectorJava(matrix, vector, expected, SIZE, SIZE);
        for (int i = 0; i < SIZE; i++) {
            assertEquals(expected.get(i) + beta * initialOutput.get(i), output.get(i), 0.01f);
        }
    }

    @Test
    public void testSgemm() throws TornadoExecutionPlanException {
        FloatArray matrixA = randomArray(SIZE * SIZE);
        FloatArray matrixB = randomArray(SIZE * SIZE);
        FloatArray matrixC = new FloatArray(SIZE * SIZE);
        FloatArray expected = new FloatArray(SIZE * SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrixA, matrixB) //
                .libraryTask("sgemm", CuBlas::cublasSgemm, //
                        CuBlasOperation.CUBLAS_OP_N.operation(), CuBlasOperation.CUBLAS_OP_N.operation(), //
                        SIZE, SIZE, SIZE, 1.0f, matrixB, SIZE, matrixA, SIZE, 0.0f, matrixC, SIZE) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixC);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        matrixMultiplyJava(matrixA, matrixB, expected, SIZE);
        for (int i = 0; i < SIZE * SIZE; i++) {
            assertEquals(expected.get(i), matrixC.get(i), 0.01f * Math.max(1.0f, Math.abs(expected.get(i))));
        }
    }

    @Test
    public void testSgemmTF32() throws TornadoExecutionPlanException {
        FloatArray matrixA = randomArray(SIZE * SIZE);
        FloatArray matrixB = randomArray(SIZE * SIZE);
        FloatArray matrixC = new FloatArray(SIZE * SIZE);
        FloatArray expected = new FloatArray(SIZE * SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrixA, matrixB) //
                .libraryTask("sgemmTF32", CuBlas::cublasSgemmTF32, //
                        CuBlasOperation.CUBLAS_OP_N.operation(), CuBlasOperation.CUBLAS_OP_N.operation(), //
                        SIZE, SIZE, SIZE, 1.0f, matrixB, SIZE, matrixA, SIZE, 0.0f, matrixC, SIZE) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixC);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        matrixMultiplyJava(matrixA, matrixB, expected, SIZE);
        for (int i = 0; i < SIZE * SIZE; i++) {
            // TF32: 10-bit mantissa multiply, relaxed tolerance
            assertEquals(expected.get(i), matrixC.get(i), 0.05f * Math.max(1.0f, Math.abs(expected.get(i))));
        }
    }

    @Test
    public void testSgemmWorkspace() throws TornadoExecutionPlanException {
        FloatArray matrixA = randomArray(SIZE * SIZE);
        FloatArray matrixB = randomArray(SIZE * SIZE);
        FloatArray matrixC = new FloatArray(SIZE * SIZE);
        FloatArray expected = new FloatArray(SIZE * SIZE);
        final CuBlasOptions options = new CuBlasOptions().withWorkspace(32L * 1024 * 1024);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrixA, matrixB) //
                .libraryTask("sgemm", (Integer ta, Integer tb, Integer m, Integer n, Integer k, Float alpha, FloatArray a, Integer lda, FloatArray b, Integer ldb, Float beta, FloatArray c,
                        Integer ldc) -> CuBlas.cublasSgemm(ta, tb, m, n, k, alpha, a, lda, b, ldb, beta, c, ldc).withTuning(options), //
                        CuBlasOperation.CUBLAS_OP_N.operation(), CuBlasOperation.CUBLAS_OP_N.operation(), //
                        SIZE, SIZE, SIZE, 1.0f, matrixB, SIZE, matrixA, SIZE, 0.0f, matrixC, SIZE) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixC);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            for (int i = 0; i < 3; i++) {
                plan.execute();
            }
        }

        matrixMultiplyJava(matrixA, matrixB, expected, SIZE);
        for (int i = 0; i < SIZE * SIZE; i++) {
            assertEquals(expected.get(i), matrixC.get(i), 0.01f * Math.max(1.0f, Math.abs(expected.get(i))));
        }
    }

    @Test
    public void testGemmExFP16() throws TornadoExecutionPlanException {
        HalfFloatArray matrixA = new HalfFloatArray(SIZE * SIZE);
        HalfFloatArray matrixB = new HalfFloatArray(SIZE * SIZE);
        HalfFloatArray matrixC = new HalfFloatArray(SIZE * SIZE);
        for (int i = 0; i < SIZE * SIZE; i++) {
            matrixA.set(i, new HalfFloat(random.nextFloat()));
            matrixB.set(i, new HalfFloat(random.nextFloat()));
        }

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrixA, matrixB) //
                .libraryTask("gemmEx", CuBlas::cublasGemmExFP16, //
                        CuBlasOperation.CUBLAS_OP_N.operation(), CuBlasOperation.CUBLAS_OP_N.operation(), //
                        SIZE, SIZE, SIZE, 1.0f, matrixB, SIZE, matrixA, SIZE, 0.0f, matrixC, SIZE) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixC);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                float sum = 0.0f;
                for (int p = 0; p < SIZE; p++) {
                    sum += matrixA.get(i * SIZE + p).getFloat32() * matrixB.get(p * SIZE + j).getFloat32();
                }
                assertEquals(sum, matrixC.get(i * SIZE + j).getFloat32(), 2e-3f * Math.max(1.0f, Math.abs(sum)));
            }
        }
    }

    @Test
    public void testGemmExFP16FP32() throws TornadoExecutionPlanException {
        HalfFloatArray matrixA = new HalfFloatArray(SIZE * SIZE);
        HalfFloatArray matrixB = new HalfFloatArray(SIZE * SIZE);
        FloatArray matrixC = new FloatArray(SIZE * SIZE);
        for (int i = 0; i < SIZE * SIZE; i++) {
            matrixA.set(i, new HalfFloat(random.nextFloat()));
            matrixB.set(i, new HalfFloat(random.nextFloat()));
        }

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrixA, matrixB) //
                .libraryTask("gemmEx", CuBlas::cublasGemmExFP16FP32, //
                        CuBlasOperation.CUBLAS_OP_N.operation(), CuBlasOperation.CUBLAS_OP_N.operation(), //
                        SIZE, SIZE, SIZE, 1.0f, matrixB, SIZE, matrixA, SIZE, 0.0f, matrixC, SIZE) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixC);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                float sum = 0.0f;
                for (int p = 0; p < SIZE; p++) {
                    sum += matrixA.get(i * SIZE + p).getFloat32() * matrixB.get(p * SIZE + j).getFloat32();
                }
                assertEquals(sum, matrixC.get(i * SIZE + j), 1e-3f * Math.max(1.0f, Math.abs(sum)));
            }
        }
    }

    @Test
    public void testSgemmStridedBatched() throws TornadoExecutionPlanException {
        final int batch = 8;
        final long stride = (long) SIZE * SIZE;
        FloatArray matrixA = randomArray(batch * SIZE * SIZE);
        FloatArray matrixB = randomArray(batch * SIZE * SIZE);
        FloatArray matrixC = new FloatArray(batch * SIZE * SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrixA, matrixB) //
                // Row-major batched C = A * B: column-major C_cm = B_cm * A_cm per batch
                .libraryTask("batched", CuBlas::cublasSgemmStridedBatched, //
                        CuBlasOperation.CUBLAS_OP_N.operation(), CuBlasOperation.CUBLAS_OP_N.operation(), //
                        SIZE, SIZE, SIZE, 1.0f, matrixB, SIZE, stride, matrixA, SIZE, stride, 0.0f, matrixC, SIZE, stride, batch) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixC);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int b = 0; b < batch; b++) {
            int base = b * SIZE * SIZE;
            for (int i = 0; i < SIZE; i++) {
                for (int j = 0; j < SIZE; j++) {
                    float sum = 0.0f;
                    for (int p = 0; p < SIZE; p++) {
                        sum += matrixA.get(base + i * SIZE + p) * matrixB.get(base + p * SIZE + j);
                    }
                    assertEquals(sum, matrixC.get(base + i * SIZE + j), 0.01f * Math.max(1.0f, Math.abs(sum)));
                }
            }
        }
    }

    @Test
    public void testMixedPrePostTasks() throws TornadoExecutionPlanException {
        FloatArray matrix = randomArray(SIZE * SIZE);
        FloatArray vector = randomArray(SIZE);
        FloatArray output = new FloatArray(SIZE);
        FloatArray matrixCopy = new FloatArray(SIZE * SIZE);
        for (int i = 0; i < SIZE * SIZE; i++) {
            matrixCopy.set(i, matrix.get(i));
        }

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrix, vector) //
                .task("pre", TestCuBlas::addOne, matrix) //
                .libraryTask("sgemv", CuBlas::cublasSgemv, //
                        CuBlasOperation.CUBLAS_OP_T.operation(), SIZE, SIZE, 1.0f, matrix, SIZE, vector, 1, 0.0f, output, 1) //
                .task("post", TestCuBlas::addOne, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        // Reference: (matrix + 1) * vector + 1
        FloatArray expected = new FloatArray(SIZE);
        for (int i = 0; i < SIZE * SIZE; i++) {
            matrixCopy.set(i, matrixCopy.get(i) + 1.0f);
        }
        matrixVectorJava(matrixCopy, vector, expected, SIZE, SIZE);
        for (int i = 0; i < SIZE; i++) {
            assertEquals(expected.get(i) + 1.0f, output.get(i), 0.01f * Math.max(1.0f, Math.abs(expected.get(i))));
        }
    }

    @Test
    public void testMixedTasksWithCudaGraph() throws TornadoExecutionPlanException {
        FloatArray matrix = randomArray(SIZE * SIZE);
        FloatArray vector = randomArray(SIZE);
        FloatArray output = new FloatArray(SIZE);
        FloatArray matrixOriginal = new FloatArray(SIZE * SIZE);
        for (int i = 0; i < SIZE * SIZE; i++) {
            matrixOriginal.set(i, matrix.get(i));
        }

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrix, vector) //
                .task("pre", TestCuBlas::addOne, matrix) //
                .libraryTask("sgemv", CuBlas::cublasSgemv, //
                        CuBlasOperation.CUBLAS_OP_T.operation(), SIZE, SIZE, 1.0f, matrix, SIZE, vector, 1, 0.0f, output, 1) //
                .task("post", TestCuBlas::addOne, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        FloatArray expected = new FloatArray(SIZE);
        FloatArray matrixPlusOne = new FloatArray(SIZE * SIZE);
        for (int i = 0; i < SIZE * SIZE; i++) {
            matrixPlusOne.set(i, matrixOriginal.get(i) + 1.0f);
        }
        matrixVectorJava(matrixPlusOne, vector, expected, SIZE, SIZE);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withCUDAGraph();
            // Iteration 0 captures, the rest replay
            for (int it = 0; it < 5; it++) {
                for (int i = 0; i < SIZE * SIZE; i++) {
                    matrix.set(i, matrixOriginal.get(i));
                }
                plan.execute();
                for (int i = 0; i < SIZE; i++) {
                    assertEquals("iteration " + it, expected.get(i) + 1.0f, output.get(i), 0.01f * Math.max(1.0f, Math.abs(expected.get(i))));
                }
            }
        }
    }
}
