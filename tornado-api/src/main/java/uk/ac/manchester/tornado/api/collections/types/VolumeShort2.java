/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.api.collections.types;

import static java.lang.String.format;
import static java.nio.ShortBuffer.wrap;
import static uk.ac.manchester.tornado.api.collections.types.Short2.loadFromArray;

import java.nio.ShortBuffer;

public class VolumeShort2 implements PrimitiveStorage<ShortBuffer> {

    /**
     * backing array
     */
    final protected short[] storage;

    /**
     * number of elements in the storage
     */
    final private int numElements;
    final private static int elementSize = 2;

    /**
     * Size in Y dimension
     */
    final protected int Y;

    /**
     * Size in X dimension
     */
    final protected int X;

    /**
     * Size in Y dimension
     */
    final protected int Z;

    public VolumeShort2(int width, int height, int depth, short[] array) {
        storage = array;
        X = width;
        Y = height;
        Z = depth;
        numElements = X * Y * Z * elementSize;
    }

    /**
     * Storage format for matrix
     * 
     * @param height
     *            number of columns
     * @param width
     *            number of rows
     */
    public VolumeShort2(int width, int height, int depth) {
        this(width, height, depth, new short[width * height * depth * elementSize]);
    }

    private final int toIndex(int x, int y, int z) {
        return (z * X * Y * elementSize) + (y * elementSize * X) + (x * elementSize);
    }

    public Short2 get(int x, int y, int z) {
        final int index = toIndex(x, y, z);
        return loadFromArray(storage, index);
    }

    public void set(int x, int y, int z, Short2 value) {
        final int index = toIndex(x, y, z);
        value.storeToArray(storage, index);
    }

    public int Y() {
        return Y;
    }

    public int X() {
        return X;
    }

    public int Z() {
        return Z;
    }

    public void fill(short value) {
        for (int i = 0; i < storage.length; i++)
            storage[i] = value;
    }

    public VolumeShort2 duplicate() {
        final VolumeShort2 volume = new VolumeShort2(X, Y, Z);
        volume.set(this);
        return volume;
    }

    public void set(VolumeShort2 other) {
        for (int i = 0; i < storage.length; i++)
            storage[i] = other.storage[i];
    }

    public String toString(String fmt) {
        String str = "";

        for (int z = 0; z < Z(); z++) {
            str += format("z = %d\n", z);
            for (int y = 0; y < Y(); y++) {
                for (int x = 0; x < X(); x++) {
                    final Short2 point = get(x, y, z);
                    str += format(fmt, point.getX(), point.getY()) + " ";
                }
                str += "\n";
            }
        }

        return str;
    }

    public String toString() {
        String result = format("VolumeShort2 <%d x %d x %d>", Y, X, Z);
        return result;
    }

    @Override
    public void loadFromBuffer(ShortBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public ShortBuffer asBuffer() {
        return wrap(storage);
    }

    @Override
    public int size() {
        return numElements;
    }

}
