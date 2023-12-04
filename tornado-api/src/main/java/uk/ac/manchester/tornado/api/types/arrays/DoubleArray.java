/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.api.types.arrays;

import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import uk.ac.manchester.tornado.api.internal.annotations.SegmentElementSize;

@SegmentElementSize(size = 8)
public final class DoubleArray extends TornadoNativeArray {
    private static final int DOUBLE_BYTES = 8;
    private MemorySegment segment;
    private int numberOfElements;

    private int arrayHeaderSize;

    private int baseIndex;

    private long segmentByteSize;

    public DoubleArray(int numberOfElements) {
        this.numberOfElements = numberOfElements;
        arrayHeaderSize = (int) TornadoNativeArray.ARRAY_HEADER;
        assert arrayHeaderSize >= 8;
        baseIndex = arrayHeaderSize / DOUBLE_BYTES;
        segmentByteSize = numberOfElements * DOUBLE_BYTES + arrayHeaderSize;

        segment = Arena.ofAuto().allocate(segmentByteSize, 1);
        segment.setAtIndex(JAVA_INT, 0, numberOfElements);
    }

    private static DoubleArray createSegment(double[] values) {
        DoubleArray array = new DoubleArray(values.length);
        for (int i = 0; i < values.length; i++) {
            array.set(i, values[i]);
        }
        return array;
    }

    public static DoubleArray fromArray(double[] values) {
        return createSegment(values);
    }

    public static DoubleArray fromElements(double... values) {
        return createSegment(values);
    }

    public static DoubleArray fromSegment(MemorySegment segment) {
        long byteSize = segment.byteSize();
        int numElements = (int) (byteSize / DOUBLE_BYTES);
        DoubleArray doubleArray = new DoubleArray(numElements);
        MemorySegment.copy(segment, 0, doubleArray.segment, doubleArray.baseIndex * DOUBLE_BYTES, byteSize);
        return doubleArray;
    }

    public double[] toHeapArray() {
        double[] outputArray = new double[getSize()];
        for (int i = 0; i < getSize(); i++) {
            outputArray[i] = get(i);
        }
        return outputArray;
    }

    public void set(int index, double value) {
        segment.setAtIndex(JAVA_DOUBLE, baseIndex + index, value);
    }

    public double get(int index) {
        return segment.getAtIndex(JAVA_DOUBLE, baseIndex + index);
    }

    @Override
    public void clear() {
        init(0.0);
    }

    public void init(double value) {
        for (int i = 0; i < getSize(); i++) {
            segment.setAtIndex(JAVA_DOUBLE, baseIndex + i, value);
        }
    }

    @Override
    public int getSize() {
        return numberOfElements;
    }

    @Override
    public MemorySegment getSegment() {
        return segment;
    }

    @Override
    public long getNumBytesOfSegment() {
        return segmentByteSize;
    }

    @Override
    public long getNumBytesWithoutHeader() {
        return segmentByteSize - TornadoNativeArray.ARRAY_HEADER;
    }

}
