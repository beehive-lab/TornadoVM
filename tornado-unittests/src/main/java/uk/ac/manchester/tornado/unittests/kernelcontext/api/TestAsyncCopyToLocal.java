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
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * {@code cp.async} global-to-shared copies through {@link KernelContext#asyncCopyToLocal}, for the
 * {@link ByteArray} source overload - the int8 staging path used by quantized GEMM kernels.
 *
 * <p>{@code TestMatrixMultiplicationMMACpAsync} covers the {@code HalfFloatArray} overload inside a
 * full MMA kernel; this one isolates the copy itself, so a failure points at the copy rather than at
 * the matrix pipeline. Each copy moves one 32-bit slot, i.e. 4 consecutive bytes.
 *
 * <p>How to run:
 *
 * <pre>
 * tornado-test --printKernel -V uk.ac.manchester.tornado.unittests.kernelcontext.api.TestAsyncCopyToLocal
 * </pre>
 */
public class TestAsyncCopyToLocal extends TornadoTestBase {

    private static final int SLOTS = 64;              // one 32-bit slot per thread
    private static final int BYTES = SLOTS * 4;       // 256 bytes staged per block

    /** Stages {@code BYTES} bytes into a shared tile with cp.async, then writes the tile back out. */
    public static void stageBytesKernel(KernelContext ctx, ByteArray in, IntArray out) {
        int[] tile = ctx.allocateIntLocalArray(SLOTS);
        int lane = ctx.localIdx;

        ctx.asyncCopyToLocal(tile, lane, in, lane * 4);
        ctx.asyncCopyCommit();
        ctx.asyncCopyWaitGroup(0);
        ctx.localBarrier();

        out.set(ctx.globalIdx, tile[lane]);
    }

    @Test
    public void testAsyncCopyByteArray() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);

        ByteArray input = new ByteArray(BYTES);
        for (int i = 0; i < BYTES; i++) {
            input.set(i, (byte) (i % 251)); // prime modulus: all four bytes of a slot differ
        }
        IntArray output = new IntArray(SLOTS);

        KernelContext context = new KernelContext();
        WorkerGrid worker = new WorkerGrid1D(SLOTS);
        worker.setLocalWork(SLOTS, 1, 1);
        GridScheduler grid = new GridScheduler("cpasync.stage", worker);

        TaskGraph taskGraph = new TaskGraph("cpasync") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("stage", TestAsyncCopyToLocal::stageBytesKernel, context, input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }

        for (int slot = 0; slot < SLOTS; slot++) {
            // The device is little-endian: byte 4*slot is the least significant byte of the word.
            int expected = 0;
            for (int b = 3; b >= 0; b--) {
                expected = (expected << 8) | (input.get(slot * 4 + b) & 0xFF);
            }
            assertEquals(expected, output.get(slot));
        }
    }
}
