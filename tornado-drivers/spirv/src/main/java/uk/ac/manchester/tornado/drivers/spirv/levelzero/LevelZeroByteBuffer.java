/*
 * MIT License
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class LevelZeroByteBuffer {

    private long ptrBuffer;
    private long size;
    private long alignment;

    public LevelZeroByteBuffer() {
        this.ptrBuffer = -1;
    }

    public LevelZeroByteBuffer(int size, int alignment) {
        this.size = size;
        this.alignment = alignment;
        this.ptrBuffer = -1;
    }

    public long getPtrBuffer() {
        return this.ptrBuffer;
    }

    public long getSize() {
        return this.size;
    }

    public long getAlignment() {
        return this.alignment;
    }

    public void memset(byte value, int bufferSize) {
        memset_native(this, value, bufferSize);
    }

    public void memset(int value, int bufferSize) {
        memset_nativeInt(this, value, bufferSize);
    }

    public boolean isEqual(LevelZeroByteBuffer bufferB, int size) {
        return isEqual(this.ptrBuffer, bufferB.getPtrBuffer(), size);
    }

    public void initPtr() {
        this.ptrBuffer = -1;
    }

    private native void memset_native(LevelZeroByteBuffer javaBuffer, byte value, int bufferSize);

    private native void memset_nativeInt(LevelZeroByteBuffer javaBuffer, int value, int bufferSize);

    private native boolean isEqual(long bufferAPtr, long bufferBPtr, int size);

    private native void copy_native(long ptrBuffer, byte[] array);

    /**
     * Copies the input array into the LevelZeroBuffer
     *
     * @param array
     */
    public void copy(byte[] array) {
        copy_native(this.ptrBuffer, array);
    }

    private native byte[] getByteBuffer_native(long ptrBuffer, long size);

    public byte[] getByteBuffer() {
        return getByteBuffer_native(this.ptrBuffer, this.size);
    }
}
