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
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.FP8;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.Int8Array;
import uk.ac.manchester.tornado.api.utils.QuantizationUtils;
import uk.ac.manchester.tornado.cublas.CuBlasLt;
import uk.ac.manchester.tornado.cublas.enums.CuBlasOperation;

/**
 * LLM decode-time GEMV with quantized weights: {@code y[N] = W[N,K] * x[K]}
 * (M=1, the token-generation hot loop; weight reads dominate). Default
 * N=8192, K=2048 (Llama-3.2-1B FFN down-projection shape).
 *
 * <ul>
 * <li>{@code kc-gemv-fp16} - workgroup-per-row GEMV, FP16 weights (2 B/elem)</li>
 * <li>{@code kc-gemv-fp8-e4m3} - same kernel with FP8 weights (1 B/elem);
 * {@code FP8.e4m3ToFloat} compiles to the native {@code __nv_cvt_fp8_to_halfraw}
 * hardware conversion on sm_89 (PR #952)</li>
 * <li>{@code kc-gemv-int8-dp4a} - INT8 weights and activations via dp4a</li>
 * <li>{@code cublaslt-fp8} - cuBLASLt FP8 tensor-core matmul (n=1 column)</li>
 * </ul>
 *
 * <pre>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.llm.LlmDequantGemv [N K]
 * </pre>
 */
public class LlmDequantGemv {

    private static final String BENCH = "llmdequantgemv";
    private static final int LOCAL = 256;

    public static void gemvFp16(KernelContext ctx, HalfFloatArray w, FloatArray x, FloatArray y, int k) {
        int row = ctx.groupIdx;
        int lid = ctx.localIdx;
        float[] partial = ctx.allocateFloatLocalArray(LOCAL);
        float sum = 0.0f;
        int base = row * k;
        for (int j = lid; j < k; j += LOCAL) {
            sum += w.get(base + j).getFloat32() * x.get(j);
        }
        partial[lid] = sum;
        ctx.localBarrier();
        for (int s = LOCAL / 2; s > 0; s >>= 1) {
            if (lid < s) {
                partial[lid] += partial[lid + s];
            }
            ctx.localBarrier();
        }
        if (lid == 0) {
            y.set(row, partial[0]);
        }
    }

    public static void gemvFp8(KernelContext ctx, ByteArray w, FloatArray x, FloatArray y, int k) {
        int row = ctx.groupIdx;
        int lid = ctx.localIdx;
        float[] partial = ctx.allocateFloatLocalArray(LOCAL);
        float sum = 0.0f;
        int base = row * k;
        for (int j = lid; j < k; j += LOCAL) {
            sum += FP8.e4m3ToFloat(w.get(base + j)) * x.get(j);
        }
        partial[lid] = sum;
        ctx.localBarrier();
        for (int s = LOCAL / 2; s > 0; s >>= 1) {
            if (lid < s) {
                partial[lid] += partial[lid + s];
            }
            ctx.localBarrier();
        }
        if (lid == 0) {
            y.set(row, partial[0]);
        }
    }

    public static void gemvInt8Dp4a(KernelContext ctx, Int8Array w, Int8Array xq, FloatArray y, int k, float wScale, float xScale) {
        int row = ctx.groupIdx;
        int lid = ctx.localIdx;
        int[] partial = ctx.allocateIntLocalArray(LOCAL);
        int acc = 0;
        int base = row * k;
        for (int j = lid * 4; j < k; j += LOCAL * 4) {
            acc = QuantizationUtils.dp4a(w, base + j, xq, j, acc);
        }
        partial[lid] = acc;
        ctx.localBarrier();
        for (int s = LOCAL / 2; s > 0; s >>= 1) {
            if (lid < s) {
                partial[lid] += partial[lid + s];
            }
            ctx.localBarrier();
        }
        if (lid == 0) {
            y.set(row, QuantizationUtils.dequantizeFusedResult(partial[0], wScale, xScale));
        }
    }

    static GridScheduler rowGrid(String taskId, int rows) {
        WorkerGrid1D grid = new WorkerGrid1D(rows * LOCAL);
        grid.setLocalWork(LOCAL, 1, 1);
        return new GridScheduler(taskId, grid);
    }

