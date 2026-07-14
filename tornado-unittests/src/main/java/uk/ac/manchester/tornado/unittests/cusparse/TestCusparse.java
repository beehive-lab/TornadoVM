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
package uk.ac.manchester.tornado.unittests.cusparse;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.Before;
import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.cusparse.Cusparse;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMCUDANotSupported;

/**
 * Exhaustive tests for the NVIDIA cuSPARSE library-task provider
 * ({@code nvidia/cusparse}). Covers FP32 CSR SpMV and SpMM across diagonal,
 * identity, general, rectangular, empty-row, and dense-ish sparsity patterns,
 * plus interleaving with JIT tasks, CUDA-graph capture, multiple shapes in one
 * context, and repeated-execution stability. Skipped unless the default device
 * is the CUDA backend and libtornado-cusparse (plus libcusparse) is present.
 */
public class TestCusparse extends TornadoTestBase {

    private static final Random random = new Random(42);

    @Before
    public void cusparseMustBeAvailable() {
        TornadoVMBackendType backendType = getTornadoRuntime().getDefaultDevice().getTornadoVMBackend();
        if (backendType != TornadoVMBackendType.CUDA) {
            String message = "cuSPARSE library tasks require the CUDA backend (default device is " + backendType + ")";
            switch (backendType) {
                case OPENCL, PTX, SPIRV, METAL -> assertNotBackend(backendType, message);
                default -> throw new TornadoVMCUDANotSupported(message);
            }
        }
        try {
            System.loadLibrary("tornado-cusparse");
        } catch (UnsatisfiedLinkError e) {
            throw new TornadoVMCUDANotSupported("libtornado-cusparse is not available: " + e.getMessage());
        }
    }

    /** A CSR matrix plus its dense form for reference. */
    private static final class Csr {
        final int rows;
        final int cols;
        final int nnz;
        final IntArray rowOffsets;
        final IntArray colInd;
        final FloatArray values;
        final float[] dense; // rows*cols row-major

        Csr(int rows, int cols, IntArray rowOffsets, IntArray colInd, FloatArray values, float[] dense) {
            this.rows = rows;
            this.cols = cols;
            this.nnz = values.getSize();
            this.rowOffsets = rowOffsets;
            this.colInd = colInd;
            this.values = values;
            this.dense = dense;
        }
    }

