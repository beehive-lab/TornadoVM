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
package uk.ac.manchester.tornado.unittests.kernelcontext.matrices;

import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.matrix.Matrix8x8Float;
import uk.ac.manchester.tornado.api.types.matrix.Matrix8x8Half;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Half-precision and transposed SIMD-group matrix primitives ({@code simdgroup_half8x8},
 * {@code simdgroup_load} with {@code transpose}, mixed-precision
 * {@code simdgroup_multiply_accumulate}). The GEMMs compute {@code c[i, j] = sum_p x[i, p] * w[j, p]}:
 * the weight matrix is stored with the contraction dimension contiguous, as in a linear layer,
 * and is read as the right-hand operand with a transposed load.
 *
 * <p>How to run:
 *
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.kernelcontext.matrices.TestSimdgroupMatrixHalf
 * </code>
 */
public class TestSimdgroupMatrixHalf extends TornadoTestBase {

    private static final int SIMD = 32;
    private static final int BLOCK = 32;      // 32x32 output tile per threadgroup
    private static final int THREADS = 128;   // four SIMD groups, each owning a 16x16 quadrant
    private static final int KSTEP = 32;      // contraction slice staged per step
    private static final int LDS = KSTEP + 8; // padded threadgroup row stride

    /** One SIMD group: an 8x8 tile of {@code x * w^T} from device memory, half inputs, float accumulation. */
    private static void deviceTile(KernelContext ctx, HalfFloatArray x, HalfFloatArray w, FloatArray c, int k) {
        int tile = ctx.groupIdx;
        if (tile < 1) {
            Matrix8x8Float acc = ctx.simdgroupMatrixZero();
            for (int p = 0; p < k; p += 8) {
                Matrix8x8Half a = ctx.simdgroupMatrixLoad(x, p, k);
                Matrix8x8Half b = ctx.simdgroupMatrixLoadTransposed(w, p, k);
                acc = ctx.simdgroupMatrixMultiplyAccumulate(a, b, acc);
            }
            ctx.simdgroupMatrixStore(acc, c, 0, 8);
        }
    }

    /** One SIMD group: float {@code x}, transposed float {@code w} from device memory. */
    private static void deviceTileTransposedFloat(KernelContext ctx, FloatArray x, FloatArray w, FloatArray c, int k) {
        int tile = ctx.groupIdx;
        if (tile < 1) {
            Matrix8x8Float acc = ctx.simdgroupMatrixZero();
            for (int p = 0; p < k; p += 8) {
                Matrix8x8Float a = ctx.simdgroupMatrixLoad(x, p, k);
                Matrix8x8Float b = ctx.simdgroupMatrixLoadTransposed(w, p, k);
                acc = ctx.simdgroupMatrixMultiplyAccumulate(a, b, acc);
            }
            ctx.simdgroupMatrixStore(acc, c, 0, 8);
        }
    }

