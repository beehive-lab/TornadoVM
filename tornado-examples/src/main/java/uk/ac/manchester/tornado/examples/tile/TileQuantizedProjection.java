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
 * <h2>Layout, and one honest deviation</h2>
 *
 * <p>
 * A Q4_0 block is 32 weights sharing one FP16 scale, and byte {@code j} of a block holds weight
 * {@code j} in its low nibble and weight {@code j + 16} in its high nibble. This example keeps
 * that nibble pairing, at row scale: byte {@code (row, j)} of the packed array holds weight
 * {@code (row, j)} in its low nibble and weight {@code (row, j + k/2)} in its high one.
 * </p>
 *
 * <p>
 * It does <b>not</b> keep GGUF's interleaving of the scale with the weights in one buffer. GGUF
 * writes {@code [fp16 scale][16 bytes of nibbles]} per block; reading that scale from inside a
 * tile kernel would mean viewing one byte buffer as both {@code S8} and {@code F16} at a byte
 * offset, and the tile API cannot express that - a view is one element type over one buffer. The
 * scales therefore live in their own array here. A production port would either store them
 * separately, as this does, or split them out in a preparation kernel.
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
    public static void quantisedProjection(TileContext tc, HalfFloatArray activations, Int8Array packed, FloatArray scales, FloatArray out, int m, int n, int k, int halfBlocks) {
        PartitionView aView = tc.partition(tc.view(activations, m, k), TILE, TILE);
        PartitionView packedView = tc.partition(tc.view(packed, n, k / 2), TILE, TILE);
        PartitionView scaleView = tc.partition(tc.view(scales, n, k / BLOCK), TILE, 1);
        PartitionView outView = tc.partition(tc.view(out, m, n), TILE, TILE);

        int rowBlock = tc.bidX();
        int columnBlock = tc.bidY();

        Tile mask = tc.full(DType.S32, NIBBLE, TILE, TILE);
        Tile four = tc.full(DType.S32, 4, TILE, TILE);
        Tile recentre = tc.full(DType.F32, RECENTRE, TILE, TILE);
        Tile acc = tc.zeros(DType.F32, TILE, TILE);

        for (int byteBlock = 0; byteBlock < halfBlocks; byteBlock++) {
            Tile bytes = tc.cast(packedView.load(columnBlock, byteBlock), DType.S32);

            // Low nibbles: weights at k = byteBlock * TILE ...
            Tile lowNibble = tc.bitwiseAnd(bytes, mask);
            Tile lowScale = tc.broadcast(scaleView.load(columnBlock, byteBlock), TILE, TILE);
            Tile lowWeights = tc.mul(tc.sub(tc.cast(lowNibble, DType.F32), recentre), lowScale);
            acc = tc.mma(aView.load(rowBlock, byteBlock), tc.transpose(tc.cast(lowWeights, DType.F16)), acc);

            // High nibbles: the same bytes, weights at k = k/2 + byteBlock * TILE ...
            Tile highNibble = tc.bitwiseAnd(tc.shiftRight(bytes, four), mask);
            Tile highScale = tc.broadcast(scaleView.load(columnBlock, halfBlocks + byteBlock), TILE, TILE);
            Tile highWeights = tc.mul(tc.sub(tc.cast(highNibble, DType.F32), recentre), highScale);
            acc = tc.mma(aView.load(rowBlock, halfBlocks + byteBlock), tc.transpose(tc.cast(highWeights, DType.F16)), acc);
        }

        outView.store(acc, rowBlock, columnBlock);
    }

    // -------------------------------------------------------------------------------------
    // Thread-level baseline: one thread per output element, dequantising as it reads
    // -------------------------------------------------------------------------------------

    public static void threadPerElement(HalfFloatArray activations, Int8Array packed, FloatArray scales, FloatArray out, int m, int n, int k) {
        for (@Parallel int row = 0; row < m; row++) {
            for (@Parallel int column = 0; column < n; column++) {
                float sum = 0.0f;
                int half = k / 2;
                for (int j = 0; j < half; j++) {
                    int byteValue = packed.get(column * half + j) & 0xFF;
                    float lowScale = scales.get(column * (k / BLOCK) + j / BLOCK);
                    float highScale = scales.get(column * (k / BLOCK) + (half + j) / BLOCK);
                    float low = ((byteValue & NIBBLE) - RECENTRE) * lowScale;
                    float high = (((byteValue >> 4) & NIBBLE) - RECENTRE) * highScale;
                    sum += activations.get(row * k + j).getFloat32() * low;
                    sum += activations.get(row * k + half + j).getFloat32() * high;
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
        if (m % TILE != 0 || n % TILE != 0 || k % (2 * BLOCK) != 0) {
            System.out.printf("m and n must be multiples of %d and k a multiple of %d%n", TILE, 2 * BLOCK);
            return;
        }
        TileExamples.requireCuda();

        int half = k / 2;
        int blocks = k / BLOCK;
        HalfFloatArray activations = new HalfFloatArray(m * k);
        Int8Array packed = new Int8Array(n * half);
        FloatArray scales = new FloatArray(n * blocks);
        Random random = new Random(4001);
        for (int i = 0; i < m * k; i++) {
            activations.set(i, new HalfFloat(random.nextFloat() - 0.5f));
        }
        for (int i = 0; i < n * half; i++) {
            packed.set(i, (byte) random.nextInt(256));
        }
        for (int i = 0; i < n * blocks; i++) {
            scales.set(i, 0.01f + 0.02f * random.nextFloat());
        }

        System.out.printf("Quantised projection: [%d, %d] x [%d, %d]^T, Q4_0 weights, %d iterations%n", m, k, n, k, iterations);
        System.out.printf("weights: %d KB packed + %d KB scales, against %d KB dense FP16%n%n", //
                n * half / 1024, n * blocks * 4 / 1024, n * k * 2 / 1024);

        float[] expected = reference(activations, packed, scales, m, n, k);

        FloatArray threadOut = new FloatArray(m * n);
        double threadTime = TileExamples.time(new TaskGraph("perElement") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, activations, packed, scales) //
                .task("k", TileQuantizedProjection::threadPerElement, activations, packed, scales, threadOut, m, n, k) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, threadOut), //
                new GridScheduler("perElement.k", new WorkerGrid2D(m, n)), iterations);

        FloatArray tileOut = new FloatArray(m * n);
        double tileTime = TileExamples.time(new TaskGraph("tile") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, activations, packed, scales) //
                .task("k", TileQuantizedProjection::quantisedProjection, new TileContext(), activations, packed, scales, tileOut, m, n, k, half / TILE) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, tileOut), //
                new GridScheduler("tile.k", new WorkerGrid2D(m / TILE, n / TILE)), iterations);

        double gigaFlop = 2.0 * m * n * k / 1e9;
        double tolerance = 0.05 * Math.sqrt(k);
        TileExamples.header();
        TileExamples.report("@Parallel, dequantise per element", threadTime, threadTime, gigaFlop, TileExamples.maxError(threadOut, expected), tolerance);
        TileExamples.report("TileContext, dequantise in tiles", tileTime, threadTime, gigaFlop, TileExamples.maxError(tileOut, expected), tolerance);
        TileExamples.footer();
        System.out.println();
        System.out.println("Speedups here are against the thread-level kernel, not sequential Java: both");
        System.out.println("implementations do the same dequantisation, so the comparison is the loop body.");
    }

    /** Dequantise and multiply on the host, the same nibble pairing the kernels use. */
    private static float[] reference(HalfFloatArray activations, Int8Array packed, FloatArray scales, int m, int n, int k) {
        int half = k / 2;
        int blocks = k / BLOCK;
        float[] out = new float[m * n];
        for (int column = 0; column < n; column++) {
            for (int j = 0; j < half; j++) {
                int byteValue = packed.get(column * half + j) & 0xFF;
                float low = ((byteValue & NIBBLE) - RECENTRE) * scales.get(column * blocks + j / BLOCK);
                float high = (((byteValue >> 4) & NIBBLE) - RECENTRE) * scales.get(column * blocks + (half + j) / BLOCK);
                for (int row = 0; row < m; row++) {
                    out[row * n + column] += activations.get(row * k + j).getFloat32() * low;
                    out[row * n + column] += activations.get(row * k + half + j).getFloat32() * high;
                }
            }
        }
        return out;
    }
}
