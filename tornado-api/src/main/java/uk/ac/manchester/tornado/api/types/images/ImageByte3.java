/*
 * Copyright (c) 2013-2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.api.types.images;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.utils.ByteOps;
import uk.ac.manchester.tornado.api.types.utils.StorageFormats;
import uk.ac.manchester.tornado.api.types.vectors.Byte3;

public final class ImageByte3 implements TornadoImagesInterface<ByteBuffer> {

    public static final Class<ImageByte3> TYPE = ImageByte3.class;

    private static final int elementSize = 3;
    /**
     * backing array.
     */
    private final ByteArray storage;
    /**
     * Number of rows.
     */
    private final int Y;
    /**
     * Number of columns.
     */
    private final int X;
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

    private int getIndex(int x, int y) {
        return (x * elementSize) + (y * elementSize * X);
    }

    public Byte3 get(int x) {
        return get(x, 0);
    }

    public void set(int x, Byte3 value) {
        set(x, 0, value);
    }

    public Byte3 get(int x, int y) {
        final int offset = getIndex(x, y);
        return loadFromArray(storage, offset);
    }

    private Byte3 loadFromArray(final ByteArray array, int index) {
        final Byte3 result = new Byte3();
        result.setX(array.get(index));
        result.setY(array.get(index + 1));
        result.setZ(array.get(index + 2));
        return result;
    }

    public void set(int x, int y, Byte3 value) {
        final int offset = getIndex(x, y);
        storeToArray(value, storage, offset);
    }

    private void storeToArray(Byte3 value, ByteArray array, int index) {
        array.set(index, value.getX());
        array.set(index + 1, value.getY());
        array.set(index + 2, value.getZ());
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
        return ByteBuffer.wrap(storage.toHeapArray());
    }

    @Override
    public int size() {
        return numElements;
    }

    public void clear() {
        storage.clear();
    }

    @Override
    public long getNumBytes() {
        return storage.getNumBytesOfSegment();
    }

    @Override
    public long getNumBytesWithHeader() {
        return storage.getNumBytesOfSegmentWithHeader();
    }

    @Override
    public MemorySegment getSegment() {
        return getArray().getSegment();
    }

    @Override
    public MemorySegment getSegmentWithHeader() {
        return getArray().getSegmentWithHeader();
    }
}
