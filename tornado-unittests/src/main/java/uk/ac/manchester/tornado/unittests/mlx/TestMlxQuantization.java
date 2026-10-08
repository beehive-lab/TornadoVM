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
package uk.ac.manchester.tornado.unittests.mlx;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.mlx.Mlx;

/**
 * Unit tests for the MLX quantization library tasks: affine group quantize and dequantize (2, 4 and
 * 8 bits), quantized matmul, gathered quantized matmul, and mxfp8 quantization with the
 * quantized-quantized matmul. Packed weights are checked with an independent Java decoder of MLX's
 * format. Skipped unless the default device is on the Metal backend and mlx.metallib is available.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.mlx.TestMlxQuantization
 * </code>
 */
public class TestMlxQuantization extends MlxTestBase {

    /** Decodes MLX's packed affine weights into w[rows, cols] (bits must divide 32). */
    private static double[] decodeJava(IntArray wq, float[] scales, float[] biases, int rows, int cols, int groupSize, int bits) {
        int perWord = 32 / bits;
        int mask = (1 << bits) - 1;
        int wordsPerRow = cols / perWord;
        double[] w = new double[rows * cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int word = wq.get(r * wordsPerRow + c / perWord);
                int q = (word >>> (bits * (c % perWord))) & mask;
                int g = r * (cols / groupSize) + c / groupSize;
                w[r * cols + c] = scales[g] * q + biases[g];
            }
        }
        return w;
    }

    /** y[m, n] = x[m, k] @ w[n, k]^T. */
    private static double[] matmulTransposedJava(float[] x, double[] w, int m, int k, int n) {
        double[] y = new double[m * n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                double sum = 0;
                for (int p = 0; p < k; p++) {
                    sum += x[i * k + p] * w[j * k + p];
                }
                y[i * n + j] = sum;
            }
        }
        return y;
    }

    /** Quantizes and dequantizes w[rows, cols] and checks both against the Java decoder. */
    private static void roundTrip(int rows, int cols, int groupSize, int bits) throws TornadoExecutionPlanException {
        float[] wv = values(rows * cols, -1, 1, 31L * bits + groupSize);
        FloatArray w = FloatArray.fromArray(wv);
        IntArray wq = new IntArray(rows * cols * bits / 32);
        FloatArray scales = new FloatArray(rows * cols / groupSize);
        FloatArray biases = new FloatArray(rows * cols / groupSize);
        FloatArray back = new FloatArray(rows * cols);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, w) //
                .libraryTask("quantize", Mlx::quantize, w, wq, scales, biases, rows, cols, groupSize, bits) //
                .libraryTask("dequantize", Mlx::dequantize, wq, scales, biases, back, rows, cols, groupSize, bits) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, wq, scales, biases, back);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        String what = "bits=" + bits + " group=" + groupSize;
        assertAllClose("dequantize " + what, decodeJava(wq, scales.toHeapArray(), biases.toHeapArray(), rows, cols, groupSize, bits), back, 1e-6, 1e-6);
        // Quantization error stays within one step. (Not half a step: MLX adjusts each group's scale so
        // that zero is exactly representable, which can push the far edge of the group a step away.)
        for (int i = 0; i < wv.length; i++) {
            float step = Math.abs(scales.get((i / cols) * (cols / groupSize) + (i % cols) / groupSize));
            assertTrue("quantize " + what + " element " + i + ": " + wv[i] + " vs " + back.get(i), Math.abs(wv[i] - back.get(i)) <= step + 1e-5);
        }
    }

    @Test
    public void testQuantize() throws TornadoExecutionPlanException {
        roundTrip(64, 256, 32, 4);
    }

    @Test
    public void testDequantize() throws TornadoExecutionPlanException {
        roundTrip(3, 128, 64, 4);
    }

    @Test
    public void testQuantize8Bit() throws TornadoExecutionPlanException {
        roundTrip(16, 256, 32, 8);
        roundTrip(5, 256, 128, 8);
    }

    @Test
    public void testQuantize2Bit() throws TornadoExecutionPlanException {
        roundTrip(8, 128, 64, 2);
    }

    @Test
    public void testQuantizedMatmul() throws TornadoExecutionPlanException {
        final int m = 3;
        final int k = 256;
        final int n = 40;
        final int groupSize = 32;
        final int bits = 4;
        float[] xv = values(m * k, -1, 1, 1);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray w = FloatArray.fromArray(values(n * k, -1, 1, 2));
        IntArray wq = new IntArray(n * k * bits / 32);
        FloatArray scales = new FloatArray(n * k / groupSize);
        FloatArray biases = new FloatArray(n * k / groupSize);
        FloatArray y = new FloatArray(m * n);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, w) //
                .libraryTask("quantize", Mlx::quantize, w, wq, scales, biases, n, k, groupSize, bits) //
                .libraryTask("qmm", Mlx::quantizedMatmul, x, wq, scales, biases, y, m, k, n, groupSize, bits) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, wq, scales, biases, y);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        double[] wDeq = decodeJava(wq, scales.toHeapArray(), biases.toHeapArray(), n, k, groupSize, bits);
        assertAllClose("quantizedMatmul", matmulTransposedJava(xv, wDeq, m, k, n), y, 1e-4, 1e-4);
    }

    @Test
    public void testGatherQmm() throws TornadoExecutionPlanException {
        // Mixture of experts: batch b multiplies x[lhs[b]] by expert w[rhs[b]].
        final int batches = 4;
        final int experts = 3;
        final int m = 2;
        final int k = 128;
        final int n = 16;
        final int groupSize = 32;
        final int bits = 4;
        float[] xv = values(batches * m * k, -1, 1, 3);
        FloatArray x = FloatArray.fromArray(xv);
        // w[experts, n, k] has the layout of w[experts * n, k], so one quantize call covers all experts.
        FloatArray w = FloatArray.fromArray(values(experts * n * k, -1, 1, 4));
        IntArray wq = new IntArray(experts * n * k * bits / 32);
        FloatArray scales = new FloatArray(experts * n * k / groupSize);
        FloatArray biases = new FloatArray(experts * n * k / groupSize);
        IntArray lhs = IntArray.fromElements(0, 1, 2, 3);
        IntArray rhs = IntArray.fromElements(2, 0, 1, 2);
        FloatArray y = new FloatArray(batches * m * n);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, w, lhs, rhs) //
                .libraryTask("quantize", Mlx::quantize, w, wq, scales, biases, experts * n, k, groupSize, bits) //
                .libraryTask("gatherQmm", Mlx::gatherQmm, x, wq, scales, biases, lhs, rhs, y, batches, experts, m, k, n, groupSize, bits) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, wq, scales, biases, y);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        double[] wDeq = decodeJava(wq, scales.toHeapArray(), biases.toHeapArray(), experts * n, k, groupSize, bits);
        double[] expected = new double[batches * m * n];
        for (int b = 0; b < batches; b++) {
            float[] xb = Arrays.copyOfRange(xv, lhs.get(b) * m * k, (lhs.get(b) + 1) * m * k);
            double[] we = Arrays.copyOfRange(wDeq, rhs.get(b) * n * k, (rhs.get(b) + 1) * n * k);
            System.arraycopy(matmulTransposedJava(xb, we, m, k, n), 0, expected, b * m * n, m * n);
        }
        assertAllClose("gatherQmm", expected, y, 1e-4, 1e-4);
    }

    @Test
    public void testQuantizeMxAndQqmm() throws TornadoExecutionPlanException {
        // mxfp8 (mode 0): 8-bit float codes with one shared power-of-two scale per 32 values.
        final int m = 1;
        final int k = 256;
        final int n = 64;
        float[] xv = values(m * k, -1, 1, 5);
        float[] wv = values(n * k, -1, 1, 6);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray w = FloatArray.fromArray(wv);
        IntArray wq = new IntArray(n * k / 4);
        ByteArray scales = new ByteArray(n * k / 32);
        FloatArray y = new FloatArray(m * n);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, w) //
                .libraryTask("quantizeMx", Mlx::quantizeMx, w, wq, scales, n, k, 0) //
                .libraryTask("qqmm", Mlx::qqmm, x, wq, scales, y, m, k, n, 0) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, y);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        double scale = 0;
        double worst = 0;
        double[] expected = matmulTransposedJava(xv, toDoubles(wv), m, k, n);
        for (int j = 0; j < m * n; j++) {
            scale = Math.max(scale, Math.abs(expected[j]));
            worst = Math.max(worst, Math.abs(y.get(j) - expected[j]));
        }
        // Both operands are rounded to 8-bit floats, which keep about two significant digits.
        assertTrue("qqmm differs from the float product by " + worst, worst <= 0.1 * Math.max(scale, 1));
    }

    private static double[] toDoubles(float[] v) {
        double[] d = new double[v.length];
        for (int i = 0; i < v.length; i++) {
            d[i] = v[i];
        }
        return d;
    }

    // ---------------------------------------------------------------- other types

    @Test
    public void testQuantizedMatmulHalf() throws TornadoExecutionPlanException {
        final int m = 1;
        final int k = 512;
        final int n = 64;
        final int groupSize = 64;
        final int bits = 8;
        HalfFloatArray x = half(values(m * k, -1, 1, 7));
        HalfFloatArray w = half(values(n * k, -1, 1, 8));
        IntArray wq = new IntArray(n * k * bits / 32);
        HalfFloatArray scales = new HalfFloatArray(n * k / groupSize);
        HalfFloatArray biases = new HalfFloatArray(n * k / groupSize);
        HalfFloatArray y = new HalfFloatArray(m * n);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, w) //
                .libraryTask("quantize", Mlx::quantize, w, wq, scales, biases, n, k, groupSize, bits) //
                .libraryTask("qmm", Mlx::quantizedMatmul, x, wq, scales, biases, y, m, k, n, groupSize, bits) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, wq, scales, biases, y);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        double[] wDeq = decodeJava(wq, widen(scales), widen(biases), n, k, groupSize, bits);
        assertAllClose("quantizedMatmul float16", matmulTransposedJava(widen(x), wDeq, m, k, n), y, 3e-3, 3e-2);
    }
}
