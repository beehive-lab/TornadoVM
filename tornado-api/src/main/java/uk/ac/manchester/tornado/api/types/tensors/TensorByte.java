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

import uk.ac.manchester.tornado.api.internal.annotations.SegmentElementSize;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;

import java.lang.foreign.MemorySegment;
import java.util.Arrays;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

@SegmentElementSize(size = 1)
public final class TensorByte extends Tensor {

    private static final int BYTE = 1;
    /**
     * The data type of the elements contained within the tensor.
     */
    private final DType dType;
    private final Shape shape;

    private final ByteArray tensorStorage;

    /**
     * The total number of elements in the tensor.
     */
    private int numberOfElements;

    /**
     * The memory segment representing the tensor data in native memory.
     */

    public TensorByte(Shape shape) {
        super(DType.BOOL, shape);
        this.shape = shape;
        this.numberOfElements = shape.getSize();
        this.dType = DType.BOOL;
        this.tensorStorage = new ByteArray(numberOfElements);
    }

    public void init(byte value) {
        for (int i = 0; i < getSize(); i++) {
            tensorStorage.getSegmentWithHeader().setAtIndex(JAVA_BYTE, getBaseIndex() + i, value);
        }
    }

    public void set(int index, byte value) {
        tensorStorage.getSegmentWithHeader().setAtIndex(JAVA_BYTE, getBaseIndex() + index, value);
    }

    private long getBaseIndex() {
        return (int) TornadoNativeArray.ARRAY_HEADER / BYTE;
    }

    /**
     * Gets the float value stored at the specified index of the {@link ByteArray} instance.
     *
     * @param index
     *     The index of which to retrieve the float value.
     * @return
     */
    public byte get(int index) {
        return tensorStorage.getSegmentWithHeader().getAtIndex(JAVA_BYTE, getBaseIndex() + index);
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
        init((byte) 0);
    }

    @Override
    public int getElementSize() {
        return BYTE;
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

    /**
     * Concatenates multiple {@link TensorByte} instances into a single {@link TensorByte}.
     *
     * @param arrays
     *     Variable number of {@link TensorByte} objects to be concatenated.
     * @return A new {@link TensorByte} instance containing all the elements of the input arrays,
     *     concatenated in the order they were provided.
     */
    public static TensorByte concat(TensorByte... arrays) {
        int newSize = Arrays.stream(arrays).mapToInt(TensorByte::getSize).sum();
        TensorByte concatArray = new TensorByte(new Shape(newSize));
        long currentPositionBytes = 0;
        for (TensorByte array : arrays) {
            MemorySegment.copy(array.getSegment(), 0, concatArray.getSegment(), currentPositionBytes, array.getNumBytesOfSegment());
            currentPositionBytes += array.getNumBytesOfSegment();
        }
        return concatArray;
    }
}
