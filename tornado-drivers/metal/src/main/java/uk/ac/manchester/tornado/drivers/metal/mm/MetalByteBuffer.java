/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.metal.mm;

import java.nio.ByteBuffer;

import uk.ac.manchester.tornado.drivers.metal.MetalDeviceContext;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;

/**
 * A buffer for inspecting data within an Metal device. It is not backed by any
 * userspace object.
 */
public class MetalByteBuffer {

    private static final int BYTES_PER_INTEGER = 4;
    protected final long bytes;
    protected final MetalDeviceContext deviceContext;
    private final long oclBufferId;
    private final long offset;
    protected ByteBuffer buffer;

    public MetalByteBuffer(MetalDeviceContext deviceContext, final long oclBufferId, final long offset, final long numBytes) {
        this.deviceContext = deviceContext;
        this.oclBufferId = oclBufferId;
        this.bytes = numBytes;
        this.offset = offset;
        buffer = ByteBuffer.allocate((int) numBytes);
        buffer.order(this.deviceContext.getByteOrder());
    }

    public ByteBuffer buffer() {
        return buffer;
    }

    public void read(long executionPlanId) {
        read(executionPlanId, null);
    }

    public void read(long executionPlanId, final int[] events) {
        deviceContext.readBuffer(executionPlanId, toBuffer(), offset, bytes, buffer.array(), 0, events);
    }

    public int read(long executionPlanId, long fromBuffer, final int[] toArray) {
        return deviceContext.readBuffer(executionPlanId, fromBuffer, 0, toArray.length * 4, toArray, 0, null);
    }

    public int enqueueRead(long executionPlanId) {
        return enqueueRead(executionPlanId, null);
    }

    public int enqueueRead(long executionPlanId, final int[] events) {
        return deviceContext.enqueueReadBuffer(executionPlanId, toBuffer(), offset, bytes, buffer.array(), 0, events);
    }

    public void write(long executionPlanId) {
        write(executionPlanId, null);
    }

    public void write(long executionPlanId, final int[] events) {
        // XXX: offset 0
        deviceContext.writeBuffer(executionPlanId, toBuffer(), offset, bytes, buffer.array(), 0, events);
    }

    public int enqueueWrite(long executionPlanId) {
        return enqueueWrite(executionPlanId, null);
    }

    public int enqueueWrite(long executionPlanId, final int[] events) {
        return deviceContext.enqueueWriteBuffer(executionPlanId, toBuffer(), offset, bytes, buffer.array(), 0, events);
    }

    /**
     * Write from a specific buffer space.
     *
     * @param fromBuffer
     *     buffer to enqueue
     * @param array
     *     integer array to copy to the device.
     * @param events
     *     list of events
     * @return event status
     */
    public int enqueueWrite(long executionPlanId, long fromBuffer, final int[] array, final int offset, final int[] events) {
        return deviceContext.enqueueWriteBuffer(executionPlanId, fromBuffer, offset, BYTES_PER_INTEGER * array.length, array, 0, events);
    }

    public int enqueueRead(long executionPlanId, long fromBuffer, final int[] array, final int offset, final int[] events) {
        return deviceContext.enqueueReadBuffer(executionPlanId, fromBuffer, offset, BYTES_PER_INTEGER * array.length, array, 0, events);
    }

    public void dump() {
        dump(64);
    }

    public void dump(final int width) {
        buffer.position(buffer.capacity());
        System.out.printf("Buffer  : capacity = %s, in use = %s, device = %s \n", RuntimeUtilities.humanReadableByteCount(bytes, true), RuntimeUtilities.humanReadableByteCount(buffer.position(),
                true), deviceContext.getDevice().getDeviceName());
        for (int i = 0; i < buffer.position(); i += width) {
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

    public long toBuffer() {
        return oclBufferId;
    }

    public long getOffset() {
        return offset;
    }

    public long toConstantAddress() {
        return deviceContext.getMemoryManager().toConstantAddress();
    }

    public long toAtomicAddress() {
        return deviceContext.getMemoryManager().toAtomicAddress();
    }

}
