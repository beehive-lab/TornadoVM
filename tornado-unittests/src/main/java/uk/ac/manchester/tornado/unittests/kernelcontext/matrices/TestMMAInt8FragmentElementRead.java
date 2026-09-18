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
package uk.ac.manchester.tornado.unittests.kernelcontext.matrices;

import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.MMAShape;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Reads of the int32 accumulator elements of an int8 MMA fragment inside the kernel, and the
 * byte-offset forms of the int8 fragment loads.
 *
 * <p>
 * Element {@code i} of lane {@code l} of an {@code m16n8k32 s8.s8.s32} accumulator holds row
 * {@code l / 4 + 8 (i / 2)}, column {@code 2 (l % 4) + i % 2} of the 16 x 8 tile. The first test
 * writes every element through that mapping and compares with {@code mmaStoreInt}; the second
 * scales each 32-wide K block's dot products by a per-block factor before accumulating them in
 * FP32, which is what a block-quantized integer projection needs and what a store to global memory
 * per block cannot provide; the third loads the second of two A tiles and the second of two B
 * tiles from one shared array through the byte-offset overloads.
 * </p>
 *
 * <p>
 * How to run?
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.kernelcontext.matrices.TestMMAInt8FragmentElementRead
 * </code>
 * </p>
 */
public class TestMMAInt8FragmentElementRead extends TornadoTestBase {

    private static final int M = 16;
    private static final int N = 8;
    private static final int K = 32;
    private static final int WARP_SIZE = 32;

    /** A: [16][32] int8 row-major, packed four bytes a word, eight words a row. */
    private static void stageA(KernelContext ctx, ByteArray a, int[] aTile, int aBase, int lane) {
        for (int idx = lane; idx < M * K / 4; idx += WARP_SIZE) {
            int e = idx * 4;
            int packed = (a.get(aBase + e) & 0xFF) | ((a.get(aBase + e + 1) & 0xFF) << 8) | ((a.get(aBase + e + 2) & 0xFF) << 16) | ((a.get(aBase + e + 3) & 0xFF) << 24);
            aTile[idx] = packed;
        }
    }

    /** B: [32 k][8 n] row-major bytes, staged as the int8 B operand's pair layout. */
    private static void stageB(KernelContext ctx, ByteArray b, int[] bTile, int bBase, int lane) {
        for (int idx = lane; idx < 64; idx += WARP_SIZE) {
            int kRow = idx / 4;
            int pair = idx % 4;
            int j = pair * 2;
            int k = 2 * kRow;
            int b0 = b.get(bBase + k * N + j) & 0xFF;
            int b1 = b.get(bBase + (k + 1) * N + j) & 0xFF;
            int b2 = b.get(bBase + k * N + j + 1) & 0xFF;
            int b3 = b.get(bBase + (k + 1) * N + j + 1) & 0xFF;
            bTile[idx] = b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
        }
    }

    /** One tile: the accumulator both stored and read element by element. */
    public static void tileStoredAndRead(KernelContext ctx, ByteArray a, ByteArray b, IntArray stored, IntArray read) {
        int lane = ctx.localIdx;
        int[] aTile = ctx.allocateIntLocalArray(M * K / 4);
        int[] bTile = ctx.allocateIntLocalArray(K * N / 4);
        stageA(ctx, a, aTile, 0, lane);
        stageB(ctx, b, bTile, 0, lane);
        ctx.localBarrier();
        int[] acc = ctx.mmaInt8(ctx.mmaLoadAInt8(aTile, K), ctx.mmaLoadBInt8(bTile, K), ctx.mmaFragmentInt(0), MMAShape.M16N8K32);
        ctx.mmaStoreInt(acc, stored, 0, 0, N);
        int row = lane / 4;
        int col = (lane % 4) * 2;
        read.set(row * N + col, acc[0]);
        read.set(row * N + col + 1, acc[1]);
        read.set((row + 8) * N + col, acc[2]);
        read.set((row + 8) * N + col + 1, acc[3]);
    }

