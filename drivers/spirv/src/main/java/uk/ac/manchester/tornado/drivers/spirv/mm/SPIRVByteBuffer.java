/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.spirv.mm;

import java.nio.ByteBuffer;

import uk.ac.manchester.tornado.drivers.spirv.SPIRVDeviceContext;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;

// FIXME <Refactor> <S>
public class SPIRVByteBuffer {

    protected ByteBuffer buffer;
    private long numBytes;
    private long offset;
    private SPIRVDeviceContext deviceContext;

    public SPIRVByteBuffer(SPIRVDeviceContext deviceContext, long offset, long numBytes) {
        this.numBytes = numBytes;
        this.offset = offset;
        this.deviceContext = deviceContext;
        buffer = ByteBuffer.allocate((int) numBytes);
        buffer.order(deviceContext.getDevice().getByteOrder());
    }

    public long getSize() {
        return this.numBytes;
    }

    public void read() {
        read(null);
    }

    private void read(int[] events) {
        // deviceContext.readBuffer(heapPointer() + offset, numBytes, buffer.array(), 0,
        // events);
    }

    private long heapPointer() {
        return deviceContext.getMemoryManager().toBuffer();
    }

    public int getInt(int offset) {
        return buffer.getInt(offset);
    }

    protected long toAbsoluteAddress() {
        return deviceContext.getMemoryManager().toAbsoluteDeviceAddress(offset);
    }

    public void write() {
        write(null);
    }

    public void write(int[] events) {
        deviceContext.enqueueWriteBuffer(toBuffer(), offset, numBytes, buffer.array(), 0, events);
        // deviceContext.enqueueWriteBuffer(heapPointer() + offset, numBytes,
        // buffer.array(), 0, events);
    }

    // FIXME <PENDING> enqueueWrite

    public ByteBuffer buffer() {
        return buffer;
    }

    // FIXME <REFACTOR> This method is common with the 3 backends
    public void dump(int width) {
        buffer.position(buffer.capacity());
        System.out.printf("Buffer  : capacity = %s, in use = %s, device = %s \n", RuntimeUtilities.humanReadableByteCount(numBytes, true),
                RuntimeUtilities.humanReadableByteCount(buffer.position(), true), deviceContext.getDevice().getDeviceName());
        for (int i = 0; i < buffer.position(); i += width) {
            System.out.printf("[0x%04x]: ", i + toAbsoluteAddress());
            for (int j = 0; j < Math.min(buffer.capacity() - i, width); j++) {
                if (j % 2 == 0) {
                    System.out.print(" ");
                }
                if (j < buffer.position() - i) {
                    System.out.printf("%02x", buffer.get(i + j));
                } else {
                    System.out.print("..");
                }
            }
            System.out.println();
        }
    }

    public SPIRVByteBuffer getSubBuffer(int offset, int numBytes) {
        return new SPIRVByteBuffer(deviceContext, offset, numBytes);
    }

    public long toBuffer() {
        return deviceContext.getMemoryManager().toBuffer();
    }

    public int enqueueRead() {
        return enqueueRead(null);
    }

    public int enqueueRead(final int[] events) {
        return deviceContext.enqueueReadBuffer(toBuffer(), offset, numBytes, buffer.array(), 0, events);
    }

    public int enqueueWrite() {
        return enqueueWrite(null);
    }

    public int enqueueWrite(int[] events) {
        // XXX: offset 0
        return deviceContext.enqueueWriteBuffer(toBuffer(), offset, numBytes, buffer.array(), 0, events);
    }

    public long toRelativeAddress() {
        return deviceContext.getMemoryManager().toRelativeDeviceAddress(offset);
    }
}
