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
package uk.ac.manchester.tornado.api.types.volumes;

import java.lang.foreign.MemorySegment;
import java.nio.ShortBuffer;

import uk.ac.manchester.tornado.api.types.arrays.ShortArray;
import uk.ac.manchester.tornado.api.types.vectors.Short2;

public final class VolumeShort2 implements TornadoVolumesInterface<ShortBuffer> {

    public static final Class<VolumeShort2> TYPE = VolumeShort2.class;

    private static final int ELEMENT_SIZE = 2;
    /**
     * backing array.
     */
    private final ShortArray storage;
    /**
     * Size in Y dimension.
     */
    private final int Y;
    /**
     * Size in X dimension.
     */
    private final int X;
    /**
     * Size in Y dimension.
     */
    private final int Z;
    /**
     * number of elements in the storage.
     */
    private final int numElements;

    public VolumeShort2(int width, int height, int depth, ShortArray array) {
        storage = array;
        X = width;
        Y = height;
        Z = depth;
        numElements = X * Y * Z * ELEMENT_SIZE;
    }

    /**
     * Storage format for matrix.
     *
     * @param width
     *     number of columns
     * @param depth
     *     number of rows
     */
    public VolumeShort2(int width, int height, int depth) {
        this(width, height, depth, new ShortArray(width * height * depth * ELEMENT_SIZE));
    }

    public ShortArray getArray() {
        return storage;
    }

    private int getIndex(int x, int y, int z) {
        return (z * X * Y * ELEMENT_SIZE) + (y * ELEMENT_SIZE * X) + (x * ELEMENT_SIZE);
    }

    public Short2 get(int x, int y, int z) {
        final int index = getIndex(x, y, z);
        return loadFromArray(storage, index);
    }

    private Short2 loadFromArray(final ShortArray array, int index) {
        final Short2 result = new Short2();
        result.setX(array.get(index));
        result.setY(array.get(index + 1));
        return result;
    }

    public void set(int x, int y, int z, Short2 value) {
        final int index = getIndex(x, y, z);
        storeToArray(value, storage, index);
    }

    private void storeToArray(Short2 value, ShortArray array, int index) {
        array.set(index, value.getX());
        array.set(index + 1, value.getY());
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
        storage.init(value);
    }

    public VolumeShort2 duplicate() {
        final VolumeShort2 volume = new VolumeShort2(X, Y, Z);
        volume.set(this);
        return volume;
    }

    public void set(VolumeShort2 other) {
        for (int i = 0; i < storage.getSize(); i++) {
            storage.set(i, other.storage.get(i));
        }
    }

    public String toString(String fmt) {
        StringBuilder str = new StringBuilder("");
        for (int z = 0; z < Z(); z++) {
            str.append(String.format("z = %d%n", z));
            for (int y = 0; y < Y(); y++) {
                for (int x = 0; x < X(); x++) {
                    final Short2 point = get(x, y, z);
                    str.append(String.format(fmt, point.getX(), point.getY()) + " ");
                }
                str.append("\n");
            }
        }
        return str.toString();
    }

    public String toString() {
        return String.format("VolumeShort2 <%d x %d x %d>", Y, X, Z);
    }

    @Override
    public void loadFromBuffer(ShortBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public ShortBuffer asBuffer() {
        return ShortBuffer.wrap(storage.toHeapArray());
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