    /**
     * Threadgroup-tiled {@code x * w^T} with both operands staged as half in threadgroup memory,
     * {@code w} read with a transposed load, and the result written through a threadgroup
     * buffer so that tiles past the ends of {@code m} and {@code n} are guarded.
     */
    private static void gemmHalfStaged(KernelContext ctx, FloatArray x, HalfFloatArray w, FloatArray c, int m, int n, int k) {
        HalfFloat[] xs = ctx.allocateHalfFloatLocalArray(BLOCK * LDS);
        HalfFloat[] ws = ctx.allocateHalfFloatLocalArray(BLOCK * LDS);
        float[] cs = ctx.allocateFloatLocalArray(BLOCK * BLOCK);
        int tid = ctx.localIdx;
        int colTiles = (n + BLOCK - 1) / BLOCK;
        int rowBase = (ctx.groupIdx / colTiles) * BLOCK;
        int colBase = (ctx.groupIdx % colTiles) * BLOCK;
        int sg = tid / SIMD;
        int sgRow = (sg / 2) * 16;
        int sgCol = (sg % 2) * 16;
        Matrix8x8Float acc00 = ctx.simdgroupMatrixZero();
        Matrix8x8Float acc01 = ctx.simdgroupMatrixZero();
        Matrix8x8Float acc10 = ctx.simdgroupMatrixZero();
        Matrix8x8Float acc11 = ctx.simdgroupMatrixZero();
        for (int k0 = 0; k0 < k; k0 += KSTEP) {
            for (int e = tid; e < BLOCK * KSTEP; e += THREADS) {
                int r = e / KSTEP;
                int kk = e % KSTEP;
                float xv = 0.0f;
                if (rowBase + r < m && k0 + kk < k) {
                    xv = x.get((rowBase + r) * k + k0 + kk);
                }
                xs[r * LDS + kk] = new HalfFloat(xv);
                float wv = 0.0f;
                if (colBase + r < n && k0 + kk < k) {
                    wv = w.get((colBase + r) * k + k0 + kk).getFloat32();
                }
                ws[r * LDS + kk] = new HalfFloat(wv);
            }
            ctx.localBarrier();
            for (int kk = 0; kk < KSTEP; kk += 8) {
                Matrix8x8Half a0 = ctx.simdgroupMatrixLoad(xs, sgRow * LDS + kk, LDS);
                Matrix8x8Half a1 = ctx.simdgroupMatrixLoad(xs, (sgRow + 8) * LDS + kk, LDS);
                Matrix8x8Half b0 = ctx.simdgroupMatrixLoadTransposed(ws, sgCol * LDS + kk, LDS);
                Matrix8x8Half b1 = ctx.simdgroupMatrixLoadTransposed(ws, (sgCol + 8) * LDS + kk, LDS);
                acc00 = ctx.simdgroupMatrixMultiplyAccumulate(a0, b0, acc00);
                acc01 = ctx.simdgroupMatrixMultiplyAccumulate(a0, b1, acc01);
                acc10 = ctx.simdgroupMatrixMultiplyAccumulate(a1, b0, acc10);
                acc11 = ctx.simdgroupMatrixMultiplyAccumulate(a1, b1, acc11);
            }
            ctx.localBarrier();
        }
        ctx.simdgroupMatrixStore(acc00, cs, sgRow * BLOCK + sgCol, BLOCK);
        ctx.simdgroupMatrixStore(acc01, cs, sgRow * BLOCK + sgCol + 8, BLOCK);
        ctx.simdgroupMatrixStore(acc10, cs, (sgRow + 8) * BLOCK + sgCol, BLOCK);
        ctx.simdgroupMatrixStore(acc11, cs, (sgRow + 8) * BLOCK + sgCol + 8, BLOCK);
        ctx.localBarrier();
        for (int e = tid; e < BLOCK * BLOCK; e += THREADS) {
            int r = e / BLOCK;
            int col = e % BLOCK;
            if (rowBase + r < m && colBase + col < n) {
                c.set((rowBase + r) * n + colBase + col, cs[e]);
            }
        }
    }

