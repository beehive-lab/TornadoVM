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
package uk.ac.manchester.tornado.unittests.tile;

import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.junit.Before;
import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.tile.DType;
import uk.ac.manchester.tornado.api.tile.PartitionView;
import uk.ac.manchester.tornado.api.tile.Tile;
import uk.ac.manchester.tornado.api.tile.TileContext;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.Int8Array;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Views with a row pitch and a starting offset, which is what an interleaved format needs.
 *
 * <p>
 * The motivating case is GGUF's Q4_0 block: {@code [fp16 scale][16 nibble bytes]}, 18 bytes,
 * repeated. From one allocation the scales are every ninth {@code __half} and the nibbles are 16
 * usable bytes of every 18 starting at byte 2 - two strided views over the same buffer at
 * different offsets with different element types.
 * </p>
 *
 * <p>
 * The last test is the one this feature exists for, and it reads a real GGUF-layout buffer.
 * Until strided views existed, {@code TileQuantizedProjection} had to keep the scales in a
 * separate array, and the comment there claimed the interleaved layout was unreachable from a
 * tile kernel. It was not: CUDA Tile's {@code tensor_span} takes a layout, and it was this API's
 * {@code view} that could only build a contiguous, zero-offset view.
 * </p>
 *
 * <pre>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tile.TestTileStridedViews
 * </pre>
 */
public class TestTileStridedViews extends TornadoTestBase {

    /** Weights per Q4_0 block. */
    private static final int WEIGHTS = 32;

    /** Nibble bytes per block: two weights to a byte. */
    private static final int NIBBLE_BYTES = WEIGHTS / 2;

    /** Bytes per GGUF Q4_0 block: an fp16 scale then the nibbles. */
    private static final int BLOCK_BYTES = 2 + NIBBLE_BYTES;

    /** Blocks per tile, and therefore a constant. */
    private static final int BLOCKS = 8;

    private static final int NIBBLE = 0x0F;

    @Before
    public void tileMustBeAvailable() {
        TileSupport.requireTileSupport();
    }

    /** A row pitch wider than the row: the gap between rows must be skipped, not read. */
    public static void readPitchedRows(TileContext tc, FloatArray padded, FloatArray out, int rows, int columns, int pitch) {
        PartitionView source = tc.partition(tc.viewStrided(padded, 0, rows, columns, pitch), 1, 16);
        PartitionView target = tc.partition(tc.view(out, rows, columns), 1, 16);
        int row = tc.bidX();
        target.store(tc.scale(source.load(row, 0), 2.0), row, 0);
    }

    /** A view that starts part way into the buffer. */
    public static void readFromOffset(TileContext tc, FloatArray values, FloatArray out, int rows, int offset) {
        PartitionView source = tc.partition(tc.viewStrided(values, offset, rows, 16, 16), 1, 16);
        PartitionView target = tc.partition(tc.view(out, rows, 16), 1, 16);
        int row = tc.bidX();
        target.store(source.load(row, 0), row, 0);
    }

    /**
     * Dequantise GGUF Q4_0 blocks from a single interleaved buffer.
     *
     * <p>
     * Two strided views over the same allocation: the scales as {@code HalfFloatArray} with a
     * pitch of nine halves, and the nibbles as {@code Int8Array} starting at byte 2 with a pitch
     * of eighteen bytes. The nibble pairing is GGUF's: byte {@code j} holds weight {@code j} in
     * its low nibble and weight {@code j + 16} in its high one.
     * </p>
     */
    public static void dequantiseGguf(TileContext tc, HalfFloatArray asHalf, Int8Array asBytes, FloatArray out, int blocks) {
        // Scales: one fp16 per 18 bytes, i.e. every ninth half.
        PartitionView scaleView = tc.partition(tc.viewStrided(asHalf, 0, blocks, 1, BLOCK_BYTES / 2), BLOCKS, 1);
        // Nibbles: 16 usable bytes of every 18, starting at byte 2.
        PartitionView nibbleView = tc.partition(tc.viewStrided(asBytes, 2, blocks, NIBBLE_BYTES, BLOCK_BYTES), BLOCKS, NIBBLE_BYTES);
        PartitionView outView = tc.partition(tc.view(out, blocks, WEIGHTS), BLOCKS, WEIGHTS);

        int block = tc.bidX();
        Tile scales = tc.broadcast(tc.cast(scaleView.load(block, 0), DType.F32), BLOCKS, NIBBLE_BYTES);
        Tile bytes = tc.cast(nibbleView.load(block, 0), DType.S32);
        Tile mask = tc.full(DType.S32, NIBBLE, BLOCKS, NIBBLE_BYTES);
        Tile four = tc.full(DType.S32, 4, BLOCKS, NIBBLE_BYTES);
        Tile eight = tc.full(DType.F32, 8.0, BLOCKS, NIBBLE_BYTES);

        Tile low = tc.mul(tc.sub(tc.cast(tc.bitwiseAnd(bytes, mask), DType.F32), eight), scales);
        Tile high = tc.mul(tc.sub(tc.cast(tc.bitwiseAnd(tc.shiftRight(bytes, four), mask), DType.F32), eight), scales);

        // The two halves of the block are the two halves of the output row.
        outView.store(tc.concat(low, high, 1), block, 0);
    }

