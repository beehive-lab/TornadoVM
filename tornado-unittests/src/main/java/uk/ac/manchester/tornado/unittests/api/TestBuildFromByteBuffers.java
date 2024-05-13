
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

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;

import org.junit.Test;

import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.CharArray;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * How to run?
 *
 * <p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.api.TestBuildFromByteBuffers
 * </code>
 * </p>
 */
public class TestBuildFromByteBuffers extends TornadoTestBase {
    final int SIZE = 10;

    @Test
    public void testBuildFromFloatBuffer() {
        FloatBuffer buffer = FloatBuffer.allocate(SIZE);
        buffer.put(new float[] { 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f });
        buffer.flip();

        FloatArray floatArray = FloatArray.fromFloatBuffer(buffer);

        for (int i = 0; i < SIZE; i++) {
            assertEquals(buffer.get(i), floatArray.get(i), 0.0f);

        }
    }

    @Test
    public void testBuildFromDoubleBuffer() {
        DoubleBuffer buffer = DoubleBuffer.allocate(SIZE);
        buffer.put(new double[] { 1.0, 2.0, 3.0, 4.0, 5.0, 1.0, 2.0, 3.0, 4.0, 5.0 });
        buffer.flip();

        DoubleArray doubleArray = DoubleArray.fromDoubleBuffer(buffer);

        for (int i = 0; i < SIZE; i++) {
            assertEquals(buffer.get(i), doubleArray.get(i), 0.0);
        }
    }

    @Test
    public void testBuildFromIntBuffer() {
        IntBuffer buffer = IntBuffer.allocate(SIZE);
        buffer.put(new int[] { 1, 2, 3, 4, 5, 1, 2, 3, 4, 5 });
        buffer.flip();

        IntArray intArray = IntArray.fromIntBuffer(buffer);

        for (int i = 0; i < SIZE; i++) {
            assertEquals(buffer.get(i), intArray.get(i));
        }
    }

    @Test
    public void testBuildFromLongBuffer() {
        LongBuffer buffer = LongBuffer.allocate(SIZE);
        buffer.put(new long[] { 1L, 2L, 3L, 4L, 5L, 1L, 2L, 3L, 4L, 5L });
        buffer.flip();

        LongArray longArray = LongArray.fromLongBuffer(buffer);

        for (int i = 0; i < SIZE; i++) {
            assertEquals(buffer.get(i), longArray.get(i));
        }
    }

    @Test
    public void testBuildFromShortBuffer() {
        ShortBuffer buffer = ShortBuffer.allocate(SIZE);
        buffer.put(new short[] { 1, 2, 3, 4, 5, 1, 2, 3, 4, 5 });
        buffer.flip();

        ShortArray shortArray = ShortArray.fromShortBuffer(buffer);

        for (int i = 0; i < SIZE; i++) {
            assertEquals(buffer.get(i), shortArray.get(i));
        }
    }

    @Test
    public void testBuildFromCharBuffer() {
        CharBuffer buffer = CharBuffer.allocate(SIZE);
        buffer.put(new char[] { 'a', 'b', 'c', 'd', 'e', 'a', 'b', 'c', 'd', 'e' });
        buffer.flip();

        CharArray charArray = CharArray.fromCharBuffer(buffer);

        for (int i = 0; i < SIZE; i++) {
            assertEquals(buffer.get(i), charArray.get(i));
        }
    }

    @Test
    public void testBuildFromByteBuffer() {
        ByteBuffer buffer = ByteBuffer.allocate(SIZE);
        buffer.put(new byte[] { 1, 2, 3, 4, 5, 1, 2, 3, 4, 5 });
        buffer.flip();

        ByteArray byteArray = ByteArray.fromByteBuffer(buffer);

        for (int i = 0; i < SIZE; i++) {
            assertEquals(buffer.get(i), byteArray.get(i));
        }
    }

}
