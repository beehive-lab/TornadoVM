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
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.Int8Array;
import uk.ac.manchester.tornado.cublas.CuBlas;
import uk.ac.manchester.tornado.cublas.enums.CuBlasOperation;

/**
 * LLM vocabulary (logits) projection at decode time: {@code y[V] = W[V,K] * x[K]}
 * with V=128256, K=2048 (Llama-3 vocabulary; ~0.5 GB of FP16 weights - the
 * biggest single matrix in a small Llama, purely memory-bound at M=1).
 *
 * <ul>
 * <li>{@code kc-gemv-fp16} - workgroup-per-row KernelContext GEMV</li>
 * <li>{@code kc-gemv-int8-dp4a} - INT8 weights + dp4a (halves the weight traffic)</li>
 * <li>{@code cublas-gemmex-fp16} - cublasGemmEx, FP16 in / FP32 out</li>
 * </ul>
 *
 * <pre>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.llm.LlmLogits [V K]
 * </pre>
 */
public class LlmLogits {

    private static final String BENCH = "llmlogits";

    public static void main(String[] args) throws TornadoExecutionPlanException {
        if (!LlmBench.isCudaBackend()) {
            System.out.println("LlmLogits requires the CUDA backend.");
            return;
        }
        final int v = args.length > 0 ? Integer.parseInt(args[0]) : 128256;
        final int k = args.length > 1 ? Integer.parseInt(args[1]) : 2048;
        final String shape = "1x" + v + "x" + k;
        System.out.printf("%n%s: vocab projection y[V] = W[V,K] * x  V=%d K=%d (%.2f GB fp16 weights)%n", BENCH, v, k, v * (long) k * 2 / 1e9);

        HalfFloatArray wH = LlmBench.randomFp16((int) (v * (long) k), 1, -0.05f, 0.05f);
        FloatArray x = LlmBench.randomFp32(k, 2, -1.0f, 1.0f);
        HalfFloatArray xH = new HalfFloatArray(k);
        for (int i = 0; i < k; i++) {
            xH.set(i, new uk.ac.manchester.tornado.api.types.HalfFloat(x.get(i)));
        }

        // INT8 per-tensor quantization.
        float wMax = 0.0f;
        for (int i = 0; i < v * k; i++) {
            wMax = Math.max(wMax, Math.abs(wH.get(i).getFloat32()));
        }
        float xMax = 0.0f;
        for (int i = 0; i < k; i++) {
            xMax = Math.max(xMax, Math.abs(x.get(i)));
        }
        final float wScale = wMax / 127.0f;
        final float xScale = xMax / 127.0f;
        Int8Array wQ = new Int8Array(v * k);
        Int8Array xQ = new Int8Array(k);
        for (int i = 0; i < v * k; i++) {
            wQ.set(i, (byte) Math.round(wH.get(i).getFloat32() / wScale));
        }
        for (int i = 0; i < k; i++) {
            xQ.set(i, (byte) Math.round(x.get(i) / xScale));
        }

        // CPU reference (fp16 weights x fp32 activations).
        FloatArray yRef = new FloatArray(v);
        for (int i = 0; i < v; i++) {
            float sum = 0.0f;
            for (int j = 0; j < k; j++) {
                sum += wH.get(i * k + j).getFloat32() * x.get(j);
            }
            yRef.set(i, sum);
        }
        final double weightBytesFp16 = v * (double) k * 2;

        FloatArray yFp16 = new FloatArray(v);
        FloatArray yInt8 = new FloatArray(v);
        FloatArray yGemmEx = new FloatArray(v);

        // 1. KernelContext GEMV, FP16 weights (reuses the LlmDequantGemv kernel).
        TaskGraph gFp16 = new TaskGraph("lg16") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, wH, x) //
                .task("gemv", LlmDequantGemv::gemvFp16, new KernelContext(), wH, x, yFp16, k) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, yFp16);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(gFp16.snapshot())) {
            plan.withGridScheduler(LlmDequantGemv.rowGrid("lg16.gemv", v));
            double ms = LlmBench.timeMs(plan, LlmBench.DEFAULT_WARMUP, LlmBench.DEFAULT_ITERATIONS);
            String val = LlmBench.checkTol("kc-gemv-fp16", LlmBench.maxRelError(yFp16, yRef), 1e-2f);
            double gbs = weightBytesFp16 / (ms * 1e-3) / 1e9;
            LlmBench.report(BENCH, "kc-gemv-fp16", "fp16", shape, ms, LlmBench.gflopsGemm(1, v, k, ms), val + " " + String.format("%.0f GB/s", gbs));
        }

        // 2. INT8 dp4a GEMV.
        FloatArray refInt8 = new FloatArray(v);
        for (int i = 0; i < v; i++) {
            int acc = 0;
            for (int j = 0; j < k; j++) {
                acc += wQ.get(i * k + j) * xQ.get(j);
            }
            refInt8.set(i, acc * wScale * xScale);
        }
        TaskGraph gInt8 = new TaskGraph("lgi8") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, wQ, xQ) //
                .task("gemv", LlmDequantGemv::gemvInt8Dp4a, new KernelContext(), wQ, xQ, yInt8, k, wScale, xScale) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, yInt8);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(gInt8.snapshot())) {
            plan.withGridScheduler(LlmDequantGemv.rowGrid("lgi8.gemv", v));
            double ms = LlmBench.timeMs(plan, LlmBench.DEFAULT_WARMUP, LlmBench.DEFAULT_ITERATIONS);
            String val = LlmBench.checkTol("kc-gemv-int8-dp4a", LlmBench.maxRelError(yInt8, refInt8), 1e-2f);
            double gbs = weightBytesFp16 / 2 / (ms * 1e-3) / 1e9;
            LlmBench.report(BENCH, "kc-gemv-int8-dp4a", "int8", shape, ms, LlmBench.gflopsGemm(1, v, k, ms), val + " " + String.format("%.0f GB/s", gbs));
        }

        // 3. cublasGemmEx FP16 in / FP32 out. W row-major [V,K] is col-major
        // [K,V]; op(A)=A^T gives y[V] = W x with m=K, n=V.
        TaskGraph gEx = new TaskGraph("lgex") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, wH, xH) //
                .libraryTask("gemv", CuBlas::cublasGemmExFP16FP32, //
                        CuBlasOperation.CUBLAS_OP_T.operation(), CuBlasOperation.CUBLAS_OP_N.operation(), //
                        v, 1, k, 1.0f, wH, k, xH, k, 0.0f, yGemmEx, v) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, yGemmEx);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(gEx.snapshot())) {
            double ms = LlmBench.timeMs(plan, LlmBench.DEFAULT_WARMUP, LlmBench.DEFAULT_ITERATIONS);
            String val = LlmBench.checkTol("cublas-gemmex-fp16", LlmBench.maxRelError(yGemmEx, yRef), 1e-2f);
            double gbs = weightBytesFp16 / (ms * 1e-3) / 1e9;
            LlmBench.report(BENCH, "cublas-gemmex-fp16", "fp16", shape, ms, LlmBench.gflopsGemm(1, v, k, ms), val + " " + String.format("%.0f GB/s", gbs));
        }

        System.exit(0);
    }
}
