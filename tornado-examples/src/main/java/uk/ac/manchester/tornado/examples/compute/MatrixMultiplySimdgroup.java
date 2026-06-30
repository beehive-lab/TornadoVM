/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.examples.compute;

import java.util.Arrays;
import java.util.Random;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.matrix.Matrix8x8Float;

/**
 * Single-precision matrix multiply C = A x B using Apple's {@code simdgroup_float8x8}
 * hardware matrix units, via {@link KernelContext#matrixMultiply8x8}.
 *
 * <p>Each work group is one 32-lane SIMD group and cooperatively computes one 8x8
 * output tile, accumulating over the full K dimension with the C fragment kept in
 * registers. A naive one-thread-per-output-element kernel is run for comparison.
 *
 * <p>All dimensions must be multiples of 8. Matrices are row-major.
 *
 * <p>How to run:
 * <pre>
 *   tornado -m tornado.examples/uk.ac.manchester.tornado.examples.compute.MatrixMultiplySimdgroup
 * </pre>
 */
public class MatrixMultiplySimdgroup {

    private static final int TILE = 8;
    private static final int SIMD_GROUP = 32; // Apple Silicon SIMD width
    private static final int BLOCK = 32;      // tiled GEMM: 32x32 output tile per threadgroup
    private static final int BLOCK_THREADS = 128; // four SIMD groups per threadgroup
    private static final int WARMUP = 50;
    private static final int ITERATIONS = 100;

    /** MMA kernel: one SIMD group per 8x8 output tile. */
    private static void gemmSimdgroup(KernelContext ctx, FloatArray a, FloatArray b, FloatArray c, int m, int n, int k) {
        int tilesPerRow = n / TILE;
        int tileIdx = ctx.groupIdx;
        int tileRow = tileIdx / tilesPerRow;
        int tileCol = tileIdx % tilesPerRow;

        int aBase = tileRow * TILE * k; // row-major A[M][K], tile origin (tileRow*8, 0)
        int bBase = tileCol * TILE;     // row-major B[K][N], tile origin (0, tileCol*8)
        int cBase = tileRow * TILE * n + tileCol * TILE;

        ctx.matrixMultiply8x8(a, aBase, k, b, bBase, n, c, cBase, n, k);
    }

    /** Tiled MMA kernel: one threadgroup (128 threads / 4 SIMD groups) per 32x32 tile,
     *  staging 32x8/8x32 blocks of A/B in threadgroup memory and reusing them across a
     *  4x4 grid of register fragments. Built from the simdgroupMatrix* primitives. */
    private static void gemmTiled(KernelContext ctx, FloatArray a, FloatArray b, FloatArray c, int m, int n, int k) {
        float[] as = ctx.allocateFloatLocalArray(256);
        float[] bs = ctx.allocateFloatLocalArray(256);
        int tilesPerRow = n / BLOCK;
        int rowBase = (ctx.groupIdx / tilesPerRow) * BLOCK;
        int colBase = (ctx.groupIdx % tilesPerRow) * BLOCK;
        int tid = ctx.localIdx;
        int sgRow = (tid / SIMD_GROUP) / 2;
        int sgCol = (tid / SIMD_GROUP) % 2;
        Matrix8x8Float acc00 = ctx.simdgroupMatrixZero();
        Matrix8x8Float acc01 = ctx.simdgroupMatrixZero();
        Matrix8x8Float acc10 = ctx.simdgroupMatrixZero();
        Matrix8x8Float acc11 = ctx.simdgroupMatrixZero();
        for (int kb = 0; kb < k; kb += TILE) {
            for (int e = tid; e < 256; e += BLOCK_THREADS) {
                as[e] = a.get((rowBase + e / TILE) * k + (kb + e % TILE));
            }
            for (int e = tid; e < 256; e += BLOCK_THREADS) {
                bs[e] = b.get((kb + e / BLOCK) * n + (colBase + e % BLOCK));
            }
            ctx.localBarrier();
            Matrix8x8Float a0 = ctx.simdgroupMatrixLoad(as, (sgRow * 2) * 64, TILE);
            Matrix8x8Float a1 = ctx.simdgroupMatrixLoad(as, (sgRow * 2 + 1) * 64, TILE);
            Matrix8x8Float b0 = ctx.simdgroupMatrixLoad(bs, (sgCol * 2) * TILE, BLOCK);
            Matrix8x8Float b1 = ctx.simdgroupMatrixLoad(bs, (sgCol * 2 + 1) * TILE, BLOCK);
            acc00 = ctx.simdgroupMatrixMultiplyAccumulate(a0, b0, acc00);
            acc01 = ctx.simdgroupMatrixMultiplyAccumulate(a0, b1, acc01);
            acc10 = ctx.simdgroupMatrixMultiplyAccumulate(a1, b0, acc10);
            acc11 = ctx.simdgroupMatrixMultiplyAccumulate(a1, b1, acc11);
            ctx.localBarrier();
        }
        int cr0 = rowBase + (sgRow * 2) * TILE;
        int cr1 = rowBase + (sgRow * 2 + 1) * TILE;
        int cc0 = colBase + (sgCol * 2) * TILE;
        int cc1 = colBase + (sgCol * 2 + 1) * TILE;
        ctx.simdgroupMatrixStore(acc00, c, cr0 * n + cc0, n);
        ctx.simdgroupMatrixStore(acc01, c, cr0 * n + cc1, n);
        ctx.simdgroupMatrixStore(acc10, c, cr1 * n + cc0, n);
        ctx.simdgroupMatrixStore(acc11, c, cr1 * n + cc1, n);
    }

