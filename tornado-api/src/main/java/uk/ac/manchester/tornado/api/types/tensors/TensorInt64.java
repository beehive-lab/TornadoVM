/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.api.types.tensors;

import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.internal.annotations.SegmentElementSize;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;

import java.lang.foreign.MemorySegment;
import java.nio.LongBuffer;
import java.util.Arrays;

import static java.lang.foreign.ValueLayout.JAVA_LONG;

@SegmentElementSize(size = 8)
public final class TensorInt64 extends Tensor {
    private static final int LONG_BYTES = 8;
    /**
     * The data type of the elements contained within the tensor.
     */
    private final DType dType;
    private final Shape shape;

    private final LongArray tensorStorage;

    /**
     * The total number of elements in the tensor.
     */
    private int numberOfElements;

    /**
     * The memory segment representing the tensor data in native memory.
     */

    public TensorInt64(Shape shape) {
        super(DType.INT64, shape);
        this.shape = shape;
        this.numberOfElements = shape.getSize();
        this.dType = DType.INT64;
        this.tensorStorage = new LongArray(numberOfElements);
    }

    public void init(long value) {
        for (int i = 0; i < getSize(); i++) {
            tensorStorage.getSegmentWithHeader().setAtIndex(JAVA_LONG, getBaseIndex() + i, value);
        }
    }

    public void set(int index, long value) {
        tensorStorage.getSegmentWithHeader().setAtIndex(JAVA_LONG, getBaseIndex() + index, value);
    }

    private long getBaseIndex() {
        return (int) TornadoNativeArray.ARRAY_HEADER / LONG_BYTES;
    }

    /**
     * Gets the half_float value stored at the specified index of the {@link HalfFloatArray} instance.
     *
     * @param index
     *     The index of which to retrieve the float value.
     * @return
     */
    public long get(int index) {
        return tensorStorage.getSegmentWithHeader().getAtIndex(JAVA_LONG, getBaseIndex() + index);
    }

    @Override
    public int getSize() {
        return numberOfElements;
    }

    @Override
    public MemorySegment getSegment() {
        return tensorStorage.getSegment();
    }

    @Override
    public MemorySegment getSegmentWithHeader() {
        return tensorStorage.getSegmentWithHeader();
    }

    @Override
    public long getNumBytesOfSegmentWithHeader() {
        return tensorStorage.getNumBytesOfSegmentWithHeader();
    }

    @Override
    public long getNumBytesOfSegment() {
        return tensorStorage.getNumBytesOfSegment();
    }

    @Override
    protected void clear() {
        init((long) 0);
    }

    @Override
    public int getElementSize() {
        return LONG_BYTES;
    }

    @Override
    public Shape getShape() {
        return this.shape;
    }

    @Override
    public String getDTypeAsString() {
        return dType.toString();
    }

    @Override
    public DType getDType() {
        return dType;
    }

    public LongBuffer getLongBuffer() {
        return getSegment().asByteBuffer().asLongBuffer();
    }

    public static void initialize(TensorInt64 tensor, long value) {
        for (@Parallel int i = 0; i < tensor.getSize(); i++) {
            tensor.set(i, value);
        }
    }

    /**
     * Concatenates multiple {@link TensorInt64} instances into a single {@link TensorInt64}.
     *
     * @param arrays
     *     Variable number of {@link TensorInt64} objects to be concatenated.
     * @return A new {@link TensorInt64} instance containing all the elements of the input arrays,
     *     concatenated in the order they were provided.
     */
    public static TensorInt64 concat(TensorInt64... arrays) {
        int newSize = Arrays.stream(arrays).mapToInt(TensorInt64::getSize).sum();
        TensorInt64 concatArray = new TensorInt64(new Shape(newSize));
        long currentPositionBytes = 0;
        for (TensorInt64 array : arrays) {
            MemorySegment.copy(array.getSegment(), 0, concatArray.getSegment(), currentPositionBytes, array.getNumBytesOfSegment());
            currentPositionBytes += array.getNumBytesOfSegment();
        }
        return concatArray;
    }
}
