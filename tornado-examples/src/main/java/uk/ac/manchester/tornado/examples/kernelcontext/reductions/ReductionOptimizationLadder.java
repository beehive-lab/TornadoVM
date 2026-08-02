/*
 * Copyright (c) 2026 APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.examples.kernelcontext.reductions;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * A three-stage sum-reduction optimization ladder over a large array, run back-to-back on the
 * same input so the wall-clock progression is directly comparable:
 * <ol>
 * <li><b>Interleaved addressing</b> -- {@code if (tid % (2*stride) == 0)}: warp-divergent, causes
 * shared-memory bank conflicts.</li>
 * <li><b>Sequential addressing</b> -- {@code if (tid < stride)}: same amount of work, but
 * divergence-free and conflict-free.</li>
 * <li><b>First-add-during-load</b> -- each thread sums two elements from global memory before
 * entering the tree reduction, halving the number of blocks needed for the same input size.</li>
 * </ol>
 * Correctness for all three stages is unit-tested in
 * {@code tornado-unittests/.../kernelcontext/reductions/TestReductionOptimizationLadder.java}
 * (small, fixed-sum input). This example exists to make the PERFORMANCE progression reproducible
 * outside the test harness, on data large enough for the differences to be visible.
 *
 * <p>
 * How to run (correctness + wall-clock timing, printed to stdout):
 * </p>
 *
 * <pre>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.kernelcontext.reductions.ReductionOptimizationLadder
 * </pre>
 *
 * <p>
 * How to double-check the kernel-level timing with Nsight Systems instead of trusting the
 * wall-clock prints alone:
 * </p>
 *
 * <pre>
 * source setvars.sh
 * nsys profile --trace=cuda,nvtx -o reduction-ladder \
 *   tornado -m tornado.examples/uk.ac.manchester.tornado.examples.kernelcontext.reductions.ReductionOptimizationLadder
 * nsys stats --report cuda_gpu_kern_sum reduction-ladder.nsys-rep
 * </pre>
 *
 * <p>
 * Or, for a quick per-kernel timing read without a full nsys capture:
 * </p>
 *
 * <pre>
 * tornado --enableProfiler console -m tornado.examples/uk.ac.manchester.tornado.examples.kernelcontext.reductions.ReductionOptimizationLadder
 * </pre>
 */
public class ReductionOptimizationLadder {

    private static final int BLOCK_SIZE = 256;
    private static final int NUM_BLOCKS = 16384; // SIZE = 4,194,304 elements (~16MB as float)
    private static final int SIZE = BLOCK_SIZE * NUM_BLOCKS;

    private static void reduceInterleaved(KernelContext ctx, FloatArray input, FloatArray partials) {
        float[] shared = ctx.allocateFloatLocalArray(BLOCK_SIZE);
        int tid = ctx.localIdx;
        shared[tid] = input.get(ctx.globalIdx);
        ctx.localBarrier();

        for (int stride = 1; stride < BLOCK_SIZE; stride *= 2) {
            if (tid % (2 * stride) == 0) {
                shared[tid] += shared[tid + stride];
            }
            ctx.localBarrier();
        }
        if (tid == 0) {
            partials.set(ctx.groupIdx, shared[0]);
        }
    }

    private static void reduceSequential(KernelContext ctx, FloatArray input, FloatArray partials) {
        float[] shared = ctx.allocateFloatLocalArray(BLOCK_SIZE);
        int tid = ctx.localIdx;
        shared[tid] = input.get(ctx.globalIdx);
        ctx.localBarrier();

        for (int stride = BLOCK_SIZE / 2; stride > 0; stride >>= 1) {
            if (tid < stride) {
                shared[tid] += shared[tid + stride];
            }
            ctx.localBarrier();
        }
        if (tid == 0) {
            partials.set(ctx.groupIdx, shared[0]);
        }
    }

    private static void reduceFirstAddDuringLoad(KernelContext ctx, FloatArray input, FloatArray partials) {
        float[] shared = ctx.allocateFloatLocalArray(BLOCK_SIZE);
        int tid = ctx.localIdx;
        int i = ctx.groupIdx * (BLOCK_SIZE * 2) + tid;
        shared[tid] = input.get(i) + input.get(i + BLOCK_SIZE);
        ctx.localBarrier();

        for (int stride = BLOCK_SIZE / 2; stride > 0; stride >>= 1) {
            if (tid < stride) {
                shared[tid] += shared[tid + stride];
            }
            ctx.localBarrier();
        }
        if (tid == 0) {
            partials.set(ctx.groupIdx, shared[0]);
        }
    }

    private static float hostSum(FloatArray partials) {
        float total = 0.0f;
        for (int i = 0; i < partials.getSize(); i++) {
            total += partials.get(i);
        }
        return total;
    }

    private static FloatArray buildInput() {
        FloatArray input = new FloatArray(SIZE);
        for (int i = 0; i < SIZE; i++) {
            input.set(i, 1.0f); // sum is exactly SIZE -- keeps correctness-checking trivial
        }
        return input;
    }

    private static void report(String name, FloatArray partials, long warmupNs, long steadyStateNs) {
        float sum = hostSum(partials);
        boolean correct = Math.abs(sum - SIZE) < 0.01f;
        System.out.printf("%-24s sum=%.1f (expected %d, %s)  first-run=%.3f ms  steady-state=%.3f ms%n", name, sum, SIZE, correct ? "correct" : "WRONG", warmupNs / 1_000_000.0, steadyStateNs / 1_000_000.0);
    }