    /**
     * Two K blocks, each with its own scale: {@code out = (float) dot0 * s0 + (float) dot1 * s1},
     * with a fresh fragment per block and the scaled products accumulated in FP32.
     */
    public static void blockScaled(KernelContext ctx, ByteArray a, ByteArray b, FloatArray scales, FloatArray out) {
        int lane = ctx.localIdx;
        int[] aTile = ctx.allocateIntLocalArray(M * K / 4);
        int[] bTile = ctx.allocateIntLocalArray(K * N / 4);
        float o0 = 0.0f;
        float o1 = 0.0f;
        float o2 = 0.0f;
        float o3 = 0.0f;
        for (int block = 0; block < 2; block++) {
            stageA(ctx, a, aTile, block * M * K, lane);
            stageB(ctx, b, bTile, block * K * N, lane);
            ctx.localBarrier();
            int[] acc = ctx.mmaInt8(ctx.mmaLoadAInt8(aTile, K), ctx.mmaLoadBInt8(bTile, K), ctx.mmaFragmentInt(0), MMAShape.M16N8K32);
            float s = scales.get(block);
            o0 += (float) acc[0] * s;
            o1 += (float) acc[1] * s;
            o2 += (float) acc[2] * s;
            o3 += (float) acc[3] * s;
            ctx.localBarrier();
        }
        int row = lane / 4;
        int col = (lane % 4) * 2;
        out.set(row * N + col, o0);
        out.set(row * N + col + 1, o1);
        out.set((row + 8) * N + col, o2);
        out.set((row + 8) * N + col + 1, o3);
    }

    /** Two A tiles and two B tiles in one shared array each; the second of each is multiplied. */
    public static void secondTilesByOffset(KernelContext ctx, ByteArray a, ByteArray b, IntArray out) {
        int lane = ctx.localIdx;
        int[] aTile = ctx.allocateIntLocalArray(2 * M * K / 4);
        int[] bTile = ctx.allocateIntLocalArray(2 * K * N / 4);
        for (int t = 0; t < 2; t++) {
            for (int idx = lane; idx < M * K / 4; idx += WARP_SIZE) {
                int e = idx * 4;
                int base = t * M * K + e;
                aTile[t * (M * K / 4) + idx] = (a.get(base) & 0xFF) | ((a.get(base + 1) & 0xFF) << 8) | ((a.get(base + 2) & 0xFF) << 16) | ((a.get(base + 3) & 0xFF) << 24);
            }
            for (int idx = lane; idx < 64; idx += WARP_SIZE) {
                int kRow = idx / 4;
                int pair = idx % 4;
                int j = pair * 2;
                int k = 2 * kRow;
                int base = t * K * N;
                int b0 = b.get(base + k * N + j) & 0xFF;
                int b1 = b.get(base + (k + 1) * N + j) & 0xFF;
                int b2 = b.get(base + k * N + j + 1) & 0xFF;
                int b3 = b.get(base + (k + 1) * N + j + 1) & 0xFF;
                bTile[t * 64 + idx] = b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
            }
        }
        ctx.localBarrier();
        // Byte offsets: the second A tile starts at 16 rows x 32 bytes, the second B tile at 8
        // columns x 32 bytes.
        int[] acc = ctx.mmaInt8(ctx.mmaLoadAInt8(aTile, K, M * K), ctx.mmaLoadBInt8(bTile, K, N * K), ctx.mmaFragmentInt(0), MMAShape.M16N8K32);
        ctx.mmaStoreInt(acc, out, 0, 0, N);
    }

    private static ByteArray randomBytes(int n, long seed) {
        Random rng = new Random(seed);
        ByteArray arr = new ByteArray(n);
        for (int i = 0; i < n; i++) {
            arr.set(i, (byte) (rng.nextInt(256) - 128));
        }
        return arr;
    }

    private static int dot(ByteArray a, ByteArray b, int aBase, int bBase, int row, int col) {
        int sum = 0;
        for (int k = 0; k < K; k++) {
            sum += a.get(aBase + row * K + k) * b.get(bBase + k * N + col);
        }
        return sum;
    }

    private static GridScheduler oneWarp(String task) {
        WorkerGrid1D grid = new WorkerGrid1D(WARP_SIZE);
        grid.setLocalWork(WARP_SIZE, 1, 1);
        return new GridScheduler(task, grid);
    }

