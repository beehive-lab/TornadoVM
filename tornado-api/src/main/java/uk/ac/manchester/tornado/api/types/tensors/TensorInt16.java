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
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;

import java.lang.foreign.MemorySegment;
import java.nio.ShortBuffer;
import java.util.Arrays;

import static java.lang.foreign.ValueLayout.JAVA_SHORT;

@SegmentElementSize(size = 2)
public final class TensorInt16 extends Tensor {

    private static final int SHORT_BYTES = 2;
    /**
     * The data type of the elements contained within the tensor.
     */
    private final DType dType;
    private final Shape shape;

    private final ShortArray tensorStorage;

    /**
     * The total number of elements in the tensor.
     */
    private int numberOfElements;

    /**
     * The memory segment representing the tensor data in native memory.
     */

    public TensorInt16(Shape shape) {
        super(DType.HALF_FLOAT, shape);
        this.shape = shape;
        this.numberOfElements = shape.getSize();
        this.dType = DType.HALF_FLOAT;
        this.tensorStorage = new ShortArray(numberOfElements);
    }

    public void init(short value) {
        for (int i = 0; i < getSize(); i++) {
            tensorStorage.getSegmentWithHeader().setAtIndex(JAVA_SHORT, getBaseIndex() + i, value);
        }
    }

    public void set(int index, short value) {
        tensorStorage.getSegmentWithHeader().setAtIndex(JAVA_SHORT, getBaseIndex() + index, value);
    }

    private long getBaseIndex() {
        return (int) TornadoNativeArray.ARRAY_HEADER / SHORT_BYTES;
    }

    /**
     * Gets the half_float value stored at the specified index of the {@link HalfFloatArray} instance.
     *
     * @param index
     *     The index of which to retrieve the float value.
     * @return
     */
    public short get(int index) {
        return tensorStorage.getSegmentWithHeader().getAtIndex(JAVA_SHORT, getBaseIndex() + index);
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
        init((short) 0);
    }

    @Override
    public int getElementSize() {
        return SHORT_BYTES;
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

    public ShortBuffer getShortBuffer() {
        return getSegment().asByteBuffer().asShortBuffer();
    }

    public static void initialize(TensorInt16 tensor, short value) {
        for (@Parallel int i = 0; i < tensor.getSize(); i++) {
            tensor.set(i, value);
        }
    }

    /**
     * Concatenates multiple {@link TensorInt16} instances into a single {@link TensorInt16}.
     *
     * @param arrays
     *     Variable number of {@link TensorInt16} objects to be concatenated.
     * @return A new {@link TensorInt16} instance containing all the elements of the input arrays,
     *     concatenated in the order they were provided.
     */
    public static TensorInt16 concat(TensorInt16... arrays) {
        int newSize = Arrays.stream(arrays).mapToInt(TensorInt16::getSize).sum();
        TensorInt16 concatArray = new TensorInt16(new Shape(newSize));
        long currentPositionBytes = 0;
        for (TensorInt16 array : arrays) {
            MemorySegment.copy(array.getSegment(), 0, concatArray.getSegment(), currentPositionBytes, array.getNumBytesOfSegment());
            currentPositionBytes += array.getNumBytesOfSegment();
        }
        return concatArray;
    }
}
