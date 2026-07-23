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
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.BFloat16;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMCUDANotSupported;

/**
 * bfloat16 codec tests: host-side round-trip correctness plus on-device decode - a kernel
 * expands raw bf16 bit patterns (stored in a {@link ShortArray}) to {@code float}, which on
 * the CUDA backend exercises the native {@code __bfloat162float} conversion node against the
 * software {@link BFloat16} decoder byte-for-byte.
 *
 * <p>How to run: {@code tornado-test -V uk.ac.manchester.tornado.unittests.arrays.TestBFloat16}</p>
 */
public class TestBFloat16 extends TornadoTestBase {

    /** Device kernels target the CUDA backend (native conversion); others report Unsupported. */
    private void assumeCudaBackend() {
        TornadoVMBackendType backendType = getTornadoRuntime().getDefaultDevice().getTornadoVMBackend();
        if (backendType != TornadoVMBackendType.CUDA) {
            String message = "BF16 device kernels require the CUDA backend (default device is " + backendType + ")";
            switch (backendType) {
                case OPENCL, PTX, SPIRV, METAL -> assertNotBackend(backendType, message);
                default -> throw new TornadoVMCUDANotSupported(message);
            }
        }
    }

    /** Kernel: expand raw bf16 bits to float on the device. */
    public static void decodeBF16(ShortArray in, FloatArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, BFloat16.bf16ToFloat(in.get(i)));
        }
    }

    // - Host codec -

    @Test
    public void testBF16ExactValues() {
        assertEquals(0.0f, BFloat16.bf16ToFloat((short) 0x0000), 0.0f);
        assertEquals(1.0f, BFloat16.bf16ToFloat((short) 0x3F80), 0.0f);
        assertEquals(-2.0f, BFloat16.bf16ToFloat((short) 0xC000), 0.0f);
        assertEquals(0.5f, BFloat16.bf16ToFloat((short) 0x3F00), 0.0f);
        assertEquals(3.3895314e38f, BFloat16.bf16ToFloat((short) 0x7F7F), 0.0f); // max finite
        assertEquals(Float.POSITIVE_INFINITY, BFloat16.bf16ToFloat((short) 0x7F80), 0.0f);
        assertEquals(Float.NEGATIVE_INFINITY, BFloat16.bf16ToFloat((short) 0xFF80), 0.0f);
        assertTrue(Float.isNaN(BFloat16.bf16ToFloat((short) 0x7FC0)));
        // Smallest subnormal: 2^-133.
        assertEquals(Math.scalb(1.0f, -133), BFloat16.bf16ToFloat((short) 0x0001), 0.0f);
    }

    /**
     * The reference decode: bf16 is the top 16 bits of the float32 pattern, so
     * {@code intBitsToFloat(bits << 16)} is the ground truth the arithmetic decoder must match.
     */
    @Test
    public void testBF16DecodeMatchesBitShiftReference() {
        for (int b = 0; b < 65536; b++) {
            float viaBits = Float.intBitsToFloat(b << 16);
            float viaCodec = BFloat16.bf16ToFloat((short) b);
            if (Float.isNaN(viaBits)) {
                assertTrue("bits 0x" + Integer.toHexString(b), Float.isNaN(viaCodec));
            } else {
                assertEquals("bits 0x" + Integer.toHexString(b), viaBits, viaCodec, 0.0f);
            }
        }
    }

    @Test
    public void testBF16RoundTripHost() {
        // Every decoded value must re-encode to the same bit pattern (canonical codes).
        for (int b = 0; b < 65536; b++) {
            float f = BFloat16.bf16ToFloat((short) b);
            if (Float.isNaN(f) || Float.isInfinite(f)) {
                continue;
            }
            if (f == 0.0f) {
                continue; // 0x0000 / 0x8000 both decode to zero; -0 re-encodes to +0
            }
            assertEquals("bits 0x" + Integer.toHexString(b), (short) b, BFloat16.bf16FromFloat(f));
        }
    }

    @Test
    public void testBF16EncodeNearest() {
        // Encoding an arbitrary float must land within half a bf16 step (7 mantissa bits).
        java.util.Random rng = new java.util.Random(7);
        for (int i = 0; i < 5000; i++) {
            float v = (rng.nextFloat() - 0.5f) * 2000.0f;
            float q = BFloat16.bf16ToFloat(BFloat16.bf16FromFloat(v));
            float tol = Math.max(1e-38f, Math.abs(v) / 128.0f); // half of 1/128 relative step, x2 slack
            assertEquals(v, q, tol);
            // Idempotent.
            assertEquals(q, BFloat16.bf16ToFloat(BFloat16.bf16FromFloat(q)), 0.0f);
        }
    }

    @Test
    public void testBF16Overflow() {
        assertEquals(Float.POSITIVE_INFINITY, BFloat16.bf16ToFloat(BFloat16.bf16FromFloat(Float.POSITIVE_INFINITY)), 0.0f);
        assertEquals(Float.NEGATIVE_INFINITY, BFloat16.bf16ToFloat(BFloat16.bf16FromFloat(Float.NEGATIVE_INFINITY)), 0.0f);
        assertEquals((short) 0x0000, BFloat16.bf16FromFloat(0.0f));
        assertEquals((short) 0x0000, BFloat16.bf16FromFloat(-0.0f));
    }

    // - On-device decode (CUDA native conversion vs host software decode) -

    @Test
    public void testBF16DecodeOnDevice() throws TornadoExecutionPlanException {
        assumeCudaBackend();
        float[] values = { 0.0f, 1.0f, -1.0f, 0.5f, 2.0f, -3.5f, 0.125f, 100.0f, -0.25f, 3.0e38f, 1.0e-38f, -7.75f };
        ShortArray in = new ShortArray(values.length);
        for (int i = 0; i < values.length; i++) {
            in.set(i, BFloat16.bf16FromFloat(values[i]));
        }
        FloatArray out = new FloatArray(values.length);

        TaskGraph tg = new TaskGraph("bf0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, in) //
                .task("t0", TestBFloat16::decodeBF16, in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        ImmutableTaskGraph itg = tg.snapshot();
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
            plan.execute();
        }

        for (int i = 0; i < values.length; i++) {
            // Device output must match the host decode of the same bf16 bits, exactly.
            assertEquals(BFloat16.bf16ToFloat(in.get(i)), out.get(i), 0.0f);
        }
    }

    @Test
    public void testBF16DecodeSweepOnDevice() throws TornadoExecutionPlanException {
        assumeCudaBackend();
        // Sweep a stride through the full 16-bit code space (finite codes checked exactly).
        int n = 4096;
        ShortArray in = new ShortArray(n);
        for (int i = 0; i < n; i++) {
            in.set(i, (short) (i * 16));
        }
        FloatArray out = new FloatArray(n);
        TaskGraph tg = new TaskGraph("bf1") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, in) //
                .task("t0", TestBFloat16::decodeBF16, in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(tg.snapshot())) {
            plan.execute();
        }
        for (int i = 0; i < n; i++) {
            float host = BFloat16.bf16ToFloat(in.get(i));
            if (Float.isNaN(host)) {
                continue;
            }
            assertEquals("bits 0x" + Integer.toHexString(in.get(i) & 0xFFFF), host, out.get(i), 0.0f);
        }
    }
}
