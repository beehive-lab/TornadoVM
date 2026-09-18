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

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMMetalNotSupported;

/**
 * Regression tests for consuming a {@code new HalfFloat(float)} as a value in the same kernel
 * rather than storing it: {@code getHalfFloatValue()} for the raw f16 bits, and
 * {@code getFloat32()} for the value rounded through half precision.
 *
 * <p>Both used to abort compilation on the CUDA backend. {@code HalfFloat.getHalfFloatValue()} was
 * inlined into a read of the object's {@code halfFloatValue} field, and the HalfFloat replacement
 * phase then dissolved the allocation into the value it wraps - leaving that value as the base of
 * a memory address, which address lowering refuses:
 * {@code address origin unimplemented: ...} from MetalAddressLowering, whose whitelist is the same as
 * the CUDA and OpenCL ones. Metal also had no float-to-half conversion node at all, and its leftover
 * HalfFloatPlaceholder was replaced by the value it wrapped, so a half reached integer arithmetic
 * where the bit pattern was meant.
 *
 * <p><b>These tests have never been executed.</b> The change they cover was written on Linux, where
 * the Metal backend compiles but cannot run, so it is verified by reading and by its CUDA and OpenCL
 * twins only. Run this class on macOS before trusting it.
 *
 * <p>The kernels are the ones from the two reports, in their original form. The
 * {@code KernelContext} ones carry the shapes the failures were actually hit with - explicit
 * {@code globalIdx} indexing, and Q8_0 weights dequantized into a shared tile - and the
 * {@code @Parallel} ones cover the same expressions on the loop path.
 *
 * <p>How to run: {@code tornado-test -V uk.ac.manchester.tornado.unittests.arrays.TestHalfFloatValueConversionMetal}</p>
 */
public class TestHalfFloatValueConversionMetal extends TornadoTestBase {

    private static final int SIZE = 256;
    private static final int LOCAL_SIZE = 64;

    /** Q8_0 block layout: a 2-byte fp16 scale followed by 32 int8 quants. */
    private static final int Q8_BLOCK_BYTES = 34;
    private static final int Q8_BLOCK_QUANTS = 32;

    /**
     * This change is the Metal half of the fix; the CUDA and OpenCL ones are separate changes, so
     * skip those rather than assert against behaviour this change does not affect.
     */
    private void assumeMetalBackend() {
        TornadoVMBackendType backendType = getTornadoRuntime().getDefaultDevice().getTornadoVMBackend();
        if (backendType != TornadoVMBackendType.METAL) {
            String message = "This HalfFloat value-conversion fix targets the Metal backend (default device is " + backendType + ")";
            switch (backendType) {
                case CUDA, OPENCL -> assertNotBackend(backendType, message);
                default -> throw new TornadoVMMetalNotSupported(message);
            }
        }
    }

    // -----------------------------------------------------------------------
    // KernelContext kernels - the reproducer from the first report, unchanged
    // -----------------------------------------------------------------------

    /** The case that always worked: the conversion is the store itself. */
    public static void store(KernelContext ctx, FloatArray in, HalfFloatArray out) {
        int i = ctx.globalIdx;
        out.set(i, new HalfFloat(in.get(i) * 2.0f));
    }

    /** Reported as {@code address origin unimplemented: ...calc.MulNode}. */
    public static void value(KernelContext ctx, FloatArray in, IntArray out) {
        int i = ctx.globalIdx;
        float v = in.get(i) * 2.0f;
        out.set(i, new HalfFloat(v).getHalfFloatValue() & 0xFFFF);
    }

    /**
     * Reported as {@code address origin unimplemented: CUDAConvertFloatToHalf}. With no arithmetic
     * in front of it, the conversion node itself is named as the rejected address origin.
     */
    public static void valueOfLoad(KernelContext ctx, FloatArray in, IntArray out) {
        int i = ctx.globalIdx;
        out.set(i, new HalfFloat(in.get(i)).getHalfFloatValue() & 0xFFFF);
    }

    /** The same conversion consumed as a float: a round trip through half precision. */
    public static void float32OfNew(KernelContext ctx, FloatArray in, FloatArray out) {
        int i = ctx.globalIdx;
        out.set(i, new HalfFloat(in.get(i) * 2.0f).getFloat32());
    }

