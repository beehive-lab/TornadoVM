/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.FP8;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.FP8Array;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMCUDANotSupported;

/**
 * FP8 (8-bit float, CUDA-only storage type) tests: host-side codec correctness plus on-device
 * dequantization - a kernel reads a {@link FP8Array} and expands each element to {@code float},
 * proving the {@link FP8} arithmetic decoders compile and run on the CUDA backend.
 *
 * <p>How to run: {@code tornado-test -V uk.ac.manchester.tornado.unittests.arrays.TestFP8}</p>
 */
public class TestFP8 extends TornadoTestBase {

    /**
     * FP8 is a CUDA-only storage type (the array is registered only in the CUDA device dispatch and
     * the decoders are tuned for the CUDA code generator). The on-device tests must therefore run on
     * the CUDA backend only; on other backends they report Unsupported rather than Failed. The
     * host-codec tests are backend-agnostic and always run.
     */
    private void assumeCudaBackend() {
        TornadoVMBackendType backendType = getTornadoRuntime().getDefaultDevice().getTornadoVMBackend();
        if (backendType != TornadoVMBackendType.CUDA) {
            String message = "FP8 device kernels require the CUDA backend (default device is " + backendType + ")";
            switch (backendType) {
                case OPENCL, PTX, METAL -> assertNotBackend(backendType, message);
                default -> throw new TornadoVMCUDANotSupported(message);
            }
        }
    }

