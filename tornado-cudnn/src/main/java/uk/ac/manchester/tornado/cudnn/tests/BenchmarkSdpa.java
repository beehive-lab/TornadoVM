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
package uk.ac.manchester.tornado.cudnn.tests;

import java.util.Random;

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
 * Benchmark: causal scaled-dot-product attention (the LLM decode/prefill
 * kernel) with a JIT-compiled naive attention kernel (FP32) vs the fused cuDNN
 * flash-attention library task (FP16, FP32 accumulate). Results are
 * cross-validated with a relaxed FP16 tolerance.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.cudnn/uk.ac.manchester.tornado.cudnn.tests.BenchmarkSdpa [batch] [heads] [seq] [headDim] [iterations]
 * </code>
 */
public class BenchmarkSdpa {

    private static final int WARMUP_ITERATIONS = 10;

    /**
     * Naive causal attention, one thread per (batch*head, query-row):
     * O[i] = softmax(Q[i] * K^T * scale, causal) * V.
     */
    public static void attention(FloatArray q, FloatArray k, FloatArray v, FloatArray o, FloatArray scores, int bh, int s, int d, float scale) {
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

    private static double benchmark(TornadoExecutionPlan executionPlan, int iterations) {
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            executionPlan.execute();
        }
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            executionPlan.execute();
        }
        return (System.nanoTime() - start) / (double) iterations;
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {

        final int b = (args.length > 0) ? Integer.parseInt(args[0]) : 4;
        final int h = (args.length > 1) ? Integer.parseInt(args[1]) : 16;
        final int s = (args.length > 2) ? Integer.parseInt(args[2]) : 1024;
        final int d = (args.length > 3) ? Integer.parseInt(args[3]) : 128;
        final int iterations = (args.length > 4) ? Integer.parseInt(args[4]) : 20;
        final float scale = (float) (1.0 / Math.sqrt(d));
        // ~4*b*h*s^2*d FLOPs (QK^T + PV), halved by causal masking
        final double gflop = 2.0 * b * h * (double) s * s * d * 1e-9;

        System.out.println("Causal SDPA benchmark: b=" + b + ", h=" + h + ", s=" + s + ", d=" + d + ", " + iterations + " iterations (+" + WARMUP_ITERATIONS + " warm-up)");

        final int bh = b * h;
        FloatArray qF = new FloatArray(bh * s * d);
        FloatArray kF = new FloatArray(bh * s * d);
        FloatArray vF = new FloatArray(bh * s * d);
        FloatArray oJit = new FloatArray(bh * s * d);
        FloatArray scoresScratch = new FloatArray(bh * s * s);
        HalfFloatArray qH = new HalfFloatArray(bh * s * d);
        HalfFloatArray kH = new HalfFloatArray(bh * s * d);
        HalfFloatArray vH = new HalfFloatArray(bh * s * d);
        HalfFloatArray oCuDnn = new HalfFloatArray(bh * s * d);

        Random random = new Random(42);
        for (int i = 0; i < bh * s * d; i++) {
            float value = random.nextFloat() - 0.5f;
            HalfFloat half = new HalfFloat(value);
            qH.set(i, half);
            kH.set(i, new HalfFloat(random.nextFloat() - 0.5f));
            vH.set(i, new HalfFloat(random.nextFloat() - 0.5f));
            // FP32 kernel consumes the same FP16-rounded values for comparability
            qF.set(i, qH.get(i).getFloat32());
            kF.set(i, kH.get(i).getFloat32());
            vF.set(i, vH.get(i).getFloat32());
        }

        TaskGraph jitGraph = new TaskGraph("jitAttention") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, qF, kF, vF) //
                .task("attention", BenchmarkSdpa::attention, qF, kF, vF, oJit, scoresScratch, bh, s, d, scale) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, oJit);

        TaskGraph cudnnGraph = new TaskGraph("cudnnSdpa") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, qH, kH, vH) //
                .libraryTask("sdpa", CuDnn::sdpaForward, qH, kH, vH, oCuDnn, b, h, s, s, d, scale, true) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, oCuDnn);

        double jitTime;
        double cudnnTime;

        try (TornadoExecutionPlan jitPlan = new TornadoExecutionPlan(jitGraph.snapshot())) {
            jitTime = benchmark(jitPlan, iterations);
            jitPlan.execute().transferToHost(oJit);
        }

        try (TornadoExecutionPlan cudnnPlan = new TornadoExecutionPlan(cudnnGraph.snapshot())) {
            cudnnTime = benchmark(cudnnPlan, iterations);
            cudnnPlan.execute().transferToHost(oCuDnn);
        }

        boolean isResultCorrect = true;
        for (int i = 0; i < bh * s * d; i++) {
            float expected = oJit.get(i);
            float actual = oCuDnn.get(i).getFloat32();
            if (Math.abs(expected - actual) > 0.02f + 2e-2f * Math.abs(expected)) {
                System.out.println("Mismatch at " + i + ": jit=" + expected + ", cudnn=" + actual);
                isResultCorrect = false;
                break;
            }
        }

        System.out.printf("TornadoVM JIT attention kernel (FP32)  : %10.3f ms | %9.2f GFLOP/s%n", jitTime * 1e-6, gflop / (jitTime * 1e-9));
        System.out.printf("cuDNN fused flash attention (FP16)     : %10.3f ms | %9.2f GFLOP/s%n", cudnnTime * 1e-6, gflop / (cudnnTime * 1e-9));
        System.out.printf("Speedup (cuDNN SDPA vs JIT attention)  : %10.2fx%n", jitTime / cudnnTime);
        System.out.println(isResultCorrect ? "Results match" : "Results DO NOT match");
    }
}
