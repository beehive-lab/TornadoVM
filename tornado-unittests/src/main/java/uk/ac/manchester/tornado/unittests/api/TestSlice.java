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
package uk.ac.manchester.tornado.unittests.api;

import org.junit.Test;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.CharArray;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

import static org.junit.Assert.assertEquals;

/**
 * How to run?
 *
 * <p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.api.TestSlice
 * </code>
 * </p>
 */
public class TestSlice extends TornadoTestBase {
    private final int numElements = 256;

    @Test
    public void testFloatArraySlice() {

        FloatArray a = new FloatArray(numElements);
        FloatArray b = new FloatArray(numElements);
        FloatArray e = new FloatArray(numElements);

        a.init(100.0f);
        b.init(5.0f);
        e.init(12f);

        FloatArray c = FloatArray.concat(a, b, e);

        FloatArray slice = c.slice(256, numElements);

        for (int i = 0; i < slice.getSize(); i++) {
            assertEquals("Mismatch in second part of slice", 5.0f, slice.get(i), 0.0f);
        }

    }

    @Test
    public void testDoubleArraySlice() {

        DoubleArray a = new DoubleArray(numElements);
        DoubleArray b = new DoubleArray(numElements);
        DoubleArray e = new DoubleArray(numElements);

        a.init(100.0d);
        b.init(5d);
        e.init(12d);

        DoubleArray c = DoubleArray.concat(a, b, e);

        DoubleArray slice = c.slice(256, numElements);

        for (int i = 0; i < slice.getSize(); i++) {
            assertEquals("Mismatch in second part of slice", 5.0d, slice.get(i), 0.0f);
        }
    }

    @Test
    public void testByteArraySlice() {

        ByteArray a = new ByteArray(numElements);
        ByteArray b = new ByteArray(numElements);
        ByteArray e = new ByteArray(numElements);

        a.init((byte) 100);
        b.init((byte) 5);
        e.init((byte) 12);

        ByteArray c = ByteArray.concat(a, b, e);

        ByteArray slice = c.slice(256, numElements);

        for (int i = 0; i < slice.getSize(); i++) {
            assertEquals("Mismatch in second part of slice", 5.0, slice.get(i), 0.0f);
        }
    }

    @Test
    public void testLongArraySlice() {

        LongArray a = new LongArray(numElements);
        LongArray b = new LongArray(numElements);
        LongArray e = new LongArray(numElements);

        a.init(100L);
        b.init(5L);
        e.init(12L);

        LongArray c = LongArray.concat(a, b, e);
        LongArray slice = c.slice(256, numElements);

        for (int i = 0; i < slice.getSize(); i++) {
            assertEquals("Mismatch in second part of slice", 5.0, slice.get(i), 0.0f);
        }
    }

    @Test
    public void testIntArraySlice() {

        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);
        IntArray e = new IntArray(numElements);

        a.init(100);
        b.init(5);
        e.init(12);

        IntArray c = IntArray.concat(a, b, e);

        IntArray slice = c.slice(256, numElements);

        for (int i = 0; i < slice.getSize(); i++) {
            assertEquals("Mismatch in second part of slice", 5.0d, slice.get(i), 0.0f);
        }
    }

    @Test
    public void testShortArraySlice() {

        ShortArray a = new ShortArray(numElements);
        ShortArray b = new ShortArray(numElements);
        ShortArray e = new ShortArray(numElements);

        a.init((short) 100);
        b.init((short) 5);
        e.init((short) 12);

        ShortArray c = ShortArray.concat(a, b, e);

        ShortArray slice = c.slice(256, numElements);

        for (int i = 0; i < slice.getSize(); i++) {
            assertEquals("Mismatch in second part of slice", 5.0, slice.get(i), 0.0f);
        }
    }

    @Test
    public void testCharArraySlice() {

        CharArray a = new CharArray(numElements);
        CharArray b = new CharArray(numElements);
        CharArray e = new CharArray(numElements);

        a.init((char) 100);
        b.init((char) 5);
        e.init((char) 12);

        CharArray c = CharArray.concat(a, b, e);
        CharArray slice = c.slice(256, numElements);

        for (int i = 0; i < slice.getSize(); i++) {
            assertEquals("Mismatch in second part of slice", 5.0d, slice.get(i), 0.0f);
        }
    }

    @Test
    public void testHalfFloatArraySlice() {

        HalfFloatArray a = new HalfFloatArray(numElements);
        HalfFloatArray b = new HalfFloatArray(numElements);
        HalfFloatArray e = new HalfFloatArray(numElements);

        a.init(new HalfFloat(100));
        b.init(new HalfFloat(5));
        e.init(new HalfFloat(12));

        HalfFloatArray c = HalfFloatArray.concat(a, b, e);

        HalfFloatArray slice = c.slice(256, numElements);

        for (int i = 0; i < slice.getSize(); i++) {
            assertEquals("Mismatch in second part of slice", 5.0d, slice.get(i).getFloat32(), 0.0f);
        }

    }

}
