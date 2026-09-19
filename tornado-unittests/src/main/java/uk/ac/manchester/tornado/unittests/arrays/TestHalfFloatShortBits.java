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
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMCUDANotSupported;

/**
 * Regression tests for {@code new HalfFloat(short)}, which takes the half's <em>raw bit
 * pattern</em> - it does not convert a number to half precision.
 *
 * <p>The CUDA backend used to let that short through as an integer, so the store assigned it to a
 * {@code __half} destination and C++ converted it numerically: the bits of -8.0 ({@code 0xC800})
 * were written as -14336.0, silently, for every element. The value now goes through
 * {@code __ushort_as_half}, which reinterprets it and drops the sign extension a {@code short}
 * picks up on the Java operand stack.
 *
 * <p>The raw-bits constructor is what a kernel reaches for when it stages quantized weights, so
 * the KernelContext forms - explicit {@code globalIdx} indexing, and a shared tile of packed bits -
 * are covered alongside the {@code @Parallel} loop.
 *
 * <p>How to run: {@code tornado-test -V uk.ac.manchester.tornado.unittests.arrays.TestHalfFloatShortBits}</p>
 */
public class TestHalfFloatShortBits extends TornadoTestBase {

    private static final int LOCAL_SIZE = 4;

    /** Half bit patterns worth pinning: both signs, zero, a subnormal, and the largest finite half. */
    private static final float[] VALUES = { 0.0f, 1.0f, -1.0f, 8.0f, -8.0f, 0.5f, -0.5f, 65504.0f, -65504.0f, 6.0e-8f, -6.0e-8f, 1024.0f };

    /**
     * The fix is applied to the CUDA backend only; the OpenCL/Metal HalfFloat replacement phases
     * share the pattern and are not changed here, so skip them rather than assert against
     * behaviour this change does not touch.
     */
    private void assumeCudaBackend() {
        TornadoVMBackendType backendType = getTornadoRuntime().getDefaultDevice().getTornadoVMBackend();
        if (backendType != TornadoVMBackendType.CUDA) {
            String message = "This HalfFloat raw-bits fix targets the CUDA backend (default device is " + backendType + ")";
            switch (backendType) {
                case OPENCL, METAL -> assertNotBackend(backendType, message);
                default -> throw new TornadoVMCUDANotSupported(message);
            }
        }
    }

