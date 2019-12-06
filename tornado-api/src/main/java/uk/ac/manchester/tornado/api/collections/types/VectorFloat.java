/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2019, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * GNU Classpath is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 * 
 * GNU Classpath is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with GNU Classpath; see the file COPYING.  If not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library.  Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 * 
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under
 * terms of your choice, provided that you also meet, for each linked
 * independent module, the terms and conditions of the license of that
 * module.  An independent module is a module which is not derived from
 * or based on this library.  If you modify this library, you may extend
 * this exception to your version of the library, but you are not
 * obligated to do so.  If you do not wish to do so, delete this
 * exception statement from your version.
 *
 */
package uk.ac.manchester.tornado.api.collections.types;

import static java.lang.Float.MAX_VALUE;
import static java.lang.Float.MIN_VALUE;
import static java.lang.String.format;
import static java.nio.FloatBuffer.wrap;
import static java.util.Arrays.copyOf;
import static uk.ac.manchester.tornado.api.collections.types.FloatOps.fmt;

import java.nio.FloatBuffer;

import uk.ac.manchester.tornado.api.collections.math.TornadoMath;

public class VectorFloat implements PrimitiveStorage<FloatBuffer> {

    private final int numElements;
    private final float[] storage;
    private static final int elementSize = 1;

    /**
     * Creates a vector using the provided backing array
     * 
     * @param numElements
     * @param array
     */
    protected VectorFloat(int numElements, float[] array) {
        this.numElements = numElements;
        this.storage = array;
    }

    /**
     * Creates an empty vector with
     * 
     * @param numElements
     */
    public VectorFloat(int numElements) {
        this(numElements, new float[numElements]);
    }

    /**
     * Creates an new vector from the provided storage
     * 
     * @param storage
     */
    public VectorFloat(float[] storage) {
        this(storage.length / elementSize, storage);
    }

    /**
     * Returns the float at the given index of this vector
     * 
     * @param index
     * @return value
     */
    public float get(int index) {
        return storage[index];
    }

    /**
     * Sets the float at the given index of this vector
     * 
     * @param index
     * @param value
     */
    public void set(int index, float value) {
        storage[index] = value;
    }

    /**
     * Sets the elements of this vector to that of the provided vector
     * 
     * @param values
     */
    public void set(VectorFloat values) {
        for (int i = 0; i < values.storage.length; i++) {
            storage[i] = values.storage[i];
        }
    }

    /**
     * Sets the elements of this vector to that of the provided array
     * 
     * @param values
     */
    public void set(float[] values) {
        for (int i = 0; i < values.length; i++) {
            storage[i] = values[i];
        }
    }

    /**
     * Sets all elements to value
     * 
     * @param value
     */
    public void fill(float value) {
        for (int i = 0; i < storage.length; i++) {
            storage[i] = value;
        }
    }

    /**
     * Returns slice of this vector
     * 
     * @param start
     *            starting index
     * @param numElements
     *            number of elements
     * @return
     */
    public VectorFloat subVector(int start, int length) {
        final VectorFloat v = new VectorFloat(length);
        for (int i = 0; i < length; i++) {
            v.storage[i] = storage[i + start];
        }
        return v;
    }

    /**
     * Duplicates this vector
     * 
     * @return
     */
    public VectorFloat duplicate() {
        return new VectorFloat(copyOf(storage, storage.length));
    }

    public static float min(VectorFloat v) {
        float result = MAX_VALUE;
        for (int i = 0; i < v.storage.length; i++) {
            result = Math.min(v.storage[i], result);
        }
        return result;
    }

    public static float max(VectorFloat v) {
        float result = MIN_VALUE;
        for (int i = 0; i < v.storage.length; i++) {
            result = Math.max(v.storage[i], result);
        }
        return result;
    }

    /**
     * Vector equality test
     * 
     * @param vector
     * @return true if vectors match
     */
    public boolean isEqual(VectorFloat vector) {
        return TornadoMath.isEqual(storage, vector.storage);
    }

    /**
     * dot product (this . this)
     * 
     * @return
     */
    public static final float dot(VectorFloat a, VectorFloat b) {
        float sum = 0;
        for (int i = 0; i < a.size(); i++) {
            sum += a.get(i) * b.get(i);
        }
        return sum;
    }

    /**
     * Prints the vector using the specified format string
     * 
     * @param fmt
     * @return
     */
    public String toString(String fmt) {
        String str = "[ ";

        for (int i = 0; i < numElements; i++) {
            str += format(fmt, get(i)) + " ";
        }
        str += "]";
        return str;
    }

    public String toString() {
        String str = format("VectorFloat <%d>", numElements);
        if (numElements < 32) {
            str += toString(fmt);
        }
        return str;
    }

    @Override
    public void loadFromBuffer(FloatBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public FloatBuffer asBuffer() {
        return wrap(storage);
    }

    @Override
    public int size() {
        return numElements;
    }
}
