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

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.cublas.CuBlasLt;
import uk.ac.manchester.tornado.cublas.enums.CuBlasOperation;
import uk.ac.manchester.tornado.cudnn.CuDnn;

/**
 * One full Llama-style transformer decoder layer (prefill, inference) as a
 * single TornadoVM TaskGraph mixing JIT kernels and NVIDIA library tasks:
 *
 * <pre>
 * rmsnorm (JIT) -> QKV projection (cuBLASLt FP16) -> split to BHSD (JIT)
 *   -> causal flash attention (cuDNN SDPA) -> merge heads (JIT)
 *   -> output projection (cuBLASLt) -> residual (JIT)
 *   -> rmsnorm (JIT) -> FFN up + fused bias/GELU (cuBLASLt epilogue)
 *   -> FFN down (cuBLASLt) -> residual (JIT)
 * </pre>
 *
 * 11 tasks (6 JIT + 5 library) in one graph, one shared device context,
 * optionally captured and replayed as a single CUDA graph
 * ({@code --cudaGraph}). Default shape: Llama-3.2-1B prefill - s=512,
 * hidden=2048, heads=32, headDim=64, ffn=8192 (MHA simplification of GQA,
 * no RoPE - this is a scheduling/throughput benchmark, not a model).
 *
 * <pre>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.llm.LlamaLayerHybrid [s] [cudagraph]
 * </pre>
 */
public class LlamaLayerHybrid {

    private static final String BENCH = "llamalayer";

    /** RMSNorm pass 1: per-row inverse RMS. One thread per row (s-way parallel). */
    public static void rmsnormReduce(FloatArray x, FloatArray invRms, int s, int e) {
        for (@Parallel int i = 0; i < s; i++) {
            float ss = 0.0f;
            for (int j = 0; j < e; j++) {
                float val = x.get(i * e + j);
                ss += val * val;
            }
            invRms.set(i, 1.0f / TornadoMath.sqrt(ss / e + 1e-5f));
        }
    }

    /** RMSNorm pass 2: scale and narrow to FP16 for the tensor-core GEMMs (s*e-way parallel). */
    public static void rmsnormScale(FloatArray x, FloatArray invRms, HalfFloatArray out, int s, int e) {
        for (@Parallel int i = 0; i < s; i++) {
            for (@Parallel int j = 0; j < e; j++) {
                // The float value must land in a local before the HalfFloat
                // allocation: the CUDA half-float replacement phase only
                // rewrites `new HalfFloat(<variable>)`, not inline expressions.
                float scaled = x.get(i * e + j) * invRms.get(i);
                HalfFloat h = new HalfFloat(scaled);
                out.set(i * e + j, h);
            }
        }
    }

    /** Splits the packed QKV projection [s, 3e] into BHSD Q/K/V tensors [h, s, d]. */
    public static void splitQkv(HalfFloatArray qkv, HalfFloatArray q, HalfFloatArray k, HalfFloatArray v, int s, int h, int d) {
        int e = h * d;
        for (@Parallel int head = 0; head < h; head++) {
            for (@Parallel int i = 0; i < s; i++) {
                for (int dd = 0; dd < d; dd++) {
                    int dst = (head * s + i) * d + dd;
                    q.set(dst, qkv.get(i * 3 * e + head * d + dd));
                    k.set(dst, qkv.get(i * 3 * e + e + head * d + dd));
                    v.set(dst, qkv.get(i * 3 * e + 2 * e + head * d + dd));
                }
            }
        }
    }

    /** Merges BHSD attention output [h, s, d] back to [s, e]. */
    public static void mergeHeads(HalfFloatArray attn, HalfFloatArray out, int s, int h, int d) {
        for (@Parallel int head = 0; head < h; head++) {
            for (@Parallel int i = 0; i < s; i++) {
                for (int dd = 0; dd < d; dd++) {
                    out.set(i * h * d + head * d + dd, attn.get((head * s + i) * d + dd));
                }
            }
        }
    }

