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
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.cudnn.CuDnn;

/**
 * Benchmark: 2D convolution (ResNet-style layer, NCHW FP32) with a
 * JIT-compiled naive direct convolution kernel vs a cuDNN library task on the
 * same device buffers. Results are cross-validated.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.cudnn/uk.ac.manchester.tornado.cudnn.tests.BenchmarkConv2d [batch] [channels] [hw] [filters] [iterations]
 * </code>
 */
public class BenchmarkConv2d {

    private static final int WARMUP_ITERATIONS = 10;
    private static final int R = 3;
    private static final int S = 3;
    private static final int PAD = 1;
    private static final int STRIDE = 1;

    /**
     * Naive direct convolution (cross-correlation), one thread per output
     * element over (n*k, outH, outW).
     */
    public static void convDirect(FloatArray input, FloatArray filter, FloatArray output, int n, int c, int h, int w, int k) {
        int outH = h;
        int outW = w;
        for (@Parallel int nk = 0; nk < n * k; nk++) {
            for (@Parallel int oh = 0; oh < outH; oh++) {
                for (@Parallel int ow = 0; ow < outW; ow++) {
                    int in = nk / k;
                    int ok = nk % k;
                    float sum = 0.0f;
                    for (int ic = 0; ic < c; ic++) {
                        for (int fr = 0; fr < R; fr++) {
                            for (int fs = 0; fs < S; fs++) {
                                int ih = oh * STRIDE + fr - PAD;
                                int iw = ow * STRIDE + fs - PAD;
                                if (ih >= 0 && ih < h && iw >= 0 && iw < w) {
                                    sum += input.get(((in * c + ic) * h + ih) * w + iw) * filter.get(((ok * c + ic) * R + fr) * S + fs);
                                }
                            }
                        }
                    }
                    output.set((nk * outH + oh) * outW + ow, sum);
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

        final int n = (args.length > 0) ? Integer.parseInt(args[0]) : 8;
        final int c = (args.length > 1) ? Integer.parseInt(args[1]) : 64;
        final int hw = (args.length > 2) ? Integer.parseInt(args[2]) : 56;
        final int k = (args.length > 3) ? Integer.parseInt(args[3]) : 64;
        final int iterations = (args.length > 4) ? Integer.parseInt(args[4]) : 50;
        final int outH = hw;
        final int outW = hw;
        final double gflop = 2.0 * n * k * c * R * S * outH * outW * 1e-9;

        System.out.println("Conv2d benchmark: NCHW " + n + "x" + c + "x" + hw + "x" + hw + ", " + k + " 3x3 filters (pad 1, stride 1), " + iterations + " iterations (+" + WARMUP_ITERATIONS
                + " warm-up)");

        FloatArray input = new FloatArray(n * c * hw * hw);
        FloatArray filter = new FloatArray(k * c * R * S);
        FloatArray outputJit = new FloatArray(n * k * outH * outW);
        FloatArray outputCuDnn = new FloatArray(n * k * outH * outW);

        Random random = new Random(42);
        for (int i = 0; i < input.getSize(); i++) {
            input.set(i, random.nextFloat() - 0.5f);
        }
        for (int i = 0; i < filter.getSize(); i++) {
            filter.set(i, random.nextFloat() - 0.5f);
        }

        TaskGraph jitGraph = new TaskGraph("jit") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input, filter) //
                .task("conv", BenchmarkConv2d::convDirect, input, filter, outputJit, n, c, hw, hw, k) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, outputJit);

        TaskGraph cudnnGraph = new TaskGraph("cudnn") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input, filter) //
                .libraryTask("conv", CuDnn::cudnnConv2d, input, filter, outputCuDnn, n, c, hw, hw, k, R, S, PAD, STRIDE) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, outputCuDnn);

        double jitTime;
        double cudnnTime;

        try (TornadoExecutionPlan jitPlan = new TornadoExecutionPlan(jitGraph.snapshot())) {
            jitTime = benchmark(jitPlan, iterations);
            jitPlan.execute().transferToHost(outputJit);
        }

        try (TornadoExecutionPlan cudnnPlan = new TornadoExecutionPlan(cudnnGraph.snapshot())) {
            cudnnTime = benchmark(cudnnPlan, iterations);
            cudnnPlan.execute().transferToHost(outputCuDnn);
        }

        boolean isResultCorrect = true;
        for (int i = 0; i < outputJit.getSize(); i++) {
            float expected = outputJit.get(i);
            if (Math.abs(expected - outputCuDnn.get(i)) > 1e-2f * Math.max(1.0f, Math.abs(expected))) {
                System.out.println("Mismatch at " + i + ": jit=" + expected + ", cudnn=" + outputCuDnn.get(i));
                isResultCorrect = false;
                break;
            }
        }

        System.out.printf("TornadoVM JIT direct convolution  : %10.3f ms | %9.2f GFLOP/s%n", jitTime * 1e-6, gflop / (jitTime * 1e-9));
        System.out.printf("cuDNN library task                : %10.3f ms | %9.2f GFLOP/s%n", cudnnTime * 1e-6, gflop / (cudnnTime * 1e-9));
        System.out.printf("Speedup (cuDNN vs JIT)            : %10.2fx%n", jitTime / cudnnTime);
        System.out.println(isResultCorrect ? "Results match" : "Results DO NOT match");
    }
}
