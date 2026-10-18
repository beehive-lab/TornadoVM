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
package uk.ac.manchester.tornado.unittests.api;

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
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * The native arrays made from Java arrays ({@code fromArray}, {@code fromElements}) hold the same
 * values, element for element, for every primitive array type.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.api.TestArrayFactories
 * </code>
 */
public class TestArrayFactories extends TornadoTestBase {

    // odd lengths, so a copy that rounds to whole words or vectors would show
    private static final int[] SIZES = { 1, 7, 1023, 100_003 };

    @Test
    public void testFloatArray() {
        for (int n : SIZES) {
            float[] values = new float[n];
            for (int i = 0; i < n; i++) {
                values[i] = i * 0.5f - 3.25f;
            }
            assertArrayEquals(values, FloatArray.fromArray(values).toHeapArray());
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
            assertArrayEquals(values, DoubleArray.fromArray(values).toHeapArray());
        }
    }

    @Test
    public void testIntArray() {
        for (int n : SIZES) {
            int[] values = new int[n];
            for (int i = 0; i < n; i++) {
                values[i] = i * 31 - 1000;
            }
            assertArrayEquals(values, IntArray.fromArray(values).toHeapArray());
        }
    }

    @Test
    public void testLongArray() {
        for (int n : SIZES) {
            long[] values = new long[n];
            for (int i = 0; i < n; i++) {
                values[i] = i * 3_000_000_019L - 5;
            }
            assertArrayEquals(values, LongArray.fromArray(values).toHeapArray());
        }
    }

    @Test
    public void testShortArray() {
        for (int n : SIZES) {
            short[] values = new short[n];
            for (int i = 0; i < n; i++) {
                values[i] = (short) (i * 7 - 300);
            }
            assertArrayEquals(values, ShortArray.fromArray(values).toHeapArray());
        }
    }

    @Test
    public void testCharArray() {
        for (int n : SIZES) {
            char[] values = new char[n];
            for (int i = 0; i < n; i++) {
                values[i] = (char) ('a' + i % 26);
            }
            assertArrayEquals(values, CharArray.fromArray(values).toHeapArray());
        }
    }

    @Test
    public void testByteArray() {
        for (int n : SIZES) {
            byte[] values = new byte[n];
            for (int i = 0; i < n; i++) {
                values[i] = (byte) (i * 13 - 100);
            }
            assertArrayEquals(values, ByteArray.fromArray(values).toHeapArray());
        }
    }

    @Test
    public void testInt8Array() {
        for (int n : SIZES) {
            byte[] values = new byte[n];
            for (int i = 0; i < n; i++) {
                values[i] = (byte) (i * 11 - 50);
            }
            Int8Array array = Int8Array.fromArray(values);
            assertEquals(n, array.getSize());
            for (int i = 0; i < n; i++) {
                assertEquals(values[i], array.get(i));
            }
        }
    }
}