    /**
     * Same tiling as {@link #gemmHalfStaged} with {@code x} staged as float and {@code w} as half:
     * a float-by-half multiply-accumulate.
     */
    private static void gemmMixedStaged(KernelContext ctx, FloatArray x, HalfFloatArray w, FloatArray c, int m, int n, int k) {
        float[] xs = ctx.allocateFloatLocalArray(BLOCK * LDS);
        HalfFloat[] ws = ctx.allocateHalfFloatLocalArray(BLOCK * LDS);
        float[] cs = ctx.allocateFloatLocalArray(BLOCK * BLOCK);
        int tid = ctx.localIdx;
        int colTiles = (n + BLOCK - 1) / BLOCK;
        int rowBase = (ctx.groupIdx / colTiles) * BLOCK;
        int colBase = (ctx.groupIdx % colTiles) * BLOCK;
        int sg = tid / SIMD;
        int sgRow = (sg / 2) * 16;
        int sgCol = (sg % 2) * 16;
        Matrix8x8Float acc00 = ctx.simdgroupMatrixZero();
        Matrix8x8Float acc01 = ctx.simdgroupMatrixZero();
        Matrix8x8Float acc10 = ctx.simdgroupMatrixZero();
        Matrix8x8Float acc11 = ctx.simdgroupMatrixZero();
        for (int k0 = 0; k0 < k; k0 += KSTEP) {
            for (int e = tid; e < BLOCK * KSTEP; e += THREADS) {
                int r = e / KSTEP;
                int kk = e % KSTEP;
                float xv = 0.0f;
                if (rowBase + r < m && k0 + kk < k) {
                    xv = x.get((rowBase + r) * k + k0 + kk);
                }
                xs[r * LDS + kk] = xv;
                float wv = 0.0f;
                if (colBase + r < n && k0 + kk < k) {
                    wv = w.get((colBase + r) * k + k0 + kk).getFloat32();
                }
                ws[r * LDS + kk] = new HalfFloat(wv);
            }
            ctx.localBarrier();
            for (int kk = 0; kk < KSTEP; kk += 8) {
                Matrix8x8Float a0 = ctx.simdgroupMatrixLoad(xs, sgRow * LDS + kk, LDS);
                Matrix8x8Float a1 = ctx.simdgroupMatrixLoad(xs, (sgRow + 8) * LDS + kk, LDS);
                Matrix8x8Half b0 = ctx.simdgroupMatrixLoadTransposed(ws, sgCol * LDS + kk, LDS);
                Matrix8x8Half b1 = ctx.simdgroupMatrixLoadTransposed(ws, (sgCol + 8) * LDS + kk, LDS);
                acc00 = ctx.simdgroupMatrixMultiplyAccumulate(a0, b0, acc00);
                acc01 = ctx.simdgroupMatrixMultiplyAccumulate(a0, b1, acc01);
                acc10 = ctx.simdgroupMatrixMultiplyAccumulate(a1, b0, acc10);
                acc11 = ctx.simdgroupMatrixMultiplyAccumulate(a1, b1, acc11);
            }
            ctx.localBarrier();
        }
        ctx.simdgroupMatrixStore(acc00, cs, sgRow * BLOCK + sgCol, BLOCK);
        ctx.simdgroupMatrixStore(acc01, cs, sgRow * BLOCK + sgCol + 8, BLOCK);
        ctx.simdgroupMatrixStore(acc10, cs, (sgRow + 8) * BLOCK + sgCol, BLOCK);
        ctx.simdgroupMatrixStore(acc11, cs, (sgRow + 8) * BLOCK + sgCol + 8, BLOCK);
        ctx.localBarrier();
        for (int e = tid; e < BLOCK * BLOCK; e += THREADS) {
            int r = e / BLOCK;
            int col = e % BLOCK;
            if (rowBase + r < m && colBase + col < n) {
                c.set((rowBase + r) * n + colBase + col, cs[e]);
            }
        }
    }

