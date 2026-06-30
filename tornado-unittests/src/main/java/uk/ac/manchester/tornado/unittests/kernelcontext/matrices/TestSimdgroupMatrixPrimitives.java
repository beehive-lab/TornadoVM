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
 * Unit tests for the low-level {@code simdgroup_float8x8} matrix-unit primitives
 * ({@link KernelContext#simdgroupMatrixZero} / {@code simdgroupMatrixLoad} /
 * {@code simdgroupMatrixMultiplyAccumulate} / {@code simdgroupMatrixStore}).
 *
 * <p>The GEMM tiling loop is written in plain Java in the kernel below; only the four
 * primitive calls are replaced by hardware instructions. This exercises the opaque
 * fragment value flowing through a normally-compiled, loop-carried accumulator.
 *
 * <p>Metal-only: the primitives have no OpenCL/PTX/SPIR-V equivalent.
 */
public class TestSimdgroupMatrixPrimitives extends TornadoTestBase {

    private static final int TILE = 8;
    private static final int SIMD_GROUP = 32;

    /**
     * One SIMD group per 8x8 output tile, accumulating over K with the fragment in
     * registers. The loop and index math are ordinary Java; the four ctx.simdgroupMatrix*
     * calls are the only intrinsics.
     */
    private static void gemm(KernelContext ctx, FloatArray a, FloatArray b, FloatArray c, int m, int n, int k) {
        int tilesPerRow = n / TILE;
        int tileIdx = ctx.groupIdx;
        int tileRow = tileIdx / tilesPerRow;
        int tileCol = tileIdx % tilesPerRow;

        int aBase = tileRow * TILE * k;
        int bBase = tileCol * TILE;
        int cBase = tileRow * TILE * n + tileCol * TILE;

        Matrix8x8Float acc = ctx.simdgroupMatrixZero();
        for (int p = 0; p < k; p += TILE) {
            Matrix8x8Float af = ctx.simdgroupMatrixLoad(a, aBase + p, k);
            Matrix8x8Float bf = ctx.simdgroupMatrixLoad(b, bBase + p * n, n);
            acc = ctx.simdgroupMatrixMultiplyAccumulate(af, bf, acc);
        }
        ctx.simdgroupMatrixStore(acc, c, cBase, n);
    }

    /** 16x16 register-tiled (4 accumulators) reading A/B directly from device,
     *  no threadgroup float[] staging. Isolates multi-accumulator fragments from local arrays. */
    private static void gemmRegTiled(KernelContext ctx, FloatArray a, FloatArray b, FloatArray c, int m, int n, int k) {
        int tilesPerRow = n / 16;
        int t = ctx.groupIdx;
        int rowBase = (t / tilesPerRow) * 16;
        int colBase = (t % tilesPerRow) * 16;
        Matrix8x8Float acc00 = ctx.simdgroupMatrixZero();
        Matrix8x8Float acc01 = ctx.simdgroupMatrixZero();
        Matrix8x8Float acc10 = ctx.simdgroupMatrixZero();
        Matrix8x8Float acc11 = ctx.simdgroupMatrixZero();
        for (int p = 0; p < k; p += TILE) {
            Matrix8x8Float a0 = ctx.simdgroupMatrixLoad(a, rowBase * k + p, k);
            Matrix8x8Float a1 = ctx.simdgroupMatrixLoad(a, (rowBase + 8) * k + p, k);
            Matrix8x8Float b0 = ctx.simdgroupMatrixLoad(b, p * n + colBase, n);
            Matrix8x8Float b1 = ctx.simdgroupMatrixLoad(b, p * n + colBase + 8, n);
            acc00 = ctx.simdgroupMatrixMultiplyAccumulate(a0, b0, acc00);
            acc01 = ctx.simdgroupMatrixMultiplyAccumulate(a0, b1, acc01);
            acc10 = ctx.simdgroupMatrixMultiplyAccumulate(a1, b0, acc10);
            acc11 = ctx.simdgroupMatrixMultiplyAccumulate(a1, b1, acc11);
        }
        ctx.simdgroupMatrixStore(acc00, c, rowBase * n + colBase, n);
        ctx.simdgroupMatrixStore(acc01, c, rowBase * n + colBase + 8, n);
        ctx.simdgroupMatrixStore(acc10, c, (rowBase + 8) * n + colBase, n);
        ctx.simdgroupMatrixStore(acc11, c, (rowBase + 8) * n + colBase + 8, n);
    }

    @Test
    public void testRegisterTiled() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);
        int m = 32, n = 32, k = 32;
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
        int numTiles = (m / 16) * (n / 16);
        WorkerGrid1D grid = new WorkerGrid1D(numTiles * SIMD_GROUP);
        grid.setLocalWork(SIMD_GROUP, 1, 1);
        GridScheduler scheduler = new GridScheduler("s0.t0", grid);
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestSimdgroupMatrixPrimitives::gemmRegTiled, ctx, a, b, c, m, n, k) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.withGridScheduler(scheduler).execute();
        }
        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(ref.get(i), c.get(i), 1e-3f * k);
        }
    }

    /** Full threadgroup-staged tiled GEMM written over the primitives in user-kernel code:
     *  four SIMD groups per 32x32 tile, staging 32x8/8x32 blocks of A/B in threadgroup memory
     *  and reusing them across a 4x4 grid of register fragments. */
    private static void gemmTiledPrimitives(KernelContext ctx, FloatArray a, FloatArray b, FloatArray c, int m, int n, int k) {
        float[] as = ctx.allocateFloatLocalArray(256);
        float[] bs = ctx.allocateFloatLocalArray(256);
        int tilesPerRow = n / 32;
        int rowBase = (ctx.groupIdx / tilesPerRow) * 32;
        int colBase = (ctx.groupIdx % tilesPerRow) * 32;
        int tid = ctx.localIdx;
        int sgRow = (tid / 32) / 2;
        int sgCol = (tid / 32) % 2;
        Matrix8x8Float acc00 = ctx.simdgroupMatrixZero();
        Matrix8x8Float acc01 = ctx.simdgroupMatrixZero();
        Matrix8x8Float acc10 = ctx.simdgroupMatrixZero();
        Matrix8x8Float acc11 = ctx.simdgroupMatrixZero();
        for (int kb = 0; kb < k; kb += 8) {
            for (int e = tid; e < 256; e += 128) {
                as[e] = a.get((rowBase + e / 8) * k + (kb + e % 8));
            }
            for (int e = tid; e < 256; e += 128) {
                bs[e] = b.get((kb + e / 32) * n + (colBase + e % 32));
            }
            ctx.localBarrier();
            Matrix8x8Float a0 = ctx.simdgroupMatrixLoad(as, (sgRow * 2) * 64, 8);
            Matrix8x8Float a1 = ctx.simdgroupMatrixLoad(as, (sgRow * 2 + 1) * 64, 8);
            Matrix8x8Float b0 = ctx.simdgroupMatrixLoad(bs, (sgCol * 2) * 8, 32);
            Matrix8x8Float b1 = ctx.simdgroupMatrixLoad(bs, (sgCol * 2 + 1) * 8, 32);
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

    @Test
    public void testTiledGemmViaPrimitives() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);
        int m = 64, n = 64, k = 64;
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
        int numTiles = (m / 32) * (n / 32);
        WorkerGrid1D grid = new WorkerGrid1D(numTiles * 128);
        grid.setLocalWork(128, 1, 1);
        GridScheduler scheduler = new GridScheduler("s0.t0", grid);
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestSimdgroupMatrixPrimitives::gemmTiledPrimitives, ctx, a, b, c, m, n, k) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.withGridScheduler(scheduler).execute();
        }
        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(ref.get(i), c.get(i), 1e-3f * k);
        }
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
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

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
        int numTiles = (m / TILE) * (n / TILE);
        WorkerGrid1D grid = new WorkerGrid1D(numTiles * SIMD_GROUP);
        grid.setLocalWork(SIMD_GROUP, 1, 1);
        GridScheduler scheduler = new GridScheduler("s0.t0", grid);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestSimdgroupMatrixPrimitives::gemm, ctx, a, b, c, m, n, k) //
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
        runAndCheck(8, 8, 8);
    }

    @Test
    public void testSingleTileDeepK() throws TornadoExecutionPlanException {
        runAndCheck(8, 8, 256);
    }

    @Test
    public void testMultiTile() throws TornadoExecutionPlanException {
        runAndCheck(64, 64, 64);
    }
}