    /**
     * Dequantizes and packs two vertically-adjacent Q8_0 weights into one int of two f16 values -
     * the helper from the second report's tensor-core GEMM, in the form it had when it failed.
     * Reading the packed halves back, rather than storing them into a HalfFloatArray, is what the
     * backend could not lower, and is why that kernel had to fall back to a ~40-operation software
     * float-to-half in its inner K loop.
     */
    private static int packQ8Halves(ByteArray w, int col, int k, int blocksPerRow) {
        int kBlock = k >>> 5;
        int kIn = k & 31;
        int off0 = (col * blocksPerRow + kBlock) * Q8_BLOCK_BYTES;
        int off1 = off0 + blocksPerRow * Q8_BLOCK_BYTES;
        float v0 = w.getHalfFloat(off0).getFloat32() * w.get(off0 + 2 + kIn);
        float v1 = w.getHalfFloat(off1).getFloat32() * w.get(off1 + 2 + kIn);
        int lo = new HalfFloat(v0).getHalfFloatValue() & 0xFFFF;
        int hi = new HalfFloat(v1).getHalfFloatValue() & 0xFFFF;
        return lo | (hi << 16);
    }

    /**
     * Stages packed Q8_0 weights through a shared tile, as the GEMM does, and writes the tile out.
     * The tiling is what the second report could not reduce away: the packed halves are consumed as
     * register values feeding shared memory, never as a store to a HalfFloatArray.
     */
    public static void stageQ8Tile(KernelContext ctx, ByteArray w, IntArray out, int blocksPerRow) {
        int lane = ctx.localIdx;
        int slot = ctx.globalIdx;
        int[] tile = ctx.allocateIntLocalArray(LOCAL_SIZE);

        int col = (slot / Q8_BLOCK_QUANTS) * 2;
        int k = slot % Q8_BLOCK_QUANTS;
        tile[lane] = packQ8Halves(w, col, k, blocksPerRow);

        ctx.localBarrier();
        out.set(slot, tile[lane]);
    }

    // -----------------------------------------------------------------------
    // The same expressions on the @Parallel loop path
    // -----------------------------------------------------------------------

