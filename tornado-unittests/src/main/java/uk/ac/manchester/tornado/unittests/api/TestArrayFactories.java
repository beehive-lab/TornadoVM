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
 */
package uk.ac.manchester.tornado.unittests.api;

import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.CharArray;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.Int8Array;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Copies between Java arrays and native arrays ({@code fromArray}, {@code fromElements},
 * {@code toHeapArray}) keep every element, for every primitive array type. Each direction is
 * checked against the element-wise accessors ({@code get}, {@code set}), and the header that
 * kernels read the length from is checked too.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.api.TestArrayFactories
 * </code>
 */
public class TestArrayFactories extends TornadoTestBase {

    // empty, then odd lengths, so a copy that rounds to whole words or vectors would show
    private static final int[] SIZES = { 0, 1, 7, 1023, 100_003 };

    /** The length in the array's header, which kernels read, and the Java-side length. */
    private static void assertLength(int n, TornadoNativeArray array) {
        assertEquals(n, array.getSize());
        assertEquals(n, array.getSegmentWithHeader().get(JAVA_LONG, 0));
    }

    @Test
    public void testFloatArray() {
        for (int n : SIZES) {
            float[] values = new float[n];
            for (int i = 0; i < n; i++) {
                values[i] = i * 0.5f - 3.25f;
            }
            FloatArray copied = FloatArray.fromArray(values);
            assertLength(n, copied);
            for (int i = 0; i < n; i++) {
                assertEquals(values[i], copied.get(i));
            }
            FloatArray written = new FloatArray(n);
            for (int i = 0; i < n; i++) {
                written.set(i, values[i]);
            }
            assertArrayEquals(values, written.toHeapArray());
            assertArrayEquals(values, copied.toHeapArray());
        }
        assertArrayEquals(new float[] { 1.5f, -2f }, FloatArray.fromElements(1.5f, -2f).toHeapArray());
    }

    @Test
    public void testDoubleArray() {
        for (int n : SIZES) {
            double[] values = new double[n];
            for (int i = 0; i < n; i++) {
                values[i] = i * 0.25 - 7.5;
            }
            DoubleArray copied = DoubleArray.fromArray(values);
            assertLength(n, copied);
            for (int i = 0; i < n; i++) {
                assertEquals(values[i], copied.get(i));
            }
            DoubleArray written = new DoubleArray(n);
            for (int i = 0; i < n; i++) {
                written.set(i, values[i]);
            }
            assertArrayEquals(values, written.toHeapArray());
            assertArrayEquals(values, copied.toHeapArray());
        }
        assertArrayEquals(new double[] { 1.5, -2.0 }, DoubleArray.fromElements(1.5, -2.0).toHeapArray());
    }

    @Test
    public void testIntArray() {
        for (int n : SIZES) {
            int[] values = new int[n];
            for (int i = 0; i < n; i++) {
                values[i] = i * 31 - 1000;
            }
            IntArray copied = IntArray.fromArray(values);
            assertLength(n, copied);
            for (int i = 0; i < n; i++) {
                assertEquals(values[i], copied.get(i));
            }
            IntArray written = new IntArray(n);
            for (int i = 0; i < n; i++) {
                written.set(i, values[i]);
            }
            assertArrayEquals(values, written.toHeapArray());
            assertArrayEquals(values, copied.toHeapArray());
        }
        assertArrayEquals(new int[] { 3, -4 }, IntArray.fromElements(3, -4).toHeapArray());
    }

    @Test
    public void testLongArray() {
        for (int n : SIZES) {
            long[] values = new long[n];
            for (int i = 0; i < n; i++) {
                values[i] = i * 3_000_000_019L - 5;
            }
            LongArray copied = LongArray.fromArray(values);
            assertLength(n, copied);
            for (int i = 0; i < n; i++) {
                assertEquals(values[i], copied.get(i));
            }
            LongArray written = new LongArray(n);
            for (int i = 0; i < n; i++) {
                written.set(i, values[i]);
            }
            assertArrayEquals(values, written.toHeapArray());
            assertArrayEquals(values, copied.toHeapArray());
        }
        assertArrayEquals(new long[] { 3L, -4L }, LongArray.fromElements(3L, -4L).toHeapArray());
    }

    @Test
    public void testShortArray() {
        for (int n : SIZES) {
            short[] values = new short[n];
            for (int i = 0; i < n; i++) {
                values[i] = (short) (i * 7 - 300);
            }
            ShortArray copied = ShortArray.fromArray(values);
            assertLength(n, copied);
            for (int i = 0; i < n; i++) {
                assertEquals(values[i], copied.get(i));
            }
            ShortArray written = new ShortArray(n);
            for (int i = 0; i < n; i++) {
                written.set(i, values[i]);
            }
            assertArrayEquals(values, written.toHeapArray());
            assertArrayEquals(values, copied.toHeapArray());
        }
        assertArrayEquals(new short[] { (short) 3, (short) -4 }, ShortArray.fromElements((short) 3, (short) -4).toHeapArray());
    }

    @Test
    public void testCharArray() {
        for (int n : SIZES) {
            char[] values = new char[n];
            for (int i = 0; i < n; i++) {
                values[i] = (char) ('a' + i % 26);
            }
            CharArray copied = CharArray.fromArray(values);
            assertLength(n, copied);
            for (int i = 0; i < n; i++) {
                assertEquals(values[i], copied.get(i));
            }
            CharArray written = new CharArray(n);
            for (int i = 0; i < n; i++) {
                written.set(i, values[i]);
            }
            assertArrayEquals(values, written.toHeapArray());
            assertArrayEquals(values, copied.toHeapArray());
        }
        assertArrayEquals(new char[] { 'x', 'y' }, CharArray.fromElements('x', 'y').toHeapArray());
    }

    @Test
    public void testByteArray() {
        for (int n : SIZES) {
            byte[] values = new byte[n];
            for (int i = 0; i < n; i++) {
                values[i] = (byte) (i * 13 - 100);
            }
            ByteArray copied = ByteArray.fromArray(values);
            assertLength(n, copied);
            for (int i = 0; i < n; i++) {
                assertEquals(values[i], copied.get(i));
            }
            ByteArray written = new ByteArray(n);
            for (int i = 0; i < n; i++) {
                written.set(i, values[i]);
            }
            assertArrayEquals(values, written.toHeapArray());
            assertArrayEquals(values, copied.toHeapArray());
        }
        assertArrayEquals(new byte[] { (byte) 3, (byte) -4 }, ByteArray.fromElements((byte) 3, (byte) -4).toHeapArray());
    }

    @Test
    public void testInt8Array() {
        for (int n : SIZES) {
            byte[] values = new byte[n];
            for (int i = 0; i < n; i++) {
                values[i] = (byte) (i * 11 - 50);
            }
            Int8Array copied = Int8Array.fromArray(values);
            assertLength(n, copied);
            for (int i = 0; i < n; i++) {
                assertEquals(values[i], copied.get(i));
            }
            Int8Array written = new Int8Array(n);
            for (int i = 0; i < n; i++) {
                written.set(i, values[i]);
            }
            assertArrayEquals(values, written.toHeapArray());
            assertArrayEquals(values, copied.toHeapArray());
        }
        assertArrayEquals(new byte[] { (byte) 3, (byte) -4 }, Int8Array.fromElements((byte) 3, (byte) -4).toHeapArray());
    }
}
