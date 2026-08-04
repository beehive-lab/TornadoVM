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

import org.junit.Test;

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
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Correctness check for the three-stage sum-reduction optimization ladder (interleaved addressing
 * -> sequential addressing -> first-add-during-load), each stage reducing per-block into a
 * partials array that's summed on the host. The point of porting this progression is
 * PERFORMANCE, not just correctness -- this test only proves each stage computes the right
 * answer; the actual timing comparison lives in the standalone, larger-data runnable example at
 * {@code tornado-examples/.../kernelcontext/reductions/ReductionOptimizationLadder.java}, which
 * also documents the {@code nsys}/{@code --enableProfiler} commands used to verify the expected
 * performance progression.
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.kernelcontext.reductions.TestReductionOptimizationLadder
 * </code>
 */
public class TestReductionOptimizationLadder extends TornadoTestBase {

    private static final int BLOCK_SIZE = 256;
    private static final int NUM_BLOCKS = 64;
    private static final int SIZE = BLOCK_SIZE * NUM_BLOCKS;

    /** Stage 1: interleaved addressing -- warp-divergent, shared-memory bank conflicts. */
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

    /** Stage 2: sequential addressing -- fixes divergence/bank conflicts, same amount of work. */
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

    /** Stage 3: first-add-during-load -- halves the block count needed for the same input size. */
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

    private FloatArray buildInput() {
        FloatArray input = new FloatArray(SIZE);
        for (int i = 0; i < SIZE; i++) {
            input.set(i, 1.0f); // sum is exactly SIZE -- avoids float-accumulation-order noise
        }
        return input;
    }

    @Test
    public void testInterleavedAddressingStage() throws TornadoExecutionPlanException {
        FloatArray input = buildInput();
        FloatArray partials = new FloatArray(NUM_BLOCKS);
        KernelContext context = new KernelContext();
        WorkerGrid worker = new WorkerGrid1D(SIZE);
        worker.setLocalWork(BLOCK_SIZE, 1, 1);
        GridScheduler grid = new GridScheduler("s0.t0", worker);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .task("t0", TestReductionOptimizationLadder::reduceInterleaved, context, input, partials) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, partials);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(grid).execute();
        }

        assertEquals((float) SIZE, hostSum(partials), 0.01f);
    }

    @Test
    public void testSequentialAddressingStage() throws TornadoExecutionPlanException {
        FloatArray input = buildInput();
        FloatArray partials = new FloatArray(NUM_BLOCKS);
        KernelContext context = new KernelContext();
        WorkerGrid worker = new WorkerGrid1D(SIZE);
        worker.setLocalWork(BLOCK_SIZE, 1, 1);
        GridScheduler grid = new GridScheduler("s0.t0", worker);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .task("t0", TestReductionOptimizationLadder::reduceSequential, context, input, partials) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, partials);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(grid).execute();
        }

        assertEquals((float) SIZE, hostSum(partials), 0.01f);
    }

    @Test
    public void testFirstAddDuringLoadStage() throws TornadoExecutionPlanException {
        FloatArray input = buildInput();
        int halfBlocks = NUM_BLOCKS / 2;
        FloatArray partials = new FloatArray(halfBlocks);
        KernelContext context = new KernelContext();
        WorkerGrid worker = new WorkerGrid1D(halfBlocks * BLOCK_SIZE);
        worker.setLocalWork(BLOCK_SIZE, 1, 1);
        GridScheduler grid = new GridScheduler("s0.t0", worker);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .task("t0", TestReductionOptimizationLadder::reduceFirstAddDuringLoad, context, input, partials) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, partials);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(grid).execute();
        }

        assertEquals((float) SIZE, hostSum(partials), 0.01f);
    }

}
