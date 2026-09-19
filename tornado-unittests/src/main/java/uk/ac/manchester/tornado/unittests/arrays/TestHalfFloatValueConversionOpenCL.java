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
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMOpenCLNotSupported;

/**
 * Regression tests for consuming a {@code new HalfFloat(float)} as a value in the same kernel
 * rather than storing it: {@code getHalfFloatValue()} for the raw f16 bits, and
 * {@code getFloat32()} for the value rounded through half precision.
 *
 * <p>Both used to abort compilation on the CUDA backend. {@code HalfFloat.getHalfFloatValue()} was
 * inlined into a read of the object's {@code halfFloatValue} field, and the HalfFloat replacement
 * phase then dissolved the allocation into the value it wraps - leaving that value as the base of
 * a memory address, which address lowering refuses:
 * {@code address origin unimplemented: OCLConvertFloatToHalf} (or, with any arithmetic in front of
 * the conversion, {@code ...calc.MulNode}) - the same defect the CUDA backend has, reported against
 * it in the two linked issues. On OpenCL the store did not work either: it failed one step earlier
 * with {@code node is not LIRLowerable: NewHalfFloatInstance}, because the inline-write fixes the
 * CUDA phase already carries had never been ported.
 *
 * <p>The kernels are the ones from the two reports, in their original form. The
 * {@code KernelContext} ones carry the shapes the failures were actually hit with - explicit
 * {@code globalIdx} indexing, and Q8_0 weights dequantized into a shared tile - and the
 * {@code @Parallel} ones cover the same expressions on the loop path.
 *
 * <p>How to run: {@code tornado-test -V uk.ac.manchester.tornado.unittests.arrays.TestHalfFloatValueConversionOpenCL}</p>
 */
public class TestHalfFloatValueConversionOpenCL extends TornadoTestBase {

    private static final int SIZE = 256;
    private static final int LOCAL_SIZE = 64;

    /** Q8_0 block layout: a 2-byte fp16 scale followed by 32 int8 quants. */
    private static final int Q8_BLOCK_BYTES = 34;
    private static final int Q8_BLOCK_QUANTS = 32;

