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
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import uk.ac.manchester.tornado.api.internal.annotations.SegmentElementSize;

@SegmentElementSize(size = 2)
public final class ShortArray extends TornadoNativeArray {
    private static final int SHORT_BYTES = 2;
    private MemorySegment segment;
    private int numberOfElements;
    private int arrayHeaderSize;

    private int baseIndex;

    private long segmentByteSize;

    public ShortArray(int numberOfElements) {
        this.numberOfElements = numberOfElements;
        arrayHeaderSize = (int) TornadoNativeArray.ARRAY_HEADER;
        assert arrayHeaderSize >= 4;
        baseIndex = arrayHeaderSize / SHORT_BYTES;
        segmentByteSize = numberOfElements * SHORT_BYTES + arrayHeaderSize;

        segment = Arena.ofAuto().allocate(segmentByteSize, 1);
        segment.setAtIndex(JAVA_INT, 0, numberOfElements);
    }

    private static ShortArray createSegment(short[] values) {
        ShortArray array = new ShortArray(values.length);
        for (int i = 0; i < values.length; i++) {
            array.set(i, values[i]);
        }
        return array;
    }

    public static ShortArray fromArray(short[] values) {
        return createSegment(values);
    }

    public static ShortArray fromElements(short... values) {
        return createSegment(values);
    }

    public static ShortArray fromSegment(MemorySegment segment) {
        long byteSize = segment.byteSize();
        int numElements = (int) (byteSize / SHORT_BYTES);
        ShortArray shortArray = new ShortArray(numElements);
        MemorySegment.copy(segment, 0, shortArray.segment, shortArray.baseIndex * SHORT_BYTES, byteSize);
        return shortArray;
    }

    public short[] toHeapArray() {
        short[] outputArray = new short[getSize()];
        for (int i = 0; i < getSize(); i++) {
            outputArray[i] = get(i);
        }
        return outputArray;
    }

    public void set(int index, short value) {
        segment.setAtIndex(JAVA_SHORT, baseIndex + index, value);
    }

    public short get(int index) {
        return segment.getAtIndex(JAVA_SHORT, baseIndex + index);
    }

    @Override
    public void clear() {
        init((short) 0);
    }

    public void init(short value) {
        for (int i = 0; i < getSize(); i++) {
            segment.setAtIndex(JAVA_SHORT, baseIndex + i, value);
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
