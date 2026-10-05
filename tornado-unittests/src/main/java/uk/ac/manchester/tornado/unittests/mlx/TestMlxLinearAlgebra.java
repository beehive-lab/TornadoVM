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

import static org.junit.Assert.assertThrows;

import java.util.Arrays;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.BFloat16Array;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.mlx.MlxLinearAlgebra;

/**
 * Unit tests for the MLX linear-algebra library tasks: matmul, transposed matmul, addmm, einsum,
 * tensordot, inner, outer and Kronecker products, segmented and gathered matmuls, cross products and
 * norms. Arrays are row-major, as in MLX. Each test checks the task against a sequential Java
 * reference. Skipped unless the default device is on the Metal backend and mlx.metallib is available.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.mlx.TestMlxLinearAlgebra
 * </code>
 */
public class TestMlxLinearAlgebra extends MlxTestBase {

    /** c[m, n] = a[m, k] @ b[k, n] (b at offset bOff), with b given as [n, k] if transposed. */
    private static double[] matmulJava(float[] a, int aOff, float[] b, int bOff, int m, int k, int n, boolean bTransposed) {
        double[] c = new double[m * n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                double sum = 0;
                for (int p = 0; p < k; p++) {
                    sum += (double) a[aOff + i * k + p] * (bTransposed ? b[bOff + j * k + p] : b[bOff + p * n + j]);
                }
                c[i * n + j] = sum;
            }
        }
        return c;
    }

    private static double[] matmulJava(float[] a, float[] b, int m, int k, int n, boolean bTransposed) {
        return matmulJava(a, 0, b, 0, m, k, n, bTransposed);
    }

    @Test
    public void testMatmul() throws TornadoExecutionPlanException {
        final int m = 37;
        final int k = 65;
        final int n = 29;
        float[] av = values(m * k, -1, 1, 1);
        float[] bv = values(k * n, -1, 1, 2);
        FloatArray a = FloatArray.fromArray(av);
        FloatArray b = FloatArray.fromArray(bv);
        FloatArray c = new FloatArray(m * n);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("matmul", MlxLinearAlgebra::matmul, a, b, c, m, k, n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertAllClose("matmul", matmulJava(av, bv, m, k, n, false), c, 1e-4, 1e-4);
    }

    @Test
    public void testMatmulTransposed() throws TornadoExecutionPlanException {
        final int m = 64;
        final int k = 256;
        final int n = 96;
        float[] av = values(m * k, -1, 1, 3);
        float[] wv = values(n * k, -1, 1, 4);
        FloatArray a = FloatArray.fromArray(av);
        FloatArray w = FloatArray.fromArray(wv);
        FloatArray c = new FloatArray(m * n);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, w) //
                .libraryTask("matmul", MlxLinearAlgebra::matmulTransposed, a, w, c, m, k, n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertAllClose("matmulTransposed", matmulJava(av, wv, m, k, n, true), c, 1e-4, 1e-4);
    }

    @Test
    public void testAddmm() throws TornadoExecutionPlanException {
        final int m = 17;
        final int k = 40;
        final int n = 23;
        final float alpha = 0.5f;
        final float beta = 2.0f;
        float[] cv = values(m * n, -1, 1, 5);
        float[] av = values(m * k, -1, 1, 6);
        float[] bv = values(k * n, -1, 1, 7);
        FloatArray cIn = FloatArray.fromArray(cv);
        FloatArray a = FloatArray.fromArray(av);
        FloatArray b = FloatArray.fromArray(bv);
        FloatArray output = new FloatArray(m * n);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, cIn, a, b) //
                .libraryTask("addmm", MlxLinearAlgebra::addmm, cIn, a, b, output, m, k, n, alpha, beta) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        double[] ab = matmulJava(av, bv, m, k, n, false);
        double[] expected = new double[m * n];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = alpha * ab[i] + beta * cv[i];
        }
        assertAllClose("addmm", expected, output, 1e-4, 1e-4);
    }

    @Test
    public void testEinsumBatchedMatmul() throws TornadoExecutionPlanException {
        final int batch = 3;
        final int m = 64;
        final int k = 32;
        final int n = 96;
        float[] av = values(batch * m * k, -1, 1, 8);
        float[] bv = values(batch * k * n, -1, 1, 9);
        FloatArray a = FloatArray.fromArray(av);
        FloatArray b = FloatArray.fromArray(bv);
        FloatArray output = new FloatArray(batch * m * n);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("einsum", MlxLinearAlgebra::einsumBatchedMatmul, a, b, output, batch, m, k, n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        double[] expected = new double[batch * m * n];
        for (int bb = 0; bb < batch; bb++) {
            System.arraycopy(matmulJava(av, bb * m * k, bv, bb * k * n, m, k, n, false), 0, expected, bb * m * n, m * n);
        }
        assertAllClose("einsum bij,bjk->bik", expected, output, 1e-4, 1e-4);
    }

    @Test
    public void testTensordot() throws TornadoExecutionPlanException {
        // a[m, 4, 8] and b[4, 8, n], contracted over both middle axes: a[m, 32] @ b[32, n].
        final int m = 64;
        final int n = 96;
        float[] av = values(m * 32, -1, 1, 10);
        float[] bv = values(32 * n, -1, 1, 11);
        FloatArray a = FloatArray.fromArray(av);
        FloatArray b = FloatArray.fromArray(bv);
        FloatArray output = new FloatArray(m * n);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("tensordot", MlxLinearAlgebra::tensordot, a, b, output, m, 4, 8, n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertAllClose("tensordot", matmulJava(av, bv, m, 32, n, false), output, 1e-4, 1e-4);
    }

    @Test
    public void testTensordotAxis() throws TornadoExecutionPlanException {
        final int m = 64;
        final int k = 32;
        final int n = 96;
        float[] av = values(m * k, -1, 1, 12);
        float[] bv = values(k * n, -1, 1, 13);
        FloatArray a = FloatArray.fromArray(av);
        FloatArray b = FloatArray.fromArray(bv);
        FloatArray output = new FloatArray(m * n);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("tensordot", MlxLinearAlgebra::tensordotAxis, a, b, output, m, k, n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertAllClose("tensordotAxis", matmulJava(av, bv, m, k, n, false), output, 1e-4, 1e-4);
    }

    @Test
    public void testInner() throws TornadoExecutionPlanException {
        final int n = 100_000;
        float[] av = values(n, -1, 1, 14);
        float[] bv = values(n, -1, 1, 15);
        FloatArray a = FloatArray.fromArray(av);
        FloatArray b = FloatArray.fromArray(bv);
        FloatArray output = new FloatArray(1);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("inner", MlxLinearAlgebra::inner, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        double dot = 0;
        for (int i = 0; i < n; i++) {
            dot += (double) av[i] * bv[i];
        }
        assertAllClose("inner", new double[] { dot }, output, 1e-3, 1e-3);
    }

    @Test
    public void testOuter() throws TornadoExecutionPlanException {
        final int m = 37;
        final int n = 53;
        float[] av = values(m, -2, 2, 16);
        float[] bv = values(n, -2, 2, 17);
        FloatArray a = FloatArray.fromArray(av);
        FloatArray b = FloatArray.fromArray(bv);
        FloatArray output = new FloatArray(m * n);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("outer", MlxLinearAlgebra::outer, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        double[] expected = new double[m * n];
        for (int t = 0; t < expected.length; t++) {
            expected[t] = (double) av[t / n] * bv[t % n];
        }
        assertAllClose("outer", expected, output, 1e-6, 1e-6);
    }

    @Test
    public void testKron() throws TornadoExecutionPlanException {
        // a[5, 4] (x) b[3, 6] = out[15, 24]
        float[] av = values(5 * 4, -2, 2, 18);
        float[] bv = values(3 * 6, -2, 2, 19);
        FloatArray a = FloatArray.fromArray(av);
        FloatArray b = FloatArray.fromArray(bv);
        FloatArray output = new FloatArray(15 * 24);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("kron", MlxLinearAlgebra::kron, a, b, output, 5, 4, 3, 6) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        double[] expected = new double[15 * 24];
        for (int t = 0; t < expected.length; t++) {
            int r = t / 24;
            int c = t % 24;
            expected[t] = (double) av[(r / 3) * 4 + c / 6] * bv[(r % 3) * 6 + c % 6];
        }
        assertAllClose("kron", expected, output, 1e-6, 1e-6);
    }

    @Test
    public void testSegmentedMm() throws TornadoExecutionPlanException {
        // Four segments of the k axis: [0, 10), [10, 64), [5, 5) (empty) and [20, 40).
        final int m = 16;
        final int k = 64;
        final int n = 24;
        int[] segs = { 0, 10, 10, 64, 5, 5, 20, 40 };
        float[] av = values(m * k, -1, 1, 20);
        float[] bv = values(k * n, -1, 1, 21);
        IntArray segments = IntArray.fromArray(segs);
        FloatArray a = FloatArray.fromArray(av);
        FloatArray b = FloatArray.fromArray(bv);
        FloatArray output = new FloatArray(4 * m * n);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, segments, a, b) //
                .libraryTask("segmentedMm", MlxLinearAlgebra::segmentedMm, a, b, segments, output, m, k, n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        double[] expected = new double[4 * m * n];
        for (int s = 0; s < 4; s++) {
            for (int i = 0; i < m; i++) {
                for (int j = 0; j < n; j++) {
                    double acc = 0;
                    for (int p = segs[2 * s]; p < segs[2 * s + 1]; p++) {
                        acc += (double) av[i * k + p] * bv[p * n + j];
                    }
                    expected[(s * m + i) * n + j] = acc;
                }
            }
        }
        assertAllClose("segmentedMm", expected, output, 1e-4, 1e-4);
    }

    @Test
    public void testGatherMm() throws TornadoExecutionPlanException {
        // out[i] = a[lhs[i]] @ b[rhs[i]] for five (lhs, rhs) pairs over 4 a and 3 b matrices.
        final int batchesA = 4;
        final int batchesB = 3;
        final int m = 64;
        final int k = 32;
        final int n = 96;
        int[] lhs = { 3, 0, 1, 2, 3 };
        int[] rhs = { 2, 0, 1, 1, 2 };
        float[] av = values(batchesA * m * k, -1, 1, 22);
        float[] bv = values(batchesB * k * n, -1, 1, 23);
        FloatArray a = FloatArray.fromArray(av);
        FloatArray b = FloatArray.fromArray(bv);
        IntArray lhsIndices = IntArray.fromArray(lhs);
        IntArray rhsIndices = IntArray.fromArray(rhs);
        FloatArray output = new FloatArray(lhs.length * m * n);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, lhsIndices, rhsIndices) //
                .libraryTask("gatherMm", MlxLinearAlgebra::gatherMm, a, b, lhsIndices, rhsIndices, output, batchesA, batchesB, m, k, n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        double[] expected = new double[lhs.length * m * n];
        for (int i = 0; i < lhs.length; i++) {
            System.arraycopy(matmulJava(av, lhs[i] * m * k, bv, rhs[i] * k * n, m, k, n, false), 0, expected, i * m * n, m * n);
        }
        assertAllClose("gatherMm", expected, output, 1e-4, 1e-4);
    }

    @Test
    public void testCross() throws TornadoExecutionPlanException {
        final int count = 1000;
        float[] av = values(count * 3, -3, 3, 24);
        float[] bv = values(count * 3, -3, 3, 25);
        FloatArray a = FloatArray.fromArray(av);
        FloatArray b = FloatArray.fromArray(bv);
        FloatArray output = new FloatArray(count * 3);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("cross", MlxLinearAlgebra::cross, a, b, output, count) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        double[] expected = new double[count * 3];
        for (int i = 0; i < count; i++) {
            int p = 3 * i;
            expected[p] = (double) av[p + 1] * bv[p + 2] - (double) av[p + 2] * bv[p + 1];
            expected[p + 1] = (double) av[p + 2] * bv[p] - (double) av[p] * bv[p + 2];
            expected[p + 2] = (double) av[p] * bv[p + 1] - (double) av[p + 1] * bv[p];
        }
        assertAllClose("cross", expected, output, 1e-5, 1e-5);
    }

    @Test
    public void testNorm() throws TornadoExecutionPlanException {
        final int rows = 37;
        final int cols = 300;
        float[] xv = values(rows * cols, -2, 2, 26);
        FloatArray x = FloatArray.fromArray(xv);
        for (float ord : new float[] { 1f, 2f, 3f, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY }) {
            FloatArray output = new FloatArray(rows);

            TaskGraph taskGraph = new TaskGraph("g") //
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                    .libraryTask("norm", MlxLinearAlgebra::norm, x, output, rows, cols, ord) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
                plan.execute();
            }

            double[] expected = new double[rows];
            for (int r = 0; r < rows; r++) {
                double acc = ord == Float.NEGATIVE_INFINITY ? Double.MAX_VALUE : 0;
                for (int c = 0; c < cols; c++) {
                    double v = Math.abs(xv[r * cols + c]);
                    acc = ord == Float.POSITIVE_INFINITY ? Math.max(acc, v) : ord == Float.NEGATIVE_INFINITY ? Math.min(acc, v) : acc + Math.pow(v, ord);
                }
                expected[r] = Double.isInfinite(ord) ? acc : Math.pow(acc, 1.0 / ord);
            }
            assertAllClose("norm ord=" + ord, expected, output, 1e-4, 1e-5);
        }
    }

    @Test
    public void testL2Norm() throws TornadoExecutionPlanException {
        final int rows = 37;
        final int cols = 300;
        float[] xv = values(rows * cols, -2, 2, 27);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray output = new FloatArray(rows);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("l2Norm", MlxLinearAlgebra::l2Norm, x, output, rows, cols) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        double[] expected = new double[rows];
        for (int r = 0; r < rows; r++) {
            double s = 0;
            for (int c = 0; c < cols; c++) {
                s += (double) xv[r * cols + c] * xv[r * cols + c];
            }
            expected[r] = Math.sqrt(s);
        }
        assertAllClose("l2Norm", expected, output, 1e-5, 1e-5);
    }

    @Test
    public void testFrobeniusNorm() throws TornadoExecutionPlanException {
        final int batch = 6;
        final int rows = 37;
        final int cols = 50;
        float[] xv = values(batch * rows * cols, -2, 2, 28);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray output = new FloatArray(batch);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("frobenius", MlxLinearAlgebra::frobeniusNorm, x, output, batch, rows, cols) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        double[] expected = new double[batch];
        for (int b = 0; b < batch; b++) {
            double s = 0;
            for (int i = 0; i < rows * cols; i++) {
                s += (double) xv[b * rows * cols + i] * xv[b * rows * cols + i];
            }
            expected[b] = Math.sqrt(s);
        }
        assertAllClose("frobeniusNorm", expected, output, 1e-5, 1e-5);
    }

    // ---------------------------------------------------------------- other shapes and types

    @Test
    public void testMatmulTransposedAsGemv() throws TornadoExecutionPlanException {
        // m = 1: y[1, rows] = x[1, cols] @ w[rows, cols]^T, the decode-time matrix-vector product.
        final int rows = 1000;
        final int cols = 2048;
        float[] wv = values(rows * cols, -0.05f, 0.05f, 29);
        float[] xv = values(cols, -1, 1, 30);
        FloatArray w = FloatArray.fromArray(wv);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray y = new FloatArray(rows);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, w, x) //
                .libraryTask("gemv", MlxLinearAlgebra::matmulTransposed, x, w, y, 1, cols, rows) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, y);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertAllClose("gemv", matmulJava(xv, wv, 1, cols, rows, true), y, 1e-4, 1e-4);
    }

    @Test
    public void testMatmulHalf() throws TornadoExecutionPlanException {
        final int m = 64;
        final int k = 96;
        final int n = 80;
        HalfFloatArray a = half(values(m * k, -1, 1, 31));
        HalfFloatArray b = half(values(k * n, -1, 1, 32));
        HalfFloatArray c = new HalfFloatArray(m * n);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("matmul", MlxLinearAlgebra::matmul, a, b, c, m, k, n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertAllClose("matmul float16", matmulJava(widen(a), widen(b), m, k, n, false), c, 2e-3, 2e-2);
    }

    @Test
    public void testMatmulBFloat16() throws TornadoExecutionPlanException {
        final int m = 64;
        final int k = 96;
        final int n = 80;
        BFloat16Array a = bf16(values(m * k, -1, 1, 33));
        BFloat16Array b = bf16(values(k * n, -1, 1, 34));
        BFloat16Array c = new BFloat16Array(m * n);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("matmul", MlxLinearAlgebra::matmul, a, b, c, m, k, n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertAllClose("matmul bfloat16", matmulJava(widen(a), widen(b), m, k, n, false), c, 1e-2, 1e-1);
    }

    @Test
    public void testMatmulTransposedHalfAsGemv() throws TornadoExecutionPlanException {
        final int rows = 512;
        final int cols = 4096;
        HalfFloatArray w = half(values(rows * cols, -0.05f, 0.05f, 35));
        HalfFloatArray x = half(values(cols, -1, 1, 36));
        HalfFloatArray y = new HalfFloatArray(rows);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, w, x) //
                .libraryTask("gemv", MlxLinearAlgebra::matmulTransposed, x, w, y, 1, cols, rows) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, y);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertAllClose("gemv float16", matmulJava(widen(x), widen(w), 1, cols, rows, true), y, 2e-3, 2e-3);
    }

    @Test
    public void testCrossHalf() throws TornadoExecutionPlanException {
        final int count = 100;
        HalfFloatArray a = half(values(count * 3, -2, 2, 37));
        HalfFloatArray b = half(values(count * 3, -2, 2, 38));
        HalfFloatArray output = new HalfFloatArray(count * 3);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("cross", MlxLinearAlgebra::cross, a, b, output, count) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        float[] x = widen(a);
        float[] y = widen(b);
        double[] expected = new double[count * 3];
        for (int i = 0; i < count; i++) {
            int p = 3 * i;
            expected[p] = x[p + 1] * y[p + 2] - x[p + 2] * y[p + 1];
            expected[p + 1] = x[p + 2] * y[p] - x[p] * y[p + 2];
            expected[p + 2] = x[p] * y[p + 1] - x[p + 1] * y[p];
        }
        assertAllClose("cross float16", expected, output, 5e-3, 5e-3);
    }

    @Test
    public void testMatmulOfOneByOneFails() {
        // m == n == 1 has no TornadoVM/MLX kernel.
        FloatArray a = FloatArray.fromArray(values(8, -1, 1, 39));
        FloatArray b = FloatArray.fromArray(Arrays.copyOf(values(8, -1, 1, 40), 8));
        FloatArray c = new FloatArray(1);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("matmul", MlxLinearAlgebra::matmul, a, b, c, 1, 8, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        assertThrows(RuntimeException.class, () -> {
            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
                plan.execute();
            }
        });
    }
}
