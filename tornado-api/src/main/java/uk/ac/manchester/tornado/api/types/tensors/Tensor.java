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

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.FloatBuffer;

import uk.ac.manchester.tornado.api.internal.annotations.SegmentElementSize;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;

@SegmentElementSize(size = 4) //This needs to be fixed
public final class Tensor extends TornadoNativeArray implements AbstractTensor {

    /**
     * The data type of the elements contained within the tensor.
     */
    private final DType dType;

    /**
     * The total number of elements in the tensor.
     */
    private int numberOfElements;

    /**
     * The memory segment representing the tensor data in native memory.
     */
    private MemorySegment segment;

    /**
     * The index offset in the memory segment where the tensor data starts.
     */
    private int baseIndex;

    /**
     * The size in bytes of the memory segment including the header.
     */
    private long segmentByteSize;

    /**
     * The shape of the tensor, representing its dimensions.
     */
    private Shape shape;

    /**
     * Constructs a tensor with a specified size, memory segment, and data type.
     *
     * @param size
     *     the number of elements in the tensor
     * @param memorySegment
     *     the memory segment where the tensor is stored
     * @param dtype
     *     the data type of the tensor elements
     */
    // This is problematic, it needs the segment to be shifted to cater the ARRAY_HEADER
    public Tensor(int size, MemorySegment memorySegment, DType dtype) {
        assert size >= 0;
        this.numberOfElements = size;
        this.segment = memorySegment;
        this.dType = dtype;
    }

    public Tensor(Shape shape, MemorySegment memorySegment, DType dtype) {
        this.segment = memorySegment;
        this.dType = dtype;
        this.shape = shape;
    }

    /**
     * Constructs a tensor with a specified shape and data type. The memory for the tensor is automatically allocated.
     *
     * @param shape
     *     the shape of the tensor
     * @param dtype
     *     the data type of the tensor elements
     */
    public Tensor(Shape shape, DType dtype) {
        this.shape = shape;
        this.numberOfElements = shape.getSize();
        this.dType = dtype;
        this.baseIndex = (int) TornadoNativeArray.ARRAY_HEADER / dtype.getByteSize();
        this.segmentByteSize = (long) numberOfElements * dtype.getByteSize() + TornadoNativeArray.ARRAY_HEADER;
        this.segment = Arena.ofAuto().allocate(segmentByteSize, 1);
        segment.setAtIndex(JAVA_INT, 0, numberOfElements);
    }

    //    TensorFP16=NEW\\\

    /**
     * Creates a tensor from a float array.
     *
     * @param values
     *     the float array to convert into a tensor
     * @return a new tensor containing the provided values
     */
    public static Tensor fromArray(float[] values) {
        return createSegment(values);
    }

    public static Tensor fromFloatBuffer(FloatBuffer floatBuffer) {
        Shape shape = new Shape(floatBuffer.capacity());
        Tensor tensor = new Tensor(shape, DType.FLOAT);
        for (int i = 0; i < floatBuffer.capacity(); i++) {
            tensor.set(i, floatBuffer.get(i));
        }
        return tensor;
    }

    // Private helper method to support fromArray
    private static Tensor createSegment(float[] values) {
        Shape shape = new Shape(values.length);
        Tensor array = new Tensor(shape, DType.FLOAT);
        for (int i = 0; i < values.length; i++) {
            array.set(i, values[i]);
        }
        return array;
    }

    public MemorySegment getMemorySegmentNoHead() {
        return segment.asSlice(TornadoNativeArray.ARRAY_HEADER);
    }

    @Override
    public int getSize() {
        return numberOfElements;
    }

    public String getDTypeAsString() {
        return dType.toString();
    }

    public DType getDType() {
        return dType;
    }

    @Override
    public MemorySegment getSegment() {
        return segment.asSlice(TornadoNativeArray.ARRAY_HEADER);
    }

    @Override
    public MemorySegment getSegmentWithHeader() {
        return segment;
    }

    @Override
    public long getNumBytesOfSegmentWithHeader() {
        return 0;
    }

    @Override
    public long getNumBytesOfSegment() {
        return 0;
    }

    public long getNumBytesWithoutHeader() {
        return 0;
    }

    @Override
    protected void clear() {
    }

    public void set(int index, HalfFloat value) {
        segment.setAtIndex(JAVA_SHORT, baseIndex + index, value.getHalfFloatValue());
    }

    public void set(int index, float value) {
        segment.setAtIndex(JAVA_FLOAT, baseIndex + index, value);
    }

    public HalfFloat get(int index) {
        short halfFloatValue = segment.getAtIndex(JAVA_SHORT, baseIndex + index);
        return new HalfFloat(halfFloatValue);
    }

    public float getFloatValue(int index) {
        return segment.getAtIndex(JAVA_FLOAT, baseIndex + index);
    }

    //Example usage: tensorC.set(i, tensorA.get(i, Float.class) + tensorB.get(i, Float.class));
    public <T> T get(int index, Class<T> type) {
        if (type.equals(HalfFloat.class)) {
            short halfFloatValue = segment.getAtIndex(JAVA_SHORT, baseIndex + index);
            return type.cast(new HalfFloat(halfFloatValue)); // You need to ensure HalfFloat can be cast to T
        } else if (type.equals(Float.class)) {
            float floatValue = segment.getAtIndex(JAVA_FLOAT, baseIndex + index);
            return type.cast(floatValue); // Autoboxing from float to Float
        }
        throw new IllegalArgumentException(STR."Unsupported type: \{type}");
    }

    public void init(HalfFloat value) {
        assert dType.equals(DType.HALF_FLOAT);
        for (int i = 0; i < getSize(); i++) {
            segment.setAtIndex(JAVA_SHORT, baseIndex + i, value.getHalfFloatValue());
        }
    }

    public void init(float value) {
        assert dType.equals(DType.FLOAT);
        for (int i = 0; i < getSize(); i++) {
            segment.setAtIndex(JAVA_FLOAT, baseIndex + i, value);
        }
    }

    @Override
    public int getElementSize() {
        return numberOfElements;
    }

    public Shape getShape() {
        return shape;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();

        // Basic information
        sb.append("Tensor(").append(dType.toString()).append(", ").append(shape.toString()).append(")\n");

        int elementsPerLine = 10; // Adjust based on tensor size and preferences
        for (int i = 0; i < getSize(); i++) {
            HalfFloat value = get(i);

            sb.append(value.getFloat32());
            if ((i + 1) % elementsPerLine == 0) {
                sb.append("\n");
            } else {
                sb.append(" ");
            }
        }
        return sb.toString();
    }

}