    public static void main(String[] args) throws uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException {
        if (!LlmBench.isCudaBackend()) {
            System.out.println("LlmDequantGemv requires the CUDA backend.");
            return;
        }
        final int n = args.length > 0 ? Integer.parseInt(args[0]) : 8192;
        final int k = args.length > 1 ? Integer.parseInt(args[1]) : 2048;
        final String shape = "1x" + n + "x" + k;
        System.out.printf("%n%s: decode GEMV y[N] = W[N,K] * x  N=%d K=%d%n", BENCH, n, k);
        final double weightGB = (double) n * k;

        // FP16 master weights; every quantized form is derived from (and validated
        // against) the same logical matrix.
        HalfFloatArray wH = LlmBench.randomFp16((int) (n * (long) k), 1, -1.0f, 1.0f);
        FloatArray x = LlmBench.randomFp32(k, 2, -1.0f, 1.0f);

        ByteArray wFp8 = new ByteArray(n * k);
        float[] wFp8AsFloat = new float[n * k];
        for (int i = 0; i < n * k; i++) {
            byte e4m3 = FP8.e4m3FromFloat(wH.get(i).getFloat32());
            wFp8.set(i, e4m3);
            wFp8AsFloat[i] = FP8.e4m3ToFloat(e4m3);
        }

        // Per-tensor symmetric INT8 quantization of weights and activations.
        float wMax = 0.0f;
        for (int i = 0; i < n * k; i++) {
            wMax = Math.max(wMax, Math.abs(wH.get(i).getFloat32()));
        }
        float xMax = 0.0f;
        for (int i = 0; i < k; i++) {
            xMax = Math.max(xMax, Math.abs(x.get(i)));
        }
        final float wScale = wMax / 127.0f;
        final float xScale = xMax / 127.0f;
        Int8Array wQ = new Int8Array(n * k);
        Int8Array xQ = new Int8Array(k);
        for (int i = 0; i < n * k; i++) {
            wQ.set(i, (byte) Math.round(wH.get(i).getFloat32() / wScale));
        }
        for (int i = 0; i < k; i++) {
            xQ.set(i, (byte) Math.round(x.get(i) / xScale));
        }

        // FP8 activations for the cuBLASLt FP8 path.
        ByteArray xFp8 = new ByteArray(k);
        float[] xFp8AsFloat = new float[k];
        for (int i = 0; i < k; i++) {
            xFp8.set(i, FP8.e4m3FromFloat(x.get(i)));
            xFp8AsFloat[i] = FP8.e4m3ToFloat(xFp8.get(i));
        }

        // CPU references (each variant against its own quantized weights, so the
        // check isolates kernel correctness from quantization error).
        FloatArray refFp16 = new FloatArray(n);
        FloatArray refFp8 = new FloatArray(n);
        for (int i = 0; i < n; i++) {
            float sF16 = 0.0f;
            float sF8 = 0.0f;
            for (int j = 0; j < k; j++) {
                sF16 += wH.get(i * k + j).getFloat32() * x.get(j);
                sF8 += wFp8AsFloat[i * k + j] * x.get(j);
            }
            refFp16.set(i, sF16);
            refFp8.set(i, sF8);
        }

        FloatArray yFp16 = new FloatArray(n);
        FloatArray yFp8 = new FloatArray(n);
        FloatArray yInt8 = new FloatArray(n);
        HalfFloatArray yLtFp8 = new HalfFloatArray(n);

        // 1. FP16 weights (bandwidth baseline: 2 B/elem)
        TaskGraph gFp16 = new TaskGraph("g16") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, wH, x) //
                .task("gemv", LlmDequantGemv::gemvFp16, new KernelContext(), wH, x, yFp16, k) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, yFp16);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(gFp16.snapshot())) {
            plan.withGridScheduler(rowGrid("g16.gemv", n));
            double ms = LlmBench.timeMs(plan, LlmBench.DEFAULT_WARMUP, LlmBench.DEFAULT_ITERATIONS);
            String v = LlmBench.checkTol("kc-gemv-fp16", LlmBench.maxRelError(yFp16, refFp16), 1e-2f);
            double gbs = weightGB * 2 / (ms * 1e-3) / 1e9;
            LlmBench.report(BENCH, "kc-gemv-fp16", "fp16", shape, ms, LlmBench.gflopsGemm(1, n, k, ms), v + " " + String.format("%.0f GB/s", gbs));
        }

        // 2. FP8 weights, native hardware decode on sm_89 (1 B/elem)
        TaskGraph gFp8 = new TaskGraph("g8") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, wFp8, x) //
                .task("gemv", LlmDequantGemv::gemvFp8, new KernelContext(), wFp8, x, yFp8, k) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, yFp8);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(gFp8.snapshot())) {
            plan.withGridScheduler(rowGrid("g8.gemv", n));
            double ms = LlmBench.timeMs(plan, LlmBench.DEFAULT_WARMUP, LlmBench.DEFAULT_ITERATIONS);
            String v = LlmBench.checkTol("kc-gemv-fp8-e4m3", LlmBench.maxRelError(yFp8, refFp8), 1e-2f);
            double gbs = weightGB / (ms * 1e-3) / 1e9;
            LlmBench.report(BENCH, "kc-gemv-fp8-e4m3", "fp8", shape, ms, LlmBench.gflopsGemm(1, n, k, ms), v + " " + String.format("%.0f GB/s", gbs));
        }

        // 3. INT8 weights + activations via dp4a (1 B/elem, integer tensor path)
        FloatArray refInt8 = new FloatArray(n);
        for (int i = 0; i < n; i++) {
            int acc = 0;
            for (int j = 0; j < k; j++) {
                acc += wQ.get(i * k + j) * xQ.get(j);
            }
            refInt8.set(i, acc * wScale * xScale);
        }
        TaskGraph gInt8 = new TaskGraph("gi8") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, wQ, xQ) //
                .task("gemv", LlmDequantGemv::gemvInt8Dp4a, new KernelContext(), wQ, xQ, yInt8, k, wScale, xScale) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, yInt8);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(gInt8.snapshot())) {
            plan.withGridScheduler(rowGrid("gi8.gemv", n));
            double ms = LlmBench.timeMs(plan, LlmBench.DEFAULT_WARMUP, LlmBench.DEFAULT_ITERATIONS);
            String v = LlmBench.checkTol("kc-gemv-int8-dp4a", LlmBench.maxRelError(yInt8, refInt8), 1e-2f);
            double gbs = weightGB / (ms * 1e-3) / 1e9;
            LlmBench.report(BENCH, "kc-gemv-int8-dp4a", "int8", shape, ms, LlmBench.gflopsGemm(1, n, k, ms), v + " " + String.format("%.0f GB/s", gbs));
        }

        // 4. cuBLASLt FP8 tensor cores (TN form; W row-major [N,K] is col-major [K,N])
        FloatArray refLtFp8 = new FloatArray(n);
        for (int i = 0; i < n; i++) {
            float s = 0.0f;
            for (int j = 0; j < k; j++) {
                s += wFp8AsFloat[i * k + j] * xFp8AsFloat[j];
            }
            refLtFp8.set(i, s);
        }
        try {
            TaskGraph gLt = new TaskGraph("glt8") //
                    .transferToDevice(DataTransferMode.FIRST_EXECUTION, wFp8, xFp8) //
                    .libraryTask("gemv", CuBlasLt::ltMatmulFP8, //
                            CuBlasOperation.CUBLAS_OP_T.operation(), CuBlasOperation.CUBLAS_OP_N.operation(), //
                            n, 1, k, 1.0f, wFp8, k, xFp8, k, 0.0f, yLtFp8, n) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, yLtFp8);
            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(gLt.snapshot())) {
                double ms = LlmBench.timeMs(plan, LlmBench.DEFAULT_WARMUP, LlmBench.DEFAULT_ITERATIONS);
                String v = LlmBench.checkTol("cublaslt-fp8", LlmBench.maxRelError(yLtFp8, refLtFp8), 2e-2f);
                double gbs = weightGB / (ms * 1e-3) / 1e9;
                LlmBench.report(BENCH, "cublaslt-fp8", "fp8", shape, ms, LlmBench.gflopsGemm(1, n, k, ms), v + " " + String.format("%.0f GB/s", gbs));
            }
        } catch (Exception e) {
            System.out.println("  cublaslt-fp8 unavailable on this configuration: " + e.getMessage());
            LlmBench.csv(BENCH, "cublaslt-fp8", "fp8", shape, 0, 0, "UNSUPPORTED");
        }

        System.exit(0);
    }
}