    @Test
    public void testElementReadsMatchTheStore() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);
        ByteArray a = randomBytes(M * K, 1);
        ByteArray b = randomBytes(K * N, 2);
        IntArray stored = new IntArray(M * N);
        IntArray read = new IntArray(M * N);
        TaskGraph tg = new TaskGraph("frag") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t", TestMMAInt8FragmentElementRead::tileStoredAndRead, new KernelContext(), a, b, stored, read) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, stored, read);
        ImmutableTaskGraph itg = tg.snapshot();
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
            plan.withGridScheduler(oneWarp("frag.t")).execute();
        }
        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                assertEquals("reference C[" + i + "][" + j + "]", dot(a, b, 0, 0, i, j), stored.get(i * N + j));
                assertEquals("element read C[" + i + "][" + j + "]", stored.get(i * N + j), read.get(i * N + j));
            }
        }
    }

    @Test
    public void testBlockScaledAccumulation() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);
        ByteArray a = randomBytes(2 * M * K, 3);
        ByteArray b = randomBytes(2 * K * N, 4);
        FloatArray scales = new FloatArray(2);
        scales.set(0, 0.0625f);
        scales.set(1, -0.375f);
        FloatArray out = new FloatArray(M * N);
        TaskGraph tg = new TaskGraph("frag") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, scales) //
                .task("t", TestMMAInt8FragmentElementRead::blockScaled, new KernelContext(), a, b, scales, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        ImmutableTaskGraph itg = tg.snapshot();
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
            plan.withGridScheduler(oneWarp("frag.t")).execute();
        }
        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                float expected = (float) dot(a, b, 0, 0, i, j) * scales.get(0) + (float) dot(a, b, M * K, K * N, i, j) * scales.get(1);
                // Both scales are powers of two over small integers: the FP32 products are exact.
                assertEquals("C[" + i + "][" + j + "]", expected, out.get(i * N + j), 0.0f);
            }
        }
    }

    @Test
    public void testByteOffsetLoadsSelectTheSecondTiles() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);
        ByteArray a = randomBytes(2 * M * K, 5);
        ByteArray b = randomBytes(2 * K * N, 6);
        IntArray out = new IntArray(M * N);
        TaskGraph tg = new TaskGraph("frag") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t", TestMMAInt8FragmentElementRead::secondTilesByOffset, new KernelContext(), a, b, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        ImmutableTaskGraph itg = tg.snapshot();
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
            plan.withGridScheduler(oneWarp("frag.t")).execute();
        }
        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                assertEquals("C[" + i + "][" + j + "]", dot(a, b, M * K, K * N, i, j), out.get(i * N + j));
            }
        }
    }

    /** Keeps the fragment live across a runtime-controlled loop and its exit proxy. */
    public static void loopExitReads(KernelContext ctx, ByteArray a, ByteArray b,
                                      IntArray iterations, IntArray out) {
        int lane = ctx.localIdx;
        int[] aTile = ctx.allocateIntLocalArray(M * K / 4);
        int[] bTile = ctx.allocateIntLocalArray(K * N / 4);
        stageA(ctx, a, aTile, 0, lane);
        stageB(ctx, b, bTile, 0, lane);
        ctx.localBarrier();
        byte[] fragA = ctx.mmaLoadAInt8(aTile, K);
        byte[] fragB = ctx.mmaLoadBInt8(bTile, K);
        int[] acc = ctx.mmaFragmentInt(1);
        for (int k = 0; k < iterations.get(0); k++) {
            acc = ctx.mmaInt8(fragA, fragB, acc, MMAShape.M16N8K32);
        }
        out.set(lane * 4, acc[0]);
        out.set(lane * 4 + 1, acc[1]);
        out.set(lane * 4 + 2, acc[2]);
        out.set(lane * 4 + 3, acc[3]);
    }

    @Test
    public void testFragmentReadsAfterLoopExit() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);
        ByteArray a = randomBytes(M * K, 7);
        ByteArray b = randomBytes(K * N, 8);
        IntArray iterations = new IntArray(1);
        IntArray out = new IntArray(M * N);
        TaskGraph graph = new TaskGraph("exit_reads")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, iterations)
                .task("t", TestMMAInt8FragmentElementRead::loopExitReads, new KernelContext(), a, b, iterations, out)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(oneWarp("exit_reads.t"));
            for (int count : new int[] { 0, 1, 3 }) {
                iterations.set(0, count);
                plan.execute();
                for (int lane = 0; lane < WARP_SIZE; lane++) {
                    for (int i = 0; i < 4; i++) {
                        int row = lane / 4 + 8 * (i / 2);
                        int col = (lane % 4) * 2 + i % 2;
                        assertEquals("iterations " + count + " lane " + lane + " element " + i,
                                1 + count * dot(a, b, 0, 0, row, col), out.get(lane * 4 + i));
                    }
                }
            }
        }
    }
}
