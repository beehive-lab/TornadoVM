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

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.function.IntToDoubleFunction;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.LibraryTask1;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.mlx.MlxCreation;

/**
 * Unit tests for the MLX array-construction library tasks: ranges, constants, identity and
 * triangular matrices, windows, meshgrid, and the matrix-structure operations diag, diagonal, trace,
 * tril and triu. Constructors write into {@code out}, whose length sets the size. Each test checks
 * the task against a sequential Java reference. Skipped unless the default device is on the Metal
 * backend and mlx.metallib is available.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.mlx.TestMlxCreation
 * </code>
 */
public class TestMlxCreation extends MlxTestBase {

    private static final int SIZE = 777;
    private static final int ROWS = 23;
    private static final int COLS = 31;
    private static final int WINDOW = 257;

    private static void constant(LibraryTask1<FloatArray> mlx, float expected) throws TornadoExecutionPlanException {
        FloatArray output = new FloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, output) //
                .libraryTask("constant", mlx, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        double[] e = new double[SIZE];
        Arrays.fill(e, expected);
        assertAllClose("constant", e, output, 0, 0);
    }

    private static void window(LibraryTask1<FloatArray> mlx, int m, IntToDoubleFunction ref) throws TornadoExecutionPlanException {
        FloatArray output = new FloatArray(m);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, output) //
                .libraryTask("window", mlx, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        double[] expected = new double[m];
        for (int i = 0; i < m; i++) {
            expected[i] = ref.applyAsDouble(i);
        }
        assertAllClose("window m=" + m, expected, output, 1e-5, 1e-5);
    }

    /** Position i of a window of length m, in [0, 1]. */
    private static double x(int i, int m) {
        return (double) i / (m - 1);
    }

    /** {@code a} with every element not in the triangle {@code keep(column - row)} set to zero. */
    private static double[] triangleJava(float[] a, int k, boolean lower) {
        double[] out = new double[ROWS * COLS];
        for (int t = 0; t < ROWS * COLS; t++) {
            int d = t % COLS - t / COLS;
            out[t] = (lower ? d <= k : d >= k) ? a[t] : 0;
        }
        return out;
    }

    // ---------------------------------------------------------------- ranges and constants