    public static void bitsOfComputedParallel(FloatArray a, IntArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, new HalfFloat(a.get(i) * 2.0f).getHalfFloatValue() & 0xFFFF);
        }
    }

    public static void packHalfPairParallel(FloatArray a, IntArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            int lo = new HalfFloat(a.get(i) * 2.0f).getHalfFloatValue() & 0xFFFF;
            int hi = new HalfFloat(a.get(i) * 4.0f).getHalfFloatValue() & 0xFFFF;
            out.set(i, lo | (hi << 16));
        }
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    public void testStore() throws TornadoExecutionPlanException {
        assumeMetalBackend();
        FloatArray in = input();
        HalfFloatArray out = new HalfFloatArray(SIZE);

        TaskGraph tg = new TaskGraph("st").transferToDevice(DataTransferMode.EVERY_EXECUTION, in, out) //
                .task("t0", TestHalfFloatValueConversionMetal::store, new KernelContext(), in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        execute(tg, "st");

        for (int i = 0; i < SIZE; i++) {
            float expected = Float.float16ToFloat(Float.floatToFloat16(in.get(i) * 2.0f));
            assertEquals("out[" + i + "]", expected, out.get(i).getFloat32(), 0.0f);
        }
    }

    @Test
    public void testValue() throws TornadoExecutionPlanException {
        assumeMetalBackend();
        FloatArray in = input();
        IntArray out = new IntArray(SIZE);

        TaskGraph tg = new TaskGraph("va").transferToDevice(DataTransferMode.EVERY_EXECUTION, in, out) //
                .task("t0", TestHalfFloatValueConversionMetal::value, new KernelContext(), in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        execute(tg, "va");

        for (int i = 0; i < SIZE; i++) {
            assertEquals("bits[" + i + "]", Float.floatToFloat16(in.get(i) * 2.0f) & 0xFFFF, out.get(i));
        }
    }

    @Test
    public void testValueOfLoad() throws TornadoExecutionPlanException {
        assumeMetalBackend();
        FloatArray in = input();
        IntArray out = new IntArray(SIZE);

        TaskGraph tg = new TaskGraph("vl").transferToDevice(DataTransferMode.EVERY_EXECUTION, in, out) //
                .task("t0", TestHalfFloatValueConversionMetal::valueOfLoad, new KernelContext(), in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        execute(tg, "vl");

        for (int i = 0; i < SIZE; i++) {
            assertEquals("bits[" + i + "]", Float.floatToFloat16(in.get(i)) & 0xFFFF, out.get(i));
        }
    }

    @Test
    public void testFloat32OfNew() throws TornadoExecutionPlanException {
        assumeMetalBackend();
        FloatArray in = input();
        FloatArray out = new FloatArray(SIZE);

        TaskGraph tg = new TaskGraph("f3").transferToDevice(DataTransferMode.EVERY_EXECUTION, in, out) //
                .task("t0", TestHalfFloatValueConversionMetal::float32OfNew, new KernelContext(), in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        execute(tg, "f3");

        for (int i = 0; i < SIZE; i++) {
            // The device rounds through half precision, so the oracle does too: an exact comparison.
            float expected = Float.float16ToFloat(Float.floatToFloat16(in.get(i) * 2.0f));
            assertEquals("out[" + i + "]", expected, out.get(i), 0.0f);
        }
    }

    /** The second report's kernel: Q8_0 weights dequantized and packed into a shared tile. */
    @Test
    public void testStageQ8Tile() throws TornadoExecutionPlanException {
        assumeMetalBackend();
        int blocksPerRow = 1;
        int columns = 2 * (SIZE / Q8_BLOCK_QUANTS);
        ByteArray weights = q8Weights(columns, blocksPerRow);
        IntArray out = new IntArray(SIZE);

        TaskGraph tg = new TaskGraph("q8").transferToDevice(DataTransferMode.EVERY_EXECUTION, weights, out) //
                .task("t0", TestHalfFloatValueConversionMetal::stageQ8Tile, new KernelContext(), weights, out, blocksPerRow) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        execute(tg, "q8");

        for (int slot = 0; slot < SIZE; slot++) {
            int col = (slot / Q8_BLOCK_QUANTS) * 2;
            int k = slot % Q8_BLOCK_QUANTS;
            assertEquals("packed[" + slot + "]", packQ8HalvesHost(weights, col, k, blocksPerRow), out.get(slot));
        }
    }

    @Test
    public void testBitsOfComputedParallel() throws TornadoExecutionPlanException {
        assumeMetalBackend();
        FloatArray in = input();
        IntArray out = new IntArray(SIZE);

        TaskGraph tg = new TaskGraph("bp").transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("t0", TestHalfFloatValueConversionMetal::bitsOfComputedParallel, in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(tg.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals("bits[" + i + "]", Float.floatToFloat16(in.get(i) * 2.0f) & 0xFFFF, out.get(i));
        }
    }

    @Test
    public void testPackHalfPairParallel() throws TornadoExecutionPlanException {
        assumeMetalBackend();
        FloatArray in = input();
        IntArray out = new IntArray(SIZE);

        TaskGraph tg = new TaskGraph("pp").transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("t0", TestHalfFloatValueConversionMetal::packHalfPairParallel, in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(tg.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < SIZE; i++) {
            int expected = (Float.floatToFloat16(in.get(i) * 2.0f) & 0xFFFF) //
                    | ((Float.floatToFloat16(in.get(i) * 4.0f) & 0xFFFF) << 16);
            assertEquals("packed[" + i + "]", expected, out.get(i));
        }
    }

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    private void execute(TaskGraph tg, String name) throws TornadoExecutionPlanException {
        WorkerGrid1D worker = new WorkerGrid1D(SIZE);
        worker.setLocalWork(LOCAL_SIZE, 1, 1);
        GridScheduler grid = new GridScheduler(name + ".t0", worker);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(tg.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }
    }

    private static FloatArray input() {
        FloatArray a = new FloatArray(SIZE);
        for (int i = 0; i < SIZE; i++) {
            a.set(i, i * 0.125f - 16.0f);
        }
        return a;
    }

    /** {@code columns} rows of {@code blocksPerRow} Q8_0 blocks: an fp16 scale plus 32 int8 quants. */
    private static ByteArray q8Weights(int columns, int blocksPerRow) {
        ByteArray weights = new ByteArray(columns * blocksPerRow * Q8_BLOCK_BYTES);
        for (int block = 0; block < columns * blocksPerRow; block++) {
            int base = block * Q8_BLOCK_BYTES;
            short scale = Float.floatToFloat16(0.0125f * (1 + block % 5));
            weights.set(base, (byte) (scale & 0xFF));
            weights.set(base + 1, (byte) ((scale >> 8) & 0xFF));
            for (int q = 0; q < Q8_BLOCK_QUANTS; q++) {
                weights.set(base + 2 + q, (byte) (((block * 31 + q * 7) % 255) - 127));
            }
        }
        return weights;
    }

    /** Host mirror of {@link #packQ8Halves}, rounding through half precision exactly as the device does. */
    private static int packQ8HalvesHost(ByteArray w, int col, int k, int blocksPerRow) {
        int kBlock = k >>> 5;
        int kIn = k & 31;
        int off0 = (col * blocksPerRow + kBlock) * Q8_BLOCK_BYTES;
        int off1 = off0 + blocksPerRow * Q8_BLOCK_BYTES;
        float v0 = w.getHalfFloat(off0).getFloat32() * w.get(off0 + 2 + kIn);
        float v1 = w.getHalfFloat(off1).getFloat32() * w.get(off1 + 2 + kIn);
        return (Float.floatToFloat16(v0) & 0xFFFF) | ((Float.floatToFloat16(v1) & 0xFFFF) << 16);
    }
}
