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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.BFloat16Array;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.CharArray;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.Int8Array;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Regression tests for {@code init(value)} on the native arrays, which fills the data region in bulk
 * (one {@code MemorySegment.fill} for zero, otherwise one element doubled with
 * {@code MemorySegment.copy}) instead of storing element by element.
 *
 * <p>Each test compares {@code init(v)} with the per-element {@code set()} loop it replaces, byte for
 * byte and including the array header, so a fill that starts at the wrong offset, overwrites the
 * header, stops short of the last element, or changes a bit pattern (-0.0, a NaN payload, a half or
 * bfloat16 encoding) fails. Sizes cover empty arrays, every size around the doubling steps, and a
 * size that is not a power of two. No device is used.
 *
 * <p>How to run: {@code tornado-test -V uk.ac.manchester.tornado.unittests.arrays.TestArrayInit}</p>
 */
public class TestArrayInit extends TornadoTestBase {

    private static final int[] SIZES = { 0, 1, 2, 3, 5, 7, 8, 9, 31, 64, 1000, 4097, 65539 };

    private static void assertSameBytes(String what, MemorySegment expected, MemorySegment actual) {
        assertEquals(expected.byteSize(), actual.byteSize(), what + ": size");
        assertEquals(-1L, expected.mismatch(actual), what + ": first differing byte");
    }

    @Test
    public void testFloatArray() {
        float[] values = { 0f, -0f, 1f, -1.5f, Float.NaN, Float.intBitsToFloat(0x7fc00001), Float.MIN_VALUE, Float.MAX_VALUE, Float.NEGATIVE_INFINITY };
        for (int size : SIZES) {
            for (float v : values) {
                FloatArray expected = new FloatArray(size);
                FloatArray actual = new FloatArray(size);
                for (int i = 0; i < size; i++) {
                    expected.set(i, v);
                }
                actual.init(v);
                assertSameBytes("FloatArray(" + size + ").init(" + v + ")", expected.getSegmentWithHeader(), actual.getSegmentWithHeader());
            }
        }
    }

    @Test
    public void testDoubleArray() {
        double[] values = { 0d, -0d, 1d, -2.25, Double.NaN, Double.longBitsToDouble(0x7ff8000000000123L), Double.MIN_VALUE, Double.POSITIVE_INFINITY };
        for (int size : SIZES) {
            for (double v : values) {
                DoubleArray expected = new DoubleArray(size);
                DoubleArray actual = new DoubleArray(size);
                for (int i = 0; i < size; i++) {
                    expected.set(i, v);
                }
                actual.init(v);
                assertSameBytes("DoubleArray(" + size + ").init(" + v + ")", expected.getSegmentWithHeader(), actual.getSegmentWithHeader());
            }
        }
    }

    @Test
    public void testIntArray() {
        int[] values = { 0, 1, -1, 0x01020304, Integer.MIN_VALUE, 0x00000100 };
        for (int size : SIZES) {
            for (int v : values) {
                IntArray expected = new IntArray(size);
                IntArray actual = new IntArray(size);
                for (int i = 0; i < size; i++) {
                    expected.set(i, v);
                }
                actual.init(v);
                assertSameBytes("IntArray(" + size + ").init(" + v + ")", expected.getSegmentWithHeader(), actual.getSegmentWithHeader());
            }
        }
    }

    @Test
    public void testLongArray() {
        long[] values = { 0L, 1L, -1L, 0x0102030405060708L, Long.MIN_VALUE, 0x100L };
        for (int size : SIZES) {
            for (long v : values) {
                LongArray expected = new LongArray(size);
                LongArray actual = new LongArray(size);
                for (int i = 0; i < size; i++) {
                    expected.set(i, v);
                }
                actual.init(v);
                assertSameBytes("LongArray(" + size + ").init(" + v + ")", expected.getSegmentWithHeader(), actual.getSegmentWithHeader());
            }
        }
    }

    @Test
    public void testShortArray() {
        short[] values = { 0, 1, -1, 0x0102, Short.MIN_VALUE, 0x3c00 };
        for (int size : SIZES) {
            for (short v : values) {
                ShortArray expected = new ShortArray(size);
                ShortArray actual = new ShortArray(size);
                for (int i = 0; i < size; i++) {
                    expected.set(i, v);
                }
                actual.init(v);
                assertSameBytes("ShortArray(" + size + ").init(" + v + ")", expected.getSegmentWithHeader(), actual.getSegmentWithHeader());
            }
        }
    }

