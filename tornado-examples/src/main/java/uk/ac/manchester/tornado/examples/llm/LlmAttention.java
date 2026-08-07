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
import uk.ac.manchester.tornado.cudnn.CuDnn;

/**
 * Causal scaled-dot-product attention (prefill) at Llama shapes
 * (default b=1, h=32, s=512, d=64 = Llama-3.2-1B):
 *
 * <ul>
 * <li>{@code jit-fused} - one JIT kernel per (head, query row): scores, softmax
 * and PV in registers/scratch (FP32)</li>
 * <li>{@code hybrid-composed} - three tasks in one TaskGraph: JIT causal-scores
 * GEMM, {@code cudnnSoftmax} library task, JIT PV GEMM - the "mix JIT and
 * library tasks freely" story</li>
 * <li>{@code cudnn-sdpa} - single fused cuDNN flash-attention kernel (FP16)</li>
 * </ul>
 *
 * <pre>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.llm.LlmAttention [b h s d]
 * </pre>
 */
public class LlmAttention {

    private static final String BENCH = "llmattention";

    /** Naive causal attention, one thread per (batch*head, query row). */
    public static void attentionFused(FloatArray q, FloatArray k, FloatArray v, FloatArray o, FloatArray scores, int bh, int s, int d, float scale) {
        for (@Parallel int head = 0; head < bh; head++) {
            for (@Parallel int i = 0; i < s; i++) {
                int base = head * s * d;
                int scoreBase = (head * s + i) * s;
                float max = Float.NEGATIVE_INFINITY;
                for (int j = 0; j <= i; j++) {
                    float sum = 0.0f;
                    for (int dd = 0; dd < d; dd++) {
                        sum += q.get(base + i * d + dd) * k.get(base + j * d + dd);
                    }
                    sum *= scale;
                    scores.set(scoreBase + j, sum);
                    max = TornadoMath.max(max, sum);
                }
                float denom = 0.0f;
                for (int j = 0; j <= i; j++) {
                    float e = TornadoMath.exp(scores.get(scoreBase + j) - max);
                    scores.set(scoreBase + j, e);
                    denom += e;
                }
                for (int dd = 0; dd < d; dd++) {
                    float acc = 0.0f;
                    for (int j = 0; j <= i; j++) {
                        acc += scores.get(scoreBase + j) * v.get(base + j * d + dd);
                    }
                    o.set(base + i * d + dd, acc / denom);
                }
            }
        }
    }

    /** Scaled causal scores: S[head,i,j] = scale * Q[head,i]*K[head,j], -1e30 above the diagonal. */
    public static void causalScores(FloatArray q, FloatArray k, FloatArray scores, int bh, int s, int d, float scale) {
        for (@Parallel int head = 0; head < bh; head++) {
            for (@Parallel int i = 0; i < s; i++) {
                int base = head * s * d;
                int scoreBase = (head * s + i) * s;
                for (int j = 0; j < s; j++) {
                    if (j <= i) {
                        float sum = 0.0f;
                        for (int dd = 0; dd < d; dd++) {
                            sum += q.get(base + i * d + dd) * k.get(base + j * d + dd);
                        }
                        scores.set(scoreBase + j, sum * scale);
                    } else {
                        scores.set(scoreBase + j, -1e30f);
                    }
                }
            }
        }
    }

