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
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.cudnn.CuDnn;

/**
 * Benchmark: cuDNN convolutions of ResNet-50's layer shapes in the four forms the library tasks
 * offer, FP32 and FP16, channels-first (NCHW) and channels-last (NHWC). Each form is one library
 * task on device-resident buffers, timed over the execution plan.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.cudnn/uk.ac.manchester.tornado.cudnn.tests.BenchmarkConv2dLayouts [batch] [iterations]
 * </code>
 */
public class BenchmarkConv2dLayouts {

    private static final int WARMUP_ITERATIONS = 10;

    /** ResNet-50 convolutions: (channels, size, filters, kernel, stride). */
    private static final int[][] LAYERS = { //
            { 3, 224, 64, 7, 2 }, //
            { 64, 56, 64, 3, 1 }, //
            { 64, 56, 256, 1, 1 }, //
            { 256, 56, 64, 1, 1 }, //
            { 128, 28, 128, 3, 1 }, //
            { 256, 14, 256, 3, 1 }, //
            { 1024, 14, 256, 1, 1 }, //
            { 512, 7, 512, 3, 1 }, //
            { 2048, 7, 512, 1, 1 } };

    private static double timeMs(TaskGraph graph, int iterations) throws TornadoExecutionPlanException {
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                plan.execute();
            }
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                plan.execute();
            }
            return (System.nanoTime() - start) * 1e-6 / iterations;
        }
    }

    private static FloatArray floats(int size, Random random) {
        FloatArray a = new FloatArray(size);
        for (int i = 0; i < size; i++) {
            a.set(i, random.nextFloat() - 0.5f);
        }
        return a;
    }

    private static HalfFloatArray halves(int size, Random random) {
        HalfFloatArray a = new HalfFloatArray(size);
        for (int i = 0; i < size; i++) {
            a.set(i, new HalfFloat(random.nextFloat() - 0.5f));
        }
        return a;
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {
        final int n = (args.length > 0) ? Integer.parseInt(args[0]) : 32;
        final int iterations = (args.length > 1) ? Integer.parseInt(args[1]) : 50;
        Random random = new Random(42);

        System.out.println("cuDNN conv2d by layout, batch " + n + ", " + iterations + " iterations (+" + WARMUP_ITERATIONS + " warm-up); ms per call and TFLOP/s");
        System.out.printf("%-30s %18s %18s %18s %18s%n", "layer (c, hw, k, kernel, stride)", "FP32 NCHW", "FP32 NHWC", "FP16 NCHW", "FP16 NHWC");
        double[] totals = new double[4];
        for (int[] layer : LAYERS) {
            int c = layer[0];
            int hw = layer[1];
            int k = layer[2];
            int r = layer[3];
            int stride = layer[4];
            int pad = r / 2;
            int out = (hw + 2 * pad - r) / stride + 1;
            double tflop = 2.0 * n * k * c * r * r * out * out * 1e-12;

            FloatArray x = floats(n * c * hw * hw, random);
            FloatArray w = floats(k * c * r * r, random);
            FloatArray y = new FloatArray(n * k * out * out);
            HalfFloatArray xh = halves(n * c * hw * hw, random);
            HalfFloatArray wh = halves(k * c * r * r, random);
            HalfFloatArray yh = new HalfFloatArray(n * k * out * out);

            double[] ms = new double[4];
            ms[0] = timeMs(new TaskGraph("f32nchw").transferToDevice(DataTransferMode.FIRST_EXECUTION, x, w) //
                    .libraryTask("conv", CuDnn::cudnnConv2d, x, w, y, n, c, hw, hw, k, r, r, pad, stride).transferToHost(DataTransferMode.UNDER_DEMAND, y), iterations);
            ms[1] = timeMs(new TaskGraph("f32nhwc").transferToDevice(DataTransferMode.FIRST_EXECUTION, x, w) //
                    .libraryTask("conv", CuDnn::cudnnConv2dNHWC, x, w, y, n, c, hw, hw, k, r, r, pad, stride).transferToHost(DataTransferMode.UNDER_DEMAND, y), iterations);
            ms[2] = timeMs(new TaskGraph("f16nchw").transferToDevice(DataTransferMode.FIRST_EXECUTION, xh, wh) //
                    .libraryTask("conv", CuDnn::cudnnConv2dFP16, xh, wh, yh, n, c, hw, hw, k, r, r, pad, stride).transferToHost(DataTransferMode.UNDER_DEMAND, yh), iterations);
            ms[3] = timeMs(new TaskGraph("f16nhwc").transferToDevice(DataTransferMode.FIRST_EXECUTION, xh, wh) //
                    .libraryTask("conv", CuDnn::cudnnConv2dNHWCFP16, xh, wh, yh, n, c, hw, hw, k, r, r, pad, stride).transferToHost(DataTransferMode.UNDER_DEMAND, yh), iterations);

            StringBuilder row = new StringBuilder(String.format("%-30s", c + ", " + hw + ", " + k + ", " + r + "x" + r + ", " + stride));
            for (int i = 0; i < 4; i++) {
                totals[i] += ms[i];
                row.append(String.format(" %8.3f (%6.1f)", ms[i], tflop / (ms[i] * 1e-3)));
            }
            System.out.println(row);
        }
        System.out.printf("%-30s %8.3f %18.3f %18.3f %18.3f%n", "sum", totals[0], totals[1], totals[2], totals[3]);
        System.out.printf("FP16 NHWC vs FP16 NCHW: %.2fx; FP32 NHWC vs FP32 NCHW: %.2fx%n", totals[2] / totals[3], totals[0] / totals[1]);
    }
}
