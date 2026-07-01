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
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.matrix.Matrix8x8Float;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Unit tests for a threadgroup-tiled GEMM built from the {@code KernelContext.simdgroupMatrix*}
 * primitives on Apple's {@code simdgroup_float8x8} hardware matrix units.
 *
 * <p>Each threadgroup (four SIMD groups, 128 threads) computes a 32x32 output tile,
 * staging 32x8 / 8x32 blocks of A and B in {@code threadgroup} memory and reusing them
 * across a 4x4 grid of register fragments.
 *
 * <p>Metal-only: simdgroup_matrix has no equivalent in the OpenCL, PTX or SPIR-V
 * backends, so the tests are skipped there.
 *
 * <p>How to run:
 * <code>
 * tornado-test --threadInfo --printKernel -V uk.ac.manchester.tornado.unittests.kernelcontext.matrices.TestSimdgroupTiledMatrix
 * </code>
 */
public class TestSimdgroupTiledMatrix extends TornadoTestBase {

    private static final int BLOCK = 32;     // 32x32 output tile per threadgroup
    private static final int THREADS = 128;  // four SIMD groups

    /** Threadgroup-tiled GEMM built from the simdgroupMatrix* primitives: four SIMD groups
     *  per 32x32 tile staging 32x8/8x32 blocks of A/B in threadgroup memory. */
    private static void gemm(KernelContext ctx, FloatArray a, FloatArray b, FloatArray c, int m, int n, int k) {
        float[] as = ctx.allocateFloatLocalArray(256);
        float[] bs = ctx.allocateFloatLocalArray(256);
        int tilesPerRow = n / BLOCK;
        int rowBase = (ctx.groupIdx / tilesPerRow) * BLOCK;
        int colBase = (ctx.groupIdx % tilesPerRow) * BLOCK;
        int tid = ctx.localIdx;
        int sgRow = (tid / 32) / 2;
        int sgCol = (tid / 32) % 2;
        Matrix8x8Float acc00 = ctx.simdgroupMatrixZero();
        Matrix8x8Float acc01 = ctx.simdgroupMatrixZero();
        Matrix8x8Float acc10 = ctx.simdgroupMatrixZero();
        Matrix8x8Float acc11 = ctx.simdgroupMatrixZero();
        for (int kb = 0; kb < k; kb += 8) {
            for (int e = tid; e < 256; e += THREADS) {
                as[e] = a.get((rowBase + e / 8) * k + (kb + e % 8));
            }
            for (int e = tid; e < 256; e += THREADS) {
                bs[e] = b.get((kb + e / BLOCK) * n + (colBase + e % BLOCK));
            }
            ctx.localBarrier();
            Matrix8x8Float a0 = ctx.simdgroupMatrixLoad(as, (sgRow * 2) * 64, 8);
            Matrix8x8Float a1 = ctx.simdgroupMatrixLoad(as, (sgRow * 2 + 1) * 64, 8);
            Matrix8x8Float b0 = ctx.simdgroupMatrixLoad(bs, (sgCol * 2) * 8, BLOCK);
            Matrix8x8Float b1 = ctx.simdgroupMatrixLoad(bs, (sgCol * 2 + 1) * 8, BLOCK);
            acc00 = ctx.simdgroupMatrixMultiplyAccumulate(a0, b0, acc00);
            acc01 = ctx.simdgroupMatrixMultiplyAccumulate(a0, b1, acc01);
            acc10 = ctx.simdgroupMatrixMultiplyAccumulate(a1, b0, acc10);
            acc11 = ctx.simdgroupMatrixMultiplyAccumulate(a1, b1, acc11);
            ctx.localBarrier();
        }
        int cr0 = rowBase + (sgRow * 2) * 8;
        int cr1 = rowBase + (sgRow * 2 + 1) * 8;
        int cc0 = colBase + (sgCol * 2) * 8;
        int cc1 = colBase + (sgCol * 2 + 1) * 8;
        ctx.simdgroupMatrixStore(acc00, c, cr0 * n + cc0, n);
        ctx.simdgroupMatrixStore(acc01, c, cr0 * n + cc1, n);
        ctx.simdgroupMatrixStore(acc10, c, cr1 * n + cc0, n);
        ctx.simdgroupMatrixStore(acc11, c, cr1 * n + cc1, n);
    }

    private static void cpuReference(FloatArray a, FloatArray b, FloatArray c, int m, int n, int k) {
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                float acc = 0.0f;
                for (int p = 0; p < k; p++) {
                    acc += a.get(i * k + p) * b.get(p * n + j);
                }
                c.set(i * n + j, acc);
            }
        }
    }

    private void runAndCheck(int m, int n, int k) throws TornadoExecutionPlanException {
        // simdgroup_matrix is Metal-only.
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);
        assertNotBackend(TornadoVMBackendType.CUDA);

        FloatArray a = new FloatArray(m * k);
        FloatArray b = new FloatArray(k * n);
        FloatArray c = new FloatArray(m * n);
        FloatArray ref = new FloatArray(m * n);

        Random rnd = new Random(7);
        for (int i = 0; i < a.getSize(); i++) {
            a.set(i, rnd.nextFloat() - 0.5f);
        }
        for (int i = 0; i < b.getSize(); i++) {
            b.set(i, rnd.nextFloat() - 0.5f);
        }
        cpuReference(a, b, ref, m, n, k);

        KernelContext ctx = new KernelContext();
        int numTiles = (m / BLOCK) * (n / BLOCK);
        WorkerGrid1D grid = new WorkerGrid1D(numTiles * THREADS);
        grid.setLocalWork(THREADS, 1, 1);
        GridScheduler scheduler = new GridScheduler("s0.t0", grid);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestSimdgroupTiledMatrix::gemm, ctx, a, b, c, m, n, k) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(scheduler).execute();
        }

        float tol = 1e-3f * k;
        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(ref.get(i), c.get(i), tol);
        }
    }

    @Test
    public void testSingleTile() throws TornadoExecutionPlanException {
        runAndCheck(32, 32, 8);
    }

    @Test
    public void testSingleTileDeepK() throws TornadoExecutionPlanException {
        runAndCheck(32, 32, 256);
    }

    @Test
    public void testMultiTile() throws TornadoExecutionPlanException {
        runAndCheck(64, 64, 64);
    }

    @Test
    public void testRectangular() throws TornadoExecutionPlanException {
        runAndCheck(128, 64, 32);
    }
}