    // -------------------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------------------

    private static void run(String taskId, TaskGraph graph, int blocks) throws TornadoExecutionPlanException {
        GridScheduler grid = new GridScheduler(taskId, new WorkerGrid1D(blocks));
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }
    }

    @Test
    public void testPitchedRows() throws TornadoExecutionPlanException {
        final int rows = 16;
        final int columns = 16;
        final int pitch = 24;
        FloatArray padded = new FloatArray(rows * pitch);
        FloatArray out = new FloatArray(rows * columns);
        Random random = new Random(1801);
        for (int i = 0; i < rows * pitch; i++) {
            // The padding is poison: if the stride is ignored it lands in the output.
            padded.set(i, (i % pitch) < columns ? random.nextFloat() : 1000.0f);
        }

        run("pitch.k", new TaskGraph("pitch") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, padded) //
                .task("k", TestTileStridedViews::readPitchedRows, new TileContext(), padded, out, rows, columns, pitch) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out), rows);

        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                assertEquals("(" + row + "," + column + ")", padded.get(row * pitch + column) * 2.0f, out.get(row * columns + column), 1e-6);
            }
        }
    }

    @Test
    public void testViewFromOffset() throws TornadoExecutionPlanException {
        final int rows = 8;
        final int offset = 37;
        FloatArray values = new FloatArray(offset + rows * 16);
        FloatArray out = new FloatArray(rows * 16);
        Random random = new Random(1807);
        for (int i = 0; i < values.getSize(); i++) {
            values.set(i, i < offset ? -999.0f : random.nextFloat());
        }

        run("offset.k", new TaskGraph("offset") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, values) //
                .task("k", TestTileStridedViews::readFromOffset, new TileContext(), values, out, rows, offset) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out), rows);

        for (int i = 0; i < rows * 16; i++) {
            assertEquals("element " + i, values.get(offset + i), out.get(i), 1e-6);
        }
    }

    @Test
    public void testDequantiseInterleavedGgufBlocks() throws TornadoExecutionPlanException {
        final int blocks = BLOCKS;
        // One allocation in GGUF Q4_0 layout, viewed two ways by the kernel.
        int bytes = blocks * BLOCK_BYTES;
        Int8Array asBytes = new Int8Array(bytes);
        HalfFloatArray asHalf = new HalfFloatArray(bytes / 2);
        FloatArray out = new FloatArray(blocks * WEIGHTS);

        Random random = new Random(1811);
        float[] scales = new float[blocks];
        int[][] nibbles = new int[blocks][NIBBLE_BYTES];
        for (int block = 0; block < blocks; block++) {
            scales[block] = 0.02f + 0.05f * random.nextFloat();
            // The scale occupies the first two bytes of the block, written through the half view.
            asHalf.set(block * (BLOCK_BYTES / 2), new HalfFloat(scales[block]));
            for (int j = 0; j < NIBBLE_BYTES; j++) {
                int value = random.nextInt(256);
                nibbles[block][j] = value;
                asBytes.set(block * BLOCK_BYTES + 2 + j, (byte) value);
            }
        }

        run("gguf.k", new TaskGraph("gguf") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, asBytes, asHalf) //
                .task("k", TestTileStridedViews::dequantiseGguf, new TileContext(), asHalf, asBytes, out, blocks) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out), 1);

        for (int block = 0; block < blocks; block++) {
            // The scale the device reads is the fp16 rounding of what was written.
            float scale = asHalf.get(block * (BLOCK_BYTES / 2)).getFloat32();
            for (int j = 0; j < NIBBLE_BYTES; j++) {
                float low = ((nibbles[block][j] & NIBBLE) - 8.0f) * scale;
                float high = (((nibbles[block][j] >> 4) & NIBBLE) - 8.0f) * scale;
                assertEquals("block " + block + " weight " + j, low, out.get(block * WEIGHTS + j), 1e-3);
                assertEquals("block " + block + " weight " + (j + NIBBLE_BYTES), high, out.get(block * WEIGHTS + NIBBLE_BYTES + j), 1e-3);
            }
        }
    }
}
