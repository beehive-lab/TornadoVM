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
package uk.ac.manchester.tornado.examples.llm;

import java.util.Random;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.cublas.CuBlas;
import uk.ac.manchester.tornado.cublas.enums.CuBlasOperation;
import uk.ac.manchester.tornado.cusparse.Cusparse;

/**
 * Pruned-FFN sparse matvec: {@code y = W x} with W an N x K CSR matrix
 * (default 8192 x 2048), swept across sparsity levels. Honest crossover
 * benchmark: cuSPARSE SpMV only beats the dense GEMV (cuBLAS) once the matrix
 * is sparse enough - the CSV captures where.
 *
 * <pre>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.llm.LlmSparseFfn [N K]
 * </pre>
 */
public class LlmSparseFfn {

    private static final String BENCH = "llmsparseffn";
    private static final double[] DENSITIES = { 0.30, 0.10, 0.05, 0.01 };

    /** CSR SpMV: y[i] = sum over row i of values * x[col]. */
    public static void spmvJit(IntArray rowOffsets, IntArray colInd, FloatArray values, FloatArray x, FloatArray y, int rows) {
        for (@Parallel int i = 0; i < rows; i++) {
            float sum = 0.0f;
            for (int p = rowOffsets.get(i); p < rowOffsets.get(i + 1); p++) {
                sum += values.get(p) * x.get(colInd.get(p));
            }
            y.set(i, sum);
        }
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {
        if (!LlmBench.isCudaBackend()) {
            System.out.println("LlmSparseFfn requires the CUDA backend.");
            return;
        }
        final int n = args.length > 0 ? Integer.parseInt(args[0]) : 8192;
        final int k = args.length > 1 ? Integer.parseInt(args[1]) : 2048;
        System.out.printf("%n%s: pruned FFN SpMV y[N] = W[N,K] x  N=%d K=%d%n", BENCH, n, k);

        FloatArray x = LlmBench.randomFp32(k, 7, -1.0f, 1.0f);

        // Dense GEMV baseline is density-independent: W row-major [N,K] is
        // column-major [K,N], so op(A)=A^T gives y = W x.
        FloatArray wDense = new FloatArray(n * k);
        FloatArray yDense = new FloatArray(n);

        for (double density : DENSITIES) {
            String shape = n + "x" + k + "@d" + density;
            Random rng = new Random(42);
            // Build CSR + matching dense array.
            wDense.init(0.0f);
            int[] rowPtr = new int[n + 1];
            java.util.ArrayList<Integer> cols = new java.util.ArrayList<>();
            java.util.ArrayList<Float> vals = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < k; j++) {
                    if (rng.nextDouble() < density) {
                        float v = rng.nextFloat() - 0.5f;
                        wDense.set(i * k + j, v);
                        cols.add(j);
                        vals.add(v);
                    }
                }
                rowPtr[i + 1] = cols.size();
            }
            int nnz = cols.size();
            IntArray rowOffsets = new IntArray(n + 1);
            IntArray colInd = new IntArray(nnz);
            FloatArray values = new FloatArray(nnz);
            for (int i = 0; i <= n; i++) {
                rowOffsets.set(i, rowPtr[i]);
            }
            for (int i = 0; i < nnz; i++) {
                colInd.set(i, cols.get(i));
                values.set(i, vals.get(i));
            }
            // CPU reference
            FloatArray yRef = new FloatArray(n);
            for (int i = 0; i < n; i++) {
                float sum = 0.0f;
                for (int p = rowPtr[i]; p < rowPtr[i + 1]; p++) {
                    sum += vals.get(p) * x.get(cols.get(p));
                }
                yRef.set(i, sum);
            }
            double sparseGflop = 2.0 * nnz * 1e-9;
            double denseGflop = 2.0 * n * (double) k * 1e-9;
            System.out.printf("  density %.2f (nnz=%d, %.0f%% sparse)%n", density, nnz, (1.0 - density) * 100);

            FloatArray ySpJit = new FloatArray(n);
            FloatArray ySpLib = new FloatArray(n);

            // 1. JIT CSR SpMV
            TaskGraph gJit = new TaskGraph("spjit" + (int) (density * 100)) //
                    .transferToDevice(DataTransferMode.FIRST_EXECUTION, rowOffsets, colInd, values, x) //
                    .task("spmv", LlmSparseFfn::spmvJit, rowOffsets, colInd, values, x, ySpJit, n) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, ySpJit);
            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(gJit.snapshot())) {
                double ms = LlmBench.timeMs(plan, LlmBench.DEFAULT_WARMUP, LlmBench.DEFAULT_ITERATIONS);
                String v = LlmBench.checkTol("jit-csr-spmv", LlmBench.maxRelError(ySpJit, yRef), 1e-3f);
                LlmBench.report(BENCH, "jit-csr-spmv", "fp32", shape, ms, sparseGflop / (ms * 1e-3), v);
            }

            // 2. cuSPARSE SpMV
            TaskGraph gLib = new TaskGraph("splib" + (int) (density * 100)) //
                    .transferToDevice(DataTransferMode.FIRST_EXECUTION, rowOffsets, colInd, values, x) //
                    .libraryTask("spmv", Cusparse::cusparseSpMV, n, k, nnz, rowOffsets, colInd, values, x, ySpLib) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, ySpLib);
            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(gLib.snapshot())) {
                double ms = LlmBench.timeMs(plan, LlmBench.DEFAULT_WARMUP, LlmBench.DEFAULT_ITERATIONS);
                String v = LlmBench.checkTol("cusparse-spmv", LlmBench.maxRelError(ySpLib, yRef), 1e-3f);
                LlmBench.report(BENCH, "cusparse-spmv", "fp32", shape, ms, sparseGflop / (ms * 1e-3), v);
            }

            // 3. Dense cuBLAS GEMV on the same logical matrix (zeros included)
            TaskGraph gDense = new TaskGraph("dense" + (int) (density * 100)) //
                    .transferToDevice(DataTransferMode.FIRST_EXECUTION, wDense, x) //
                    .libraryTask("gemv", CuBlas::cublasSgemv, //
                            CuBlasOperation.CUBLAS_OP_T.operation(), k, n, 1.0f, wDense, k, x, 1, 0.0f, yDense, 1) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, yDense);
            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(gDense.snapshot())) {
                double ms = LlmBench.timeMs(plan, LlmBench.DEFAULT_WARMUP, LlmBench.DEFAULT_ITERATIONS);
                String v = LlmBench.checkTol("cublas-dense-gemv", LlmBench.maxRelError(yDense, yRef), 1e-3f);
                LlmBench.report(BENCH, "cublas-dense-gemv", "fp32", shape, ms, denseGflop / (ms * 1e-3), v);
            }
        }

        System.exit(0);
    }
}
