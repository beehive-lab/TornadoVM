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
package uk.ac.manchester.tornado.examples.tile;

import java.util.Random;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.WorkerGrid2D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.tile.DType;
import uk.ac.manchester.tornado.api.tile.PartitionView;
import uk.ac.manchester.tornado.api.tile.Tile;
import uk.ac.manchester.tornado.api.tile.TileContext;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.Int8Array;

/**
 * A 4-bit quantised projection - the matmul at the heart of a quantised LLM - dequantised and
 * multiplied inside a tile kernel.
 *
 * <p>
 * The kernel this is modelled on is {@code Qwen35MMAKernels.projectionMMAQ4_0} from
 * <a href="https://github.com/beehive-lab/GPULlama3.java/pull/150">GPULlama3.java#150</a>. That
 * one reaches tensor cores by hand: a lane owns eight consecutive weights, nibbles are unpacked
 * per lane, the B panel is staged into shared memory through a swizzle, two barriers bracket each
 * Q4_0 block, and two {@code mma.sync} calls are issued per staging round against
 * {@code ctx.mmaFragment} accumulators. It is about 120 lines of index arithmetic, and the
 * comments in it record which of several stagings measured fastest.
 * </p>
 *
 * <p>
 * The tile version below is the loop body you would write if the hardware were not in the way:
 * mask a nibble, recentre it, scale it, multiply. No lane, no barrier, no fragment, no swizzle.
 * The dequantisation is ordinary tile arithmetic, and {@code mma} is one call.
 * </p>
 *
 * <h2>Layout</h2>
 *
 * <p>
 * A Q4_0 block is 32 weights sharing one FP16 scale, laid out as
 * {@code [fp16 scale][16 nibble bytes]} = 18 bytes, and byte {@code j} holds weight {@code j} in
 * its low nibble and weight {@code j + 16} in its high one. This example reads that layout
 * <b>as GGUF writes it</b>: one allocation, viewed twice with {@code viewStrided} - as halves
 * for the scales and as bytes for the nibbles, at different offsets and with the block pitch as
 * the row stride. The k-block goes into the offset, which is a runtime value, so the row pitch
 * steps one output column at a time.
 * </p>
 *
 * <p>
 * An earlier version of this example kept the scales in a separate array and claimed the
 * interleaved layout could not be reached from a tile kernel. That was wrong: CUDA Tile's
 * {@code tensor_span} takes a layout, and what was missing was a strided view on this side.
 * </p>
 *
 * <pre>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.tile.TileQuantizedProjection [m] [n] [k] [iterations]
 * </pre>
 */
public class TileQuantizedProjection {

    /** Weights per Q4_0 block, and the k-step of the tile kernel so a step is exactly one block. */
    private static final int BLOCK = 32;

    private static final int TILE = 32;

    /** Nibble mask and the offset a Q4_0 nibble is recentred by. */
    /** Nibble bytes per block: two weights to a byte. */
    private static final int NIBBLE_BYTES = BLOCK / 2;

    /** Bytes per GGUF Q4_0 block: an fp16 scale then the nibbles. */
    private static final int BLOCK_BYTES = 2 + NIBBLE_BYTES;

    private static final int NIBBLE = 0x0F;

    private static final float RECENTRE = 8.0f;

    // -------------------------------------------------------------------------------------
    // The tile kernel
    // -------------------------------------------------------------------------------------

