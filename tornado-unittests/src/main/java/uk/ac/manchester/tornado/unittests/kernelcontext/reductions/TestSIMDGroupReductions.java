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
package uk.ac.manchester.tornado.unittests.kernelcontext.reductions;

import static org.junit.Assert.assertEquals;

import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Unit tests for SIMD-group intrinsics exposed via {@link KernelContext}:
 * {@code simdSum}, {@code simdShuffleDown}, and {@code simdBroadcastFirst}.
 *
 * <p>These tests run on Metal (MSL SIMD-group functions) and PTX (CUDA
 * {@code shfl.sync} warp-shuffle instructions). OpenCL support is
 * implemented using {@code cl_khr_subgroups} sub-group built-ins but is
 * currently skipped because the extension is not available on all devices
 * (e.g. NVIDIA). Tests are skipped on SPIR-V which does not yet support
 * these intrinsics.
 *
 * <p>How to run:
 * <code>
 * tornado-test --threadInfo --printKernel -V uk.ac.manchester.tornado.unittests.kernelcontext.reductions.TestSIMDGroupReductions
 * </code>
 */
public class TestSIMDGroupReductions extends TornadoTestBase {

    /**
     * SIMD group size for Apple Silicon (M- and A-series) GPUs.
     * All three tests use localSize == SIMD_GROUP_SIZE so that each work group
     * is exactly one SIMD group, simplifying expected-value computation.
     */
    private static final int SIMD_GROUP_SIZE = 32;

    // -------------------------------------------------------------------------
    // Kernel methods
    // -------------------------------------------------------------------------

