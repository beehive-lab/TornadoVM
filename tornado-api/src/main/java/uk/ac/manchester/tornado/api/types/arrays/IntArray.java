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

import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import uk.ac.manchester.tornado.api.internal.annotations.SegmentElementSize;

@SegmentElementSize(size = 4)
public final class IntArray extends TornadoNativeArray {
    private static final int INT_BYTES = 4;
    private int numberOfElements;
    private MemorySegment segment;
    private int arrayHeaderSize;

    private int baseIndex;

    private long segmentByteSize;

    public IntArray(int numberOfElements) {
        this.numberOfElements = numberOfElements;
        arrayHeaderSize = (int) TornadoNativeArray.ARRAY_HEADER;
        baseIndex = arrayHeaderSize / INT_BYTES;
        segmentByteSize = numberOfElements * INT_BYTES + arrayHeaderSize;

        segment = Arena.ofAuto().allocate(segmentByteSize, 1);
        segment.setAtIndex(JAVA_INT, 0, numberOfElements);
    }

    private static IntArray createSegment(int[] values) {
        IntArray array = new IntArray(values.length);
        for (int i = 0; i < values.length; i++) {
            array.set(i, values[i]);
        }
        return array;
    }

    public static IntArray fromArray(int[] values) {
        return createSegment(values);
    }

    public static IntArray fromElements(int... values) {
        return createSegment(values);
    }

    public static IntArray fromSegment(MemorySegment segment) {
        long byteSize = segment.byteSize();
        int numElements = (int) (byteSize / INT_BYTES);
        IntArray intArray = new IntArray(numElements);
        MemorySegment.copy(segment, 0, intArray.segment, intArray.baseIndex * INT_BYTES, byteSize);
        return intArray;
    }

    public int[] toHeapArray() {
        int[] outputArray = new int[getSize()];
        for (int i = 0; i < getSize(); i++) {
            outputArray[i] = get(i);
        }
        return outputArray;
    }

    public void set(int index, int value) {
        segment.setAtIndex(JAVA_INT, baseIndex + index, value);
    }

    public int get(int index) {
        return segment.getAtIndex(JAVA_INT, baseIndex + index);
    }

    @Override
    public void clear() {
        init(0);
    }

    public void init(int value) {
        for (int i = 0; i < getSize(); i++) {
            segment.setAtIndex(JAVA_INT, baseIndex + i, value);
        }
    }

    @Override
    public int getSize() {
        return numberOfElements;
    }

    @Override
    public long getNumBytesOfSegment() {
        return segmentByteSize;
    }

    @Override
    public long getNumBytesWithoutHeader() {
        return segmentByteSize - TornadoNativeArray.ARRAY_HEADER;
    }

    @Override
    public MemorySegment getSegment() {
        return segment;
    }

}
