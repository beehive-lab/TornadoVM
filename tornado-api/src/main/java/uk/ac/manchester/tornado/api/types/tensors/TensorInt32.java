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
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;

import java.lang.foreign.MemorySegment;
import java.nio.IntBuffer;
import java.util.Arrays;

import static java.lang.foreign.ValueLayout.JAVA_INT;

@SegmentElementSize(size = 4)

public final class TensorInt32 extends Tensor {
    private static final int INT_BYTES = 4;
    /**
     * The data type of the elements contained within the tensor.
     */
    private final DType dType;
    private final Shape shape;

    private final IntArray tensorStorage;

    /**
     * The total number of elements in the tensor.
     */
    private int numberOfElements;

    /**
     * The memory segment representing the tensor data in native memory.
     */

    public TensorInt32(Shape shape) {
        super(DType.INT32, shape);
        this.shape = shape;
        this.numberOfElements = shape.getSize();
        this.dType = DType.INT32;
        this.tensorStorage = new IntArray(numberOfElements);
    }

    public void init(int value) {
        for (int i = 0; i < getSize(); i++) {
            tensorStorage.getSegmentWithHeader().setAtIndex(JAVA_INT, getBaseIndex() + i, value);
        }
    }

    public void set(int index, int value) {
        tensorStorage.getSegmentWithHeader().setAtIndex(JAVA_INT, getBaseIndex() + index, value);
    }

    private long getBaseIndex() {
        return (int) TornadoNativeArray.ARRAY_HEADER / INT_BYTES;
    }

    /**
     * Gets the half_float value stored at the specified index of the {@link HalfFloatArray} instance.
     *
     * @param index
     *     The index of which to retrieve the float value.
     * @return
     */
    public int get(int index) {
        return tensorStorage.getSegmentWithHeader().getAtIndex(JAVA_INT, getBaseIndex() + index);
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
        init(0);
    }

    @Override
    public int getElementSize() {
        return INT_BYTES;
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

    public IntBuffer getIntBuffer() {
        return getSegment().asByteBuffer().asIntBuffer();
    }

    public static void initialize(TensorInt32 tensor, int value) {
        for (@Parallel int i = 0; i < tensor.getSize(); i++) {
            tensor.set(i, value);
        }
    }

    /**
     * Concatenates multiple {@link TensorInt32} instances into a single {@link TensorInt32}.
     *
     * @param arrays
     *     Variable number of {@link TensorInt32} objects to be concatenated.
     * @return A new {@link TensorInt32} instance containing all the elements of the input arrays,
     *     concatenated in the order they were provided.
     */
    public static TensorInt32 concat(TensorInt32... arrays) {
        int newSize = Arrays.stream(arrays).mapToInt(TensorInt32::getSize).sum();
        TensorInt32 concatArray = new TensorInt32(new Shape(newSize));
        long currentPositionBytes = 0;
        for (TensorInt32 array : arrays) {
            MemorySegment.copy(array.getSegment(), 0, concatArray.getSegment(), currentPositionBytes, array.getNumBytesOfSegment());
            currentPositionBytes += array.getNumBytesOfSegment();
        }
        return concatArray;
    }
}
