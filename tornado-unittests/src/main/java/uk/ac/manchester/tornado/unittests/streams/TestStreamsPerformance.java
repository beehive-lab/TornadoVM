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
package uk.ac.manchester.tornado.unittests.streams;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Demonstrates <b>how to enable</b> PTX intra-plan concurrency (CUDA streams) and <b>measures</b> its
 * wall-clock effect on three deliberately different workloads.
 *
 * <h3>Usage (the one line that matters)</h3>
 * <pre>
 * try (TornadoExecutionPlan plan = new TornadoExecutionPlan(immutableTaskGraph)) {
 *     plan.withIntraPlanConcurrency();   // route DAG-independent ops to H2D / COMPUTE pool / D2H streams
 *     plan.execute();
 * }
 * </pre>
 *
 * <h3>The three tests (and what each is meant to show)</h3>
 * <ul>
 *   <li>{@link #testOverlapSpeedup()} - TaskGraph <b>{@value #TG_OVERLAP}</b>: independent
 *       {@code H2D -> compute -> D2H} pipelines. Tests transfer/compute overlap across units.</li>
 *   <li>{@link #testComputeConcurrency()} - TaskGraph <b>{@value #TG_LARGE}</b>: two independent
 *       <em>large</em> (SM-saturating) kernels per unit. A single large kernel fills the GPU, so the
 *       two cannot co-reside even on different COMPUTE streams - this is the honest <em>negative</em>
 *       case (concurrency does not help when one kernel already saturates the device).</li>
 *   <li>{@link #testSmallKernelConcurrency()} - TaskGraph <b>{@value #TG_SMALL}</b>: two independent
 *       <em>small</em> (non-saturating) kernels per unit. These co-reside across the COMPUTE stream
 *       pool and overlap - the case where intra-plan concurrency yields a real speedup.</li>
 * </ul>
 *
 * <h3>Naming convention (so the Nsight Systems timeline is unambiguous)</h3>
 * Every GPU row is named {@code <taskgraph>_<task>_<method>}. Each test uses a distinct TaskGraph
 * name and distinct kernel method names, so a kernel seen in the GUI immediately identifies its test:
 * e.g. {@code concurrent_small_a3_smallaxpya...} is task {@code a3} of {@link #testSmallKernelConcurrency()}.
 *
 * <h3>How to read the numbers honestly</h3>
 * <ul>
 *   <li><b>Asserted:</b> numerical correctness in both single-stream and concurrent modes.</li>
 *   <li><b>Reported (printed, NOT asserted):</b> median execution time per mode and the speedup.
 *       Wall-clock speedup is workload-, GPU-, and driver-dependent, so it is <em>not</em> a pass/fail
 *       gate (that would be flaky). The numbers are printed for a human to inspect.</li>
 * </ul>
 *
 * How to run (and profile):
 * <pre>
 *   tornado-test -V uk.ac.manchester.tornado.unittests.streams.TestStreamsPerformance
 *
 *   nsys profile --trace=cuda,nvtx -o streams_perf \
 *     tornado -ea -m tornado.unittests/uk.ac.manchester.tornado.unittests.tools.TornadoTestRunner \
 *     --params "uk.ac.manchester.tornado.unittests.streams.TestStreamsPerformance#testSmallKernelConcurrency"
 * </pre>
 */
public class TestStreamsPerformance extends TornadoTestBase {

    /** Independent units per workload (more units => more pipelining / concurrency opportunity). */
    private static final int UNITS = 8;

    /** Large-workload array size: 6M floats = 24 MB, so transfers are substantial. */
    private static final int N_LARGE = 6 * 1024 * 1024;
    /** Inner-loop work per element for the large kernels (keeps compute ~comparable to copy). */
    private static final int LARGE_ITERATIONS = 256;

    /** Small-workload array size: 32K floats = 128 KB => only a handful of thread blocks (does NOT saturate the SMs). */
    private static final int N_SMALL = 32 * 1024;
    /** Heavy per-thread inner loop so each small kernel still takes a meaningful amount of time. */
    private static final int SMALL_ITERATIONS = 1 << 16;

    /** TaskGraph names - also the Nsight timeline row prefixes, one per test for unambiguous attribution. */
    private static final String TG_OVERLAP = "overlap_pipeline";
    private static final String TG_LARGE = "concurrent_large";
    private static final String TG_SMALL = "concurrent_small";

    private static final int WARMUP = 3;   // excluded: kernel compile + first-exec effects + JIT
    private static final int ITERATIONS = 10;
    private static final float DELTA = 1e-2f;
    private static final float ALPHA = 0.5f;

    // ---------------------------------------------------------------------------------------------
    // Kernels. Two recurrences are used so the "A" and "B" kernels of a unit are genuinely distinct:
    //   A-recurrence: v0 = alpha*x + y ; v = v*alpha + y
    //   B-recurrence: v0 = alpha*y + x ; v = v*alpha + x
    // Each test has its own named kernel methods so the GPU rows in Nsight identify the owning test.
    // ---------------------------------------------------------------------------------------------

    /** {@link #testOverlapSpeedup()} kernel (A-recurrence, large grid). */
    public static void overlapAxpy(FloatArray x, FloatArray y, FloatArray result, float alpha) {
        for (@Parallel int i = 0; i < result.getSize(); i++) {
            float val = alpha * x.get(i) + y.get(i);
            for (int j = 0; j < LARGE_ITERATIONS; j++) {
                val = val * alpha + y.get(i);
            }
            result.set(i, val);
        }
    }

    /** {@link #testComputeConcurrency()} kernel A (A-recurrence, large/SM-saturating grid). */
    public static void largeAxpyA(FloatArray x, FloatArray y, FloatArray result, float alpha) {
        for (@Parallel int i = 0; i < result.getSize(); i++) {
            float val = alpha * x.get(i) + y.get(i);
            for (int j = 0; j < LARGE_ITERATIONS; j++) {
                val = val * alpha + y.get(i);
            }
            result.set(i, val);
        }
    }

    /** {@link #testComputeConcurrency()} kernel B (B-recurrence, large/SM-saturating grid). */
    public static void largeAxpyB(FloatArray x, FloatArray y, FloatArray result, float alpha) {
        for (@Parallel int i = 0; i < result.getSize(); i++) {
            float val = alpha * y.get(i) + x.get(i);
            for (int j = 0; j < LARGE_ITERATIONS; j++) {
                val = val * alpha + x.get(i);
            }
            result.set(i, val);
        }
    }

    /** {@link #testSmallKernelConcurrency()} kernel A (A-recurrence, small/non-saturating grid, heavy loop). */
    public static void smallAxpyA(FloatArray x, FloatArray y, FloatArray result, float alpha) {
        for (@Parallel int i = 0; i < result.getSize(); i++) {
            float val = alpha * x.get(i) + y.get(i);
            for (int j = 0; j < SMALL_ITERATIONS; j++) {
                val = val * alpha + y.get(i);
            }
            result.set(i, val);
        }
    }

    /** {@link #testSmallKernelConcurrency()} kernel B (B-recurrence, small/non-saturating grid, heavy loop). */
    public static void smallAxpyB(FloatArray x, FloatArray y, FloatArray result, float alpha) {
        for (@Parallel int i = 0; i < result.getSize(); i++) {
            float val = alpha * y.get(i) + x.get(i);
            for (int j = 0; j < SMALL_ITERATIONS; j++) {
                val = val * alpha + x.get(i);
            }
            result.set(i, val);
        }
    }

    /** Reference value for the A-recurrence after {@code iters} iterations. */
    private static float expectedA(float x, float y, float alpha, int iters) {
        float val = alpha * x + y;
        for (int j = 0; j < iters; j++) {
            val = val * alpha + y;
        }
        return val;
    }

    /** Reference value for the B-recurrence after {@code iters} iterations. */
    private static float expectedB(float x, float y, float alpha, int iters) {
        float val = alpha * y + x;
        for (int j = 0; j < iters; j++) {
            val = val * alpha + x;
        }
        return val;
    }

    /** Median of an array of timings (ns). */
    private static long medianNanos(long[] samples) {
        long[] copy = samples.clone();
        java.util.Arrays.sort(copy);
        return copy[copy.length / 2];
    }

    /**
     * Times {@code ITERATIONS} steady-state executions (after {@code WARMUP}) of the given plan,
     * optionally enabling intra-plan concurrency, and returns the median execution time in nanoseconds.
     */
    private static long timeExecutions(ImmutableTaskGraph itg, boolean concurrent) throws TornadoExecutionPlanException {
        long[] samples = new long[ITERATIONS];
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
            if (concurrent) {
                plan.withIntraPlanConcurrency();
            }
            for (int w = 0; w < WARMUP; w++) {
                plan.execute();
            }
            for (int i = 0; i < ITERATIONS; i++) {
                long t0 = System.nanoTime();
                plan.execute();
                samples[i] = System.nanoTime() - t0;
            }
        }
        return medianNanos(samples);
    }

    /** Prints the single-vs-concurrent comparison block (timing is informational, never asserted). */
    private static void report(String title, String workload, long singleNs, long concurrentNs) {
        double singleMs = singleNs / 1.0e6;
        double concurrentMs = concurrentNs / 1.0e6;
        System.out.println("==== TestStreamsPerformance (" + title + ") ====");
        System.out.printf("  workload          : %s%n", workload);
        System.out.printf("  single-stream     : %.2f ms (median of %d)%n", singleMs, ITERATIONS);
        System.out.printf("  intra-plan concur : %.2f ms (median of %d)%n", concurrentMs, ITERATIONS);
        System.out.printf("  speedup           : %.2fx  (>1 means concurrency helped)%n", singleMs / concurrentMs);
        System.out.println("  note: wall-clock speedup is workload/GPU/driver dependent; not a pass/fail gate.");
    }

    /**
     * Transfer/compute overlap: {@value #UNITS} <em>independent</em> {@code H2D -> compute -> D2H}
     * pipelines (TaskGraph {@value #TG_OVERLAP}). While unit <i>i</i>'s kernel runs on a COMPUTE
     * stream, unit <i>i+1</i>'s input copy can run on the H2D stream, etc. The ceiling is roughly
     * {@code max(sum H2D, sum compute, sum D2H)} rather than their sum.
     */
    @Test
    public void testOverlapSpeedup() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        FloatArray[] x = new FloatArray[UNITS];
        FloatArray[] y = new FloatArray[UNITS];
        FloatArray[] r = new FloatArray[UNITS];

        TaskGraph tg = new TaskGraph(TG_OVERLAP);
        for (int u = 0; u < UNITS; u++) {
            x[u] = new FloatArray(N_LARGE);
            y[u] = new FloatArray(N_LARGE);
            r[u] = new FloatArray(N_LARGE);
            x[u].init(u + 1.0f);
            y[u].init(u + 2.0f);

            tg.transferToDevice(DataTransferMode.EVERY_EXECUTION, x[u], y[u])
                    .task("u" + u, TestStreamsPerformance::overlapAxpy, x[u], y[u], r[u], ALPHA)
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, r[u]);
        }
        ImmutableTaskGraph itg = tg.snapshot();

        long singleNs = timeExecutions(itg, false);
        long concurrentNs = timeExecutions(itg, true);

        for (int u = 0; u < UNITS; u++) {
            float expected = expectedA(u + 1.0f, u + 2.0f, ALPHA, LARGE_ITERATIONS);
            for (int i = 0; i < N_LARGE; i++) {
                assertEquals(expected, r[u].get(i), DELTA);
            }
        }
        report("transfer/compute overlap", String.format("%d independent units, %d MB/array, %d iters",
                UNITS, (N_LARGE * 4L) / (1024 * 1024), LARGE_ITERATIONS), singleNs, concurrentNs);
    }

    /**
     * Compute concurrency on <em>large</em> kernels (TaskGraph {@value #TG_LARGE}). Each unit issues two
     * independent kernels over the same inputs ({@code x,y -> r} and {@code x,y -> r2}). Each kernel's
     * grid is large enough to saturate the SMs, so even when issued to different COMPUTE streams the GPU
     * runs them serially - the honest negative case. Inputs are shared so the H2D cost is shared and the
     * test isolates the compute phase.
     */
    @Test
    public void testComputeConcurrency() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        FloatArray[] x = new FloatArray[UNITS];
        FloatArray[] y = new FloatArray[UNITS];
        FloatArray[] r = new FloatArray[UNITS];
        FloatArray[] r2 = new FloatArray[UNITS];

        TaskGraph tg = new TaskGraph(TG_LARGE);
        for (int u = 0; u < UNITS; u++) {
            x[u] = new FloatArray(N_LARGE);
            y[u] = new FloatArray(N_LARGE);
            r[u] = new FloatArray(N_LARGE);
            r2[u] = new FloatArray(N_LARGE);
            x[u].init(u + 1.0f);
            y[u].init(u + 2.0f);

            tg.transferToDevice(DataTransferMode.EVERY_EXECUTION, x[u], y[u])
                    .task("a" + u, TestStreamsPerformance::largeAxpyA, x[u], y[u], r[u], ALPHA)
                    .task("b" + u, TestStreamsPerformance::largeAxpyB, x[u], y[u], r2[u], ALPHA)
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, r[u], r2[u]);
        }
        ImmutableTaskGraph itg = tg.snapshot();

        long singleNs = timeExecutions(itg, false);
        long concurrentNs = timeExecutions(itg, true);

        for (int u = 0; u < UNITS; u++) {
            float ea = expectedA(u + 1.0f, u + 2.0f, ALPHA, LARGE_ITERATIONS);
            float eb = expectedB(u + 1.0f, u + 2.0f, ALPHA, LARGE_ITERATIONS);
            for (int i = 0; i < N_LARGE; i++) {
                assertEquals(ea, r[u].get(i), DELTA);
                assertEquals(eb, r2[u].get(i), DELTA);
            }
        }
        report("compute concurrency, LARGE kernels", String.format("%d units x 2 independent large kernels, %d MB/array, %d iters",
                UNITS, (N_LARGE * 4L) / (1024 * 1024), LARGE_ITERATIONS), singleNs, concurrentNs);
    }

    /**
     * Compute concurrency on <em>small</em> (non-saturating) kernels (TaskGraph {@value #TG_SMALL}).
     * Identical structure to {@link #testComputeConcurrency()} but each kernel uses a small grid
     * ({@value #N_SMALL} elements => a handful of thread blocks) with a heavy per-thread loop. Because a
     * single small kernel does not fill the SMs, kernels from different COMPUTE streams co-reside and
     * overlap - the case where the COMPUTE stream pool yields a real wall-clock win.
     */
    @Test
    public void testSmallKernelConcurrency() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        FloatArray[] x = new FloatArray[UNITS];
        FloatArray[] y = new FloatArray[UNITS];
        FloatArray[] r = new FloatArray[UNITS];
        FloatArray[] r2 = new FloatArray[UNITS];

        TaskGraph tg = new TaskGraph(TG_SMALL);
        for (int u = 0; u < UNITS; u++) {
            x[u] = new FloatArray(N_SMALL);
            y[u] = new FloatArray(N_SMALL);
            r[u] = new FloatArray(N_SMALL);
            r2[u] = new FloatArray(N_SMALL);
            x[u].init(u + 1.0f);
            y[u].init(u + 2.0f);

            tg.transferToDevice(DataTransferMode.EVERY_EXECUTION, x[u], y[u])
                    .task("a" + u, TestStreamsPerformance::smallAxpyA, x[u], y[u], r[u], ALPHA)
                    .task("b" + u, TestStreamsPerformance::smallAxpyB, x[u], y[u], r2[u], ALPHA)
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, r[u], r2[u]);
        }
        ImmutableTaskGraph itg = tg.snapshot();

        long singleNs = timeExecutions(itg, false);
        long concurrentNs = timeExecutions(itg, true);

        for (int u = 0; u < UNITS; u++) {
            float ea = expectedA(u + 1.0f, u + 2.0f, ALPHA, SMALL_ITERATIONS);
            float eb = expectedB(u + 1.0f, u + 2.0f, ALPHA, SMALL_ITERATIONS);
            for (int i = 0; i < N_SMALL; i++) {
                assertEquals(ea, r[u].get(i), DELTA);
                assertEquals(eb, r2[u].get(i), DELTA);
            }
        }
        report("compute concurrency, SMALL kernels", String.format("%d units x 2 independent small kernels, %d KB/array, %d iters",
                UNITS, (N_SMALL * 4L) / 1024, SMALL_ITERATIONS), singleNs, concurrentNs);
    }

}