    /** Kernel: dequantize E4M3 bytes to float on the device (FP8 weights live in a ByteArray). */
    public static void decodeE4M3(ByteArray in, FloatArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, FP8.e4m3ToFloat(in.get(i)));
        }
    }

    /** Kernel: dequantize E5M2 bytes to float on the device. */
    public static void decodeE5M2(ByteArray in, FloatArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, FP8.e5m2ToFloat(in.get(i)));
        }
    }

    /** Kernel: FP8-weighted multiply-add - out[i] = FP8(a[i]) * FP8(b[i]). Proves FP8 decode is
     *  usable inside real arithmetic on the device, not just a copy. */
    public static void mulE4M3(ByteArray a, ByteArray b, FloatArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, FP8.e4m3ToFloat(a.get(i)) * FP8.e4m3ToFloat(b.get(i)));
        }
    }

    /** Kernel: quantize to E4M3 then dequantize, on the device - proves in-kernel FP8 encode
     *  (activation quantization), not just decode. */
    public static void roundTripE4M3(FloatArray in, FloatArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, FP8.e4m3ToFloat(FP8.e4m3FromFloat(in.get(i))));
        }
    }

    // - Host codec -

    @Test
    public void testE4M3ExactValues() {
        // Spot values from the OCP E4M3 spec.
        assertEquals(0.0f, FP8.e4m3ToFloat((byte) 0x00), 0.0f);
        assertEquals(448.0f, FP8.e4m3ToFloat((byte) 0x7E), 0.0f); // max normal
        assertEquals(1.0f, FP8.e4m3ToFloat((byte) 0x38), 0.0f);   // exp=7, mant=0
        assertEquals(-2.0f, FP8.e4m3ToFloat((byte) 0xC0), 0.0f);  // sign, exp=8, mant=0
        assertEquals(0.001953125f, FP8.e4m3ToFloat((byte) 0x01), 0.0f); // smallest subnormal
    }

    @Test
    public void testE4M3RoundTripHost() {
        // Every value produced by decoding a byte must re-encode to the same byte (canonical codes).
        for (int b = 0; b < 256; b++) {
            float f = FP8.e4m3ToFloat((byte) b);
            if (Float.isNaN(f)) {
                continue; // NaN codes aren't round-tripped by value
            }
            if (f == 0.0f) {
                continue; // 0x00 and 0x80 both decode to zero; -0 re-encodes to +0
            }
            byte re = FP8.e4m3FromFloat(f);
            assertEquals("byte 0x" + Integer.toHexString(b), (byte) b, re);
        }
    }

    @Test
    public void testE4M3Saturation() {
        assertEquals(448.0f, FP8.e4m3ToFloat(FP8.e4m3FromFloat(1.0e6f)), 0.0f);
        assertEquals(-448.0f, FP8.e4m3ToFloat(FP8.e4m3FromFloat(-1.0e6f)), 0.0f);
    }

    @Test
    public void testE5M2SpecialValues() {
        assertEquals(Float.POSITIVE_INFINITY, FP8.e5m2ToFloat((byte) 0x7C), 0.0f);
        assertEquals(Float.NEGATIVE_INFINITY, FP8.e5m2ToFloat((byte) 0xFC), 0.0f);
        assertEquals(57344.0f, FP8.e5m2ToFloat((byte) 0x7B), 0.0f); // max finite
        assertEquals(1.0f, FP8.e5m2ToFloat((byte) 0x3C), 0.0f);
    }

    @Test
    public void testE5M2RoundTripHost() {
        for (int b = 0; b < 256; b++) {
            float f = FP8.e5m2ToFloat((byte) b);
            if (Float.isNaN(f) || Float.isInfinite(f)) {
                continue;
            }
            if (f == 0.0f) {
                continue;
            }
            assertEquals("byte 0x" + Integer.toHexString(b), (byte) b, FP8.e5m2FromFloat(f));
        }
    }

    @Test
    public void testE4M3NearestQuantization() {
        // Encode/decode of an arbitrary value must land within one E4M3 step (3 mantissa bits ->
        // ~1/8 relative near the value) and never further than the exact host round-trip allows.
        java.util.Random rng = new java.util.Random(7);
        for (int i = 0; i < 5000; i++) {
            float v = (rng.nextFloat() - 0.5f) * 800.0f; // spans the +/-448 E4M3 range and beyond
            float q = FP8.e4m3ToFloat(FP8.e4m3FromFloat(v));
            if (Math.abs(v) > FP8.E4M3_MAX) {
                assertEquals(Math.signum(v) * FP8.E4M3_MAX, q, 0.0f); // saturates
            } else {
                float tol = Math.max(0.002f, 0.0625f * Math.abs(v)); // half of the 1/8 step
                assertEquals(v, q, tol);
            }
            // Quantization is idempotent: re-quantizing a quantized value is a no-op.
            assertEquals(q, FP8.e4m3ToFloat(FP8.e4m3FromFloat(q)), 0.0f);
        }
    }

    @Test
    public void testZeroAndSaturation() {
        // Zero encodes to the zero byte; -0.0 collapses to +0.0 (kernel-safe encode, -0 == +0).
        assertEquals((byte) 0x00, FP8.e4m3FromFloat(0.0f));
        assertEquals((byte) 0x00, FP8.e4m3FromFloat(-0.0f));
        assertEquals(0.0f, FP8.e4m3ToFloat((byte) 0x80), 0.0f);
        // E4M3 has no infinity: overflow saturates to the max finite magnitude.
        assertEquals(448.0f, FP8.e4m3ToFloat(FP8.e4m3FromFloat(Float.POSITIVE_INFINITY)), 0.0f);
        assertEquals(-448.0f, FP8.e4m3ToFloat(FP8.e4m3FromFloat(Float.NEGATIVE_INFINITY)), 0.0f);
        // E5M2 is IEEE-like: overflow becomes infinity.
        assertEquals(Float.POSITIVE_INFINITY, FP8.e5m2ToFloat(FP8.e5m2FromFloat(1.0e9f)), 0.0f);
        assertEquals(Float.NEGATIVE_INFINITY, FP8.e5m2ToFloat(FP8.e5m2FromFloat(-1.0e9f)), 0.0f);
        // E5M2 rounding boundary to infinity: just under stays finite, just over goes to inf.
        assertEquals(57344.0f, FP8.e5m2ToFloat(FP8.e5m2FromFloat(60000.0f)), 0.0f);
        assertEquals(Float.POSITIVE_INFINITY, FP8.e5m2ToFloat(FP8.e5m2FromFloat(62000.0f)), 0.0f);
    }

    @Test
    public void testFP8ArrayHost() {
        FP8Array a = FP8Array.fromFloatsE4M3(new float[] { 1.0f, -2.0f, 0.5f, 448.0f, 0.0f });
        assertEquals(1.0f, a.getE4M3(0), 0.0f);
        assertEquals(-2.0f, a.getE4M3(1), 0.0f);
        assertEquals(0.5f, a.getE4M3(2), 0.0f);
        assertEquals(448.0f, a.getE4M3(3), 0.0f);
        assertEquals(0.0f, a.getE4M3(4), 0.0f);
        assertEquals(5, a.getSize());

        FP8Array b = FP8Array.fromFloatsE5M2(new float[] { 3.0f, -16.0f });
        assertEquals(3.0f, b.getE5M2(0), 0.0f);
        assertEquals(-16.0f, b.getE5M2(1), 0.0f);
    }

    // - On-device dequantization (CUDA) -

    @Test
    public void testE4M3DecodeOnDevice() throws TornadoExecutionPlanException {
        assumeCudaBackend();
        float[] values = { 0.0f, 1.0f, -1.0f, 0.5f, 2.0f, -3.5f, 0.125f, 448.0f, -448.0f, 0.015625f, 100.0f, -0.25f };
        ByteArray in = new ByteArray(values.length);
        for (int i = 0; i < values.length; i++) {
            in.set(i, FP8.e4m3FromFloat(values[i]));
        }
        FloatArray out = new FloatArray(values.length);

        TaskGraph tg = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, in) //
                .task("t0", TestFP8::decodeE4M3, in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        ImmutableTaskGraph itg = tg.snapshot();
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
            plan.execute();
        }

        for (int i = 0; i < values.length; i++) {
            // Device output must match the host decode of the same FP8 bits, exactly.
            assertEquals(FP8.e4m3ToFloat(in.get(i)), out.get(i), 0.0f);
        }
    }

    @Test
    public void testE5M2DecodeOnDevice() throws TornadoExecutionPlanException {
        assumeCudaBackend();
        float[] values = { 0.0f, 1.0f, -1.0f, 0.5f, 16.0f, -256.0f, 57344.0f, 0.0001f, -12.5f, 3.0f };
        ByteArray in = new ByteArray(values.length);
        for (int i = 0; i < values.length; i++) {
            in.set(i, FP8.e5m2FromFloat(values[i]));
        }
        FloatArray out = new FloatArray(values.length);

        TaskGraph tg = new TaskGraph("s1") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, in) //
                .task("t0", TestFP8::decodeE5M2, in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(tg.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < values.length; i++) {
            assertEquals(FP8.e5m2ToFloat(in.get(i)), out.get(i), 0.0f);
        }
    }

    @Test
    public void testE4M3DecodeLargeArrayOnDevice() throws TornadoExecutionPlanException {
        assumeCudaBackend();
        // Cover the whole E4M3 code space (256 values) through the device decode kernel.
        int n = 256;
        ByteArray in = new ByteArray(n);
        for (int i = 0; i < n; i++) {
            in.set(i, (byte) i);
        }
        FloatArray out = new FloatArray(n);
        TaskGraph tg = new TaskGraph("s2") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, in) //
                .task("t0", TestFP8::decodeE4M3, in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(tg.snapshot())) {
            plan.execute();
        }
        for (int i = 0; i < n; i++) {
            float host = FP8.e4m3ToFloat((byte) i);
            if (Float.isNaN(host)) {
                continue; // NaN codes: skip (NaN != NaN)
            }
            assertEquals(host, out.get(i), 0.0f);
        }
    }

    @Test
    public void testE4M3MultiplyOnDevice() throws TornadoExecutionPlanException {
        assumeCudaBackend();
        // FP8 weights used in real arithmetic on the device: element-wise product of two FP8 arrays.
        float[] av = { 1.0f, 2.0f, -0.5f, 3.0f, 0.25f, -4.0f, 8.0f, 0.125f };
        float[] bv = { 2.0f, -1.0f, 4.0f, 0.5f, 8.0f, -0.25f, 0.5f, 16.0f };
        int n = av.length;
        ByteArray a = new ByteArray(n);
        ByteArray b = new ByteArray(n);
        for (int i = 0; i < n; i++) {
            a.set(i, FP8.e4m3FromFloat(av[i]));
            b.set(i, FP8.e4m3FromFloat(bv[i]));
        }
        FloatArray out = new FloatArray(n);
        TaskGraph tg = new TaskGraph("s3") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestFP8::mulE4M3, a, b, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(tg.snapshot())) {
            plan.execute();
        }
        for (int i = 0; i < n; i++) {
            float expected = FP8.e4m3ToFloat(a.get(i)) * FP8.e4m3ToFloat(b.get(i));
            assertEquals(expected, out.get(i), 0.0f);
        }
    }

    @Test
    public void testE4M3EncodeOnDevice() throws TornadoExecutionPlanException {
        assumeCudaBackend();
        // In-kernel quantization: quantize float -> E4M3 -> float on the device, matching the host
        // codec exactly (covers normals, subnormals, saturation and negatives).
        float[] values = { 0.0f, 1.0f, -1.3f, 0.7f, 2.5f, -3.14f, 0.02f, 500.0f, -500.0f, 0.001f, 42.0f, -0.6f };
        FloatArray in = new FloatArray(values.length);
        for (int i = 0; i < values.length; i++) {
            in.set(i, values[i]);
        }
        FloatArray out = new FloatArray(values.length);
        TaskGraph tg = new TaskGraph("s4") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, in) //
                .task("t0", TestFP8::roundTripE4M3, in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(tg.snapshot())) {
            plan.execute();
        }
        for (int i = 0; i < values.length; i++) {
            float host = FP8.e4m3ToFloat(FP8.e4m3FromFloat(values[i]));
            assertEquals(host, out.get(i), 0.0f);
        }
    }
}
