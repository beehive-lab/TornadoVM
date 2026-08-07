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

import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid2D;
import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.cublas.CuBlasLt;
import uk.ac.manchester.tornado.cublas.enums.CuBlasOperation;
import uk.ac.manchester.tornado.cutlass.Cutlass;
import uk.ac.manchester.tornado.examples.compute.MatrixMultiplicationMMA;

/**
 * LLM FFN up-projection with fused epilogue: {@code C = gelu(A * W + bias)}
 * (tanh-approximation GELU). Compares an unfused two-task TaskGraph (tensor-core
 * MMA GEMM + separate JIT GELU kernel) against single fused library kernels
 * from cuBLASLt and CUTLASS - the "one TaskGraph, JIT + fused epilogue" story.
 *
 * <pre>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.llm.LlmFfnFused [M N K]
 * </pre>
 */
public class LlmFfnFused {

    private static final String BENCH = "llmffnfused";
    private static final int BM = 128;
    private static final int BN = 128;
    private static final int THREADS_PER_BLOCK = 256;

    /** GELU (tanh approximation) + bias applied to the FP32 GEMM result. */
    public static void biasGelu(FloatArray in, FloatArray bias, FloatArray out, int m, int n) {
        for (@Parallel int i = 0; i < m; i++) {
            for (@Parallel int j = 0; j < n; j++) {
                float x = in.get(i * n + j) + bias.get(j);
                float inner = 0.7978845608f * (x + 0.044715f * x * x * x);
                out.set(i * n + j, 0.5f * x * (1.0f + TornadoMath.tanh(inner)));
            }
        }
    }

    private static float geluRef(float x) {
        return 0.5f * x * (1.0f + (float) Math.tanh(0.7978845608 * (x + 0.044715 * x * x * x)));
    }

    public static void main(String[] args) throws uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException {
        if (!LlmBench.isCudaBackend()) {
            System.out.println("LlmFfnFused requires the CUDA backend.");
            return;
        }
        final int m = args.length > 0 ? Integer.parseInt(args[0]) : 512;
        final int n = args.length > 1 ? Integer.parseInt(args[1]) : 8192;
        final int k = args.length > 2 ? Integer.parseInt(args[2]) : 2048;
        final String shape = m + "x" + n + "x" + k;
        System.out.printf("%n%s: fused GEMM+bias+GELU  M=%d N=%d K=%d%n", BENCH, m, n, k);

        HalfFloatArray aH = LlmBench.randomFp16(m * k, 1, -0.5f, 0.5f);
        HalfFloatArray bH = LlmBench.randomFp16(k * n, 2, -0.5f, 0.5f);
        HalfFloatArray biasH = LlmBench.randomFp16(n, 3, -0.5f, 0.5f);
        FloatArray biasF = LlmBench.toFp32(biasH);
        FloatArray aF = LlmBench.toFp32(aH);
        FloatArray bF = LlmBench.toFp32(bH);

        FloatArray gemmScratch = new FloatArray(m * n);
        FloatArray cJit = new FloatArray(m * n);
        HalfFloatArray cLt = new HalfFloatArray(m * n);
        HalfFloatArray cCutlass = new HalfFloatArray(m * n);
        FloatArray cRefGemm = new FloatArray(m * n);
        FloatArray cRef = new FloatArray(m * n);

        // Device FP32 GEMM reference + host GELU epilogue.
        TaskGraph gRef = new TaskGraph("ref") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, aF, bF) //
                .libraryTask("lt32", CuBlasLt::ltMatmulFP32, //
                        CuBlasOperation.CUBLAS_OP_N.operation(), CuBlasOperation.CUBLAS_OP_N.operation(), //
                        n, m, k, 1.0f, bF, n, aF, k, 0.0f, cRefGemm, n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cRefGemm);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(gRef.snapshot())) {
            plan.execute();
        }
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                cRef.set(i * n + j, geluRef(cRefGemm.get(i * n + j) + biasF.get(j)));
            }
        }

        // 1. Unfused: tensor-core MMA GEMM task + JIT bias+GELU task, one TaskGraph.
        WorkerGrid2D grid = new WorkerGrid2D((m / BM) * THREADS_PER_BLOCK, n / BN);
        grid.setLocalWork(THREADS_PER_BLOCK, 1, 1);
        GridScheduler gs = new GridScheduler("fused2.gemm", grid);
        TaskGraph gJit = new TaskGraph("fused2") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, aH, bH, biasF) //
                .task("gemm", MatrixMultiplicationMMA::gemmMMA, new KernelContext(), aH, bH, gemmScratch, m, n, k) //
                .task("gelu", LlmFfnFused::biasGelu, gemmScratch, biasF, cJit, m, n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cJit);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(gJit.snapshot())) {
            plan.withGridScheduler(gs);
            double ms = LlmBench.timeMs(plan, LlmBench.DEFAULT_WARMUP, LlmBench.DEFAULT_ITERATIONS);
            String v = LlmBench.checkTol("kc-mma+jit-gelu", LlmBench.maxRelError(cJit, cRef), 2e-2f);
            LlmBench.report(BENCH, "kc-mma+jit-gelu", "fp16", shape, ms, LlmBench.gflopsGemm(m, n, k, ms), v);
        }

        // 2. cuBLASLt fused GEMM+bias+GELU (bias per column-major row = per output feature).
        TaskGraph gLt = new TaskGraph("ltfused") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, aH, bH, biasH) //
                .libraryTask("gemm", CuBlasLt::ltMatmulGeluBiasFP16, //
                        CuBlasOperation.CUBLAS_OP_N.operation(), CuBlasOperation.CUBLAS_OP_N.operation(), //
                        n, m, k, 1.0f, bH, n, aH, k, 0.0f, cLt, n, biasH) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cLt);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(gLt.snapshot())) {
            double ms = LlmBench.timeMs(plan, LlmBench.DEFAULT_WARMUP, LlmBench.DEFAULT_ITERATIONS);
            String v = LlmBench.checkTol("cublaslt-gelu-bias", LlmBench.maxRelError(cLt, cRef), 2e-2f);
            LlmBench.report(BENCH, "cublaslt-gelu-bias", "fp16", shape, ms, LlmBench.gflopsGemm(m, n, k, ms), v);
        }

        // 3. CUTLASS fused GEMM+bias+GELU (row-major, bias length n).
        TaskGraph gCutlass = new TaskGraph("cutlassfused") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, aH, bH, biasH) //
                .libraryTask("gemm", Cutlass::cutlassGemmBiasGelu, m, n, k, aH, bH, biasH, cCutlass) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cCutlass);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(gCutlass.snapshot())) {
            double ms = LlmBench.timeMs(plan, LlmBench.DEFAULT_WARMUP, LlmBench.DEFAULT_ITERATIONS);
            String v = LlmBench.checkTol("cutlass-gelu-bias", LlmBench.maxRelError(cCutlass, cRef), 2e-2f);
            LlmBench.report(BENCH, "cutlass-gelu-bias", "fp16", shape, ms, LlmBench.gflopsGemm(m, n, k, ms), v);
        }

        System.exit(0);
    }
}
