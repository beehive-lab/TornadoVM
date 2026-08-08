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
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.FP8Array;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * {@code cp.async} global-to-shared copies through {@link KernelContext#asyncCopyToLocal}, covering
 * all three source overloads ({@link ByteArray}, {@link FP8Array}, {@link HalfFloatArray}) and both
 * halves of the synchronisation contract - {@link KernelContext#asyncCopyCommit()} and
 * {@link KernelContext#asyncCopyWaitGroup(int)} with a zero <em>and</em> a non-zero argument.
 *
 * <p>{@code TestMatrixMultiplicationMMACpAsync} covers the {@code HalfFloatArray} overload inside a
 * full MMA kernel; this one isolates the copy itself, so a failure points at the copy rather than at
 * the matrix pipeline. Each copy moves one 32-bit slot, i.e. 4 consecutive bytes (or 2 fp16 values).
 *
 * <p>The tests are ordered by how much of the contract they exercise:
 *
 * <ol>
 * <li>{@code testAsyncCopyByteArray} - one copy per thread, each lane reads back its own slot.
 * <li>{@code testAsyncCopyVisibleAcrossLanes} - same copy, but every lane reads a slot staged by a
 * <em>different</em> lane. A tile that was never actually shared (or a barrier that does not publish
 * it) passes the first test and fails this one.
 * <li>{@code testAsyncCopyMultipleSlotsPerThread} - several copies batched into one commit group,
 * with a strided write / contiguous read so the lanes cross.
 * <li>{@code testDoubleBufferedPipeline} - the pattern {@code cp.async} exists for: stage chunk
 * {@code k+1} while consuming chunk {@code k}, using {@code asyncCopyWaitGroup(1)} to keep one group
 * in flight. This is the only test that exercises a non-zero wait-group argument and more than one
 * committed group.
 * <li>{@code testAsyncCopyFP8Array} / {@code testAsyncCopyHalfFloatArray} - the remaining two source
 * overloads, asserting the same little-endian packing contract.
 * </ol>
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

    private static final int SLOTS_PER_THREAD = 4;    // wide-tile test: copies batched per thread
    private static final int WIDE_SLOTS = SLOTS * SLOTS_PER_THREAD;
    private static final int WIDE_BYTES = WIDE_SLOTS * 4;

    private static final int CHUNKS = 4;              // pipeline test: chunks streamed per block
    private static final int PIPELINE_SLOTS = SLOTS * CHUNKS;
    private static final int PIPELINE_BYTES = PIPELINE_SLOTS * 4;

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

    /**
     * Same staging as {@link #stageBytesKernel}, but each lane reads the slot staged by the lane at
     * the mirrored position. The result is only correct if the copies really landed in shared memory
     * and the barrier published the whole tile.
     */
    public static void stageBytesReversedKernel(KernelContext ctx, ByteArray in, IntArray out) {
        int[] tile = ctx.allocateIntLocalArray(SLOTS);
        int lane = ctx.localIdx;

        ctx.asyncCopyToLocal(tile, lane, in, lane * 4);
        ctx.asyncCopyCommit();
        ctx.asyncCopyWaitGroup(0);
        ctx.localBarrier();

        out.set(ctx.globalIdx, tile[SLOTS - 1 - lane]);
    }

    /**
     * Issues {@code SLOTS_PER_THREAD} copies per thread into one commit group. Writes are strided by
     * the block size (coalesced) while reads are contiguous per lane, so every lane consumes slots
     * staged by other lanes.
     */
    public static void stageWideTileKernel(KernelContext ctx, ByteArray in, IntArray out) {
        int[] tile = ctx.allocateIntLocalArray(WIDE_SLOTS);
        int lane = ctx.localIdx;

        for (int s = 0; s < SLOTS_PER_THREAD; s++) {
            int slot = s * SLOTS + lane;
            ctx.asyncCopyToLocal(tile, slot, in, slot * 4);
        }
        ctx.asyncCopyCommit();
        ctx.asyncCopyWaitGroup(0);
        ctx.localBarrier();

        int acc = 0;
        for (int s = 0; s < SLOTS_PER_THREAD; s++) {
            acc += tile[lane * SLOTS_PER_THREAD + s];
        }
        out.set(ctx.globalIdx, acc);
    }

    /**
     * Double-buffered software pipeline: chunk {@code k+1} is issued before chunk {@code k} is
     * consumed, and {@code asyncCopyWaitGroup(1)} retires only the older group, leaving the
     * just-issued one in flight. A wait-group that over-waits still produces the right sum (only
     * slower), but one that under-waits reads a tile that has not landed, so the sum is wrong.
     */
    public static void pipelinedStageKernel(KernelContext ctx, ByteArray in, IntArray out) {
        int[] tile = ctx.allocateIntLocalArray(2 * SLOTS); // two buffers, alternating
        int lane = ctx.localIdx;

        // Prologue: get chunk 0 in flight before the loop.
        ctx.asyncCopyToLocal(tile, lane, in, lane * 4);
        ctx.asyncCopyCommit();

        int acc = 0;
        for (int k = 0; k < CHUNKS - 1; k++) {
            int next = k + 1;
            ctx.asyncCopyToLocal(tile, (next & 1) * SLOTS + lane, in, next * BYTES + lane * 4);
            ctx.asyncCopyCommit();
            ctx.asyncCopyWaitGroup(1); // chunk k has landed; chunk k+1 stays in flight
            ctx.localBarrier();

            acc += tile[(k & 1) * SLOTS + lane];
            ctx.localBarrier(); // all reads of this buffer done before it is restaged
        }

        // Epilogue: the last chunk has no successor to overlap with.
        ctx.asyncCopyWaitGroup(0);
        ctx.localBarrier();
        acc += tile[((CHUNKS - 1) & 1) * SLOTS + lane];

        out.set(ctx.globalIdx, acc);
    }

    /** {@link FP8Array} overload: four adjacent storage bytes packed into one b32 slot. */
    public static void stageFP8Kernel(KernelContext ctx, FP8Array in, IntArray out) {
        int[] tile = ctx.allocateIntLocalArray(SLOTS);
        int lane = ctx.localIdx;

        ctx.asyncCopyToLocal(tile, lane, in, lane * 4);
        ctx.asyncCopyCommit();
        ctx.asyncCopyWaitGroup(0);
        ctx.localBarrier();

        out.set(ctx.globalIdx, tile[SLOTS - 1 - lane]);
    }

    /** {@link HalfFloatArray} overload: two adjacent fp16 values packed as {@code lo | (hi << 16)}. */
    public static void stageHalfFloatKernel(KernelContext ctx, HalfFloatArray in, IntArray out) {
        int[] tile = ctx.allocateIntLocalArray(SLOTS);
        int lane = ctx.localIdx;

        ctx.asyncCopyToLocal(tile, lane, in, lane * 2);
        ctx.asyncCopyCommit();
        ctx.asyncCopyWaitGroup(0);
        ctx.localBarrier();

        out.set(ctx.globalIdx, tile[SLOTS - 1 - lane]);
    }

    /** {@code cp.async} is Ampere and later, so these kernels only lower on the CUDA backend. */
    private void assumeCpAsyncBackend() {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);
    }

    /** The device is little-endian: byte {@code base} is the least significant byte of the word. */
    private static int packLittleEndian(ByteArray src, int base) {
        int packed = 0;
        for (int b = 3; b >= 0; b--) {
            packed = (packed << 8) | (src.get(base + b) & 0xFF);
        }
        return packed;
    }

    private static ByteArray rampBytes(int size) {
        ByteArray input = new ByteArray(size);
        for (int i = 0; i < size; i++) {
            input.set(i, (byte) (i % 251)); // prime modulus: all four bytes of a slot differ
        }
        return input;
    }

    private static void runSingleBlock(TaskGraph taskGraph, String taskId) throws TornadoExecutionPlanException {
        WorkerGrid worker = new WorkerGrid1D(SLOTS);
        worker.setLocalWork(SLOTS, 1, 1);
        GridScheduler grid = new GridScheduler(taskId, worker);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }
    }

    @Test
    public void testAsyncCopyByteArray() throws TornadoExecutionPlanException {
        assumeCpAsyncBackend();

        ByteArray input = rampBytes(BYTES);
        IntArray output = new IntArray(SLOTS);

        TaskGraph taskGraph = new TaskGraph("cpasync") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("stage", TestAsyncCopyToLocal::stageBytesKernel, new KernelContext(), input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        runSingleBlock(taskGraph, "cpasync.stage");

        for (int slot = 0; slot < SLOTS; slot++) {
            assertEquals(packLittleEndian(input, slot * 4), output.get(slot));
        }
    }

    @Test
    public void testAsyncCopyVisibleAcrossLanes() throws TornadoExecutionPlanException {
        assumeCpAsyncBackend();

        ByteArray input = rampBytes(BYTES);
        IntArray output = new IntArray(SLOTS);

        TaskGraph taskGraph = new TaskGraph("cpasyncMirror") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("stage", TestAsyncCopyToLocal::stageBytesReversedKernel, new KernelContext(), input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        runSingleBlock(taskGraph, "cpasyncMirror.stage");

        for (int lane = 0; lane < SLOTS; lane++) {
            assertEquals(packLittleEndian(input, (SLOTS - 1 - lane) * 4), output.get(lane));
        }
    }

    @Test
    public void testAsyncCopyMultipleSlotsPerThread() throws TornadoExecutionPlanException {
        assumeCpAsyncBackend();

        ByteArray input = rampBytes(WIDE_BYTES);
        IntArray output = new IntArray(SLOTS);

        TaskGraph taskGraph = new TaskGraph("cpasyncWide") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("stage", TestAsyncCopyToLocal::stageWideTileKernel, new KernelContext(), input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        runSingleBlock(taskGraph, "cpasyncWide.stage");

        for (int lane = 0; lane < SLOTS; lane++) {
            int expected = 0; // int overflow wraps identically on host and device
            for (int s = 0; s < SLOTS_PER_THREAD; s++) {
                expected += packLittleEndian(input, (lane * SLOTS_PER_THREAD + s) * 4);
            }
            assertEquals(expected, output.get(lane));
        }
    }

    @Test
    public void testDoubleBufferedPipeline() throws TornadoExecutionPlanException {
        assumeCpAsyncBackend();

        ByteArray input = rampBytes(PIPELINE_BYTES);
        IntArray output = new IntArray(SLOTS);

        TaskGraph taskGraph = new TaskGraph("cpasyncPipeline") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("stage", TestAsyncCopyToLocal::pipelinedStageKernel, new KernelContext(), input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        runSingleBlock(taskGraph, "cpasyncPipeline.stage");

        for (int lane = 0; lane < SLOTS; lane++) {
            int expected = 0; // sum of this lane's slot across every chunk
            for (int k = 0; k < CHUNKS; k++) {
                expected += packLittleEndian(input, k * BYTES + lane * 4);
            }
            assertEquals(expected, output.get(lane));
        }
    }

    @Test
    public void testAsyncCopyFP8Array() throws TornadoExecutionPlanException {
        assumeCpAsyncBackend();

        // Raw storage bytes, not decoded values: the copy moves bytes and must not reinterpret them.
        FP8Array input = new FP8Array(BYTES);
        for (int i = 0; i < BYTES; i++) {
            input.set(i, (byte) (i % 251));
        }
        IntArray output = new IntArray(SLOTS);

        TaskGraph taskGraph = new TaskGraph("cpasyncFP8") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("stage", TestAsyncCopyToLocal::stageFP8Kernel, new KernelContext(), input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        runSingleBlock(taskGraph, "cpasyncFP8.stage");

        for (int lane = 0; lane < SLOTS; lane++) {
            int base = (SLOTS - 1 - lane) * 4;
            int expected = 0;
            for (int b = 3; b >= 0; b--) {
                expected = (expected << 8) | (input.get(base + b) & 0xFF);
            }
            assertEquals(expected, output.get(lane));
        }
    }

    @Test
    public void testAsyncCopyHalfFloatArray() throws TornadoExecutionPlanException {
        assumeCpAsyncBackend();

        HalfFloatArray input = new HalfFloatArray(SLOTS * 2); // two fp16 values per 32-bit slot
        for (int i = 0; i < SLOTS * 2; i++) {
            input.set(i, new HalfFloat(i * 0.5f + 1.0f));
        }
        IntArray output = new IntArray(SLOTS);

        TaskGraph taskGraph = new TaskGraph("cpasyncHalf") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("stage", TestAsyncCopyToLocal::stageHalfFloatKernel, new KernelContext(), input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        runSingleBlock(taskGraph, "cpasyncHalf.stage");

        for (int lane = 0; lane < SLOTS; lane++) {
            int src = (SLOTS - 1 - lane) * 2;
            int lo = input.get(src).getHalfFloatValue() & 0xFFFF;
            int hi = input.get(src + 1).getHalfFloatValue() & 0xFFFF;
            assertEquals(lo | (hi << 16), output.get(lane));
        }
    }
}
