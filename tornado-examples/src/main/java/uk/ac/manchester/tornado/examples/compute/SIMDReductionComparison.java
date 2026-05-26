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
 * Benchmarks three GPU reduction strategies for float sum:
 *
 * <ol>
 *   <li><b>Threadgroup memory</b> — classic tree reduction via threadgroup array + barriers
 *       (pattern from TestReductionsFloatsKernelContext)</li>
 *   <li><b>simd_sum</b> — single Metal built-in that reduces all lanes in one instruction</li>
 *   <li><b>simdShuffleDown butterfly</b> — explicit 5-step butterfly using simd_shuffle_down,
 *       demonstrating the shuffle primitive without relying on the compound simd_sum</li>
 * </ol>
 *
 * <p>All three strategies use localSize = 32 (one SIMD group per work group) and
 * produce {@code N/32} partial sums which are then summed on the CPU to get the
 * final result. Results are cross-checked against a sequential reference.
 *
 * <p>How to run:
 * <pre>
 *   tornado -m tornado.examples/uk.ac.manchester.tornado.examples.compute.SIMDReductionComparison
 * </pre>
 */

//  Correctness
//  -----------
//        Sequential sum (double) = 312,500,012,500,000  (≈ true N*(N+1)/2)
//        Threadgroup memory    result=312554131816448  relErr=1.73e-04  ✓
//        simd_sum              result=312554131816448  relErr=1.73e-04  ✓
//        simdShuffleDown       result=312554131816448  relErr=1.73e-04  ✓
//        (tiny relative error is expected FP32 rounding in 781,250 partial sums of 32)
//        Note: a naive float sequential sum gives ~2.88e14 (wrong by 8.6%) because float32
//        loses precision once the accumulator exceeds ~1.6e7; parallel reduction avoids this.
//
//        Performance (avg, end-to-end)
//  ------------------------------
//        Threadgroup memory  0.666 ms
//        simd_sum            0.427 ms   →  1.56x faster
//        simdShuffleDown     0.407 ms   →  1.64x faster
//
//        The speedup comes from two sources:
//        1. No threadgroup memory traffic — the threadgroup reduction must write all 32 lane
//        values to threadgroup float[32], then do 5 rounds of barrier-synchronized reads/writes.
//        simd_sum and simdShuffleDown stay entirely in registers.
//  2. Fewer barriers — the threadgroup path requires 5
//        threadgroup_barrier(mem_flags::mem_threadgroup) calls; the SIMD paths have none.
//
//        The simdShuffleDown butterfly is marginally faster than simd_sum because the compiler
//        can schedule the 5 adds more freely than the single opaque simd_sum intrinsic, though
//        both are well within measurement noise.
public class SIMDReductionComparison {

    // localSize == SIMD group size on Apple Silicon
    private static final int LOCAL_SIZE = 32;
    private static final int WARMUP = 50;
    private static final int ITERATIONS = 200;

    // -------------------------------------------------------------------------
    // Kernel 1: threadgroup memory tree reduction (existing pattern)
    // -------------------------------------------------------------------------
    private static void threadgroupReduction(KernelContext ctx, FloatArray input, FloatArray partials) {
        int globalIdx = ctx.globalIdx;
        int localIdx = ctx.localIdx;
        int localGroupSize = ctx.localGroupSizeX;
        int groupId = ctx.groupIdx;

        float[] local = ctx.allocateFloatLocalArray(LOCAL_SIZE);
        local[localIdx] = input.get(globalIdx);

        for (int stride = localGroupSize / 2; stride > 0; stride >>= 1) {
            ctx.localBarrier();
            if (localIdx < stride) {
                local[localIdx] += local[localIdx + stride];
            }
        }
        if (localIdx == 0) {
            partials.set(groupId, local[0]);
        }
    }

