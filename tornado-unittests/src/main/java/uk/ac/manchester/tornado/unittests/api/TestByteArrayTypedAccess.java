/*
 * Copyright (c) 2025, APT Group, Department of Computer Science,
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
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * How to run?
 *
 * <p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.api.TestTypedAccess
 * </code>
 * </p>
 */
public class TestByteArrayTypedAccess extends TornadoTestBase {

    private void assertHalfFloatEquals(String message, float expected, HalfFloat actual, float tolerance) {
        if (Float.isNaN(expected)) {
            assertTrue(message + " - NaN not preserved", Float.isNaN(actual.getFloat32()));
        } else if (Float.isInfinite(expected)) {
            assertEquals(message + " - Infinite value not preserved", expected, actual.getFloat32(), 0.0f);
        } else {
            assertEquals(message, expected, actual.getFloat32(), tolerance);
        }
    }

    @Test
    public void testByteArrayHalfFloatOperations() {
        final int numHalfFloats = 5;
        final int byteArraySize = numHalfFloats * 2; // 2 bytes per half-float

        ByteArray byteArray = new ByteArray(byteArraySize);

        // Test setting half-float values at aligned byte indices
        HalfFloat[] testValues = {
                new HalfFloat(1.5f),
                new HalfFloat(2.25f),
                new HalfFloat(-3.75f),
                new HalfFloat(0.0f),
                new HalfFloat(Float.MAX_VALUE)
        };

        // Set values at 2-byte aligned indices
        for (int i = 0; i < numHalfFloats; i++) {
            int byteIndex = i * 2;
            byteArray.setHalfFloat(byteIndex, testValues[i]);
        }

        // Verify values can be retrieved correctly
        for (int i = 0; i < numHalfFloats; i++) {
            int byteIndex = i * 2;
            HalfFloat retrieved = byteArray.getHalfFloat(byteIndex);
            assertEquals("Half-float value mismatch at index " + byteIndex,
                    testValues[i].getFloat32(), retrieved.getFloat32(), 0.01f);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testByteArrayHalfFloatMisalignedSet() {
        ByteArray byteArray = new ByteArray(10);
        HalfFloat value = new HalfFloat(1.0f);

        // This should throw IllegalArgumentException due to misaligned byte index
        byteArray.setHalfFloat(1, value); // byte index 1 is not aligned to 2-byte boundary
    }

    @Test(expected = IllegalArgumentException.class)
    public void testByteArrayHalfFloatMisalignedGet() {
        ByteArray byteArray = new ByteArray(10);

        // This should throw IllegalArgumentException due to misaligned byte index
        byteArray.getHalfFloat(3); // byte index 3 is not aligned to 2-byte boundary
    }

    @Test
    public void testByteArrayHalfFloatBoundaryValues() {
        ByteArray byteArray = new ByteArray(8);

        // Test boundary half-float values
        HalfFloat zero = new HalfFloat(0.0f);
        HalfFloat negativeZero = new HalfFloat(-0.0f);
        HalfFloat one = new HalfFloat(1.0f);
        HalfFloat minusOne = new HalfFloat(-1.0f);

        byteArray.setHalfFloat(0, zero);
        byteArray.setHalfFloat(2, negativeZero);
        byteArray.setHalfFloat(4, one);
        byteArray.setHalfFloat(6, minusOne);

        assertEquals(0.0f, byteArray.getHalfFloat(0).getFloat32(), 0.001f);
        assertEquals(-0.0f, byteArray.getHalfFloat(2).getFloat32(), 0.001f);
        assertEquals(1.0f, byteArray.getHalfFloat(4).getFloat32(), 0.001f);
        assertEquals(-1.0f, byteArray.getHalfFloat(6).getFloat32(), 0.001f);
    }

    @Test
    public void testByteArrayHalfFloatSpecialValues() {
        ByteArray byteArray = new ByteArray(16); // 8 half-floats

        // Test boundary and special values
        HalfFloat[] specialValues = {
                new HalfFloat(0.0f),
                new HalfFloat(-0.0f),
                new HalfFloat(1.0f),
                new HalfFloat(-1.0f),
                new HalfFloat(Float.POSITIVE_INFINITY),
                new HalfFloat(Float.NEGATIVE_INFINITY),
                new HalfFloat(Float.NaN),
                new HalfFloat(65504.0f) // Max finite half-float value
        };

        // Set and verify special values
        for (int i = 0; i < specialValues.length; i++) {
            int byteIndex = i * 2;
            byteArray.setHalfFloat(byteIndex, specialValues[i]);

            HalfFloat retrieved = byteArray.getHalfFloat(byteIndex);
            assertHalfFloatEquals("Special value not preserved at index " + i,
                    specialValues[i].getFloat32(), retrieved, 0.001f);
        }
    }

    @Test
    public void testByteArrayHalfFloatSequentialAndOverwrite() {
        ByteArray byteArray = new ByteArray(8); // 4 half-floats

        // Test sequential access
        for (int i = 0; i < 4; i++) {
            HalfFloat value = new HalfFloat(i + 0.5f);
            byteArray.setHalfFloat(i * 2, value);
        }

        // Verify sequential values
        for (int i = 0; i < 4; i++) {
            HalfFloat retrieved = byteArray.getHalfFloat(i * 2);
            assertEquals("Sequential half-float mismatch at position " + i,
                    i + 0.5f, retrieved.getFloat32(), 0.001f);
        }

        // Test overwrite behavior
        HalfFloat overwriteValue = new HalfFloat(-99.75f);
        byteArray.setHalfFloat(4, overwriteValue); // Overwrite position 2

        HalfFloat retrieved = byteArray.getHalfFloat(4);
        assertEquals("Overwrite failed", -99.75f, retrieved.getFloat32(), 0.001f);

        // Verify other values unchanged
        assertEquals("Adjacent value corrupted", 0.5f, byteArray.getHalfFloat(0).getFloat32(), 0.001f);
        assertEquals("Adjacent value corrupted", 3.5f, byteArray.getHalfFloat(6).getFloat32(), 0.001f);
    }
}
