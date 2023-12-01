/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2023, APT Group, Department of Computer Science,
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with GNU Classpath; see the file COPYING. If not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library. Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under
 * terms of your choice, provided that you also meet, for each linked
 * independent module, the terms and conditions of the license of that
 * module. An independent module is a module which is not derived from
 * or based on this library. If you modify this library, you may extend
 * this exception to your version of the library, but you are not
 * obligated to do so. If you do not wish to do so, delete this
 * exception statement from your version.
 *
 */
package uk.ac.manchester.tornado.api.types.collections;

import java.nio.FloatBuffer;

import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.common.PrimitiveStorage;
import uk.ac.manchester.tornado.api.types.vectors.Float16;

public class VectorFloat16 implements PrimitiveStorage<FloatBuffer> {

    public static final Class<VectorFloat16> TYPE = VectorFloat16.class;

    private static final int ELEMENT_SIZE = 16;
    protected final FloatArray storage;
    private final int numElements;

    protected VectorFloat16(int numElements, FloatArray array) {
        this.numElements = numElements;
        this.storage = array;
    }

    public VectorFloat16(FloatArray array) {
        this(array.getSize() / ELEMENT_SIZE, array);
    }

    public VectorFloat16(int numElements) {
        this(numElements, new FloatArray(numElements * ELEMENT_SIZE));
    }

    private int toIndex(int index) {
        return index * ELEMENT_SIZE;
    }

    public Float16 get(int index) {
        return loadFromArray(storage, toIndex(index));
    }

    private Float16 loadFromArray(FloatArray array, int index) {
        Float16 result = new Float16();
        for (int i = 0; i < ELEMENT_SIZE; ++i) {
            result.set(i, array.get(index + i));
        }
        return result;
    }

    public void set(int index, Float16 value) {
        storeToArray(value, storage, toIndex(index));
    }

    private void storeToArray(Float16 value, FloatArray array, int index) {
        for (int i = 0; i < ELEMENT_SIZE; ++i) {
            array.set(index + i, value.get(i));
        }
    }

    public void set(VectorFloat16 values) {
        for (int i = 0; i < numElements; ++i) {
            set(i, values.get(i));
        }
    }

    public void set(FloatArray values) {
        VectorFloat16 vector = new VectorFloat16(values);
        for (int i = 0; i < numElements; ++i) {
            set(i, vector.get(i));
        }
    }

    public void fill(float value) {
        for (int i = 0; i < storage.getSize(); ++i) {
            storage.set(i, value);
        }
    }

    public VectorFloat16 duplicate() {
        VectorFloat16 vector = new VectorFloat16(numElements);
        vector.set(this);
        return vector;
    }

    public String toString() {
        if (numElements > ELEMENT_SIZE) {
            return String.format("VectorFloat16 <%d>", numElements);
        }
        StringBuilder tempString = new StringBuilder();
        for (int i = 0; i < numElements; ++i) {
            tempString.append(" ").append(get(i).toString());
        }
        return tempString.toString();
    }

    public Float16 sum() {
        Float16 result = new Float16();
        for (int i = 0; i < numElements; ++i) {
            result = Float16.add(result, get(i));
        }
        return result;
    }

    public Float16 min() {
        Float16 result = new Float16();
        for (int i = 0; i < numElements; ++i) {
            result = Float16.min(result, get(i));
        }
        return result;
    }

    public Float16 max() {
        Float16 result = new Float16();
        for (int i = 0; i < numElements; ++i) {
            result = Float16.max(result, get(i));
        }
        return result;
    }

    public int vectorWidth() {
        return ELEMENT_SIZE;
    }

    @Override
    public void loadFromBuffer(FloatBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public FloatBuffer asBuffer() {
        return FloatBuffer.wrap(storage.toHeapArray());
    }

    @Override
    public int size() {
        return storage.getSize();
    }

    public int getLength() {
        return numElements;
    }

    public FloatArray getArray() {
        return storage;
    }

    public void clear() {
        storage.clear();
    }
}
