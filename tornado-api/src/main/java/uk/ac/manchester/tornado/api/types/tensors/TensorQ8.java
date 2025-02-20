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

import uk.ac.manchester.tornado.api.types.arrays.ByteArray;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public class TensorQ8 extends Tensor {
    private final boolean  DEBUG_TENSOR_Q8 = false;
    /** Storage for the quantized tensor data including scales and values. */
    private final ByteArray tensorStorage;

    /** Total number of elements in the tensor. */
    private final int numberOfElements;

    /** Shape information for the tensor. */
    private final Shape shape;

    /** Data type of the tensor (QINT8). */
    private final DType dType;

    /** Number of values in each quantization block. */
    private final int blockSize;

    /** Total bytes per block including scale and quantized values. */
    private final int bytesPerBlock;

    /**
     * Constructs a new Q8 tensor with the specified shape.
     * Allocates memory and initializes the tensor storage.
     *
     * @param shape The shape of the tensor to create
     */
    public TensorQ8(Shape shape) {
        super(DType.QINT8, shape);
        this.shape = shape;
        this.numberOfElements = shape.getSize();
        this.dType = DType.QINT8;
        this.blockSize = GGMLType.Q8_0.getBlockSize();

        // Each block contains:
        // - 2 bytes for float16 scale
        // - blockSize bytes for quantized values
        this.bytesPerBlock = Float16.BYTES + blockSize;

        // Calculate number of blocks needed to store all elements
        int numBlocks = (numberOfElements + blockSize - 1) / blockSize;

        // Calculate total storage size in bytes, including header
        long dataSize = (long)numBlocks * bytesPerBlock;
        long totalSize = dataSize;

        if (DEBUG_TENSOR_Q8) {
            System.out.println("Debug info:");
            System.out.println("Number of elements: " + numberOfElements);
            System.out.println("Block size: " + blockSize);
            System.out.println("Bytes per block: " + bytesPerBlock);
            System.out.println("Number of blocks: " + numBlocks);
            System.out.println("Data size: " + dataSize);
            System.out.println("Total size with header: " + totalSize);
        }

        this.tensorStorage = new ByteArray(numberOfElements, totalSize);
    }

    /**
     * Constructs a Q8 tensor using existing memory segment data.
     * Used for creating a tensor view of pre-existing quantized data.
     *
     * @param numberOfElements The number of elements in the tensor
     * @param memorySegment The memory segment containing the quantized data
     */
    public TensorQ8(int numberOfElements, MemorySegment memorySegment) {
        super(DType.QINT8, new Shape(numberOfElements));
        this.shape = new Shape(numberOfElements);
        this.numberOfElements = numberOfElements;
        this.dType = DType.QINT8;
        this.blockSize = GGMLType.Q8_0.getBlockSize();

        // Each block contains:
        // - 2 bytes for float16 scale
        // - blockSize bytes for quantized values
        this.bytesPerBlock = Float16.BYTES + blockSize;

        // Calculate number of blocks needed to store all elements
        int numBlocks = (numberOfElements + blockSize - 1) / blockSize;

        // Calculate total storage size in bytes, including header
        long dataSize = (long)numBlocks * bytesPerBlock;
        long totalSize = dataSize;

        if (DEBUG_TENSOR_Q8) {
            System.out.println("Debug info:");
            System.out.println("Number of elements: " + numberOfElements);
            System.out.println("Block size: " + blockSize);
            System.out.println("Bytes per block: " + bytesPerBlock);
            System.out.println("Number of blocks: " + numBlocks);
            System.out.println("Data size: " + dataSize);
            System.out.println("Total size with header: " + totalSize);
        }

        this.tensorStorage = ByteArray.fromSegment(memorySegment, numberOfElements);
    }

    private float[] getBlockValues(int blockIndex) {
        float[] values = new float[blockSize];
        int blockOffset = blockIndex * bytesPerBlock;

        try {
            float scale = Float.float16ToFloat(readShort(tensorStorage.getSegmentWithHeader(),   blockOffset));
            for (int i = 0; i < blockSize; i++) {
                byte quant = readByte(tensorStorage.getSegmentWithHeader(),   blockOffset + Float16.BYTES + i);
                values[i] = quant * scale;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to read block " + blockIndex + " at offset " + blockOffset + ": " + e.getMessage());
        }
        return values;
    }

    /**
     * Gets a single float value from the tensor at the specified index.
     * The value is dequantized using the scale factor from its containing block.
     *
     * @param index The index of the value to retrieve
     * @return The dequantized float value
     * @throws IndexOutOfBoundsException if the index is out of bounds
     * @throws RuntimeException if there is an error reading the value
     */
    public float getFloat(int index) {
        if (index < 0 || index >= numberOfElements) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for length " + numberOfElements);
        }

        int blockIndex = index / blockSize;
        int withinBlockIndex = index % blockSize;
        int blockOffset = blockIndex * bytesPerBlock;

        try {
            float scale = Float.float16ToFloat(readShort(tensorStorage.getSegmentWithHeader(),   blockOffset));
            byte quant = readByte(tensorStorage.getSegmentWithHeader(),  + blockOffset + Float16.BYTES + withinBlockIndex);
            return quant * scale;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get float at index " + index + " (block " + blockIndex + ", offset " + blockOffset + "): " + e.getMessage());
        }
    }

    /**
     * Sets a float value in the tensor at the specified index.
     * Updates the entire block's scale factor when any value in the block changes.
     *
     * @param index The index where the value should be set
     * @param value The float value to set
     * @throws IndexOutOfBoundsException if the index is out of bounds
     * @throws RuntimeException if there is an error writing the value
     */
    public void setFloat(int index, float value) {
        if (index < 0 || index >= numberOfElements) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for length " + numberOfElements);
        }

        int blockIndex = index / blockSize;
        int withinBlockIndex = index % blockSize;

        // Get current block values
        float[] blockValues = getBlockValues(blockIndex);
        blockValues[withinBlockIndex] = value;

        // Compute optimal scale for block
        float scale = computeOptimalScale(blockValues);

        // Update block
        int blockOffset = blockIndex * bytesPerBlock;

        try {
            // Write scale
            writeShort(tensorStorage.getSegmentWithHeader(),   blockOffset, Float.floatToFloat16(scale));

            // Write quantized values
            for (int i = 0; i < blockValues.length; i++) {
                int quantized = Math.min(127, Math.max(-128, Math.round(blockValues[i] / scale)));
                writeByte(tensorStorage.getSegmentWithHeader(), blockOffset + Float16.BYTES + i, (byte)quantized);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to set float at index " + index +  " (block " + blockIndex + ", offset " + blockOffset + "): " + e.getMessage());
        }
    }

    /**
     * Computes the optimal scale factor for a block of values.
     * The scale is chosen to maximize the use of the INT8 range (-128 to 127).
     *
     * @param values The array of float values to compute the scale for
     * @return The optimal scale factor for quantizing the values
     */

    private float computeOptimalScale(float[] values) {
        float maxAbs = 1e-5f;
        for (float value : values) {
            maxAbs = Math.max(maxAbs, Math.abs(value));
        }
        return maxAbs / 127.0f;
    }


    static short readShort(MemorySegment memorySegment, long offset) {
        return memorySegment.get(ValueLayout.JAVA_SHORT, offset);
    }

    static byte readByte(MemorySegment memorySegment, long offset) {
        return memorySegment.get(ValueLayout.JAVA_BYTE, offset);
    }

    static void writeShort(MemorySegment memorySegment, long offset, short value) {
        memorySegment.set(ValueLayout.JAVA_SHORT, offset, value);
    }

    static void writeByte(MemorySegment memorySegment, long offset, byte value) {
        memorySegment.set(ValueLayout.JAVA_BYTE, offset, value);
    }

    @Override
    public Shape getShape() {
        return shape;
    }

    @Override
    public String getDTypeAsString() {
        return dType.QINT8.toString();
    }

    @Override
    public DType getDType() {
        return DType.QINT8;
    }

    @Override
    public int getSize() {
        return shape.getSize();
    }

    @Override
    public MemorySegment getSegment() {
        return tensorStorage.getSegmentWithHeader();
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

    }

    @Override
    public int getElementSize() {
        return DType.QINT8.getByteSize();
    }
}