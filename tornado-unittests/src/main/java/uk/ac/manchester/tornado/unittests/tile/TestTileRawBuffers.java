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
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Views of one raw {@link ByteArray} at more than one element type.
 *
 * <p>
 * A {@code ByteArray} is an untyped buffer, which is how a packed format arrives in memory: a
 * record of a fixed byte pitch, with fields of different widths at fixed offsets inside it.
 * Reading it from a tile kernel means viewing the same allocation twice - once per field type -
 * and that is what {@link TileContext#viewStrided(ByteArray, DType, int, int, int, int)}
 * expresses. It lowers to two {@code ct::layout_strided_mapping}s over
 * {@code reinterpret_cast}s of the same pointer, which is what a hand-written CUDA Tile kernel
 * does for the same job.
 * </p>
 *
 * <p>
 * The record used here is the one that motivated the feature - eighteen bytes holding an fp16
 * scale then sixteen bytes of two packed 4-bit fields - but nothing in the API knows that: the
 * decode below is ordinary tile arithmetic (mask, shift, cast, concatenate, broadcast) over two
 * views.
 * </p>
 *
 * <pre>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tile.TestTileRawBuffers
 * </pre>
 */
public class TestTileRawBuffers extends TornadoTestBase {

    /** Bytes per record: one fp16 field plus sixteen packed bytes. */
    private static final int RECORD_BYTES = 18;

    /** The packed-byte field's width, in bytes. */
    private static final int PACKED_BYTES = 16;

    /** Values per record: each packed byte holds two. */
    private static final int VALUES = 32;

    /** Records per tile. */
    private static final int ROWS = 32;

    @Before
    public void tileMustBeAvailable() {
        TileSupport.requireTileSupport();
    }

    /**
     * Reads {@code records} records out of one buffer and writes the decoded values.
     *
     * <p>
     * Two views of {@code raw}: the fp16 field on an 18-byte pitch (nine halves), and the packed
     * bytes on the same pitch starting two bytes in. The low field of byte {@code j} is value
     * {@code j} and the high field is value {@code j + 16}, so the two halves concatenate along
     * axis 1 in that order.
     * </p>
     */
    public static void decodePackedRecords(TileContext tc, ByteArray raw, FloatArray out, int records) {
        PartitionView scaleView = tc.partition(tc.viewStrided(raw, DType.F16, 0, records, 1, RECORD_BYTES / 2), ROWS, 1);
        PartitionView packedView = tc.partition(tc.viewStrided(raw, DType.S8, 2, records, PACKED_BYTES, RECORD_BYTES), ROWS, PACKED_BYTES);
        PartitionView outView = tc.partition(tc.view(out, records, VALUES), ROWS, VALUES);

        // A byte read as S8 sign-extends, so mask to the byte's own width before unpacking.
        Tile bytes = tc.bitwiseAnd(tc.cast(packedView.load(0, 0), DType.S32), tc.full(DType.S32, 255.0, ROWS, PACKED_BYTES));
        Tile low = tc.bitwiseAnd(bytes, tc.full(DType.S32, 15.0, ROWS, PACKED_BYTES));
        Tile high = tc.shiftRight(bytes, tc.full(DType.S32, 4.0, ROWS, PACKED_BYTES));

        Tile values = tc.cast(tc.concat(low, high, 1), DType.F32);
        Tile centred = tc.sub(values, tc.full(DType.F32, 8.0, ROWS, VALUES));
        Tile scale = tc.broadcast(tc.cast(scaleView.load(0, 0), DType.F32), ROWS, VALUES);
        outView.store(tc.mul(centred, scale), 0, 0);
    }

    /**
     * The same buffer read as bytes only, to pin down that an {@code S8} view of a
     * {@code ByteArray} sees the bytes the host wrote.
     */
    public static void readBytes(TileContext tc, ByteArray raw, FloatArray out, int count) {
        PartitionView byteView = tc.partition(tc.view(raw, count), ROWS);
        PartitionView outView = tc.partition(tc.view(out, count), ROWS);
        outView.store(tc.cast(byteView.load(tc.bidX()), DType.F32), tc.bidX());
    }

    @Test
    public void testByteViewReadsRawBytes() throws TornadoExecutionPlanException {
        ByteArray raw = new ByteArray(ROWS);
        for (int i = 0; i < ROWS; i++) {
            // Span the signed range, so a sign-extension mistake shows up.
            raw.set(i, (byte) (i * 8 - 128));
        }
        FloatArray out = new FloatArray(ROWS);
        WorkerGrid1D worker = new WorkerGrid1D(1);
        worker.setLocalWork(1, 1, 1);
        TaskGraph graph = new TaskGraph("raw") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, raw) //
                .task("k", TestTileRawBuffers::readBytes, new TileContext(), raw, out, ROWS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(new GridScheduler("raw.k", worker)).execute();
        }
        for (int i = 0; i < ROWS; i++) {
            assertEquals("byte " + i, raw.get(i), (byte) out.get(i));
        }
    }

    @Test
    public void testTwoViewsOfOneBufferAtDifferentTypes() throws TornadoExecutionPlanException {
        final int records = ROWS;
        ByteArray raw = new ByteArray(records * RECORD_BYTES);
        float[] scales = new float[records];
        Random random = new Random(4242);
        for (int record = 0; record < records; record++) {
            int base = record * RECORD_BYTES;
            float scale = 0.05f + 0.2f * random.nextFloat();
            raw.setHalfFloat(base, new HalfFloat(scale));
            // Read the fp16 back, so the reference uses the value the buffer actually holds.
            scales[record] = raw.getHalfFloat(base).getFloat32();
            for (int j = 0; j < PACKED_BYTES; j++) {
                raw.set(base + 2 + j, (byte) random.nextInt(256));
            }
        }

        FloatArray out = new FloatArray(records * VALUES);
        WorkerGrid1D worker = new WorkerGrid1D(1);
        worker.setLocalWork(1, 1, 1);
        TaskGraph graph = new TaskGraph("packed") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, raw) //
                .task("k", TestTileRawBuffers::decodePackedRecords, new TileContext(), raw, out, records) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(new GridScheduler("packed.k", worker)).execute();
        }

        for (int record = 0; record < records; record++) {
            for (int j = 0; j < PACKED_BYTES; j++) {
                int packed = raw.get(record * RECORD_BYTES + 2 + j) & 0xFF;
                double expectedLow = ((packed & 0x0F) - 8.0) * scales[record];
                double expectedHigh = (((packed >> 4) & 0x0F) - 8.0) * scales[record];
                assertEquals("low field of record " + record + " byte " + j, //
                        expectedLow, out.get(record * VALUES + j), 1e-4);
                assertEquals("high field of record " + record + " byte " + j, //
                        expectedHigh, out.get(record * VALUES + PACKED_BYTES + j), 1e-4);
            }
        }
    }
}