    /**
     * Runs the plan twice and returns {first-run, second-run} elapsed nanoseconds. The first run
     * pays the JIT/NVRTC compile cost for this specific kernel on top of actual execution; the
     * second run is the steady-state number. This split exists because an EARLIER version of this
     * example only timed a single run per stage and reported misleading double-digit "speedups"
     * that were almost entirely JIT compilation overhead, not real kernel improvement -- caught by
     * cross-checking with {@code nsys stats --report cuda_gpu_kern_sum}, which showed the true
     * GPU-kernel-only time for "interleaved" vs "sequential" addressing differing by under 2%, not
     * the 6-8x the naive single-run wall-clock timing implied. Always cross-check a wall-clock
     * "speedup" claim against real kernel timing before trusting it.
     */
    private static long[] timeStageTwice(TornadoExecutionPlan plan, GridScheduler grid) throws TornadoExecutionPlanException {
        long start1 = System.nanoTime();
        plan.withGridScheduler(grid).execute();
        long firstRunNs = System.nanoTime() - start1;

        long start2 = System.nanoTime();
        plan.withGridScheduler(grid).execute();
        long steadyStateNs = System.nanoTime() - start2;

        return new long[] { firstRunNs, steadyStateNs };
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {
        System.out.println("Reduction optimization ladder -- SIZE=" + SIZE + " elements, BLOCK_SIZE=" + BLOCK_SIZE);
        System.out.println("Each stage runs twice: first-run includes JIT/NVRTC compilation, steady-state is the real number.");
        System.out.println();

        FloatArray input = buildInput();
        KernelContext context = new KernelContext();

        // Stage 1: interleaved addressing -- full-size grid (NUM_BLOCKS blocks).
        FloatArray interleavedPartials = new FloatArray(NUM_BLOCKS);
        WorkerGrid interleavedWorker = new WorkerGrid1D(SIZE);
        interleavedWorker.setLocalWork(BLOCK_SIZE, 1, 1);
        GridScheduler interleavedGrid = new GridScheduler();
        interleavedGrid.addWorkerGrid("s0.t0", interleavedWorker);

        TaskGraph interleavedGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .task("t0", ReductionOptimizationLadder::reduceInterleaved, context, input, interleavedPartials) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, interleavedPartials);
        long[] interleavedTimes;
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(interleavedGraph.snapshot())) {
            interleavedTimes = timeStageTwice(plan, interleavedGrid);
        }
        report("interleaved addressing", interleavedPartials, interleavedTimes[0], interleavedTimes[1]);

        // Stage 2: sequential addressing -- same full-size grid.
        FloatArray sequentialPartials = new FloatArray(NUM_BLOCKS);
        WorkerGrid sequentialWorker = new WorkerGrid1D(SIZE);
        sequentialWorker.setLocalWork(BLOCK_SIZE, 1, 1);
        GridScheduler sequentialGrid = new GridScheduler();
        sequentialGrid.addWorkerGrid("s1.t0", sequentialWorker);

        TaskGraph sequentialGraph = new TaskGraph("s1") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .task("t0", ReductionOptimizationLadder::reduceSequential, context, input, sequentialPartials) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, sequentialPartials);
        long[] sequentialTimes;
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(sequentialGraph.snapshot())) {
            sequentialTimes = timeStageTwice(plan, sequentialGrid);
        }
        report("sequential addressing", sequentialPartials, sequentialTimes[0], sequentialTimes[1]);

        // Stage 3: first-add-during-load -- half the blocks (each thread pre-sums 2 elements).
        FloatArray firstAddPartials = new FloatArray(NUM_BLOCKS / 2);
        WorkerGrid firstAddWorker = new WorkerGrid1D((NUM_BLOCKS / 2) * BLOCK_SIZE);
        firstAddWorker.setLocalWork(BLOCK_SIZE, 1, 1);
        GridScheduler firstAddGrid = new GridScheduler();
        firstAddGrid.addWorkerGrid("s2.t0", firstAddWorker);

        TaskGraph firstAddGraph = new TaskGraph("s2") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .task("t0", ReductionOptimizationLadder::reduceFirstAddDuringLoad, context, input, firstAddPartials) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, firstAddPartials);
        long[] firstAddTimes;
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(firstAddGraph.snapshot())) {
            firstAddTimes = timeStageTwice(plan, firstAddGrid);
        }
        report("first-add-during-load", firstAddPartials, firstAddTimes[0], firstAddTimes[1]);

        long interleavedNs = interleavedTimes[1];
        long sequentialNs = sequentialTimes[1];
        long firstAddNs = firstAddTimes[1];

        System.out.println();
        System.out.println("Steady-state speedups (compare these, not the first-run numbers):");
        System.out.printf("  sequential vs interleaved:   %.2fx%n", (double) interleavedNs / sequentialNs);
        System.out.printf("  first-add vs sequential:     %.2fx%n", (double) sequentialNs / firstAddNs);
        System.out.printf("  first-add vs interleaved:    %.2fx%n", (double) interleavedNs / firstAddNs);
    }

}