    /**
     * {@code out = activations * dequantise(packed)^T}, dequantising as it goes.
     *
     * <p>
     * One pass per nibble half. The low nibbles of byte column {@code j} are weights at
     * {@code k = j}, the high nibbles are weights at {@code k = j + k/2}, so each byte tile
     * contributes to two different k-steps and both halves of every byte are used exactly once.
     * </p>
     */
    public static void quantisedProjection(TileContext tc, HalfFloatArray activations, Int8Array packedBytes, HalfFloatArray packedHalves, FloatArray out, int m, int n, int k) {
        PartitionView aView = tc.partition(tc.view(activations, m, k), TILE, TILE);
        PartitionView outView = tc.partition(tc.view(out, m, n), TILE, TILE);

        int rowBlock = tc.bidX();
        int columnBlock = tc.bidY();
        int blocksPerColumn = k / BLOCK;

        Tile mask = tc.full(DType.S32, NIBBLE, TILE, NIBBLE_BYTES);
        Tile four = tc.full(DType.S32, 4, TILE, NIBBLE_BYTES);
        Tile recentre = tc.full(DType.F32, RECENTRE, TILE, NIBBLE_BYTES);
        Tile acc = tc.zeros(DType.F32, TILE, TILE);

        for (int blockStep = 0; blockStep < blocksPerColumn; blockStep++) {
            // Two strided views over the SAME weight allocation, in GGUF Q4_0 layout. The k-block
            // goes into the offset, which is a runtime value, and the row pitch then steps one
            // output column at a time - so a tile row is a column and a tile column is a nibble
            // byte of that column's block.
            //
            //   scale  of (column c, block b) = half  [ b * 9  + c * blocksPerColumn * 9 ]
            //   nibble of (column c, block b) = byte  [ 2 + b * 18 + c * blocksPerColumn * 18 + j ]
            PartitionView scaleView = tc.partition( //
                    tc.viewStrided(packedHalves, blockStep * (BLOCK_BYTES / 2), n, 1, blocksPerColumn * (BLOCK_BYTES / 2)), TILE, 1);
            PartitionView nibbleView = tc.partition( //
                    tc.viewStrided(packedBytes, 2 + blockStep * BLOCK_BYTES, n, NIBBLE_BYTES, blocksPerColumn * BLOCK_BYTES), TILE, NIBBLE_BYTES);

            Tile scales = tc.broadcast(tc.cast(scaleView.load(columnBlock, 0), DType.F32), TILE, NIBBLE_BYTES);
            Tile bytes = tc.cast(nibbleView.load(columnBlock, 0), DType.S32);

            // A Q4_0 byte holds weight j in its low nibble and weight j + 16 in its high one, so
            // the dequantised block is the two halves concatenated.
            Tile low = tc.mul(tc.sub(tc.cast(tc.bitwiseAnd(bytes, mask), DType.F32), recentre), scales);
            Tile high = tc.mul(tc.sub(tc.cast(tc.bitwiseAnd(tc.shiftRight(bytes, four), mask), DType.F32), recentre), scales);
            Tile weights = tc.concat(low, high, 1);

            acc = tc.mma(aView.load(rowBlock, blockStep), tc.transpose(tc.cast(weights, DType.F16)), acc);
        }

        outView.store(acc, rowBlock, columnBlock);
    }

    // -------------------------------------------------------------------------------------
    // Thread-level baseline: one thread per output element, dequantising as it reads
    // -------------------------------------------------------------------------------------

    public static void threadPerElement(HalfFloatArray activations, Int8Array packedBytes, HalfFloatArray packedHalves, FloatArray out, int m, int n, int k) {
        for (@Parallel int row = 0; row < m; row++) {
            for (@Parallel int column = 0; column < n; column++) {
                int blocksPerColumn = k / BLOCK;
                float sum = 0.0f;
                for (int b = 0; b < blocksPerColumn; b++) {
                    int blockBase = (column * blocksPerColumn + b) * BLOCK_BYTES;
                    float scale = packedHalves.get(blockBase / 2).getFloat32();
                    for (int j = 0; j < NIBBLE_BYTES; j++) {
                        int byteValue = packedBytes.get(blockBase + 2 + j) & 0xFF;
                        float low = ((byteValue & NIBBLE) - RECENTRE) * scale;
                        float high = (((byteValue >> 4) & NIBBLE) - RECENTRE) * scale;
                        sum += activations.get(row * k + b * BLOCK + j).getFloat32() * low;
                        sum += activations.get(row * k + b * BLOCK + NIBBLE_BYTES + j).getFloat32() * high;
                    }
                }
                out.set(row * n + column, sum);
            }
        }
    }

    // -------------------------------------------------------------------------------------

