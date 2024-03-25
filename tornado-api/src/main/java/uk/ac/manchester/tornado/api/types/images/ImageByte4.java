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
import uk.ac.manchester.tornado.api.types.vectors.Byte4;
import uk.ac.manchester.tornado.api.types.vectors.Float4;

public final class ImageByte4 implements TornadoImagesInterface<ByteBuffer> {

    public static final Class<ImageByte4> TYPE = ImageByte4.class;

    private static final int elementSize = 4;
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
    public ImageByte4(int width, int height, ByteArray array) {
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
    public ImageByte4(int width, int height) {
        this(width, height, new ByteArray(width * height * elementSize));
    }

    public ImageByte4(byte[][] matrix) {
        this(matrix.length / elementSize, matrix[0].length / elementSize, StorageFormats.toRowMajor(matrix));
    }

    public ByteArray getArray() {
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
        return loadFromArray(storage, offset);
    }

    private Byte4 loadFromArray(final ByteArray array, int index) {
        final Byte4 result = new Byte4();
        result.setX(array.get(index));
        result.setY(array.get(index + 1));
        result.setZ(array.get(index + 2));
        result.setW(array.get(index + 3));
        return result;
    }

    public void set(int x, int y, Byte4 value) {
        final int offset = toIndex(x, y);
        storeToArray(value, storage, offset);
    }

    private void storeToArray(Byte4 value, ByteArray array, int index) {
        array.set(index, value.getX());
        array.set(index + 1, value.getY());
        array.set(index + 2, value.getZ());
        array.set(index + 3, value.getW());
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

    public ImageByte4 duplicate() {
        final ImageByte4 matrix = new ImageByte4(X, Y);
        matrix.set(this);
        return matrix;
    }

    public void set(ImageByte4 m) {
        for (int i = 0; i < storage.getSize(); i++) {
            storage.set(i, m.storage.get(i));
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

    public String summarise() {
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
            result += "\n" + toString(ByteOps.FMT_4);
        }
        return result;
    }

    @Override
    public void loadFromBuffer(ByteBuffer src) {
        asBuffer().put(src);
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
