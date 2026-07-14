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
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.FP8;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.FP8Array;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * FP8 (8-bit float, CUDA-only storage type) tests: host-side codec correctness plus on-device
 * dequantization — a kernel reads a {@link FP8Array} and expands each element to {@code float},
 * proving the {@link FP8} arithmetic decoders compile and run on the CUDA backend.
 *
 * <p>How to run: {@code tornado-test -V uk.ac.manchester.tornado.unittests.arrays.TestFloat8}</p>
 */
public class TestFP8 extends TornadoTestBase {

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

    // ── Host codec ────────────────────────────────────────────────────────────

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
            assertEquals("byte 0x" + Integer.toHexString(b), (byte) b, FP8.e5m2FromFloat(f));
        }
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

    // ── On-device dequantization (CUDA) ───────────────────────────────────────

    @Test
    public void testE4M3DecodeOnDevice() throws TornadoExecutionPlanException {
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
}