    public static void main(String[] args) {
        int m = args.length > 0 ? Integer.parseInt(args[0]) : 256;
        int n = args.length > 1 ? Integer.parseInt(args[1]) : 256;
        int k = args.length > 2 ? Integer.parseInt(args[2]) : 512;
        int iterations = args.length > 3 ? Integer.parseInt(args[3]) : 20;
        if (m % TILE != 0 || n % TILE != 0 || k % BLOCK != 0) {
            System.out.printf("m and n must be multiples of %d and k a multiple of %d%n", TILE, BLOCK);
            return;
        }
        TileExamples.requireCuda();

        int blocksPerColumn = k / BLOCK;
        int weightBytes = n * blocksPerColumn * BLOCK_BYTES;
        HalfFloatArray activations = new HalfFloatArray(m * k);
        // ONE allocation in GGUF Q4_0 layout. The kernels view it twice: as bytes for the
        // nibbles, and as halves for the scales.
        Int8Array packedBytes = new Int8Array(weightBytes);
        HalfFloatArray packedHalves = new HalfFloatArray(weightBytes / 2);

        Random random = new Random(4001);
        for (int i = 0; i < m * k; i++) {
            activations.set(i, new HalfFloat(random.nextFloat() - 0.5f));
        }
        for (int block = 0; block < n * blocksPerColumn; block++) {
            int base = block * BLOCK_BYTES;
            packedHalves.set(base / 2, new HalfFloat(0.01f + 0.02f * random.nextFloat()));
            for (int j = 0; j < NIBBLE_BYTES; j++) {
                packedBytes.set(base + 2 + j, (byte) random.nextInt(256));
            }
        }

        System.out.printf("Quantised projection: [%d, %d] x [%d, %d]^T, GGUF Q4_0 weights, %d iterations%n", m, k, n, k, iterations);
        System.out.printf("weights: %d KB in one interleaved buffer, against %d KB dense FP16%n%n", weightBytes / 1024, n * k * 2 / 1024);

        float[] expected = reference(activations, packedBytes, packedHalves, m, n, k);

        FloatArray threadOut = new FloatArray(m * n);
        double threadTime = TileExamples.time(new TaskGraph("perElement") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, activations, packedBytes, packedHalves) //
                .task("k", TileQuantizedProjection::threadPerElement, activations, packedBytes, packedHalves, threadOut, m, n, k) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, threadOut), //
                new GridScheduler("perElement.k", new WorkerGrid2D(m, n)), iterations);

        FloatArray tileOut = new FloatArray(m * n);
        double tileTime = TileExamples.time(new TaskGraph("tile") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, activations, packedBytes, packedHalves) //
                .task("k", TileQuantizedProjection::quantisedProjection, new TileContext(), activations, packedBytes, packedHalves, tileOut, m, n, k) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, tileOut), //
                new GridScheduler("tile.k", new WorkerGrid2D(m / TILE, n / TILE)), iterations);

        double gigaFlop = 2.0 * m * n * k / 1e9;
        double tolerance = 0.05 * Math.sqrt(k);
        TileExamples.header();
        TileExamples.report("@Parallel, dequantise per element", threadTime, threadTime, gigaFlop, TileExamples.maxError(threadOut, expected), tolerance);
        TileExamples.report("TileContext, dequantise in tiles", tileTime, threadTime, gigaFlop, TileExamples.maxError(tileOut, expected), tolerance);
        TileExamples.footer();
        System.out.println();
        System.out.println("Both read the same interleaved buffer, and both do the same dequantisation, so the");
        System.out.println("comparison is the loop body rather than the layout.");
    }

    /** Dequantise and multiply on the host, reading the same interleaved buffer. */
    private static float[] reference(HalfFloatArray activations, Int8Array packedBytes, HalfFloatArray packedHalves, int m, int n, int k) {
        int blocksPerColumn = k / BLOCK;
        float[] out = new float[m * n];
        for (int column = 0; column < n; column++) {
            for (int b = 0; b < blocksPerColumn; b++) {
                int base = (column * blocksPerColumn + b) * BLOCK_BYTES;
                float scale = packedHalves.get(base / 2).getFloat32();
                for (int j = 0; j < NIBBLE_BYTES; j++) {
                    int byteValue = packedBytes.get(base + 2 + j) & 0xFF;
                    float low = ((byteValue & NIBBLE) - RECENTRE) * scale;
                    float high = (((byteValue >> 4) & NIBBLE) - RECENTRE) * scale;
                    for (int row = 0; row < m; row++) {
                        out[row * n + column] += activations.get(row * k + b * BLOCK + j).getFloat32() * low;
                        out[row * n + column] += activations.get(row * k + b * BLOCK + NIBBLE_BYTES + j).getFloat32() * high;
                    }
                }
            }
        }
        return out;
    }
}
