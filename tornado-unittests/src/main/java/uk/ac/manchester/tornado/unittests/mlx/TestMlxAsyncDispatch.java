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
package uk.ac.manchester.tornado.unittests.mlx;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.mlx.Mlx;

/**
 * Host reads and writes after MLX library tasks that are dispatched without waiting for the GPU
 * ({@code -Dtornado.mlx.async=true}). The library's command buffers signal none of the Metal
 * backend's events, so a read of their output that follows them directly must still wait for them
 * rather than return the buffer's previous contents. Each test checks the outputs against a
 * sequential Java reference. Skipped unless the default device is on the Metal backend and
 * mlx.metallib is available.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V --jvm="-Dtornado.mlx.async=true" uk.ac.manchester.tornado.unittests.mlx.TestMlxAsyncDispatch
 * </code>
 */
public class TestMlxAsyncDispatch extends MlxTestBase {

    private static final int M = 64;
    private static final int K = 512;
    private static final int N = 384;

    /** c[m, n] = a[m, k] @ w[n, k]^T over the values the float16 arrays hold. */
    private static double[] matmulTransposedJava(float[] a, float[] w, int m, int k, int n) {
        double[] c = new double[m * n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                double sum = 0;
                for (int p = 0; p < k; p++) {
                    sum += (double) a[i * k + p] * w[j * k + p];
                }
                c[i * n + j] = sum;
            }
        }
        return c;
    }

    public static void copy(KernelContext context, HalfFloatArray in, HalfFloatArray out) {
        int i = context.globalIdx;
        if (i < in.getSize()) {
            out.set(i, in.get(i));
        }
    }

    private static void assertMatmul(String what, HalfFloatArray a, HalfFloatArray w, HalfFloatArray c) {
        assertAllClose(what, matmulTransposedJava(widen(a), widen(w), M, K, N), c, 1e-2, 5e-2);
    }

    @Test
    public void testReadAfterLibraryTask() throws TornadoExecutionPlanException {
        HalfFloatArray a = half(values(M * K, -1, 1, 1));
        HalfFloatArray w = half(values(N * K, -1, 1, 2));
        HalfFloatArray c = new HalfFloatArray(M * N);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, w) //
                .libraryTask("matmul", Mlx::matmulTransposed, a, w, c, M, K, N) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        run(taskGraph);

        assertMatmul("matmul", a, w, c);
    }

    @Test
    public void testReadAfterSeveralLibraryTasks() throws TornadoExecutionPlanException {
        HalfFloatArray a = half(values(M * K, -1, 1, 3));
        HalfFloatArray[] w = new HalfFloatArray[3];
        HalfFloatArray[] c = new HalfFloatArray[3];
        TaskGraph taskGraph = new TaskGraph("g").transferToDevice(DataTransferMode.FIRST_EXECUTION, a);
        for (int i = 0; i < w.length; i++) {
            w[i] = half(values(N * K, -1, 1, 4 + i));
            c[i] = new HalfFloatArray(M * N);
            taskGraph.transferToDevice(DataTransferMode.FIRST_EXECUTION, w[i]);
            taskGraph.libraryTask("matmul" + i, Mlx::matmulTransposed, a, w[i], c[i], M, K, N);
        }
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, c[0], c[1], c[2]);
        run(taskGraph);

        for (int i = 0; i < w.length; i++) {
            assertMatmul("matmul " + i, a, w[i], c[i]);
        }
    }

    /**
     * Every execution writes a new input before the library task reads it and reads the output after:
     * neither host copy may overlap the previous execution's library work.
     */
    @Test
    public void testRepeatedExecutionWithNewInputs() throws TornadoExecutionPlanException {
        HalfFloatArray a = new HalfFloatArray(M * K);
        HalfFloatArray w = half(values(N * K, -1, 1, 8));
        HalfFloatArray c = new HalfFloatArray(M * N);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, w) //
                .libraryTask("matmul", Mlx::matmulTransposed, a, w, c, M, K, N) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            for (int round = 0; round < 4; round++) {
                float[] values = values(M * K, -1, 1, 20 + round);
                for (int i = 0; i < values.length; i++) {
                    a.set(i, new HalfFloat(values[i]));
                }
                plan.execute();
                assertMatmul("matmul, execution " + round, a, w, c);
            }
        }
    }

    /** A kernel after the library task, reading its output on the device: ordered by the queue. */
    @Test
    public void testKernelAfterLibraryTask() throws TornadoExecutionPlanException {
        HalfFloatArray a = half(values(M * K, -1, 1, 9));
        HalfFloatArray w = half(values(N * K, -1, 1, 10));
        HalfFloatArray c = new HalfFloatArray(M * N);
        HalfFloatArray d = new HalfFloatArray(M * N);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, w) //
                .libraryTask("matmul", Mlx::matmulTransposed, a, w, c, M, K, N) //
                .task("copy", TestMlxAsyncDispatch::copy, context, c, d) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c, d);
        GridScheduler grid = new GridScheduler("g.copy", new WorkerGrid1D(M * N));

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }

        assertMatmul("matmul", a, w, c);
        assertMatmul("copy of matmul", a, w, d);
    }
}
