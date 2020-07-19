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

import java.nio.ByteBuffer;

public class ImageByte4 implements PrimitiveStorage<ByteBuffer> {

    /**
     * backing array
     */
    final protected byte[] storage;

    /**
     * number of elements in the storage
     */
    final private int numElements;
    final private static int elementSize = 4;

    /**
     * Number of rows
     */
    final protected int Y;

    /**
     * Number of columns
     */
    final protected int X;

    /**
     * Storage format for matrix
     *
     * @param width
     *            number of rows
     * @param height
     *            number of columns
     * @param array
     *            array reference which contains data
     */
    public ImageByte4(int width, int height, byte[] array) {
        storage = array;
        X = width;
        Y = height;
        numElements = X * Y * elementSize;
    }

    /**
     * Storage format for matrix
     *
     * @param width
     *            number of rows
     * @param height
     *            number of columns
     */
    public ImageByte4(int width, int height) {
        this(width, height, new byte[width * height * elementSize]);
    }

    public ImageByte4(byte[][] matrix) {
        this(matrix.length / elementSize, matrix[0].length / elementSize, StorageFormats.toRowMajor(matrix));
    }

    public byte[] getArray() {
        return storage;
    }

    private int toIndex(int x, int y) {
        return (x * elementSize) + (y * elementSize * X);
    }

    public Byte4 get(int x) {
        return get(x, 0);
    }

    public void set(int x, Byte4 value) {
        set(x, 0, value);
    }

    public Byte4 get(int x, int y) {
        final int offset = toIndex(x, y);
        return Byte4.loadFromArray(storage, offset);
    }

    public void set(int x, int y, Byte4 value) {
        final int offset = toIndex(x, y);
        value.storeToArray(storage, offset);
    }

    public int X() {
        return X;
    }

    public int Y() {
        return Y;
    }

    public void fill(byte value) {
        for (int i = 0; i < storage.length; i++) {
            storage[i] = value;
        }
    }

    public ImageByte4 duplicate() {
        final ImageByte4 matrix = new ImageByte4(X, Y);
        matrix.set(this);
        return matrix;
    }

    public void set(ImageByte4 m) {
        for (int i = 0; i < storage.length; i++) {
            storage[i] = m.storage[i];
        }
    }

    public Float4 mean() {
        Float4 result = new Float4();
        for (int row = 0; row < Y; row++) {
            for (int col = 0; col < X; col++) {
                final Byte4 value = get(col, row);
                final Float4 cast = new Float4(value.getX(), value.getY(), value.getZ(), value.getW());
                result = Float4.add(cast, result);

            }
        }
        return Float4.div(result, (X * Y));
    }

    public Float4 min() {
        Float4 result = new Float4(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);

        for (int row = 0; row < Y; row++) {
            for (int col = 0; col < X; col++) {
                final Byte4 value = get(col, row);
                final Float4 cast = new Float4(value.getX(), value.getY(), value.getZ(), value.getW());
                result = Float4.min(cast, result);
            }
        }

        return result;
    }

    public Float4 max() {
        Float4 result = new Float4(Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE);

        for (int row = 0; row < Y; row++) {
            for (int col = 0; col < X; col++) {
                final Byte4 value = get(col, row);
                final Float4 cast = new Float4(value.getX(), value.getY(), value.getZ(), value.getW());
                result = Float4.max(cast, result);
            }
        }

        return result;
    }

    public Float4 stdDev() {
        final Float4 mean = mean();
        Float4 varience = new Float4();
        for (int row = 0; row < Y; row++) {
            for (int col = 0; col < X; col++) {
                final Byte4 value = get(col, row);
                final Float4 cast = new Float4(value.getX(), value.getY(), value.getZ(), value.getW());

                Float4 v = Float4.sub(mean, cast);
                v = Float4.mult(v, v);
                v = Float4.div(v, (X * Y));
                varience = Float4.add(v, varience);

            }
        }
        return Float4.sqrt(varience);
    }

    public String summerise() {
        return String.format("ImageByte4<%dx%d>: min=%s, max=%s, mean=%s, sd=%s", X, Y, min(), max(), mean(), stdDev());
    }

    public String toString(String fmt) {
        String str = "";

        for (int i = 0; i < Y; i++) {
            for (int j = 0; j < X; j++) {
                str += get(j, i).toString(fmt) + " ";
            }
            str += "\n";
        }

        return str;
    }

    public String toString(String fmt, int width, int height) {
        String str = "";
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                str += get(j, i).toString(fmt) + " ";
            }
            str += "\n";
        }
        return str;
    }

    @Override
    public String toString() {
        String result = String.format("ImageByte4 <%d x %d>", X, Y);
        if (X <= 8 && Y <= 8) {
            result += "\n" + toString(ByteOps.fmt4);
        }
        return result;
    }

    @Override
    public void loadFromBuffer(ByteBuffer src) {
        asBuffer().put(src);
    }

    @Override
    public ByteBuffer asBuffer() {
        return ByteBuffer.wrap(storage);
    }

    @Override
    public int size() {
        return numElements;
    }
}
