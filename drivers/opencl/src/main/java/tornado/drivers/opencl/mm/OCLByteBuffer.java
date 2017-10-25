/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2017 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: James Clarkson
 *
 */
package tornado.drivers.opencl.mm;

import java.nio.ByteBuffer;
import tornado.common.RuntimeUtilities;
import tornado.common.Tornado;
import tornado.common.exceptions.TornadoOutOfMemoryException;
import tornado.drivers.opencl.OCLDeviceContext;

/**
 * A buffer for inspecting data within an OpenCL device. It is not backed by any
 * userspace object.
 */
public class OCLByteBuffer {

    protected ByteBuffer buffer;
    protected long bufferOffset;
    protected long bytes;

    protected final OCLDeviceContext deviceContext;

    protected long offset;

    protected OCLByteBuffer(final OCLDeviceContext device) {
        this.deviceContext = device;
    }

    public OCLByteBuffer(final OCLDeviceContext device, final long offset, final long numBytes) {
        this(device);
        this.offset = offset;
        this.bytes = numBytes;
        buffer = ByteBuffer.allocate((int) numBytes);
        buffer.order(deviceContext.getByteOrder());
    }

    public void allocate(final long numBytes) throws TornadoOutOfMemoryException {
        bytes = numBytes;

        offset = deviceContext.getMemoryManager().tryAllocate(byte[].class, numBytes, 0, getAlignment());

        buffer = ByteBuffer.allocate((int) numBytes);
        buffer.order(deviceContext.getByteOrder());

    }

    public ByteBuffer buffer() {
        return buffer;
    }

    public void read() {
        read(null);
    }

    public void read(final int[] events) {
        deviceContext.readBuffer(toBuffer(), offset, bytes, buffer.array(), events);
    }

    public int enqueueRead() {
        return enqueueRead(null);
    }

    public int enqueueRead(final int[] events) {
        return deviceContext.enqueueReadBuffer(toBuffer(), offset, bytes, buffer.array(), events);
    }

    public void write() {
        write(null);
    }

    public void write(final int[] events) {
        deviceContext.writeBuffer(toBuffer(), offset, bytes, buffer.array(), events);
    }

    public int enqueueWrite() {
        return enqueueWrite(null);
    }

    public int enqueueWrite(final int[] events) {
        return deviceContext.enqueueWriteBuffer(toBuffer(), offset, bytes, buffer.array(), events);
    }

    public void dump() {
        dump(64);
    }

    public void dump(final int width) {
        buffer.position(buffer.capacity());
        System.out.printf("Buffer  : capacity = %s, in use = %s, device = %s \n", RuntimeUtilities
                .humanReadableByteCount(bytes, true), RuntimeUtilities.humanReadableByteCount(
                buffer.position(), true), deviceContext.getDevice().getName());
        for (int i = 0; i < buffer.position(); i += width) {
            System.out.printf("[0x%04x]: ", i + toAbsoluteAddress());
            for (int j = 0; j < Math.min(buffer.capacity() - i, width); j++) {
                if (j % 2 == 0) {
                    System.out.printf(" ");
                }
                if (j < buffer.position() - i) {
                    System.out.printf("%02x", buffer.get(i + j));
                } else {
                    System.out.printf("..");
                }
            }
            System.out.println();
        }
    }

    public byte get() {
        return buffer.get();
    }

    public ByteBuffer get(final byte[] dst) {
        return buffer.get(dst);
    }

    public ByteBuffer get(final byte[] dst, final int offset, final int length) {
        return buffer.get(dst, offset, length);
    }

    public byte get(final int index) {
        return buffer.get(index);
    }

    public int getAlignment() {
        return 64;
    }

    public long getBufferOffset() {
        return offset;
    }

    public char getChar() {
        return buffer.getChar();
    }

    public char getChar(final int index) {
        return buffer.getChar(index);
    }

    public double getDouble() {
        return buffer.getDouble();
    }

    public double getDouble(final int index) {
        return buffer.getDouble(index);
    }

    public float getFloat() {
        return buffer.getFloat();
    }

    public float getFloat(final int index) {
        return buffer.getFloat(index);
    }

    public int getInt() {
        return buffer.getInt();
    }

    public int getInt(final int index) {
        return buffer.getInt(index);
    }

    public long getLong() {
        return buffer.getLong();
    }

    public long getLong(final int index) {
        return buffer.getLong(index);
    }

    public short getShort() {
        return buffer.getShort();
    }

    public short getShort(final int index) {
        return buffer.getShort(index);
    }

    public long getSize() {
        return bytes;
    }

    public ByteBuffer put(final byte b) {
        return buffer.put(b);
    }

    public final ByteBuffer put(final byte[] src) {
        return buffer.put(src);
    }

    public ByteBuffer put(final byte[] src, final int offset, final int length) {
        return buffer.put(src, offset, length);
    }

    public ByteBuffer put(final ByteBuffer src) {
        return buffer.put(src);
    }

    public ByteBuffer put(final int index, final byte b) {
        return buffer.put(index, b);
    }

    public ByteBuffer putChar(final char value) {
        return buffer.putChar(value);
    }

    public ByteBuffer putChar(final int index, final char value) {
        return buffer.putChar(index, value);
    }

    public ByteBuffer putDouble(final double value) {
        return buffer.putDouble(value);
    }

    public ByteBuffer putDouble(final int index, final double value) {
        return buffer.putDouble(index, value);
    }

    public ByteBuffer putFloat(final float value) {
        return buffer.putFloat(value);
    }

    public ByteBuffer putFloat(final int index, final float value) {
        return buffer.putFloat(index, value);
    }

    public ByteBuffer putInt(final int value) {
        return buffer.putInt(value);
    }

    public ByteBuffer putInt(final int index, final int value) {
        return buffer.putInt(index, value);
    }

    public ByteBuffer putLong(final int index, final long value) {
        return buffer.putLong(index, value);
    }

    public ByteBuffer putLong(final long value) {
        return buffer.putLong(value);
    }

    public ByteBuffer putShort(final int index, final short value) {
        return buffer.putShort(index, value);
    }

    public ByteBuffer putShort(final short value) {
        return buffer.putShort(value);
    }

    public long toAbsoluteAddress() {
        return deviceContext.getMemoryManager().toAbsoluteDeviceAddress(offset);
    }

    public long toBuffer() {
        return deviceContext.getMemoryManager().toBuffer();
    }

    public long toRelativeAddress() {
        return deviceContext.getMemoryManager().toRelativeDeviceAddress(offset);
    }

    public Object value() {
        return buffer.array();
    }

    public void zeroMemory() {
        Tornado.warn("zero memory unimplemented");

    }
}
