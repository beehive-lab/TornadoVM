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

        // Naive: one thread per output element.
        WorkerGrid1D gridNaive = new WorkerGrid1D(m * n);
        gridNaive.setLocalWork(64, 1, 1);
        GridScheduler schedNaive = new GridScheduler("naive.t0", gridNaive);
        ImmutableTaskGraph naive = new TaskGraph("naive") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", MatrixMultiplySimdgroup::gemmNaive, ctx, a, b, cNaive, m, n, k) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cNaive).snapshot();

        long[] tMma;
        long[] tNaive;
        try (TornadoExecutionPlan planMma = new TornadoExecutionPlan(mma); //
                TornadoExecutionPlan planNaive = new TornadoExecutionPlan(naive)) {
            System.out.println("Running benchmarks...");
            tMma = benchmark(planMma, schedMma);
            tNaive = benchmark(planNaive, schedNaive);
        }

        cpuReference(a, b, cRef, m, n, k);

        System.out.println("\nCorrectness");
        System.out.println("-----------");
        System.out.println("  simdgroup matches CPU reference: " + (validate(cMma, cRef, k) ? "✓" : "✗ MISMATCH"));
        System.out.println("  naive     matches CPU reference: " + (validate(cNaive, cRef, k) ? "✓" : "✗ MISMATCH"));

        double flop = 2.0 * m * n * k; // multiply-add per output element
        System.out.println("\nPerformance");
        System.out.println("-----------");
        printStats("naive", tNaive, flop);
        printStats("simdgroup_float8x8", tMma, flop);

        double avgNaive = Arrays.stream(tNaive).average().orElse(1);
        double avgMma = Arrays.stream(tMma).average().orElse(1);
        System.out.printf("%nSpeedup simdgroup vs naive: %.2fx%n", avgNaive / avgMma);
    }
}