    /** Naive kernel: one thread per output element. */
    private static void gemmNaive(KernelContext ctx, FloatArray a, FloatArray b, FloatArray c, int m, int n, int k) {
        int idx = ctx.globalIdx;
        int row = idx / n;
        int col = idx % n;
        if (row >= m) {
            return;
        }
        float acc = 0.0f;
        for (int p = 0; p < k; p++) {
            acc += a.get(row * k + p) * b.get(p * n + col);
        }
        c.set(row * n + col, acc);
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

    private static boolean validate(FloatArray actual, FloatArray expected, int k) {
        float tol = 1e-3f * k; // accumulation tolerance
        for (int i = 0; i < actual.getSize(); i++) {
            if (Math.abs(actual.get(i) - expected.get(i)) > tol) {
                System.out.printf("  mismatch at %d: %f vs %f%n", i, actual.get(i), expected.get(i));
                return false;
            }
        }
        return true;
    }

    private static long[] benchmark(TornadoExecutionPlan plan, GridScheduler grid) {
        for (int i = 0; i < WARMUP; i++) {
            plan.withGridScheduler(grid).execute();
        }
        long[] times = new long[ITERATIONS];
        for (int i = 0; i < ITERATIONS; i++) {
            long t0 = System.nanoTime();
            plan.withGridScheduler(grid).execute();
            times[i] = System.nanoTime() - t0;
        }
        return times;
    }

    private static void printStats(String label, long[] timesNs, double gflopBase) {
        double avgMs = Arrays.stream(timesNs).average().orElse(0) / 1e6;
        double gflops = gflopBase / (avgMs / 1e3) / 1e9;
        System.out.printf("  %-22s  avg=%7.3f ms   %8.1f GFLOP/s%n", label, avgMs, gflops);
    }

    public static void main(String[] args) throws Exception {
        final int m = 512;
        final int n = 512;
        final int k = 512;

        System.out.println("Matrix Multiply — simdgroup_float8x8 (MMA) vs naive");
        System.out.println("===================================================");
        System.out.printf("C[%d x %d] = A[%d x %d] x B[%d x %d]%n%n", m, n, m, k, k, n);

        FloatArray a = new FloatArray(m * k);
        FloatArray b = new FloatArray(k * n);
        FloatArray cMma = new FloatArray(m * n);
        FloatArray cTiled = new FloatArray(m * n);
        FloatArray cNaive = new FloatArray(m * n);
        FloatArray cRef = new FloatArray(m * n);

        Random rnd = new Random(42);
        for (int i = 0; i < a.getSize(); i++) {
            a.set(i, rnd.nextFloat() - 0.5f);
        }
        for (int i = 0; i < b.getSize(); i++) {
            b.set(i, rnd.nextFloat() - 0.5f);
        }

        KernelContext ctx = new KernelContext();

        // MMA: one SIMD group (32 lanes) per 8x8 output tile.
        int numTiles = (m / TILE) * (n / TILE);
        WorkerGrid1D gridMma = new WorkerGrid1D(numTiles * SIMD_GROUP);
        gridMma.setLocalWork(SIMD_GROUP, 1, 1);
        GridScheduler schedMma = new GridScheduler("mma.t0", gridMma);
        ImmutableTaskGraph mma = new TaskGraph("mma") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", MatrixMultiplySimdgroup::gemmSimdgroup, ctx, a, b, cMma, m, n, k) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cMma).snapshot();

        // Tiled MMA: one threadgroup (128 threads) per 32x32 output tile.
        int numBlocks = (m / BLOCK) * (n / BLOCK);
        WorkerGrid1D gridTiled = new WorkerGrid1D(numBlocks * BLOCK_THREADS);
        gridTiled.setLocalWork(BLOCK_THREADS, 1, 1);
        GridScheduler schedTiled = new GridScheduler("tiled.t0", gridTiled);
        ImmutableTaskGraph tiled = new TaskGraph("tiled") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", MatrixMultiplySimdgroup::gemmTiled, ctx, a, b, cTiled, m, n, k) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cTiled).snapshot();

        // Naive: one thread per output element.
        WorkerGrid1D gridNaive = new WorkerGrid1D(m * n);
        gridNaive.setLocalWork(64, 1, 1);
        GridScheduler schedNaive = new GridScheduler("naive.t0", gridNaive);
        ImmutableTaskGraph naive = new TaskGraph("naive") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", MatrixMultiplySimdgroup::gemmNaive, ctx, a, b, cNaive, m, n, k) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cNaive).snapshot();

        long[] tMma;
        long[] tTiled;
        long[] tNaive;
        try (TornadoExecutionPlan planMma = new TornadoExecutionPlan(mma); //
                TornadoExecutionPlan planTiled = new TornadoExecutionPlan(tiled); //
                TornadoExecutionPlan planNaive = new TornadoExecutionPlan(naive)) {
            System.out.println("Running benchmarks...");
            tMma = benchmark(planMma, schedMma);
            tTiled = benchmark(planTiled, schedTiled);
            tNaive = benchmark(planNaive, schedNaive);
        }

        cpuReference(a, b, cRef, m, n, k);

        System.out.println("\nCorrectness");
        System.out.println("-----------");
        System.out.println("  simdgroup       matches CPU reference: " + (validate(cMma, cRef, k) ? "✓" : "✗ MISMATCH"));
        System.out.println("  simdgroup tiled matches CPU reference: " + (validate(cTiled, cRef, k) ? "✓" : "✗ MISMATCH"));
        System.out.println("  naive           matches CPU reference: " + (validate(cNaive, cRef, k) ? "✓" : "✗ MISMATCH"));

        double flop = 2.0 * m * n * k; // multiply-add per output element
        System.out.println("\nPerformance");
        System.out.println("-----------");
        printStats("naive", tNaive, flop);
        printStats("simdgroup_float8x8", tMma, flop);
        printStats("simdgroup tiled", tTiled, flop);

        double avgNaive = Arrays.stream(tNaive).average().orElse(1);
        double avgMma = Arrays.stream(tMma).average().orElse(1);
        double avgTiled = Arrays.stream(tTiled).average().orElse(1);
        System.out.printf("%nSpeedup simdgroup       vs naive: %.2fx%n", avgNaive / avgMma);
        System.out.printf("Speedup simdgroup tiled vs naive: %.2fx%n", avgNaive / avgTiled);
        System.out.printf("Speedup simdgroup tiled vs simdgroup: %.2fx%n", avgMma / avgTiled);
    }
}