    /** {@code c[i, j] = sum_p x[i, p] * w[j, p]} in double precision. */
    private static void reference(float[] x, float[] w, float[] c, int m, int n, int k) {
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                double acc = 0.0;
                for (int p = 0; p < k; p++) {
                    acc += (double) x[i * k + p] * w[j * k + p];
                }
                c[i * n + j] = (float) acc;
            }
        }
    }

    /** Random values that are exact in half precision, so half staging is lossless. */
    private static float[] randomHalfExact(Random rnd, int size) {
        float[] v = new float[size];
        for (int i = 0; i < size; i++) {
            v[i] = new HalfFloat(rnd.nextFloat() - 0.5f).getFloat32();
        }
        return v;
    }

    private static FloatArray toFloatArray(float[] v) {
        FloatArray a = new FloatArray(v.length);
        for (int i = 0; i < v.length; i++) {
            a.set(i, v[i]);
        }
        return a;
    }

    private static HalfFloatArray toHalfFloatArray(float[] v) {
        HalfFloatArray a = new HalfFloatArray(v.length);
        for (int i = 0; i < v.length; i++) {
            a.set(i, new HalfFloat(v[i]));
        }
        return a;
    }

    private static void assertClose(float[] ref, FloatArray c, int k) {
        float tol = 1e-4f * k;
        for (int i = 0; i < ref.length; i++) {
            assertEquals("element " + i, ref[i], c.get(i), tol);
        }
    }

    private static GridScheduler scheduler(int groups, int localSize) {
        WorkerGrid1D grid = new WorkerGrid1D(groups * localSize);
        grid.setLocalWork(localSize, 1, 1);
        return new GridScheduler("s0.t0", grid);
    }

    private void assumeMetal() {
        // simdgroup_matrix is Metal-only: no equivalent in OpenCL or CUDA.
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.CUDA);
    }

    @Test
    public void testHalfDeviceTile() throws TornadoExecutionPlanException {
        assumeMetal();
        final int k = 64;
        Random rnd = new Random(11);
        float[] xv = randomHalfExact(rnd, 8 * k);
        float[] wv = randomHalfExact(rnd, 8 * k);
        float[] ref = new float[64];
        reference(xv, wv, ref, 8, 8, k);
        HalfFloatArray x = toHalfFloatArray(xv);
        HalfFloatArray w = toHalfFloatArray(wv);
        FloatArray c = new FloatArray(64);

        KernelContext ctx = new KernelContext();
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, x, w) //
                .task("t0", TestSimdgroupMatrixHalf::deviceTile, ctx, x, w, c, k) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(scheduler(1, SIMD)).execute();
        }
        assertClose(ref, c, k);
    }

    @Test
    public void testFloatTransposedDeviceTile() throws TornadoExecutionPlanException {
        assumeMetal();
        final int k = 40;
        Random rnd = new Random(12);
        float[] xv = randomHalfExact(rnd, 8 * k);
        float[] wv = randomHalfExact(rnd, 8 * k);
        float[] ref = new float[64];
        reference(xv, wv, ref, 8, 8, k);
        FloatArray x = toFloatArray(xv);
        FloatArray w = toFloatArray(wv);
        FloatArray c = new FloatArray(64);

        KernelContext ctx = new KernelContext();
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, x, w) //
                .task("t0", TestSimdgroupMatrixHalf::deviceTileTransposedFloat, ctx, x, w, c, k) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(scheduler(1, SIMD)).execute();
        }
        assertClose(ref, c, k);
    }

    private void runStaged(boolean mixed, int m, int n, int k) throws TornadoExecutionPlanException {
        assumeMetal();
        Random rnd = new Random(31L * m + 17L * n + k);
        float[] xv = randomHalfExact(rnd, m * k);
        float[] wv = randomHalfExact(rnd, n * k);
        float[] ref = new float[m * n];
        reference(xv, wv, ref, m, n, k);
        FloatArray x = toFloatArray(xv);
        HalfFloatArray w = toHalfFloatArray(wv);
        FloatArray c = new FloatArray(m * n);

        KernelContext ctx = new KernelContext();
        int groups = ((m + BLOCK - 1) / BLOCK) * ((n + BLOCK - 1) / BLOCK);
        TaskGraph taskGraph = new TaskGraph("s0").transferToDevice(DataTransferMode.FIRST_EXECUTION, x, w);
        if (mixed) {
            taskGraph.task("t0", TestSimdgroupMatrixHalf::gemmMixedStaged, ctx, x, w, c, m, n, k);
        } else {
            taskGraph.task("t0", TestSimdgroupMatrixHalf::gemmHalfStaged, ctx, x, w, c, m, n, k);
        }
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(scheduler(groups, THREADS)).execute();
        }
        assertClose(ref, c, k);
    }

    @Test
    public void testHalfStaged64() throws TornadoExecutionPlanException {
        runStaged(false, 64, 64, 64);
    }

    @Test
    public void testHalfStagedIrregular() throws TornadoExecutionPlanException {
        runStaged(false, 37, 72, 100);
    }

    @Test
    public void testMixedStaged64() throws TornadoExecutionPlanException {
        runStaged(true, 64, 64, 64);
    }

    @Test
    public void testMixedStagedIrregular() throws TornadoExecutionPlanException {
        runStaged(true, 45, 40, 72);
    }
}