    /** Build a random CSR with the given per-row nonzero probability (>=1 nnz allowed to be 0). */
    private static Csr randomCsr(int rows, int cols, double density) {
        float[] dense = new float[rows * cols];
        List<Integer> rowPtr = new ArrayList<>();
        List<Integer> col = new ArrayList<>();
        List<Float> val = new ArrayList<>();
        rowPtr.add(0);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (random.nextDouble() < density) {
                    float v = random.nextFloat() - 0.5f;
                    dense[i * cols + j] = v;
                    col.add(j);
                    val.add(v);
                }
            }
            rowPtr.add(col.size());
        }
        IntArray rowOffsets = new IntArray(rows + 1);
        for (int i = 0; i <= rows; i++) {
            rowOffsets.set(i, rowPtr.get(i));
        }
        IntArray colInd = new IntArray(Math.max(1, col.size()));
        FloatArray values = new FloatArray(Math.max(1, val.size()));
        for (int i = 0; i < col.size(); i++) {
            colInd.set(i, col.get(i));
            values.set(i, val.get(i));
        }
        return new Csr(rows, cols, rowOffsets, colInd, values, dense);
    }

    private static FloatArray randomVector(int n) {
        FloatArray v = new FloatArray(n);
        for (int i = 0; i < n; i++) {
            v.set(i, random.nextFloat() - 0.5f);
        }
        return v;
    }

    /** Dense reference y = A * x. */
    private static FloatArray spmvReference(Csr a, FloatArray x) {
        FloatArray y = new FloatArray(a.rows);
        for (int i = 0; i < a.rows; i++) {
            float acc = 0.0f;
            for (int j = 0; j < a.cols; j++) {
                acc += a.dense[i * a.cols + j] * x.get(j);
            }
            y.set(i, acc);
        }
        return y;
    }

    private static void runSpMV(Csr a, FloatArray x, FloatArray y) throws TornadoExecutionPlanException {
        TaskGraph g = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a.rowOffsets, a.colInd, a.values, x) //
                .libraryTask("spmv", Cusparse::cusparseSpMV, a.rows, a.cols, a.nnz, a.rowOffsets, a.colInd, a.values, x, y) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, y);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(g.snapshot())) {
            plan.execute();
        }
    }

    private static void assertClose(int n, FloatArray expected, FloatArray actual) {
        for (int i = 0; i < n; i++) {
            assertEquals(expected.get(i), actual.get(i), 1e-4f * Math.max(1.0f, Math.abs(expected.get(i))));
        }
    }

    public static void addOne(FloatArray array) {
        for (@Parallel int i = 0; i < array.getSize(); i++) {
            array.set(i, array.get(i) + 1.0f);
        }
    }

    // ---- SpMV over sparsity patterns ----

    @Test
    public void testSpMVGeneral() throws TornadoExecutionPlanException {
        Csr a = randomCsr(256, 256, 0.05);
        FloatArray x = randomVector(256);
        FloatArray y = new FloatArray(256);
        runSpMV(a, x, y);
        assertClose(256, spmvReference(a, x), y);
    }

    @Test
    public void testSpMVIdentity() throws TornadoExecutionPlanException {
        int n = 128;
        IntArray rowOffsets = new IntArray(n + 1);
        IntArray colInd = new IntArray(n);
        FloatArray values = new FloatArray(n);
        float[] dense = new float[n * n];
        for (int i = 0; i < n; i++) {
            rowOffsets.set(i, i);
            colInd.set(i, i);
            values.set(i, 1.0f);
            dense[i * n + i] = 1.0f;
        }
        rowOffsets.set(n, n);
        Csr a = new Csr(n, n, rowOffsets, colInd, values, dense);
        FloatArray x = randomVector(n);
        FloatArray y = new FloatArray(n);
        runSpMV(a, x, y);
        assertClose(n, x, y); // identity: y == x
    }

    @Test
    public void testSpMVDiagonal() throws TornadoExecutionPlanException {
        int n = 200;
        IntArray rowOffsets = new IntArray(n + 1);
        IntArray colInd = new IntArray(n);
        FloatArray values = new FloatArray(n);
        float[] dense = new float[n * n];
        for (int i = 0; i < n; i++) {
            float d = 2.0f + random.nextFloat();
            rowOffsets.set(i, i);
            colInd.set(i, i);
            values.set(i, d);
            dense[i * n + i] = d;
        }
        rowOffsets.set(n, n);
        Csr a = new Csr(n, n, rowOffsets, colInd, values, dense);
        FloatArray x = randomVector(n);
        FloatArray y = new FloatArray(n);
        runSpMV(a, x, y);
        assertClose(n, spmvReference(a, x), y);
    }

    @Test
    public void testSpMVRectangular() throws TornadoExecutionPlanException {
        Csr a = randomCsr(300, 180, 0.08); // rows != cols
        FloatArray x = randomVector(180);
        FloatArray y = new FloatArray(300);
        runSpMV(a, x, y);
        assertClose(300, spmvReference(a, x), y);
    }

    @Test
    public void testSpMVWithEmptyRows() throws TornadoExecutionPlanException {
        // Force some all-zero rows: low density guarantees empty rows appear.
        Csr a = randomCsr(150, 150, 0.01);
        FloatArray x = randomVector(150);
        FloatArray y = new FloatArray(150);
        runSpMV(a, x, y);
        assertClose(150, spmvReference(a, x), y);
    }

    @Test
    public void testSpMVDenseIsh() throws TornadoExecutionPlanException {
        Csr a = randomCsr(128, 128, 0.9); // nearly dense stored as CSR
        FloatArray x = randomVector(128);
        FloatArray y = new FloatArray(128);
        runSpMV(a, x, y);
        assertClose(128, spmvReference(a, x), y);
    }

    // ---- SpMM ----

    private static void assertCloseMatrix(int rows, int n, float[] expected, FloatArray actual) {
        for (int i = 0; i < rows * n; i++) {
            assertEquals(expected[i], actual.get(i), 1e-4f * Math.max(1.0f, Math.abs(expected[i])));
        }
    }

    @Test
    public void testSpMM() throws TornadoExecutionPlanException {
        Csr a = randomCsr(96, 80, 0.1);
        int n = 32;
        FloatArray b = randomVector(a.cols * n);
        FloatArray c = new FloatArray(a.rows * n);

        TaskGraph g = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a.rowOffsets, a.colInd, a.values, b) //
                .libraryTask("spmm", Cusparse::cusparseSpMM, a.rows, a.cols, n, a.nnz, a.rowOffsets, a.colInd, a.values, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(g.snapshot())) {
            plan.execute();
        }

        float[] expected = new float[a.rows * n];
        for (int i = 0; i < a.rows; i++) {
            for (int col = 0; col < n; col++) {
                float acc = 0.0f;
                for (int p = 0; p < a.cols; p++) {
                    acc += a.dense[i * a.cols + p] * b.get(p * n + col);
                }
                expected[i * n + col] = acc;
            }
        }
        assertCloseMatrix(a.rows, n, expected, c);
    }

    // ---- Integration with the TornadoVM pipeline ----

    @Test
    public void testSpMVWithJitPreAndPost() throws TornadoExecutionPlanException {
        Csr a = randomCsr(256, 256, 0.05);
        FloatArray x = randomVector(256);
        FloatArray y = new FloatArray(256);

        TaskGraph g = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a.rowOffsets, a.colInd, a.values, x) //
                .task("pre", TestCusparse::addOne, x) //
                .libraryTask("spmv", Cusparse::cusparseSpMV, a.rows, a.cols, a.nnz, a.rowOffsets, a.colInd, a.values, x, y) //
                .task("post", TestCusparse::addOne, y) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, y);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(g.snapshot())) {
            plan.execute();
        }

        FloatArray xPlusOne = new FloatArray(256);
        for (int i = 0; i < 256; i++) {
            xPlusOne.set(i, x.get(i) + 1.0f);
        }
        FloatArray expected = spmvReference(a, xPlusOne);
        for (int i = 0; i < 256; i++) {
            expected.set(i, expected.get(i) + 1.0f);
        }
        assertClose(256, expected, y);
    }

    @Test
    public void testSpMVWithCudaGraph() throws TornadoExecutionPlanException {
        Csr a = randomCsr(256, 256, 0.05);
        FloatArray x = randomVector(256);
        FloatArray y = new FloatArray(256);
        FloatArray expected = spmvReference(a, x);

        TaskGraph g = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a.rowOffsets, a.colInd, a.values, x) //
                .libraryTask("spmv", Cusparse::cusparseSpMV, a.rows, a.cols, a.nnz, a.rowOffsets, a.colInd, a.values, x, y) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, y);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(g.snapshot())) {
            plan.withCUDAGraph();
            for (int it = 0; it < 5; it++) {
                plan.execute();
                assertClose(256, expected, y);
            }
        }
    }

    @Test
    public void testMultipleShapesOneContext() throws TornadoExecutionPlanException {
        int[][] shapes = { { 64, 64 }, { 128, 96 }, { 200, 256 } };
        for (int[] s : shapes) {
            Csr a = randomCsr(s[0], s[1], 0.1);
            FloatArray x = randomVector(s[1]);
            FloatArray y = new FloatArray(s[0]);
            runSpMV(a, x, y);
            assertClose(s[0], spmvReference(a, x), y);
        }
    }

    @Test
    public void testSpMVRepeatedStability() throws TornadoExecutionPlanException {
        Csr a = randomCsr(256, 256, 0.05);
        FloatArray x = randomVector(256);
        FloatArray y = new FloatArray(256);
        FloatArray expected = spmvReference(a, x);

        TaskGraph g = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a.rowOffsets, a.colInd, a.values, x) //
                .libraryTask("spmv", Cusparse::cusparseSpMV, a.rows, a.cols, a.nnz, a.rowOffsets, a.colInd, a.values, x, y) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, y);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(g.snapshot())) {
            for (int it = 0; it < 50; it++) {
                plan.execute();
                assertClose(256, expected, y);
            }
        }
    }
}
