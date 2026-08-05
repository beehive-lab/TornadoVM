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
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * A block-wide inclusive scan composed from {@code KernelContext.simdShuffleDown} (warp-level)
 * plus one shared-memory pass to carry values across warps -- nothing in the suite composes
 * `simdShuffleDown`/`simdSum` into an actual scan today; {@code TestSIMDGroupReductions} only
 * exercises them as a plain reduction.
 *
 * <p>
 * {@code simdShuffleDown} only pulls a value from a HIGHER lane index (`shfl.sync.down`, per its
 * javadoc) -- there is no `shfl_up` equivalent in `KernelContext`, so a conventional left-to-right
 * prefix scan isn't directly expressible. What IS directly expressible with the available
 * primitive is a SUFFIX scan (each element's sum with everything AFTER it): repeated
 * {@code simdShuffleDown} at strides 1,2,4,8,16 folds each lane with higher lanes within its warp;
 * cross-warp carry-in is then a single {@code localBarrier}-guarded shared-memory pass. This is
 * also why the kernel operates on {@code float}: {@code simdShuffleDown} has no {@code int}
 * overload.
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.kernelcontext.reductions.TestWarpShuffleScan
 * </code>
 */
public class TestWarpShuffleScan extends TornadoTestBase {

    private static final int WARP_SIZE = 32;
    private static final int NUM_WARPS = 8;
    private static final int BLOCK_SIZE = WARP_SIZE * NUM_WARPS;

    private static void suffixScan(KernelContext ctx, FloatArray data) {
        int tid = ctx.localIdx;
        int lane = tid % WARP_SIZE;
        int warpId = tid / WARP_SIZE;

        float val = data.get(ctx.globalIdx);
        for (int stride = 1; stride < WARP_SIZE; stride <<= 1) {
            float neighbour = ctx.simdShuffleDown(val, stride);
            if (lane + stride < WARP_SIZE) {
                val += neighbour;
            }
        }
        // val is now the suffix-sum (this lane to the end of its warp), within the warp.

        float[] warpTotals = ctx.allocateFloatLocalArray(NUM_WARPS);
        if (lane == 0) {
            warpTotals[warpId] = val; // lane 0's value == whole-warp sum
        }
        ctx.localBarrier();

        float carry = 0.0f;
        for (int w = warpId + 1; w < NUM_WARPS; w++) {
            carry += warpTotals[w];
        }
        val += carry;

        data.set(ctx.globalIdx, val);
    }

    @Test
    public void testBlockWideSuffixScan() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);

        FloatArray data = new FloatArray(BLOCK_SIZE);
        float[] input = new float[BLOCK_SIZE];
        for (int i = 0; i < BLOCK_SIZE; i++) {
            float value = (i % 7) + 1.0f;
            data.set(i, value);
            input[i] = value;
        }

        float[] expected = new float[BLOCK_SIZE];
        float running = 0.0f;
        for (int i = BLOCK_SIZE - 1; i >= 0; i--) {
            running += input[i];
            expected[i] = running;
        }

        KernelContext context = new KernelContext();
        WorkerGrid worker = new WorkerGrid1D(BLOCK_SIZE);
        worker.setLocalWork(BLOCK_SIZE, 1, 1);
        GridScheduler grid = new GridScheduler("s0.t0", worker);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, data) //
                .task("t0", TestWarpShuffleScan::suffixScan, context, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(grid).execute();
        }

        for (int i = 0; i < BLOCK_SIZE; i++) {
            assertEquals("mismatch at index " + i, expected[i], data.get(i), 0.01f);
        }
    }

}
