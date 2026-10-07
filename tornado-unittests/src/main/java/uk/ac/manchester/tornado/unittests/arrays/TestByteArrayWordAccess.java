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
package uk.ac.manchester.tornado.unittests.arrays;

import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Word access to a {@link ByteArray}: {@code getInt}/{@code setInt} and {@code getLong}/{@code setLong} read and
 * write a whole 32- or 64-bit little-endian word at a byte offset, the same bytes as the host accessors.
 *
 * <p>
 * How to run?
 * </p>
 *
 * <p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.arrays.TestByteArrayWordAccess
 * </code>
 * </p>
 */
public class TestByteArrayWordAccess extends TornadoTestBase {

    private static final int SIZE = 4096;
    private static final int LOCAL = 128;

    public static void readInts(KernelContext context, ByteArray bytes, IntArray out, int offset) {
        int i = context.globalIdx;
        if (i < out.getSize()) {
            out.set(i, bytes.getInt(offset + (i << 2)));
        }
    }

    public static void readLongs(KernelContext context, ByteArray bytes, LongArray out, int offset) {
        int i = context.globalIdx;
        if (i < out.getSize()) {
            out.set(i, bytes.getLong(offset + (i << 3)));
        }
    }

    public static void writeWords(KernelContext context, ByteArray bytes, IntArray ints, LongArray longs) {
        int i = context.globalIdx;
        if (i < ints.getSize()) {
            // Ints in the first half of the buffer, longs in the second.
            bytes.setInt(i << 2, ints.get(i) ^ 0x5A5A5A5A);
            bytes.setLong((ints.getSize() << 2) + (i << 3), longs.get(i) + 1L);
        }
    }

    public static void copyWordsLongIndex(KernelContext context, ByteArray src, ByteArray dst, long offset, int words) {
        int i = context.globalIdx;
        if (i < words) {
            // Long byte indices: a 64-bit word to the first half of dst, its two 32-bit halves to the second.
            long at = offset + ((long) i << 3);
            long half = (long) words << 3;
            dst.setLong((long) i << 3, src.getLong(at) ^ 1L);
            dst.setInt(half + ((long) i << 3), src.getInt(at) + 1);
            dst.setInt(half + ((long) i << 3) + 4, src.getInt(at + 4));
        }
    }

    private static ByteArray randomBytes(int size, long seed) {
        Random rng = new Random(seed);
        ByteArray bytes = new ByteArray(size);
        for (int i = 0; i < size; i++) {
            bytes.set(i, (byte) rng.nextInt());
        }
        return bytes;
    }

    private static GridScheduler grid(String task, int threads) {
        WorkerGrid worker = new WorkerGrid1D(threads);
        worker.setGlobalWork(threads, 1, 1);
        worker.setLocalWork(LOCAL, 1, 1);
        return new GridScheduler(task, worker);
    }

    @Test
    public void testGetInt() throws TornadoExecutionPlanException {
        // A 4-byte offset that is not 8-byte aligned: each int stands on its own.
        int offset = 12;
        ByteArray bytes = randomBytes(offset + SIZE * 4, 1);
        IntArray out = new IntArray(SIZE);
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, bytes) //
                .task("t0", TestByteArrayWordAccess::readInts, new KernelContext(), bytes, out, offset) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(grid("s0.t0", SIZE)).execute();
        }
        for (int i = 0; i < SIZE; i++) {
            assertEquals("int " + i, bytes.getInt(offset + 4 * i), out.get(i));
        }
    }

    @Test
    public void testGetLong() throws TornadoExecutionPlanException {
        int offset = 16;
        ByteArray bytes = randomBytes(offset + SIZE * 8, 2);
        LongArray out = new LongArray(SIZE);
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, bytes) //
                .task("t0", TestByteArrayWordAccess::readLongs, new KernelContext(), bytes, out, offset) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(grid("s0.t0", SIZE)).execute();
        }
        for (int i = 0; i < SIZE; i++) {
            assertEquals("long " + i, bytes.getLong(offset + 8 * i), out.get(i));
        }
    }

    @Test
    public void testSetIntAndLong() throws TornadoExecutionPlanException {
        Random rng = new Random(3);
        IntArray ints = new IntArray(SIZE);
        LongArray longs = new LongArray(SIZE);
        for (int i = 0; i < SIZE; i++) {
            ints.set(i, rng.nextInt());
            longs.set(i, rng.nextLong());
        }
        ByteArray bytes = new ByteArray(SIZE * 12);
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, ints, longs, bytes) //
                .task("t0", TestByteArrayWordAccess::writeWords, new KernelContext(), bytes, ints, longs) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, bytes);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(grid("s0.t0", SIZE)).execute();
        }
        for (int i = 0; i < SIZE; i++) {
            assertEquals("int " + i, ints.get(i) ^ 0x5A5A5A5A, bytes.getInt(4 * i));
            assertEquals("long " + i, longs.get(i) + 1L, bytes.getLong(SIZE * 4 + 8 * i));
        }
    }

    @Test
    public void testLongIndex() throws TornadoExecutionPlanException {
        long offset = 24;
        ByteArray src = randomBytes((int) offset + SIZE * 8, 4);
        ByteArray dst = new ByteArray(SIZE * 16);
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, src, dst) //
                .task("t0", TestByteArrayWordAccess::copyWordsLongIndex, new KernelContext(), src, dst, offset, SIZE) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, dst);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(grid("s0.t0", SIZE)).execute();
        }
        long half = SIZE * 8L;
        for (int i = 0; i < SIZE; i++) {
            long at = offset + 8L * i;
            assertEquals("long " + i, src.getLong(at) ^ 1L, dst.getLong(8L * i));
            assertEquals("low int " + i, src.getInt(at) + 1, dst.getInt(half + 8L * i));
            assertEquals("high int " + i, src.getInt(at + 4), dst.getInt(half + 8L * i + 4));
        }
    }
}
