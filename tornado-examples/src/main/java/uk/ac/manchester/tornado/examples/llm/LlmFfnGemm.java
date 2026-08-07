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

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid2D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.BFloat16;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;
import uk.ac.manchester.tornado.cublas.CuBlas;
import uk.ac.manchester.tornado.cublas.CuBlasLt;
import uk.ac.manchester.tornado.cublas.enums.CuBlasOperation;
import uk.ac.manchester.tornado.cutlass.Cutlass;
import uk.ac.manchester.tornado.examples.compute.MatrixMultiplicationMMA;

/**
 * LLM FFN up-projection GEMM (prefill): {@code C[M,N] = A[M,K] * W[K,N]} at
 * Llama shapes (default M=512, N=8192, K=2048 = Llama-3.2-1B gate/up
 * projection). Climbs the whole TornadoVM-on-NVIDIA ladder in one run:
 *
 * <ol>
 * <li>{@code jit-loop-fp32} - plain {@code @Parallel} Java loops (what you get for free)</li>
 * <li>{@code kc-mma-fp16} - hand-written KernelContext tensor-core kernel (mma.sync)</li>
 * <li>{@code kc-mma-cpasync-fp16} - same + cp.async global-to-shared tile copies</li>
 * <li>{@code kc-mma-bf16} - same tiling, bf16 operands</li>
 * <li>{@code cublas-tf32} - cuBLAS library task, TF32 tensor cores, FP32 data</li>
 * <li>{@code cublaslt-fp16} - cuBLASLt library task, FP16 tensor cores</li>
 * <li>{@code cutlass-hgemm} - CUTLASS library task, FP16 tensor cores</li>
 * </ol>
 *
 * All variants are validated against a cuBLASLt FP32 device reference.
 *
 * <pre>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.llm.LlmFfnGemm [M N K]
 * </pre>
 */
public class LlmFfnGemm {

    private static final String BENCH = "llmffn";

    // Must match the (private) tiling of MatrixMultiplicationMMA kernels.
    private static final int BM = 128;
    private static final int BN = 128;
    private static final int THREADS_PER_BLOCK = 256;

    public static void gemmJitFp32(FloatArray a, FloatArray b, FloatArray c, int m, int n, int k) {
        for (@Parallel int i = 0; i < m; i++) {
            for (@Parallel int j = 0; j < n; j++) {
                float sum = 0.0f;
                for (int p = 0; p < k; p++) {
                    sum += a.get(i * k + p) * b.get(p * n + j);
                }
                c.set(i * n + j, sum);
            }
        }
    }

    private static GridScheduler mmaGrid(String taskId, int m, int n) {
        WorkerGrid2D grid = new WorkerGrid2D((m / BM) * THREADS_PER_BLOCK, n / BN);
        grid.setLocalWork(THREADS_PER_BLOCK, 1, 1);
        return new GridScheduler(taskId, grid);
    }

    private static ShortArray toBf16(HalfFloatArray src) {
        ShortArray out = new ShortArray(src.getSize());
        for (int i = 0; i < src.getSize(); i++) {
            out.set(i, BFloat16.bf16FromFloat(src.get(i).getFloat32()));
        }
        return out;
    }

