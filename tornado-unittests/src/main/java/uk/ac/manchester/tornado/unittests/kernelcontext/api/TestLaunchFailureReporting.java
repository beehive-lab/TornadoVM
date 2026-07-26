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
package uk.ac.manchester.tornado.unittests.kernelcontext.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
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
 * A kernel launch the driver rejects must be reported, not silently skipped.
 *
 * <p>The CUDA JNI layer used to log every driver error and carry on, so a rejected
 * {@code cuLaunchKernel} left the output buffer untouched and the caller read stale data as if the
 * kernel had run - a wrong answer with no exception anywhere. Launch and transfer failures now raise
 * {@code CUDAException}, which the queue wrappers translate into a bailout.
 *
 * <p>How to run:
 *
 * <pre>
 * tornado-test --ea -V uk.ac.manchester.tornado.unittests.kernelcontext.api.TestLaunchFailureReporting
 * </pre>
 */
public class TestLaunchFailureReporting extends TornadoTestBase {

    private static final int SIZE = 256;
    /**
     * 16384 floats = 64 KB of shared memory. Local arrays are emitted as static {@code __shared__}, which
     * the hardware caps at 48 KB per block, so the driver rejects the launch. (Reaching the 100 KB an
     * sm_89 GPU has available needs dynamic shared memory, which the backend does not emit yet - when it
     * does, this becomes a functional test instead of a failure-reporting one.)
     */
    private static final int OVERSIZED_LOCAL_FLOATS = 16384;

    private static void oversizedLocalKernel(KernelContext ctx, FloatArray in, FloatArray out) {
        float[] tile = ctx.allocateFloatLocalArray(OVERSIZED_LOCAL_FLOATS);
        int localIdx = ctx.localIdx;
        for (int i = localIdx; i < OVERSIZED_LOCAL_FLOATS; i += SIZE) {
            tile[i] = in.get(i % in.getSize());
        }
        ctx.localBarrier();
        out.set(ctx.globalIdx, tile[localIdx]);
    }

    @Test
    public void testRejectedLaunchIsReported() {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);
        assertNotBackend(TornadoVMBackendType.METAL);

        FloatArray in = new FloatArray(SIZE);
        FloatArray out = new FloatArray(SIZE);
        in.init(1.0f);
        out.init(-1.0f);

        KernelContext context = new KernelContext();
        WorkerGrid worker = new WorkerGrid1D(SIZE);
        worker.setLocalWork(SIZE, 1, 1);
        GridScheduler grid = new GridScheduler("badLaunch.t0", worker);

        TaskGraph taskGraph = new TaskGraph("badLaunch") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("t0", TestLaunchFailureReporting::oversizedLocalKernel, context, in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(grid).execute();
            fail("a launch the driver rejects must not be reported as success (output stayed " + out.get(0) + ")");
        } catch (TornadoExecutionPlanException | RuntimeException expected) {
            // Expected: the driver error travels out as an exception rather than being logged and dropped.
        }

        // And the output must not look like a successful run either.
        assertEquals("the rejected kernel must not have written anything", -1.0f, out.get(0), 0.0f);
    }
}