    @Test
    public void testCharArray() {
        char[] values = { '\u0000', 'a', '￿', 'Ă', '耀' };
        for (int size : SIZES) {
            for (char v : values) {
                CharArray expected = new CharArray(size);
                CharArray actual = new CharArray(size);
                for (int i = 0; i < size; i++) {
                    expected.set(i, v);
                }
                actual.init(v);
                assertSameBytes("CharArray(" + size + ").init(" + (int) v + ")", expected.getSegmentWithHeader(), actual.getSegmentWithHeader());
            }
        }
    }

    @Test
    public void testByteAndInt8Arrays() {
        byte[] values = { 0, 1, -1, 0x7f, (byte) 0x80 };
        for (int size : SIZES) {
            for (byte v : values) {
                ByteArray expected = new ByteArray(size);
                ByteArray actual = new ByteArray(size);
                for (int i = 0; i < size; i++) {
                    expected.set(i, v);
                }
                actual.init(v);
                assertSameBytes("ByteArray(" + size + ").init(" + v + ")", expected.getSegmentWithHeader(), actual.getSegmentWithHeader());

                Int8Array expected8 = new Int8Array(size);
                Int8Array actual8 = new Int8Array(size);
                for (int i = 0; i < size; i++) {
                    expected8.set(i, v);
                }
                actual8.init(v);
                assertSameBytes("Int8Array(" + size + ").init(" + v + ")", expected8.getSegmentWithHeader(), actual8.getSegmentWithHeader());
            }
        }
    }

    @Test
    public void testHalfFloatArray() {
        float[] values = { 0f, -0f, 1f, -2f, 65504f, 6.1e-5f, 5.96e-8f, Float.NaN, Float.POSITIVE_INFINITY, 0.333f };
        short[] bitPatterns = { 0x0001, (short) 0x8000, 0x3c00, (short) 0x7e00, (short) 0xfbff };
        for (int size : SIZES) {
            for (float f : values) {
                assertHalfFloatInit(size, new HalfFloat(f), "init(" + f + ")");
            }
            for (short bits : bitPatterns) {
                assertHalfFloatInit(size, new HalfFloat(bits), "init(bits 0x" + Integer.toHexString(bits & 0xffff) + ")");
            }
        }
    }

    private static void assertHalfFloatInit(int size, HalfFloat v, String what) {
        HalfFloatArray expected = new HalfFloatArray(size);
        HalfFloatArray actual = new HalfFloatArray(size);
        for (int i = 0; i < size; i++) {
            expected.set(i, v);
        }
        actual.init(v);
        assertSameBytes("HalfFloatArray(" + size + ")." + what, expected.getSegmentWithHeader(), actual.getSegmentWithHeader());
    }

    @Test
    public void testBFloat16Array() {
        // BFloat16Array.init takes the raw bit pattern; the expected array is written at those bits directly
        short[] bitPatterns = { 0, (short) 0x8000, 0x3f80, (short) 0xbfc0, 0x7fc0, 0x0001, (short) 0xff80 };
        for (int size : SIZES) {
            for (short bits : bitPatterns) {
                BFloat16Array expected = new BFloat16Array(size);
                BFloat16Array actual = new BFloat16Array(size);
                MemorySegment expectedSegment = expected.getSegmentWithHeader();
                for (int i = 0; i < size; i++) {
                    expectedSegment.set(ValueLayout.JAVA_SHORT, TornadoNativeArray.ARRAY_HEADER + 2L * i, bits);
                }
                actual.init(bits);
                assertSameBytes("BFloat16Array(" + size + ").init(bits 0x" + Integer.toHexString(bits & 0xffff) + ")", expectedSegment, actual.getSegmentWithHeader());
            }
        }
    }

    @Test
    public void testReinitialiseToZero() {
        for (int size : SIZES) {
            FloatArray expected = new FloatArray(size);
            FloatArray actual = new FloatArray(size);
            for (int i = 0; i < size; i++) {
                expected.set(i, 0f);
            }
            actual.init(3f);
            actual.init(0f);
            assertSameBytes("FloatArray(" + size + ") init(3) then init(0)", expected.getSegmentWithHeader(), actual.getSegmentWithHeader());
        }
    }

    @Test
    public void testSliceLeavesTheRestOfTheArray() {
        for (int size : SIZES) {
            if (size < 8) {
                continue;
            }
            FloatArray expected = new FloatArray(size);
            FloatArray actual = new FloatArray(size);
            expected.init(7f);
            actual.init(7f);
            FloatArray expectedSlice = expected.slice(2, size - 4);
            for (int i = 0; i < expectedSlice.getSize(); i++) {
                expectedSlice.set(i, -1.25f);
            }
            actual.slice(2, size - 4).init(-1.25f);
            assertSameBytes("FloatArray(" + size + ") slice(2, " + (size - 4) + ").init(-1.25)", expected.getSegmentWithHeader(), actual.getSegmentWithHeader());
        }
    }
}