    /**
     * This change is the OpenCL half of the fix; the CUDA one is a separate change and the Metal
     * replacement phase is untouched, so skip those rather than assert against behaviour this
     * change does not affect.
     */
    private void assumeOpenCLBackend() {
        TornadoVMBackendType backendType = getTornadoRuntime().getDefaultDevice().getTornadoVMBackend();
        if (backendType != TornadoVMBackendType.OPENCL) {
            String message = "This HalfFloat value-conversion fix targets the OpenCL backend (default device is " + backendType + ")";
            switch (backendType) {
                case CUDA, METAL -> assertNotBackend(backendType, message);
                default -> throw new TornadoVMOpenCLNotSupported(message);
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
        assumeOpenCLBackend();
        FloatArray in = input();
        HalfFloatArray out = new HalfFloatArray(SIZE);

        TaskGraph tg = new TaskGraph("st").transferToDevice(DataTransferMode.EVERY_EXECUTION, in, out) //
                .task("t0", TestHalfFloatValueConversionOpenCL::store, new KernelContext(), in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        execute(tg, "st");

        for (int i = 0; i < SIZE; i++) {
            float expected = Float.float16ToFloat(Float.floatToFloat16(in.get(i) * 2.0f));
            assertEquals("out[" + i + "]", expected, out.get(i).getFloat32(), 0.0f);
        }
    }

    @Test
    public void testValue() throws TornadoExecutionPlanException {
        assumeOpenCLBackend();
        FloatArray in = input();
        IntArray out = new IntArray(SIZE);

        TaskGraph tg = new TaskGraph("va").transferToDevice(DataTransferMode.EVERY_EXECUTION, in, out) //
                .task("t0", TestHalfFloatValueConversionOpenCL::value, new KernelContext(), in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        execute(tg, "va");

        for (int i = 0; i < SIZE; i++) {
            assertEquals("bits[" + i + "]", Float.floatToFloat16(in.get(i) * 2.0f) & 0xFFFF, out.get(i));
        }
    }

    @Test
    public void testValueOfLoad() throws TornadoExecutionPlanException {
        assumeOpenCLBackend();
        FloatArray in = input();
        IntArray out = new IntArray(SIZE);

        TaskGraph tg = new TaskGraph("vl").transferToDevice(DataTransferMode.EVERY_EXECUTION, in, out) //
                .task("t0", TestHalfFloatValueConversionOpenCL::valueOfLoad, new KernelContext(), in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        execute(tg, "vl");

        for (int i = 0; i < SIZE; i++) {
            assertEquals("bits[" + i + "]", Float.floatToFloat16(in.get(i)) & 0xFFFF, out.get(i));
        }
    }

    @Test
    public void testFloat32OfNew() throws TornadoExecutionPlanException {
        assumeOpenCLBackend();
        FloatArray in = input();
        FloatArray out = new FloatArray(SIZE);

        TaskGraph tg = new TaskGraph("f3").transferToDevice(DataTransferMode.EVERY_EXECUTION, in, out) //
                .task("t0", TestHalfFloatValueConversionOpenCL::float32OfNew, new KernelContext(), in, out) //
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
        assumeOpenCLBackend();
        int blocksPerRow = 1;
        int columns = 2 * (SIZE / Q8_BLOCK_QUANTS);
        ByteArray weights = q8Weights(columns, blocksPerRow);
        IntArray out = new IntArray(SIZE);

        TaskGraph tg = new TaskGraph("q8").transferToDevice(DataTransferMode.EVERY_EXECUTION, weights, out) //
                .task("t0", TestHalfFloatValueConversionOpenCL::stageQ8Tile, new KernelContext(), weights, out, blocksPerRow) //
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
        assumeOpenCLBackend();
        FloatArray in = input();
        IntArray out = new IntArray(SIZE);

        TaskGraph tg = new TaskGraph("bp").transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("t0", TestHalfFloatValueConversionOpenCL::bitsOfComputedParallel, in, out) //
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
        assumeOpenCLBackend();
        FloatArray in = input();
        IntArray out = new IntArray(SIZE);

        TaskGraph tg = new TaskGraph("pp").transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("t0", TestHalfFloatValueConversionOpenCL::packHalfPairParallel, in, out) //
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

    // -----------------------------------------------------------------------
    // IEEE 754 binary16 conformance of the conversion
    // -----------------------------------------------------------------------

    /**
     * Values that pin down the rounding rule rather than the arithmetic: exact ties (which must
     * round to even), the subnormal range (which must not be flushed to zero), the overflow
     * boundary, and the signed zeroes and infinities.
     */
    private static final float[] IEEE_CASES = { //
            2049.0f, 2051.0f, -2049.0f,                       // ties between representable halves
            1.0009765625f, 1.00048828125f, 1.0014648437f,     // ties around 1.0
            6.0975552e-5f, 5.9604645e-8f, 4.4703484e-8f,      // subnormals, incl. the smallest
            2.9802322e-8f, 1.0e-10f,                          // below half the smallest: to zero
            65504.0f, 65519.0f, 65520.0f, 131008.0f,          // max finite, and the overflow tie
            -65504.0f, -131008.0f, //
            0.0f, -0.0f, Float.MAX_VALUE, //
            Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NaN };

    /** Converts each input to half precision and stores it. */
    public static void ieeeConvert(KernelContext ctx, FloatArray in, HalfFloatArray out) {
        int i = ctx.globalIdx;
        out.set(i, new HalfFloat(in.get(i)));
    }

    /**
     * The conversion must be IEEE 754 binary16 with round-to-nearest-even - the same rule
     * {@link Float#floatToFloat16} implements - so the comparison is bit-exact rather than
     * approximate. A GPU that flushed subnormals to zero, truncated instead of rounding, or
     * overflowed at the wrong boundary would show up here and nowhere else in this class.
     *
     * <p>NaN is compared as a class, not a bit pattern: IEEE leaves the payload unspecified and
     * the device returns a different quiet NaN than Java does.
     */
    @Test
    public void testIeee754Binary16Rounding() throws TornadoExecutionPlanException {
        assumeOpenCLBackend();
        int n = IEEE_CASES.length;
        FloatArray in = new FloatArray(n);
        for (int i = 0; i < n; i++) {
            in.set(i, IEEE_CASES[i]);
        }
        HalfFloatArray out = new HalfFloatArray(n);

        WorkerGrid1D worker = new WorkerGrid1D(n);
        worker.setLocalWork(1, 1, 1);
        GridScheduler grid = new GridScheduler("ieee.t0", worker);

        TaskGraph tg = new TaskGraph("ieee").transferToDevice(DataTransferMode.EVERY_EXECUTION, in, out) //
                .task("t0", TestHalfFloatValueConversionOpenCL::ieeeConvert, new KernelContext(), in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(tg.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }

        for (int i = 0; i < n; i++) {
            short expected = Float.floatToFloat16(IEEE_CASES[i]);
            short actual = out.get(i).getHalfFloatValue();
            if (isQuietNaN(expected) && isQuietNaN(actual)) {
                continue;
            }
            assertEquals(String.format("half(%s): expected 0x%04X, got 0x%04X", IEEE_CASES[i], expected, actual), //
                    expected, actual);
        }
    }

    /** True for a binary16 NaN: maximum exponent and a non-zero significand. */
    private static boolean isQuietNaN(short bits) {
        return (bits & 0x7C00) == 0x7C00 && (bits & 0x03FF) != 0;
    }
    /** The short accessor must sign-extend when its result is stored as an int. */
    public static void signedBitsOfComputed(KernelContext ctx, FloatArray in, IntArray out) {
        int i = ctx.globalIdx;
        out.set(i, new HalfFloat(in.get(i) * 2.0f).getHalfFloatValue());
    }

    public static void signedBitsOfLoad(KernelContext ctx, FloatArray in, IntArray out) {
        int i = ctx.globalIdx;
        out.set(i, new HalfFloat(in.get(i)).getHalfFloatValue());
    }

    /** Widening, comparisons and arithmetic shifts must consume a signed short value. */
    public static void signedBitsWidening(KernelContext ctx, FloatArray in, LongArray longs,
                                          FloatArray floats, IntArray shifts) {
        int i = ctx.globalIdx;
        short bits = new HalfFloat(in.get(i)).getHalfFloatValue();
        longs.set(i, bits);
        floats.set(i, bits);
        shifts.set(i, (bits >> 8) + (bits < 0 ? 1 : 0));
    }

    @Test
    public void testSignedBitsOfComputed() throws TornadoExecutionPlanException {
        assumeOpenCLBackend();
        FloatArray in = signedInput();
        IntArray out = new IntArray(SIZE);
        out.init(0x5A5A5A5A);
        TaskGraph graph = new TaskGraph("signed_computed")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in, out)
                .task("t0", TestHalfFloatValueConversionOpenCL::signedBitsOfComputed, new KernelContext(), in, out)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        execute(graph, "signed_computed");
        for (int i = 0; i < SIZE; i++) {
            assertEquals("signed computed bits[" + i + "]",
                    (int) Float.floatToFloat16(in.get(i) * 2.0f), out.get(i));
        }
    }

    @Test
    public void testSignedBitsOfLoad() throws TornadoExecutionPlanException {
        assumeOpenCLBackend();
        FloatArray in = signedInput();
        IntArray out = new IntArray(SIZE);
        out.init(0x5A5A5A5A);
        TaskGraph graph = new TaskGraph("signed_load")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in, out)
                .task("t0", TestHalfFloatValueConversionOpenCL::signedBitsOfLoad, new KernelContext(), in, out)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        execute(graph, "signed_load");
        for (int i = 0; i < SIZE; i++) {
            assertEquals("signed loaded bits[" + i + "]", (int) Float.floatToFloat16(in.get(i)), out.get(i));
        }
    }

    @Test
    public void testSignedBitsWidening() throws TornadoExecutionPlanException {
        assumeOpenCLBackend();
        FloatArray in = signedInput();
        LongArray longs = new LongArray(SIZE);
        FloatArray floats = new FloatArray(SIZE);
        IntArray shifts = new IntArray(SIZE);
        TaskGraph graph = new TaskGraph("signed_widening")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in)
                .task("t0", TestHalfFloatValueConversionOpenCL::signedBitsWidening,
                        new KernelContext(), in, longs, floats, shifts)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, longs, floats, shifts);
        execute(graph, "signed_widening");
        for (int i = 0; i < SIZE; i++) {
            short expected = Float.floatToFloat16(in.get(i));
            assertEquals("long bits[" + i + "]", (long) expected, longs.get(i));
            assertEquals("float bits[" + i + "]", (float) expected, floats.get(i), 0.0f);
            assertEquals("signed shift/comparison[" + i + "]",
                    (expected >> 8) + (expected < 0 ? 1 : 0), shifts.get(i));
        }
    }

    private static FloatArray signedInput() {
        float[] values = { -8.0f, 8.0f, -0.0f, 0.0f, -1.0f, 1.0f,
                -65504.0f, 65504.0f, -5.9604645e-8f, 5.9604645e-8f,
                Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY };
        FloatArray in = new FloatArray(SIZE);
        for (int i = 0; i < SIZE; i++) {
            in.set(i, values[i % values.length]);
        }
        return in;
    }

    public static void signedBitsSharedConsumers(KernelContext ctx, FloatArray in,
                                                 ShortArray shorts, IntArray ints) {
        int i = ctx.globalIdx;
        short bits = new HalfFloat(in.get(i)).getHalfFloatValue();
        shorts.set(i, bits);
        ints.set(i, bits);
    }

    @Test
    public void testSignedBitsSharedConsumers() throws TornadoExecutionPlanException {
        assumeOpenCLBackend();
        FloatArray in = signedInput();
        ShortArray shorts = new ShortArray(SIZE);
        IntArray ints = new IntArray(SIZE);
        ints.init(0x5A5A5A5A);
        TaskGraph graph = new TaskGraph("signed_shared")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in, ints)
                .task("t0", TestHalfFloatValueConversionOpenCL::signedBitsSharedConsumers,
                        new KernelContext(), in, shorts, ints)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, shorts, ints);
        execute(graph, "signed_shared");
        for (int i = 0; i < SIZE; i++) {
            short expected = Float.floatToFloat16(in.get(i));
            assertEquals("short bits[" + i + "]", expected, shorts.get(i));
            assertEquals("shared int bits[" + i + "]", (int) expected, ints.get(i));
        }
    }
}