    public static void main(String[] args) throws uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException {
        if (!LlmBench.isCudaBackend()) {
            System.out.println("LlmFfnGemm requires the CUDA backend.");
            return;
        }
        final int m = args.length > 0 ? Integer.parseInt(args[0]) : 512;
        final int n = args.length > 1 ? Integer.parseInt(args[1]) : 8192;
        final int k = args.length > 2 ? Integer.parseInt(args[2]) : 2048;
        final String shape = m + "x" + n + "x" + k;
        System.out.printf("%n%s: FFN up-projection GEMM  M=%d N=%d K=%d%n", BENCH, m, n, k);

        HalfFloatArray aH = LlmBench.randomFp16(m * k, 1, -1.0f, 1.0f);
        HalfFloatArray bH = LlmBench.randomFp16(k * n, 2, -1.0f, 1.0f);
        FloatArray aF = LlmBench.toFp32(aH);
        FloatArray bF = LlmBench.toFp32(bH);
        ShortArray aBf = toBf16(aH);
        ShortArray bBf = toBf16(bH);

        FloatArray cRef = new FloatArray(m * n);
        FloatArray cJit = new FloatArray(m * n);
        FloatArray cMma = new FloatArray(m * n);
        FloatArray cCpAsync = new FloatArray(m * n);
        FloatArray cBf16 = new FloatArray(m * n);
        FloatArray cTf32 = new FloatArray(m * n);
        HalfFloatArray cLt = new HalfFloatArray(m * n);
        HalfFloatArray cCutlass = new HalfFloatArray(m * n);

        // Device FP32 reference via cuBLASLt (row-major trick: C^T = B^T * A^T).
        TaskGraph gRef = new TaskGraph("ref") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, aF, bF) //
                .libraryTask("lt32", CuBlasLt::ltMatmulFP32, //
                        CuBlasOperation.CUBLAS_OP_N.operation(), CuBlasOperation.CUBLAS_OP_N.operation(), //
                        n, m, k, 1.0f, bF, n, aF, k, 0.0f, cRef, n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cRef);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(gRef.snapshot())) {
            plan.execute();
        }

        // 1. JIT loop kernel, FP32
        TaskGraph gJit = new TaskGraph("jit") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, aF, bF) //
                .task("gemm", LlmFfnGemm::gemmJitFp32, aF, bF, cJit, m, n, k) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cJit);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(gJit.snapshot())) {
            double ms = LlmBench.timeMs(plan, LlmBench.DEFAULT_WARMUP, LlmBench.DEFAULT_ITERATIONS);
            String v = LlmBench.checkTol("jit-loop-fp32", LlmBench.maxRelError(cJit, cRef), 1e-3f);
            LlmBench.report(BENCH, "jit-loop-fp32", "fp32", shape, ms, LlmBench.gflopsGemm(m, n, k, ms), v);
        }

        // 2. KernelContext MMA, FP16
        TaskGraph gMma = new TaskGraph("mma") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, aH, bH) //
                .task("gemm", MatrixMultiplicationMMA::gemmMMA, new KernelContext(), aH, bH, cMma, m, n, k) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cMma);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(gMma.snapshot())) {
            plan.withGridScheduler(mmaGrid("mma.gemm", m, n));
            double ms = LlmBench.timeMs(plan, LlmBench.DEFAULT_WARMUP, LlmBench.DEFAULT_ITERATIONS);
            String v = LlmBench.checkTol("kc-mma-fp16", LlmBench.maxRelError(cMma, cRef), 2e-2f);
            LlmBench.report(BENCH, "kc-mma-fp16", "fp16", shape, ms, LlmBench.gflopsGemm(m, n, k, ms), v);
        }

        // 3. KernelContext MMA + cp.async, FP16
        TaskGraph gCpAsync = new TaskGraph("cpasync") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, aH, bH) //
                .task("gemm", MatrixMultiplicationMMA::gemmMMACpAsync, new KernelContext(), aH, bH, cCpAsync, m, n, k) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cCpAsync);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(gCpAsync.snapshot())) {
            plan.withGridScheduler(mmaGrid("cpasync.gemm", m, n));
            double ms = LlmBench.timeMs(plan, LlmBench.DEFAULT_WARMUP, LlmBench.DEFAULT_ITERATIONS);
            String v = LlmBench.checkTol("kc-mma-cpasync-fp16", LlmBench.maxRelError(cCpAsync, cRef), 2e-2f);
            LlmBench.report(BENCH, "kc-mma-cpasync-fp16", "fp16", shape, ms, LlmBench.gflopsGemm(m, n, k, ms), v);
        }

        // 4. KernelContext MMA, BF16
        TaskGraph gBf16 = new TaskGraph("bf16") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, aBf, bBf) //
                .task("gemm", MatrixMultiplicationMMA::gemmMMABF16, new KernelContext(), aBf, bBf, cBf16, m, n, k) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cBf16);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(gBf16.snapshot())) {
            plan.withGridScheduler(mmaGrid("bf16.gemm", m, n));
            double ms = LlmBench.timeMs(plan, LlmBench.DEFAULT_WARMUP, LlmBench.DEFAULT_ITERATIONS);
            float bf16Tol = (float) (Math.sqrt(k) / 256.0);
            String v = LlmBench.checkTol("kc-mma-bf16", LlmBench.maxRelError(cBf16, cRef), bf16Tol);
            LlmBench.report(BENCH, "kc-mma-bf16", "bf16", shape, ms, LlmBench.gflopsGemm(m, n, k, ms), v);
        }

        // 5. cuBLAS TF32 (FP32 data, TF32 tensor cores)
        TaskGraph gTf32 = new TaskGraph("tf32") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, aF, bF) //
                .libraryTask("gemm", CuBlas::cublasSgemmTF32, //
                        CuBlasOperation.CUBLAS_OP_N.operation(), CuBlasOperation.CUBLAS_OP_N.operation(), //
                        n, m, k, 1.0f, bF, n, aF, k, 0.0f, cTf32, n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cTf32);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(gTf32.snapshot())) {
            double ms = LlmBench.timeMs(plan, LlmBench.DEFAULT_WARMUP, LlmBench.DEFAULT_ITERATIONS);
            String v = LlmBench.checkTol("cublas-tf32", LlmBench.maxRelError(cTf32, cRef), 1e-2f);
            LlmBench.report(BENCH, "cublas-tf32", "tf32", shape, ms, LlmBench.gflopsGemm(m, n, k, ms), v);
        }

        // 6. cuBLASLt FP16
        TaskGraph gLt = new TaskGraph("lt") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, aH, bH) //
                .libraryTask("gemm", CuBlasLt::ltMatmulFP16, //
                        CuBlasOperation.CUBLAS_OP_N.operation(), CuBlasOperation.CUBLAS_OP_N.operation(), //
                        n, m, k, 1.0f, bH, n, aH, k, 0.0f, cLt, n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cLt);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(gLt.snapshot())) {
            double ms = LlmBench.timeMs(plan, LlmBench.DEFAULT_WARMUP, LlmBench.DEFAULT_ITERATIONS);
            String v = LlmBench.checkTol("cublaslt-fp16", LlmBench.maxRelError(cLt, cRef), 2e-2f);
            LlmBench.report(BENCH, "cublaslt-fp16", "fp16", shape, ms, LlmBench.gflopsGemm(m, n, k, ms), v);
        }

        // 7. CUTLASS FP16 (row-major, no transpose gymnastics)
        TaskGraph gCutlass = new TaskGraph("cutlass") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, aH, bH) //
                .libraryTask("gemm", Cutlass::cutlassHgemm, m, n, k, 1.0f, aH, bH, 0.0f, cCutlass) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cCutlass);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(gCutlass.snapshot())) {
            double ms = LlmBench.timeMs(plan, LlmBench.DEFAULT_WARMUP, LlmBench.DEFAULT_ITERATIONS);
            String v = LlmBench.checkTol("cutlass-hgemm", LlmBench.maxRelError(cCutlass, cRef), 2e-2f);
            LlmBench.report(BENCH, "cutlass-hgemm", "fp16", shape, ms, LlmBench.gflopsGemm(m, n, k, ms), v);
        }

        System.exit(0);
    }
}