    /** O[head,i] = P[head,i,:] * V[head] with softmax probabilities P. */
    public static void probsTimesV(FloatArray probs, FloatArray v, FloatArray o, int bh, int s, int d) {
        for (@Parallel int head = 0; head < bh; head++) {
            for (@Parallel int i = 0; i < s; i++) {
                int base = head * s * d;
                int scoreBase = (head * s + i) * s;
                for (int dd = 0; dd < d; dd++) {
                    float acc = 0.0f;
                    for (int j = 0; j <= i; j++) {
                        acc += probs.get(scoreBase + j) * v.get(base + j * d + dd);
                    }
                    o.set(base + i * d + dd, acc);
                }
            }
        }
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {
        if (!LlmBench.isCudaBackend()) {
            System.out.println("LlmAttention requires the CUDA backend.");
            return;
        }
        final int b = args.length > 0 ? Integer.parseInt(args[0]) : 1;
        final int h = args.length > 1 ? Integer.parseInt(args[1]) : 32;
        final int s = args.length > 2 ? Integer.parseInt(args[2]) : 512;
        final int d = args.length > 3 ? Integer.parseInt(args[3]) : 64;
        final float scale = (float) (1.0 / Math.sqrt(d));
        final int bh = b * h;
        final String shape = b + "x" + h + "x" + s + "x" + d;
        // QK^T + PV, halved by causal masking
        final double gflop = 2.0 * bh * (double) s * s * d * 1e-9;
        System.out.printf("%n%s: causal SDPA  b=%d h=%d s=%d d=%d%n", BENCH, b, h, s, d);

        HalfFloatArray qH = LlmBench.randomFp16(bh * s * d, 1, -0.5f, 0.5f);
        HalfFloatArray kH = LlmBench.randomFp16(bh * s * d, 2, -0.5f, 0.5f);
        HalfFloatArray vH = LlmBench.randomFp16(bh * s * d, 3, -0.5f, 0.5f);
        FloatArray qF = LlmBench.toFp32(qH);
        FloatArray kF = LlmBench.toFp32(kH);
        FloatArray vF = LlmBench.toFp32(vH);

        FloatArray oJit = new FloatArray(bh * s * d);
        FloatArray oComposed = new FloatArray(bh * s * d);
        HalfFloatArray oCudnn = new HalfFloatArray(bh * s * d);
        FloatArray scoresScratch = new FloatArray(bh * s * s);
        FloatArray probsScratch = new FloatArray(bh * s * s);

        // 1. Fused JIT kernel (reference).
        TaskGraph gJit = new TaskGraph("jitsdpa") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, qF, kF, vF) //
                .task("attention", LlmAttention::attentionFused, qF, kF, vF, oJit, scoresScratch, bh, s, d, scale) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, oJit);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(gJit.snapshot())) {
            double ms = LlmBench.timeMs(plan, LlmBench.DEFAULT_WARMUP, LlmBench.DEFAULT_ITERATIONS);
            LlmBench.report(BENCH, "jit-fused", "fp32", shape, ms, gflop / (ms * 1e-3), "reference");
        }

        // 2. Composed: JIT scores -> cuDNN softmax -> JIT PV, one TaskGraph.
        TaskGraph gComposed = new TaskGraph("composed") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, qF, kF, vF) //
                .task("scores", LlmAttention::causalScores, qF, kF, scoresScratch, bh, s, d, scale) //
                .libraryTask("softmax", CuDnn::cudnnSoftmax, scoresScratch, probsScratch, bh * s, s) //
                .task("pv", LlmAttention::probsTimesV, probsScratch, vF, oComposed, bh, s, d) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, oComposed);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(gComposed.snapshot())) {
            double ms = LlmBench.timeMs(plan, LlmBench.DEFAULT_WARMUP, LlmBench.DEFAULT_ITERATIONS);
            String v = LlmBench.checkTol("hybrid-composed", LlmBench.maxRelError(oComposed, oJit), 2e-2f);
            LlmBench.report(BENCH, "hybrid-composed", "fp32", shape, ms, gflop / (ms * 1e-3), v);
        }

        // 3. Fused cuDNN flash attention (FP16).
        TaskGraph gCudnn = new TaskGraph("cudnnsdpa") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, qH, kH, vH) //
                .libraryTask("sdpa", CuDnn::sdpaForward, qH, kH, vH, oCudnn, b, h, s, s, d, scale, true) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, oCudnn);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(gCudnn.snapshot())) {
            double ms = LlmBench.timeMs(plan, LlmBench.DEFAULT_WARMUP, LlmBench.DEFAULT_ITERATIONS);
            String v = LlmBench.checkTol("cudnn-sdpa", LlmBench.maxRelError(oCudnn, oJit), 5e-2f);
            LlmBench.report(BENCH, "cudnn-sdpa", "fp16", shape, ms, gflop / (ms * 1e-3), v);
        }

        System.exit(0);
    }
}
