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
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import uk.ac.manchester.tornado.api.internal.annotations.SegmentElementSize;

@SegmentElementSize(size = 8)
public final class LongArray extends TornadoNativeArray {
    private static final int LONG_BYTES = 8;
    private MemorySegment segment;
    private int numberOfElements;
    private int arrayHeaderSize;

    private int baseIndex;

    private long segmentByteSize;

    public LongArray(int numberOfElements) {
        this.numberOfElements = numberOfElements;
        arrayHeaderSize = (int) TornadoNativeArray.ARRAY_HEADER;
        baseIndex = arrayHeaderSize / LONG_BYTES;

        segmentByteSize = numberOfElements * LONG_BYTES + arrayHeaderSize;
        segment = Arena.ofAuto().allocate(segmentByteSize, 1);
        segment.setAtIndex(JAVA_INT, 0, numberOfElements);
    }

    private static LongArray createSegment(long[] values) {
        LongArray array = new LongArray(values.length);
        for (int i = 0; i < values.length; i++) {
            array.set(i, values[i]);
        }
        return array;
    }

    public static LongArray fromArray(long[] values) {
        return createSegment(values);
    }

    public static LongArray fromElements(long... values) {
        return createSegment(values);
    }

    public static LongArray fromSegment(MemorySegment segment) {
        long byteSize = segment.byteSize();
        int numElements = (int) (byteSize / LONG_BYTES);
        LongArray longArray = new LongArray(numElements);
        MemorySegment.copy(segment, 0, longArray.segment, longArray.baseIndex * LONG_BYTES, byteSize);
        return longArray;
    }

    public long[] toHeapArray() {
        long[] outputArray = new long[getSize()];
        for (int i = 0; i < getSize(); i++) {
            outputArray[i] = get(i);
        }
        return outputArray;
    }

    public void set(int index, long value) {
        segment.setAtIndex(JAVA_LONG, baseIndex + index, value);
    }

    public long get(int index) {
        return segment.getAtIndex(JAVA_LONG, baseIndex + index);
    }

    @Override
    public void clear() {
        init(0);
    }

    public void init(long value) {
        for (int i = 0; i < getSize(); i++) {
            segment.setAtIndex(JAVA_LONG, baseIndex + i, value);
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
