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
import java.util.stream.IntStream;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * Matrix-vector multiply (one row per work group) comparing two intra-group
 * reduction strategies:
 *
 * <ol>
 *   <li><b>Threadgroup tree</b> — each lane accumulates a partial dot product,
 *       then a {@code threadgroup float[32]} + 5 barrier-synchronised steps reduce
 *       it (the pattern used by {@code MatrixVectorRowMajor.matrixVectorGeneric}).</li>
 *   <li><b>simd_sum</b> — the same partial accumulation, but the 32 partials are
 *       reduced with one {@code KernelContext.simdSum} call. No threadgroup memory,
 *       no barriers.</li>
 * </ol>
 *
 * <p>Local size is fixed to 32 = one Apple-Silicon SIMD group, so {@code simd_sum}
 * reduces the whole work group in a single instruction.
 *
 * <p>How to run:
 * <pre>
 *   tornado -m tornado.examples/uk.ac.manchester.tornado.examples.compute.MatrixVectorSimdReduction
 * </pre>
 */
public class MatrixVectorSimdReduction {

    private static final int LOCAL_SIZE = 32; // one SIMD group on Apple Silicon
    private static final int WARMUP = 100;
    private static final int ITERATIONS = 200;

    /** Threadgroup-memory tree reduction (baseline). */
    private static void matvecTree(KernelContext ctx, FloatArray x, FloatArray w, FloatArray out, int n) {
        int rowId = ctx.groupIdx;
        int localId = ctx.localIdx;
        int localSize = ctx.localGroupSizeX;

        float partial = 0.0f;
        int rowOffset = rowId * n;
        for (int j = localId; j < n; j += localSize) {
            partial += w.get(rowOffset + j) * x.get(j);
        }

        float[] local = ctx.allocateFloatLocalArray(LOCAL_SIZE);
        local[localId] = partial;
        for (int stride = localSize / 2; stride > 0; stride >>= 1) {
            ctx.localBarrier();
            if (localId < stride) {
                local[localId] += local[localId + stride];
            }
        }
        if (localId == 0) {
            out.set(rowId, local[0]);
        }
    }

    /** SIMD-group reduction via simd_sum (no threadgroup memory, no barriers). */
    private static void matvecSimd(KernelContext ctx, FloatArray x, FloatArray w, FloatArray out, int n) {
        int rowId = ctx.groupIdx;
        int localId = ctx.localIdx;
        int localSize = ctx.localGroupSizeX;

        float partial = 0.0f;
        int rowOffset = rowId * n;
        for (int j = localId; j < n; j += localSize) {
            partial += w.get(rowOffset + j) * x.get(j);
        }

        float rowSum = ctx.simdSum(partial);
        if (localId == 0) {
            out.set(rowId, rowSum);
        }
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

    private static void printStats(String label, long[] timesNs) {
        double[] ms = Arrays.stream(timesNs).mapToDouble(t -> t / 1e6).toArray();
        double avg = Arrays.stream(ms).average().orElse(0);
        double min = Arrays.stream(ms).min().orElse(0);
        double max = Arrays.stream(ms).max().orElse(0);
        System.out.printf("  %-22s  avg=%6.3f ms  min=%6.3f ms  max=%6.3f ms%n", label, avg, min, max);
    }

    private static boolean validate(FloatArray a, FloatArray b) {
        for (int i = 0; i < a.getSize(); i++) {
            float x = a.get(i);
            float y = b.get(i);
            if (Math.abs(x - y) > 1e-2f * Math.max(1.0f, Math.abs(x))) {
                System.out.printf("  mismatch at %d: %f vs %f%n", i, x, y);
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) throws Exception {
        final int rows = 2048;   // output dimension d
        final int n = 8192;      // input dimension

        System.out.println("Matrix-Vector reduction — simd_sum vs threadgroup tree");
        System.out.println("======================================================");
        System.out.printf("Matrix : %d x %d (%.1f MB)   Local size: %d%n%n", rows, n, rows * n * 4.0 / 1e6, LOCAL_SIZE);

        FloatArray x = new FloatArray(n);
        FloatArray w = new FloatArray(rows * n);
        IntStream.range(0, n).forEach(i -> x.set(i, (float) ((i % 13) - 6) * 0.1f));
        IntStream.range(0, rows * n).forEach(i -> w.set(i, (float) ((i % 7) - 3) * 0.01f));

        FloatArray outTree = new FloatArray(rows);
        FloatArray outSimd = new FloatArray(rows);
        KernelContext ctx = new KernelContext();

        WorkerGrid1D gTree = new WorkerGrid1D(rows * LOCAL_SIZE);
        gTree.setLocalWork(LOCAL_SIZE, 1, 1);
        GridScheduler sTree = new GridScheduler("tree.t0", gTree);
        ImmutableTaskGraph tree = new TaskGraph("tree") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, x, w) //
                .task("t0", MatrixVectorSimdReduction::matvecTree, ctx, x, w, outTree, n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outTree).snapshot();

        WorkerGrid1D gSimd = new WorkerGrid1D(rows * LOCAL_SIZE);
        gSimd.setLocalWork(LOCAL_SIZE, 1, 1);
        GridScheduler sSimd = new GridScheduler("simd.t0", gSimd);
        ImmutableTaskGraph simd = new TaskGraph("simd") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, x, w) //
                .task("t0", MatrixVectorSimdReduction::matvecSimd, ctx, x, w, outSimd, n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outSimd).snapshot();

        long[] tTree;
        long[] tSimd;
        try (TornadoExecutionPlan planTree = new TornadoExecutionPlan(tree); //
                TornadoExecutionPlan planSimd = new TornadoExecutionPlan(simd)) {
            System.out.println("Running benchmarks...");
            tTree = benchmark(planTree, sTree);
            tSimd = benchmark(planSimd, sSimd);
        }

        System.out.println("\nCorrectness");
        System.out.println("-----------");
        System.out.println("  simd_sum matches tree reduction: " + (validate(outTree, outSimd) ? "✓" : "✗ MISMATCH"));

        System.out.println("\nPerformance (end-to-end dispatch + kernel + readback)");
        System.out.println("------------------------------------------------------");
        printStats("threadgroup tree", tTree);
        printStats("simd_sum", tSimd);

        double avgTree = Arrays.stream(tTree).average().orElse(1);
        double avgSimd = Arrays.stream(tSimd).average().orElse(1);
        System.out.printf("%nSpeedup simd_sum vs threadgroup tree: %.2fx%n", avgTree / avgSimd);
    }
}
