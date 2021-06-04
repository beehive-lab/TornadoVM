/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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

import java.nio.FloatBuffer;

public class Matrix3DFloat implements PrimitiveStorage<FloatBuffer> {
    /**
     * backing array
     */
    final protected float[] storage;

    /**
     * number of elements in the storage
     */
    final private int numElements;

    /**
     * Number of rows
     */
    final protected int X;

    /**
     * Number of columns
     */
    final protected int Y;

    /**
     * Depth
     */
    final protected int Z;

    /**
     * Storage format for matrix
     * 
     * @param width
     *            number of columns
     * @param height
     *            number of rows
     * @param array
     *            array reference which contains data
     */
    public Matrix3DFloat(int width, int height, int depth, float[] array) {
        storage = array;
        X = width;
        Y = height;
        Z = depth;
        numElements = width * height * depth;
    }

    /**
     * Storage format for matrix
     * 
     * @param width
     *            number of columns
     * @param height
     *            number of rows
     */
    public Matrix3DFloat(int width, int height, int depth) {
        this(width, height, depth, new float[width * height * depth]);
    }

    public Matrix3DFloat(float[][][] matrix) {
        this(matrix.length, matrix[0].length, matrix[0][0].length, StorageFormats.toRowMajor3D(matrix));
    }

    public float[] getFlattenedArray() {
        return storage;
    }

    public float get(int i, int j, int k) {
        return storage[StorageFormats.toRowMajor3D(i, j, k, Z, Y)];
    }

    public void set(int i, int j, int k, float value) {
        storage[StorageFormats.toRowMajor3D(i, j, k, Z, Y)] = value;
    }

    public int X() {
        return X;
    }

    public int Y() {
        return Y;
    }

    public int Z() {
        return Z;
    }

    public void fill(float value) {
        for (int i = 0; i < storage.length; i++) {
            storage[i] = value;
        }
    }

    public Matrix3DFloat duplicate() {
        Matrix3DFloat matrix = new Matrix3DFloat(X, Y, Z);
        matrix.set(this);
        return matrix;
    }

    public void set(Matrix3DFloat m) {
        for (int i = 0; i < m.storage.length; i++) {
            storage[i] = m.storage[i];
        }
    }

    public String toString(String fmt) {
        String str = "";
        for (int i = 0; i < X; i++) {
            for (int j = 0; j < Y; j++) {
                for (int k = 0; k < Z; k++) {
                    str += String.format(fmt, get(i, j, k)) + " ";
                }
            }
            str += "\n";
        }
        str.trim();
        return str;
    }

    @Override
    public String toString() {
        String result = String.format("Matrix3DFloat <%d x %d x %d>", X, Y, Z);
        if (X < 16 && Y < 16 && Z < 16) {
            result += "\n" + toString(FloatOps.FMT);
        }
        return result;
    }

    public static void scale(Matrix3DFloat matrix, float value) {
        for (int i = 0; i < matrix.storage.length; i++) {
            matrix.storage[i] *= value;
        }
    }

    @Override
    public void loadFromBuffer(FloatBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public FloatBuffer asBuffer() {
        return FloatBuffer.wrap(storage);
    }

    @Override
    public int size() {
        return numElements;
    }
}
