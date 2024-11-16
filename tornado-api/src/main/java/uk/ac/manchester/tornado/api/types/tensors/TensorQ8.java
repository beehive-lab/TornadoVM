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
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public class TensorQ8 extends Tensor {
    private final boolean  DEBUG_TENSOR_Q8 = false;
    private final ByteArray tensorStorage;
    private final int numberOfElements;
    private final Shape shape;
    private final DType dType;

    private final int blockSize;
    private final int bytesPerBlock;


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