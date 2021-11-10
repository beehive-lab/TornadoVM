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

public class LevelZeroBufferLong {

    private long ptrBuffer;
    private int size;
    private int alignment;

    public LevelZeroBufferLong() {
        this.ptrBuffer = -1;
    }

    public LevelZeroBufferLong(int size, int alignment) {
        this.size = size;
        this.alignment = alignment;
        this.ptrBuffer = -1;
    }

    public long getPtrBuffer() {
        return this.ptrBuffer;
    }

    public int getSize() {
        return this.size;
    }

    public int getAlignment() {
        return this.alignment;
    }

    public void memset(long value, int bufferSize) {
        memset_native(this, value, bufferSize);
    }

    public boolean isEqual(LevelZeroBufferLong bufferB, int size) {
        return isEqual(this.ptrBuffer, bufferB.getPtrBuffer(), size);
    }

    public void initPtr() {
        this.ptrBuffer = -1;
    }

    private native void memset_native(LevelZeroBufferLong javaBuffer, long value, int bufferSize);

    private native boolean isEqual(long bufferAPtr, long bufferBPtr, int size);

    private native void copy_native(long ptrBuffer, long[] array);

    /**
     * Copies the input array into the LevelZeroBuffer
     *
     * @param array
     */
    public void copy(long[] array) {
        copy_native(this.ptrBuffer, array);
    }

    private native long[] getLongBuffer_native(long ptrBuffer, int size);

    public long[] getLongBuffer() {
        return getLongBuffer_native(this.ptrBuffer, this.size);
    }

}