    /** Residual add: x = x + delta (FP16 delta onto the FP32 stream). */
    public static void residual(FloatArray x, HalfFloatArray delta, FloatArray out, int size) {
        for (@Parallel int i = 0; i < size; i++) {
            out.set(i, x.get(i) + delta.get(i).getFloat32());
        }
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {
        if (!LlmBench.isCudaBackend()) {
            System.out.println("LlamaLayerHybrid requires the CUDA backend.");
            return;
        }
        int sArg = 512;
        boolean cudaGraph = false;
        for (String arg : args) {
            if ("cudagraph".equals(arg) || "--cudaGraph".equals(arg)) {
                cudaGraph = true;
            } else {
                sArg = Integer.parseInt(arg);
            }
        }
        final int s = sArg;
        final int h = 32;
        final int d = 64;
        final int e = h * d; // 2048
        final int ffn = 8192;
        final float scale = (float) (1.0 / Math.sqrt(d));
        final String shape = "s" + s + "-e" + e + "-h" + h + "-ffn" + ffn + (cudaGraph ? "-cudagraph" : "");
        System.out.printf("%n%s: full decoder layer  s=%d hidden=%d heads=%d ffn=%d  cudaGraph=%b%n", BENCH, s, e, h, ffn, cudaGraph);

        // Total layer FLOPs: QKV + attention + O-proj + FFN up/down.
        final double gflop = (2.0 * s * e * 3 * e // QKV
                + 2.0 * h * (double) s * s * d // causal QK^T + PV
                + 2.0 * s * e * e // O proj
                + 2.0 * s * e * ffn * 2) * 1e-9; // FFN up + down

        FloatArray x = LlmBench.randomFp32(s * e, 1, -0.5f, 0.5f);
        HalfFloatArray wQkv = LlmBench.randomFp16(e * 3 * e, 2, -0.02f, 0.02f);
        HalfFloatArray wO = LlmBench.randomFp16(e * e, 3, -0.02f, 0.02f);
        HalfFloatArray wUp = LlmBench.randomFp16(e * ffn, 4, -0.02f, 0.02f);
        HalfFloatArray biasUp = LlmBench.randomFp16(ffn, 5, -0.02f, 0.02f);
        HalfFloatArray wDown = LlmBench.randomFp16(ffn * e, 6, -0.02f, 0.02f);

        FloatArray invRms1 = new FloatArray(s);
        FloatArray invRms2 = new FloatArray(s);
        HalfFloatArray normed1 = new HalfFloatArray(s * e);
        HalfFloatArray qkv = new HalfFloatArray(s * 3 * e);
        HalfFloatArray q = new HalfFloatArray(h * s * d);
        HalfFloatArray k = new HalfFloatArray(h * s * d);
        HalfFloatArray v = new HalfFloatArray(h * s * d);
        HalfFloatArray attn = new HalfFloatArray(h * s * d);
        HalfFloatArray merged = new HalfFloatArray(s * e);
        HalfFloatArray oProj = new HalfFloatArray(s * e);
        FloatArray x1 = new FloatArray(s * e);
        HalfFloatArray normed2 = new HalfFloatArray(s * e);
        HalfFloatArray up = new HalfFloatArray(s * ffn);
        HalfFloatArray down = new HalfFloatArray(s * e);
        FloatArray out = new FloatArray(s * e);

        final int opN = CuBlasOperation.CUBLAS_OP_N.operation();

        TaskGraph graph = new TaskGraph("llamalayer") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, x, wQkv, wO, wUp, biasUp, wDown) //
                .task("rmsnorm1r", LlamaLayerHybrid::rmsnormReduce, x, invRms1, s, e) //
                .task("rmsnorm1s", LlamaLayerHybrid::rmsnormScale, x, invRms1, normed1, s, e) //
                // QKV: row-major [s,e] x [e,3e] -> [s,3e] via the column-major swap trick
                .libraryTask("qkv", CuBlasLt::ltMatmulFP16, opN, opN, 3 * e, s, e, 1.0f, wQkv, 3 * e, normed1, e, 0.0f, qkv, 3 * e) //
                .task("split", LlamaLayerHybrid::splitQkv, qkv, q, k, v, s, h, d) //
                .libraryTask("sdpa", CuDnn::sdpaForward, q, k, v, attn, 1, h, s, s, d, scale, true) //
                .task("merge", LlamaLayerHybrid::mergeHeads, attn, merged, s, h, d) //
                .libraryTask("oproj", CuBlasLt::ltMatmulFP16, opN, opN, e, s, e, 1.0f, wO, e, merged, e, 0.0f, oProj, e) //
                .task("residual1", LlamaLayerHybrid::residual, x, oProj, x1, s * e) //
                .task("rmsnorm2r", LlamaLayerHybrid::rmsnormReduce, x1, invRms2, s, e) //
                .task("rmsnorm2s", LlamaLayerHybrid::rmsnormScale, x1, invRms2, normed2, s, e) //
                .libraryTask("ffnup", CuBlasLt::ltMatmulGeluBiasFP16, opN, opN, ffn, s, e, 1.0f, wUp, ffn, normed2, e, 0.0f, up, ffn, biasUp) //
                .libraryTask("ffndown", CuBlasLt::ltMatmulFP16, opN, opN, e, s, ffn, 1.0f, wDown, e, up, ffn, 0.0f, down, e) //
                .task("residual2", LlamaLayerHybrid::residual, x1, down, out, s * e) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            if (cudaGraph) {
                plan.withCUDAGraph();
            }
            double ms = LlmBench.timeMs(plan, LlmBench.DEFAULT_WARMUP, LlmBench.DEFAULT_ITERATIONS);

            boolean finite = true;
            double checksum = 0;
            for (int i = 0; i < s * e; i++) {
                float val = out.get(i);
                if (Float.isNaN(val) || Float.isInfinite(val)) {
                    finite = false;
                    break;
                }
                checksum += val;
            }
            String note = (finite ? "finite" : "NON-FINITE OUTPUT") + String.format(" checksum=%.3f", checksum);
            System.out.printf("  layer: %.3f ms/iter  %.1f GFLOP/s  (%s)%n", ms, gflop / (ms * 1e-3), note);
            LlmBench.csv(BENCH, cudaGraph ? "hybrid-layer-cudagraph" : "hybrid-layer", "fp16", shape, ms, gflop / (ms * 1e-3), note);
        }

        System.exit(0);
    }
}