    /** Build a half out of a bit pattern carried in an int, and store it. */
    public static void storeBits(IntArray bits, HalfFloatArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, new HalfFloat((short) bits.get(i)));
        }
    }

    /** Take a half apart into its bits and put it back together: an exact copy. */
    public static void roundTripBits(HalfFloatArray in, HalfFloatArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, new HalfFloat(in.get(i).getHalfFloatValue()));
        }
    }

    /** A constant bit pattern: 0x3C00 is half 1.0, not the number 15360. */
    public static void constantBits(HalfFloatArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, new HalfFloat((short) 0x3C00));
        }
    }

    /** The same rebuild from bits, indexed explicitly. */
    public static void storeBitsKernelContext(KernelContext ctx, IntArray bits, HalfFloatArray out) {
        int i = ctx.globalIdx;
        out.set(i, new HalfFloat((short) bits.get(i)));
    }

    /**
     * Bits staged through a shared tile before being rebuilt into halves - the shape a kernel that
     * unpacks quantized weights uses, where the bit pattern is a register value passing through
     * local memory rather than something read straight out of a HalfFloatArray.
     */
    public static void stageBitsThroughLocalTile(KernelContext ctx, IntArray bits, HalfFloatArray out) {
        int lane = ctx.localIdx;
        int i = ctx.globalIdx;
        int[] tile = ctx.allocateIntLocalArray(LOCAL_SIZE);

        tile[lane] = bits.get(i);
        ctx.localBarrier();

        out.set(i, new HalfFloat((short) tile[lane]));
    }

    @Test
    public void testStoreBits() throws TornadoExecutionPlanException {
        assumeCudaBackend();
        IntArray bits = bitsInput();
        HalfFloatArray out = new HalfFloatArray(VALUES.length);

        TaskGraph tg = new TaskGraph("sb").transferToDevice(DataTransferMode.FIRST_EXECUTION, bits) //
                .task("t0", TestHalfFloatShortBits::storeBits, bits, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(tg.snapshot())) {
            plan.execute();
        }

        assertMatchesBits(bits, out);
    }

    @Test
    public void testRoundTripBits() throws TornadoExecutionPlanException {
        assumeCudaBackend();
        HalfFloatArray in = new HalfFloatArray(VALUES.length);
        for (int i = 0; i < VALUES.length; i++) {
            in.set(i, new HalfFloat(VALUES[i]));
        }
        HalfFloatArray out = new HalfFloatArray(VALUES.length);

        TaskGraph tg = new TaskGraph("rt").transferToDevice(DataTransferMode.FIRST_EXECUTION, in) //
                .task("t0", TestHalfFloatShortBits::roundTripBits, in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(tg.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < VALUES.length; i++) {
            assertEquals("out[" + i + "]", in.get(i).getFloat32(), out.get(i).getFloat32(), 0.0f);
        }
    }

    @Test
    public void testStoreBitsKernelContext() throws TornadoExecutionPlanException {
        assumeCudaBackend();
        IntArray bits = bitsInput();
        HalfFloatArray out = new HalfFloatArray(VALUES.length);

        TaskGraph tg = new TaskGraph("kc").transferToDevice(DataTransferMode.EVERY_EXECUTION, bits, out) //
                .task("t0", TestHalfFloatShortBits::storeBitsKernelContext, new KernelContext(), bits, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        executeWithGrid(tg, "kc");

        assertMatchesBits(bits, out);
    }

    @Test
    public void testStageBitsThroughLocalTile() throws TornadoExecutionPlanException {
        assumeCudaBackend();
        IntArray bits = bitsInput();
        HalfFloatArray out = new HalfFloatArray(VALUES.length);

        TaskGraph tg = new TaskGraph("tl").transferToDevice(DataTransferMode.EVERY_EXECUTION, bits, out) //
                .task("t0", TestHalfFloatShortBits::stageBitsThroughLocalTile, new KernelContext(), bits, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        executeWithGrid(tg, "tl");

        assertMatchesBits(bits, out);
    }

    @Test
    public void testConstantBits() throws TornadoExecutionPlanException {
        assumeCudaBackend();
        HalfFloatArray out = new HalfFloatArray(16);

        TaskGraph tg = new TaskGraph("cb") //
                .task("t0", TestHalfFloatShortBits::constantBits, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(tg.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < out.getSize(); i++) {
            assertEquals("out[" + i + "]", 1.0f, out.get(i).getFloat32(), 0.0f);
        }
    }

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    private static IntArray bitsInput() {
        IntArray bits = new IntArray(VALUES.length);
        for (int i = 0; i < VALUES.length; i++) {
            // Sign-extended on purpose: a short argument reaches the kernel that way, and the
            // extension is exactly what the old lowering turned into a different number.
            bits.set(i, Float.floatToFloat16(VALUES[i]));
        }
        return bits;
    }

    private static void assertMatchesBits(IntArray bits, HalfFloatArray out) {
        for (int i = 0; i < VALUES.length; i++) {
            float expected = Float.float16ToFloat((short) bits.get(i));
            assertEquals("out[" + i + "]", expected, out.get(i).getFloat32(), 0.0f);
        }
    }

    private void executeWithGrid(TaskGraph tg, String name) throws TornadoExecutionPlanException {
        WorkerGrid1D worker = new WorkerGrid1D(VALUES.length);
        worker.setLocalWork(LOCAL_SIZE, 1, 1);
        GridScheduler grid = new GridScheduler(name + ".t0", worker);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(tg.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }
    }
}
