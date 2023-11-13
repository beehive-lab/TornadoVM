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
package uk.ac.manchester.tornado.api.types.images;

import java.nio.ByteBuffer;

import uk.ac.manchester.tornado.api.types.Byte3;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.common.PrimitiveStorage;
import uk.ac.manchester.tornado.api.types.utils.ByteOps;
import uk.ac.manchester.tornado.api.types.utils.StorageFormats;

public class ImageByte3 implements PrimitiveStorage<ByteBuffer> {

    private static final int elementSize = 3;
    /**
     * backing array.
     */
    protected final ByteArray storage;
    /**
     * Number of rows.
     */
    protected final int Y;
    /**
     * Number of columns.
     */
    protected final int X;
    /**
     * number of elements in the storage.
     */
    private final int numElements;

    /**
     * Storage format for matrix.
     *
     * @param width
     *     number of rows
     * @param height
     *     number of columns
     * @param array
     *     array reference which contains data
     */
    public ImageByte3(int width, int height, ByteArray array) {
        storage = array;
        X = width;
        Y = height;
        numElements = X * Y * elementSize;
    }

    /**
     * Storage format for matrix.
     *
     * @param width
     *     number of rows
     * @param height
     *     number of columns
     */
    public ImageByte3(int width, int height) {
        this(width, height, new ByteArray(width * height * elementSize));
    }

    public ImageByte3(byte[][] matrix) {
        this(matrix.length / elementSize, matrix[0].length / elementSize, StorageFormats.toRowMajor(matrix));
    }

    public ByteArray getArray() {
        return storage;
    }

    private int toIndex(int x, int y) {
        return (x * elementSize) + (y * elementSize * X);
    }

    public Byte3 get(int x) {
        return get(x, 0);
    }

    public void set(int x, Byte3 value) {
        set(x, 0, value);
    }

    public Byte3 get(int x, int y) {
        final int offset = toIndex(x, y);
        return Byte3.loadFromArray(storage, offset);
    }

    public void set(int x, int y, Byte3 value) {
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
        storage.init(value);
    }

    public ImageByte3 duplicate() {
        final ImageByte3 image = new ImageByte3(X, Y);
        image.set(this);
        return image;
    }

    public void set(ImageByte3 m) {
        for (int i = 0; i < storage.getSize(); i++) {
            storage.set(i, m.storage.get(i));
        }
    }

    public String toString(String fmt) {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < Y; i++) {
            for (int j = 0; j < X; j++) {
                str.append(get(j, i).toString(fmt)).append("\n");
            }
        }
        return str.toString();
    }

    public String toString() {
        String result = String.format("ImageByte3 <%d x %d>", X, Y);
        if (X <= 8 && Y <= 8) {
            result += "\n" + toString(ByteOps.FMT_3);
        }
        return result;
    }

    @Override
    public void loadFromBuffer(ByteBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public ByteBuffer asBuffer() {
        return storage.getSegment().asByteBuffer();
    }

    @Override
    public int size() {
        return numElements;
    }

    public void clear() {
        storage.clear();
    }

}