    /**
     * Classic threadgroup-memory tree reduction (pattern from TestReductionsFloatsKernelContext).
     * Included here so that irregular-size tests can cross-check all three strategies.
     */
    private static void threadgroupKernel(KernelContext ctx, FloatArray input, FloatArray partials) {
        int globalIdx = ctx.globalIdx;
        int localIdx = ctx.localIdx;
        int localGroupSize = ctx.localGroupSizeX;
        int groupId = ctx.groupIdx;

        float[] local = ctx.allocateFloatLocalArray(SIMD_GROUP_SIZE);
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

    /**
     * Uses {@code simd_sum(val)} to reduce each SIMD group to a single partial sum.
     * Lane 0 of each group writes the result to {@code partials[groupId]}.
     */
    private static void simdSumKernel(KernelContext ctx, FloatArray input, FloatArray partials) {
        int globalIdx = ctx.globalIdx;
        int localIdx = ctx.localIdx;
        int groupId = ctx.groupIdx;

        float groupSum = ctx.simdSum(input.get(globalIdx));

        if (localIdx == 0) {
            partials.set(groupId, groupSum);
        }
    }

    /**
     * Implements a SIMD butterfly reduction using {@code simd_shuffle_down(val, delta)}.
     * This is equivalent to {@code simd_sum} but uses the shuffle primitive directly,
     * testing that the shuffle intrinsic is correctly lowered to MSL.
     *
     * <p>With localSize == SIMD_GROUP_SIZE (32), the butterfly visits deltas
     * 16, 8, 4, 2, 1 and produces the same group sum as {@code simd_sum}.
     */
    private static void simdShuffleDownReductionKernel(KernelContext ctx, FloatArray input, FloatArray partials) {
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

    /**
     * Uses {@code simd_broadcast_first(val)} to broadcast lane 0's value to all
     * lanes in the SIMD group. Each thread writes the broadcast result to {@code output}.
     */
    private static void simdBroadcastFirstKernel(KernelContext ctx, FloatArray input, FloatArray output) {
        int globalIdx = ctx.globalIdx;
        output.set(globalIdx, ctx.simdBroadcastFirst(input.get(globalIdx)));
    }

    // -------------------------------------------------------------------------
    // Sequential reference
    // -------------------------------------------------------------------------

    private static float[] computeGroupSumsSequential(FloatArray input, int localSize) {
        int numGroups = input.getSize() / localSize;
        float[] sums = new float[numGroups];
        for (int g = 0; g < numGroups; g++) {
            float sum = 0;
            for (int j = 0; j < localSize; j++) {
                sum += input.get(g * localSize + j);
            }
            sums[g] = sum;
        }
        return sums;
    }

    /**
     * Pads {@code n} up to the next multiple of {@link #SIMD_GROUP_SIZE}, fills
     * {@code input[0..n-1] = i+1} and leaves the padding slots at 0.0f.
     * Returns the padded size.
     */
    private static int buildPaddedInput(FloatArray input, int n) {
        int paddedSize = ((n + SIMD_GROUP_SIZE - 1) / SIMD_GROUP_SIZE) * SIMD_GROUP_SIZE;
        for (int i = 0; i < n; i++) {
            input.set(i, (float) (i + 1));
        }
        // slots [n, paddedSize) default to 0.0f — already zero from FloatArray allocation
        return paddedSize;
    }

    private static float sequentialSum(FloatArray input, int n) {
        float acc = 0;
        for (int i = 0; i < n; i++) {
            acc += input.get(i);
        }
        return acc;
    }

    private static float collectPartials(FloatArray partials) {
        float acc = 0;
        for (int i = 0; i < partials.getSize(); i++) {
            acc += partials.get(i);
        }
        return acc;
    }

    /**
     * Runs all three reduction strategies on {@code n} data elements (which may be
     * any positive integer), verifies that each strategy produces a final sum matching
     * the sequential reference, and checks that all three strategies agree with each other.
     *
     * <p>The input is zero-padded to the next multiple of 32 so that:
     * <ul>
     *   <li>the GPU dispatch size is always a valid multiple of the local size, and</li>
     *   <li>padding threads read 0.0f and contribute nothing to the sum without any
     *       explicit bounds-check inside the kernels.</li>
     * </ul>
     *
     * @param n          number of real data elements (may not be a multiple of 32)
     * @param namePrefix unique string used to avoid task-graph name collisions between calls
     */
    private void runIrregularComparison(int n, String namePrefix) throws TornadoExecutionPlanException {
        final int localSize = SIMD_GROUP_SIZE;

        // Allocate at paddedSize so kernel reads are always in-bounds
        FloatArray input = new FloatArray(((n + localSize - 1) / localSize) * localSize);
        int paddedSize = buildPaddedInput(input, n);
        int numGroups = paddedSize / localSize;

        float expected = sequentialSum(input, n);

        FloatArray partialsTG = new FloatArray(numGroups);
        FloatArray partialsSS = new FloatArray(numGroups);
        FloatArray partialsSD = new FloatArray(numGroups);

        KernelContext ctx = new KernelContext();

        // -- Threadgroup --
        WorkerGrid1D workerTG = new WorkerGrid1D(paddedSize);
        workerTG.setLocalWork(localSize, 1, 1);
        GridScheduler gridTG = new GridScheduler(namePrefix + "tg.t0", workerTG);
        TaskGraph tgGraph = new TaskGraph(namePrefix + "tg")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input)
                .task("t0", TestSIMDGroupReductions::threadgroupKernel, ctx, input, partialsTG)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, partialsTG);

        // -- simd_sum --
        WorkerGrid1D workerSS = new WorkerGrid1D(paddedSize);
        workerSS.setLocalWork(localSize, 1, 1);
        GridScheduler gridSS = new GridScheduler(namePrefix + "ss.t0", workerSS);
        TaskGraph ssGraph = new TaskGraph(namePrefix + "ss")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input)
                .task("t0", TestSIMDGroupReductions::simdSumKernel, ctx, input, partialsSS)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, partialsSS);

        // -- simdShuffleDown --
        WorkerGrid1D workerSD = new WorkerGrid1D(paddedSize);
        workerSD.setLocalWork(localSize, 1, 1);
        GridScheduler gridSD = new GridScheduler(namePrefix + "sd.t0", workerSD);
        TaskGraph sdGraph = new TaskGraph(namePrefix + "sd")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input)
                .task("t0", TestSIMDGroupReductions::simdShuffleDownReductionKernel, ctx, input, partialsSD)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, partialsSD);

        try (TornadoExecutionPlan planTG = new TornadoExecutionPlan(tgGraph.snapshot());
             TornadoExecutionPlan planSS = new TornadoExecutionPlan(ssGraph.snapshot());
             TornadoExecutionPlan planSD = new TornadoExecutionPlan(sdGraph.snapshot())) {
            planTG.withGridScheduler(gridTG).execute();
            planSS.withGridScheduler(gridSS).execute();
            planSD.withGridScheduler(gridSD).execute();
        }

        float sumTG = collectPartials(partialsTG);
        float sumSS = collectPartials(partialsSS);
        float sumSD = collectPartials(partialsSD);

        // Use a relative tolerance scaled by n to allow for accumulated FP32 rounding
        float tol = Math.max(DELTA, Math.abs(expected) * 1e-4f);
        assertEquals("n=" + n + " threadgroup vs sequential", expected, sumTG, tol);
        assertEquals("n=" + n + " simd_sum vs sequential",    expected, sumSS, tol);
        assertEquals("n=" + n + " simdShuffle vs sequential", expected, sumSD, tol);
        // Cross-check: all three must agree with each other
        assertEquals("n=" + n + " simd_sum vs threadgroup",   sumTG, sumSS, tol);
        assertEquals("n=" + n + " simdShuffle vs threadgroup",sumTG, sumSD, tol);
    }

    /**
     * Sizes that are exact multiples of 32 but not powers of two.
     * These work with existing kernels at face value; the test checks that
     * an odd number of groups produces correct results.
     */
    @Test
    public void testIrregularSizes_MultiplesOf32() throws TornadoExecutionPlanException {
        // OpenCL support requires cl_khr_subgroups (available on Intel/AMD/ARM, not NVIDIA)
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        // 3, 5, 31, 33, 99 groups — not powers of two
        for (int n : new int[]{96, 160, 992, 1056, 3168}) {
            runIrregularComparison(n, "m32_" + n + "_");
        }
    }

    /**
     * Sizes that are NOT multiples of 32 — the standard irregular case.
     * The last group is partially filled; padding threads contribute 0.
     * Tests single-element, one-less-than-full, one-more-than-full, and large primes.
     */
    @Test
    public void testIrregularSizes_NotMultipleOf32() throws TornadoExecutionPlanException {
        // OpenCL support requires cl_khr_subgroups (available on Intel/AMD/ARM, not NVIDIA)
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        for (int n : new int[]{1, 31, 33, 63, 65, 100, 1000, 1023, 1025, 65537}) {
            runIrregularComparison(n, "irr_" + n + "_");
        }
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    /**
     * Tests that {@code simdSum(val)} correctly sums values across the SIMD group.
     *
     * <p>Setup: 256 threads organised in 8 groups of 32. Input[i] = i + 1.
     * Each group's expected sum is the sum of 32 consecutive integers starting
     * at {@code groupId * 32 + 1}.
     */
    @Test
    public void testSIMDSum() throws TornadoExecutionPlanException {
        // OpenCL support requires cl_khr_subgroups (available on Intel/AMD/ARM, not NVIDIA)
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 256;
        final int localSize = SIMD_GROUP_SIZE;
        final int numGroups = size / localSize;

        FloatArray input = new FloatArray(size);
        FloatArray partials = new FloatArray(numGroups);
        IntStream.range(0, size).forEach(i -> input.set(i, (float) (i + 1)));

        float[] expected = computeGroupSumsSequential(input, localSize);

        WorkerGrid worker = new WorkerGrid1D(size);
        worker.setLocalWork(localSize, 1, 1);
        GridScheduler grid = new GridScheduler("s0.t0", worker);
        KernelContext ctx = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input)
                .task("t0", TestSIMDGroupReductions::simdSumKernel, ctx, input, partials)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, partials);

        ImmutableTaskGraph itg = taskGraph.snapshot();
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
            plan.withGridScheduler(grid).execute();
        }

        for (int g = 0; g < numGroups; g++) {
            assertEquals("Group " + g + " sum mismatch", expected[g], partials.get(g), DELTA);
        }
    }

    /**
     * Tests that a butterfly reduction built with {@code simdShuffleDown} gives the
     * same group sums as a sequential reduction.
     *
     * <p>The kernel adds five shift levels (16, 8, 4, 2, 1) to fully reduce 32
     * lanes, mirroring the standard warp-shuffle reduction pattern.
     */
    @Test
    public void testSIMDShuffleDownReduction() throws TornadoExecutionPlanException {
        // OpenCL support requires cl_khr_subgroups (available on Intel/AMD/ARM, not NVIDIA)
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 256;
        final int localSize = SIMD_GROUP_SIZE;
        final int numGroups = size / localSize;

        FloatArray input = new FloatArray(size);
        FloatArray partials = new FloatArray(numGroups);
        IntStream.range(0, size).forEach(i -> input.set(i, (float) (i + 1)));

        float[] expected = computeGroupSumsSequential(input, localSize);

        WorkerGrid worker = new WorkerGrid1D(size);
        worker.setLocalWork(localSize, 1, 1);
        GridScheduler grid = new GridScheduler("s0.t0", worker);
        KernelContext ctx = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input)
                .task("t0", TestSIMDGroupReductions::simdShuffleDownReductionKernel, ctx, input, partials)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, partials);

        ImmutableTaskGraph itg = taskGraph.snapshot();
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
            plan.withGridScheduler(grid).execute();
        }

        for (int g = 0; g < numGroups; g++) {
            assertEquals("Group " + g + " sum mismatch", expected[g], partials.get(g), DELTA);
        }
    }

    /**
     * Tests that {@code simdBroadcastFirst(val)} copies lane 0's value to every lane.
     *
     * <p>After the broadcast, all 32 threads in each group should hold the same
     * value that thread 0 (lane 0) of that group had before the call.
     */
    @Test
    public void testSIMDBroadcastFirst() throws TornadoExecutionPlanException {
        // OpenCL support requires cl_khr_subgroups (available on Intel/AMD/ARM, not NVIDIA)
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 256;
        final int localSize = SIMD_GROUP_SIZE;
        final int numGroups = size / localSize;

        FloatArray input = new FloatArray(size);
        FloatArray output = new FloatArray(size);
        // input[i] = i + 1  so lane 0 of group g holds (g * 32 + 1)
        IntStream.range(0, size).forEach(i -> input.set(i, (float) (i + 1)));

        WorkerGrid worker = new WorkerGrid1D(size);
        worker.setLocalWork(localSize, 1, 1);
        GridScheduler grid = new GridScheduler("s0.t0", worker);
        KernelContext ctx = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input)
                .task("t0", TestSIMDGroupReductions::simdBroadcastFirstKernel, ctx, input, output)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph itg = taskGraph.snapshot();
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
            plan.withGridScheduler(grid).execute();
        }

        for (int g = 0; g < numGroups; g++) {
            float expectedVal = (float) (g * localSize + 1); // lane-0 value for group g
            for (int j = 0; j < localSize; j++) {
                assertEquals("Group " + g + " lane " + j + " mismatch",
                        expectedVal, output.get(g * localSize + j), DELTA);
            }
        }
    }
}