    // -------------------------------------------------------------------------
    // Kernel 2: simd_sum — single built-in
    // -------------------------------------------------------------------------
    private static void simdSumReduction(KernelContext ctx, FloatArray input, FloatArray partials) {
        int globalIdx = ctx.globalIdx;
        int localIdx = ctx.localIdx;
        int groupId = ctx.groupIdx;

        float groupSum = ctx.simdSum(input.get(globalIdx));

        if (localIdx == 0) {
            partials.set(groupId, groupSum);
        }
    }

    // -------------------------------------------------------------------------
    // Kernel 3: simd_shuffle_down butterfly
    // -------------------------------------------------------------------------
    private static void simdShuffleReduction(KernelContext ctx, FloatArray input, FloatArray partials) {
        int globalIdx = ctx.globalIdx;
        int localIdx = ctx.localIdx;
        int groupId = ctx.groupIdx;

        float val = input.get(globalIdx);
        val += ctx.simdShuffleDown(val, 16);
        val += ctx.simdShuffleDown(val, 8);
        val += ctx.simdShuffleDown(val, 4);
        val += ctx.simdShuffleDown(val, 2);
        val += ctx.simdShuffleDown(val, 1);

        if (localIdx == 0) {
            partials.set(groupId, val);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    /** Sum only the first {@code n} elements (the rest are zero padding).
     *  Uses double accumulation to avoid catastrophic float32 rounding on large inputs. */
    private static double sequentialSum(FloatArray input, int n) {
        double acc = 0;
        for (int i = 0; i < n; i++) {
            acc += input.get(i);
        }
        return acc;
    }

    private static double sequentialSum(FloatArray input) {
        return sequentialSum(input, input.getSize());
    }

    /** Round {@code n} up to the next multiple of {@code LOCAL_SIZE}. */
    private static int padded(int n) {
        return ((n + LOCAL_SIZE - 1) / LOCAL_SIZE) * LOCAL_SIZE;
    }

    /** Collect GPU partial sums in double to reduce CPU-side rounding. */
    private static double collectPartials(FloatArray partials) {
        double sum = 0;
        for (int i = 0; i < partials.getSize(); i++) {
            sum += partials.get(i);
        }
        return sum;
    }

    private static void printStats(String label, long[] timesNs) {
        double[] ms = Arrays.stream(timesNs).mapToDouble(t -> t / 1e6).toArray();
        double avg = Arrays.stream(ms).average().orElse(0);
        double min = Arrays.stream(ms).min().orElse(0);
        double max = Arrays.stream(ms).max().orElse(0);
        System.out.printf("  %-30s  avg=%6.3f ms  min=%6.3f ms  max=%6.3f ms%n", label, avg, min, max);
    }

    private static long[] benchmark(TornadoExecutionPlan plan, GridScheduler grid) {
        // warmup
        for (int i = 0; i < WARMUP; i++) {
            plan.withGridScheduler(grid).execute();
        }
        // timed runs
        long[] times = new long[ITERATIONS];
        for (int i = 0; i < ITERATIONS; i++) {
            long t0 = System.nanoTime();
            plan.withGridScheduler(grid).execute();
            times[i] = System.nanoTime() - t0;
        }
        return times;
    }

    public static void main(String[] args) throws Exception {
        final int size = 25_000_000; // ~100 MB of floats
        final int numGroups = size / LOCAL_SIZE;

        System.out.println("Float Sum Reduction — SIMD vs Threadgroup Memory");
        System.out.println("=================================================");
        System.out.printf("Input size : %,d floats (%.1f MB)%n", size, size * 4.0 );
        System.out.printf("Local size : %d (one SIMD group per work group)%n", LOCAL_SIZE);
        System.out.printf("Groups     : %,d%n", numGroups);
        System.out.printf("Warmup     : %d  Iterations: %d%n%n", WARMUP, ITERATIONS);

        // Input data: values 1..N
        FloatArray input = new FloatArray(size);
        IntStream.range(0, size).forEach(i -> input.set(i, (float) (i + 1)));
        double expected = sequentialSum(input);
        System.out.printf("Sequential sum = %.0f%n%n", expected);

        // Partial-sum output buffers (one per kernel, to avoid cross-contamination)
        FloatArray partialsThreadgroup = new FloatArray(numGroups);
        FloatArray partialsSIMDSum     = new FloatArray(numGroups);
        FloatArray partialsShuffle     = new FloatArray(numGroups);

        KernelContext ctx = new KernelContext();

        // ---- Threadgroup reduction ----
        WorkerGrid1D gridTG = new WorkerGrid1D(size);
        gridTG.setLocalWork(LOCAL_SIZE, 1, 1);
        GridScheduler schedulerTG = new GridScheduler("tg.t0", gridTG);

        TaskGraph tgGraph = new TaskGraph("tg")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input)
                .task("t0", SIMDReductionComparison::threadgroupReduction, ctx, input, partialsThreadgroup)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, partialsThreadgroup);
        ImmutableTaskGraph tgITG = tgGraph.snapshot();

        // ---- simd_sum reduction ----
        WorkerGrid1D gridSS = new WorkerGrid1D(size);
        gridSS.setLocalWork(LOCAL_SIZE, 1, 1);
        GridScheduler schedulerSS = new GridScheduler("ss.t0", gridSS);

        TaskGraph ssGraph = new TaskGraph("ss")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input)
                .task("t0", SIMDReductionComparison::simdSumReduction, ctx, input, partialsSIMDSum)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, partialsSIMDSum);
        ImmutableTaskGraph ssITG = ssGraph.snapshot();

        // ---- simdShuffleDown butterfly ----
        WorkerGrid1D gridSD = new WorkerGrid1D(size);
        gridSD.setLocalWork(LOCAL_SIZE, 1, 1);
        GridScheduler schedulerSD = new GridScheduler("sd.t0", gridSD);

        TaskGraph sdGraph = new TaskGraph("sd")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input)
                .task("t0", SIMDReductionComparison::simdShuffleReduction, ctx, input, partialsShuffle)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, partialsShuffle);
        ImmutableTaskGraph sdITG = sdGraph.snapshot();

        // ---- Run benchmarks ----
        long[] timesTG, timesSS, timesSD;
        try (TornadoExecutionPlan planTG = new TornadoExecutionPlan(tgITG);
             TornadoExecutionPlan planSS = new TornadoExecutionPlan(ssITG);
             TornadoExecutionPlan planSD = new TornadoExecutionPlan(sdITG)) {

            System.out.println("Running benchmarks...");
            timesTG = benchmark(planTG, schedulerTG);
            timesSS = benchmark(planSS, schedulerSS);
            timesSD = benchmark(planSD, schedulerSD);
        }

        // ---- Verify correctness ----
        double sumTG = collectPartials(partialsThreadgroup);
        double sumSS = collectPartials(partialsSIMDSum);
        double sumSD = collectPartials(partialsShuffle);

        System.out.println("\nCorrectness");
        System.out.println("-----------");
        checkResult("Threadgroup memory", expected, sumTG);
        checkResult("simd_sum           ", expected, sumSS);
        checkResult("simdShuffleDown    ", expected, sumSD);

        System.out.println("\nPerformance (end-to-end dispatch + kernel + readback)");
        System.out.println("------------------------------------------------------");
        printStats("Threadgroup memory", timesTG);
        printStats("simd_sum          ", timesSS);
        printStats("simdShuffleDown   ", timesSD);

        double avgTG = Arrays.stream(timesTG).average().orElse(1);
        double avgSS = Arrays.stream(timesSS).average().orElse(1);
        double avgSD = Arrays.stream(timesSD).average().orElse(1);
        System.out.printf("%nSpeedup simd_sum        vs threadgroup: %.2fx%n", avgTG / avgSS);
        System.out.printf("Speedup simdShuffleDown vs threadgroup: %.2fx%n", avgTG / avgSD);

        // ---- Irregular size correctness table ----
        System.out.println("\nIrregular-size correctness (zero-padded to next multiple of 32)");
        System.out.println("-----------------------------------------------------------------------");
        System.out.printf("  %-10s  %-12s  %-14s  %-14s  %-14s%n",
                "n", "paddedSize", "threadgroup", "simd_sum", "simdShuffle");

        int[] irregularSizes = {1, 31, 33, 63, 65, 100, 1000, 1023, 1025, 96, 160, 65537};
        KernelContext ctx2 = new KernelContext();
        for (int n : irregularSizes) {
            int ps = padded(n);
            int ng = ps / LOCAL_SIZE;

            FloatArray in2 = new FloatArray(ps); // extra slots are 0.0f
            IntStream.range(0, n).forEach(i -> in2.set(i, (float) (i + 1)));
            double exp2 = sequentialSum(in2, n);

            FloatArray pTG2 = new FloatArray(ng);
            FloatArray pSS2 = new FloatArray(ng);
            FloatArray pSD2 = new FloatArray(ng);

            WorkerGrid1D wTG2 = new WorkerGrid1D(ps); wTG2.setLocalWork(LOCAL_SIZE, 1, 1);
            WorkerGrid1D wSS2 = new WorkerGrid1D(ps); wSS2.setLocalWork(LOCAL_SIZE, 1, 1);
            WorkerGrid1D wSD2 = new WorkerGrid1D(ps); wSD2.setLocalWork(LOCAL_SIZE, 1, 1);

            String pfx = "irr" + n;
            GridScheduler sTG2 = new GridScheduler(pfx + "tg.t0", wTG2);
            GridScheduler sSS2 = new GridScheduler(pfx + "ss.t0", wSS2);
            GridScheduler sSD2 = new GridScheduler(pfx + "sd.t0", wSD2);

            try (TornadoExecutionPlan pTG = new TornadoExecutionPlan(new TaskGraph(pfx + "tg")
                         .transferToDevice(DataTransferMode.EVERY_EXECUTION, in2)
                         .task("t0", SIMDReductionComparison::threadgroupReduction, ctx2, in2, pTG2)
                         .transferToHost(DataTransferMode.EVERY_EXECUTION, pTG2).snapshot());
                 TornadoExecutionPlan pSS = new TornadoExecutionPlan(new TaskGraph(pfx + "ss")
                         .transferToDevice(DataTransferMode.EVERY_EXECUTION, in2)
                         .task("t0", SIMDReductionComparison::simdSumReduction, ctx2, in2, pSS2)
                         .transferToHost(DataTransferMode.EVERY_EXECUTION, pSS2).snapshot());
                 TornadoExecutionPlan pSD = new TornadoExecutionPlan(new TaskGraph(pfx + "sd")
                         .transferToDevice(DataTransferMode.EVERY_EXECUTION, in2)
                         .task("t0", SIMDReductionComparison::simdShuffleReduction, ctx2, in2, pSD2)
                         .transferToHost(DataTransferMode.EVERY_EXECUTION, pSD2).snapshot())) {
                pTG.withGridScheduler(sTG2).execute();
                pSS.withGridScheduler(sSS2).execute();
                pSD.withGridScheduler(sSD2).execute();
            }

            double sTG = collectPartials(pTG2);
            double sSS = collectPartials(pSS2);
            double sSD = collectPartials(pSD2);
            System.out.printf("  %-10d  %-12d  %-14s  %-14s  %-14s%n",
                    n, ps,
                    resultMark(exp2, sTG),
                    resultMark(exp2, sSS),
                    resultMark(exp2, sSD));
        }
    }

    private static String resultMark(double expected, double actual) {
        double relErr = (expected == 0) ? Math.abs(actual) : Math.abs(actual - expected) / Math.abs(expected);
        return relErr < 1e-3 ? String.format("%.0f ✓", actual) : String.format("%.0f ✗", actual);
    }

    private static void checkResult(String label, double expected, double actual) {
        double relErr = Math.abs(actual - expected) / Math.abs(expected);
        boolean ok = relErr < 1e-3;
        System.out.printf("  %-30s  result=%.0f  relErr=%.2e  %s%n",
                label, actual, relErr, ok ? "✓" : "✗ MISMATCH");
    }
}