    @Test
    public void testArange() throws TornadoExecutionPlanException {
        final int n = 1000;
        FloatArray output = new FloatArray(n);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, output) //
                .libraryTask("arange", MlxCreation::arange, output, -2.0f, -2.0f + 0.25f * n, 0.25f) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        double[] expected = new double[n];
        for (int i = 0; i < n; i++) {
            expected[i] = -2.0 + 0.25 * i;
        }
        assertAllClose("arange", expected, output, 1e-5, 1e-5);
    }

    @Test
    public void testLinspace() throws TornadoExecutionPlanException {
        final int n = 1000;
        FloatArray output = new FloatArray(n);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, output) //
                .libraryTask("linspace", MlxCreation::linspace, output, -1.0f, 3.0f) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        double[] expected = new double[n];
        for (int i = 0; i < n; i++) {
            expected[i] = -1.0 + 4.0 * i / (n - 1);
        }
        assertAllClose("linspace", expected, output, 1e-5, 1e-5);
    }

    @Test
    public void testFull() throws TornadoExecutionPlanException {
        constant(out -> MlxCreation.full(out, 2.5f), 2.5f);
    }

    @Test
    public void testZeros() throws TornadoExecutionPlanException {
        constant(MlxCreation::zeros, 0f);
    }

    @Test
    public void testOnes() throws TornadoExecutionPlanException {
        constant(MlxCreation::ones, 1f);
    }

    @Test
    public void testFullLikeZerosLikeOnesLike() throws TornadoExecutionPlanException {
        FloatArray like = FloatArray.fromArray(values(SIZE, -1, 1, 1));
        FloatArray full = new FloatArray(SIZE);
        FloatArray zeros = new FloatArray(SIZE);
        FloatArray ones = new FloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, like) //
                .libraryTask("fullLike", MlxCreation::fullLike, like, full, -1.5f) //
                .libraryTask("zerosLike", MlxCreation::zerosLike, like, zeros) //
                .libraryTask("onesLike", MlxCreation::onesLike, like, ones) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, full, zeros, ones);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals("fullLike " + i, -1.5f, full.get(i), 0f);
            assertEquals("zerosLike " + i, 0f, zeros.get(i), 0f);
            assertEquals("onesLike " + i, 1f, ones.get(i), 0f);
        }
    }

    // ---------------------------------------------------------------- identity and triangular matrices

    @Test
    public void testEye() throws TornadoExecutionPlanException {
        // 37x53, ones on the third superdiagonal.
        final int n = 37;
        final int m = 53;
        FloatArray output = new FloatArray(n * m);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, output) //
                .libraryTask("eye", MlxCreation::eye, output, n, m, 3) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int r = 0; r < n; r++) {
            for (int c = 0; c < m; c++) {
                assertEquals(r + "," + c, c - r == 3 ? 1f : 0f, output.get(r * m + c), 0f);
            }
        }
    }

    @Test
    public void testIdentity() throws TornadoExecutionPlanException {
        final int n = 37;
        FloatArray output = new FloatArray(n * n);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, output) //
                .libraryTask("identity", MlxCreation::identity, output, n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                assertEquals(r + "," + c, r == c ? 1f : 0f, output.get(r * n + c), 0f);
            }
        }
    }

    @Test
    public void testTri() throws TornadoExecutionPlanException {
        // Ones at and below the second subdiagonal.
        final int n = 37;
        final int m = 53;
        FloatArray output = new FloatArray(n * m);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, output) //
                .libraryTask("tri", MlxCreation::tri, output, n, m, -2) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int r = 0; r < n; r++) {
            for (int c = 0; c < m; c++) {
                assertEquals(r + "," + c, c - r <= -2 ? 1f : 0f, output.get(r * m + c), 0f);
            }
        }
    }

    @Test
    public void testTril() throws TornadoExecutionPlanException {
        float[] av = values(ROWS * COLS, -5, 5, 2);
        FloatArray a = FloatArray.fromArray(av);
        for (int k : new int[] { 0, 4, -3 }) {
            FloatArray output = new FloatArray(ROWS * COLS);

            TaskGraph taskGraph = new TaskGraph("g") //
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                    .libraryTask("tril", MlxCreation::tril, a, output, ROWS, COLS, k) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
                plan.execute();
            }

            assertAllClose("tril k=" + k, triangleJava(av, k, true), output, 0, 0);
        }
    }

    @Test
    public void testTriu() throws TornadoExecutionPlanException {
        float[] av = values(ROWS * COLS, -5, 5, 3);
        FloatArray a = FloatArray.fromArray(av);
        for (int k : new int[] { 0, 4, -3 }) {
            FloatArray output = new FloatArray(ROWS * COLS);

            TaskGraph taskGraph = new TaskGraph("g") //
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                    .libraryTask("triu", MlxCreation::triu, a, output, ROWS, COLS, k) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
                plan.execute();
            }

            assertAllClose("triu k=" + k, triangleJava(av, k, false), output, 0, 0);
        }
    }

    // ---------------------------------------------------------------- diagonals

    @Test
    public void testDiag() throws TornadoExecutionPlanException {
        // A vector of 9 placed on diagonal k of a square matrix of side 9 + |k|.
        float[] vv = values(9, -2, 2, 4);
        FloatArray v = FloatArray.fromArray(vv);
        for (int k : new int[] { 0, 4, -3 }) {
            int side = 9 + Math.abs(k);
            FloatArray output = new FloatArray(side * side);

            TaskGraph taskGraph = new TaskGraph("g") //
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, v) //
                    .libraryTask("diag", MlxCreation::diag, v, output, k) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
                plan.execute();
            }

            double[] expected = new double[side * side];
            for (int i = 0; i < 9; i++) {
                int r = k >= 0 ? i : i - k;
                int c = k >= 0 ? i + k : i;
                expected[r * side + c] = vv[i];
            }
            assertAllClose("diag k=" + k, expected, output, 0, 0);
        }
    }

    @Test
    public void testDiagonal() throws TornadoExecutionPlanException {
        float[] av = values(ROWS * COLS, -5, 5, 5);
        FloatArray a = FloatArray.fromArray(av);
        for (int k : new int[] { 0, 4, -3 }) {
            int len = Math.min(ROWS + Math.min(k, 0), COLS - Math.max(k, 0));
            FloatArray output = new FloatArray(len);

            TaskGraph taskGraph = new TaskGraph("g") //
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                    .libraryTask("diagonal", MlxCreation::diagonal, a, output, ROWS, COLS, k) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
                plan.execute();
            }

            for (int t = 0; t < len; t++) {
                assertEquals("k=" + k + " element " + t, av[(t + Math.max(-k, 0)) * COLS + t + Math.max(k, 0)], output.get(t), 0f);
            }
        }
    }

    @Test
    public void testTrace() throws TornadoExecutionPlanException {
        float[] av = values(ROWS * COLS, -5, 5, 6);
        FloatArray a = FloatArray.fromArray(av);
        for (int k : new int[] { 0, 4, -3 }) {
            int len = Math.min(ROWS + Math.min(k, 0), COLS - Math.max(k, 0));
            FloatArray output = new FloatArray(1);

            TaskGraph taskGraph = new TaskGraph("g") //
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                    .libraryTask("trace", MlxCreation::trace, a, output, ROWS, COLS, k) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
                plan.execute();
            }

            double trace = 0;
            for (int t = 0; t < len; t++) {
                trace += av[(t + Math.max(-k, 0)) * COLS + t + Math.max(k, 0)];
            }
            assertAllClose("trace k=" + k, new double[] { trace }, output, 1e-4, 1e-4);
        }
    }

    // ---------------------------------------------------------------- windows and grids

    @Test
    public void testBartlett() throws TornadoExecutionPlanException {
        window(MlxCreation::bartlett, WINDOW, i -> 1 - Math.abs(2 * x(i, WINDOW) - 1));
    }

    @Test
    public void testBlackman() throws TornadoExecutionPlanException {
        window(MlxCreation::blackman, WINDOW, i -> 0.42 - 0.5 * Math.cos(2 * Math.PI * x(i, WINDOW)) + 0.08 * Math.cos(4 * Math.PI * x(i, WINDOW)));
    }

    @Test
    public void testHamming() throws TornadoExecutionPlanException {
        window(MlxCreation::hamming, WINDOW, i -> 0.54 - 0.46 * Math.cos(2 * Math.PI * x(i, WINDOW)));
    }

    @Test
    public void testHanning() throws TornadoExecutionPlanException {
        window(MlxCreation::hanning, WINDOW, i -> 0.5 - 0.5 * Math.cos(2 * Math.PI * x(i, WINDOW)));
    }

    @Test
    public void testWindowOfLengthOneIsOne() throws TornadoExecutionPlanException {
        window(MlxCreation::hanning, 1, i -> 1);
    }

    @Test
    public void testMeshgrid() throws TornadoExecutionPlanException {
        // xy indexing: outX[ny, nx] repeats x along rows, outY repeats y along columns.
        final int nx = 13;
        final int ny = 7;
        float[] xv = values(nx, -1, 1, 7);
        float[] yv = values(ny, -1, 1, 8);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray y = FloatArray.fromArray(yv);
        FloatArray outX = new FloatArray(nx * ny);
        FloatArray outY = new FloatArray(nx * ny);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, y) //
                .libraryTask("meshgrid", MlxCreation::meshgrid, x, y, outX, outY, false) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outX, outY);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int t = 0; t < nx * ny; t++) {
            assertEquals("x " + t, xv[t % nx], outX.get(t), 0f);
            assertEquals("y " + t, yv[t / nx], outY.get(t), 0f);
        }
    }

    @Test
    public void testMeshgridIj() throws TornadoExecutionPlanException {
        // ij indexing: outX[nx, ny] repeats x along columns, outY repeats y along rows.
        final int nx = 13;
        final int ny = 7;
        float[] xv = values(nx, -1, 1, 9);
        float[] yv = values(ny, -1, 1, 10);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray y = FloatArray.fromArray(yv);
        FloatArray outX = new FloatArray(nx * ny);
        FloatArray outY = new FloatArray(nx * ny);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, y) //
                .libraryTask("meshgrid", MlxCreation::meshgrid, x, y, outX, outY, true) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outX, outY);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int t = 0; t < nx * ny; t++) {
            assertEquals("x " + t, xv[t / ny], outX.get(t), 0f);
            assertEquals("y " + t, yv[t % ny], outY.get(t), 0f);
        }
    }

    // ---------------------------------------------------------------- other types

    @Test
    public void testArangeInt() throws TornadoExecutionPlanException {
        final int n = 100;
        IntArray output = new IntArray(n);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, output) //
                .libraryTask("arange", MlxCreation::arange, output, 5f, 5f + 3f * n, 3f) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < n; i++) {
            assertEquals("element " + i, 5 + 3 * i, output.get(i));
        }
    }

    @Test
    public void testIdentityInt() throws TornadoExecutionPlanException {
        final int n = 100;
        IntArray output = new IntArray(n * n);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, output) //
                .libraryTask("identity", MlxCreation::identity, output, n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                assertEquals(r + "," + c, r == c ? 1 : 0, output.get(r * n + c));
            }
        }
    }

    @Test
    public void testOnesHalf() throws TornadoExecutionPlanException {
        final int n = 100;
        HalfFloatArray output = new HalfFloatArray(n);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, output) //
                .libraryTask("ones", MlxCreation::ones, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < n; i++) {
            assertEquals("element " + i, 1.0f, output.get(i).getFloat32(), 0f);
        }
    }
}
