/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.ptx.mm;

import uk.ac.manchester.tornado.drivers.ptx.PTXDeviceContext;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;

import java.nio.ByteBuffer;

public class PTXByteBuffer {
    protected ByteBuffer buffer;
    private long bytes;
    private long offset;
    private PTXDeviceContext deviceContext;

    public PTXByteBuffer(long bytes, long offset, PTXDeviceContext deviceContext) {
        this.bytes = bytes;
        this.offset = offset;
        this.deviceContext = deviceContext;

        buffer = ByteBuffer.allocate((int) bytes);
        buffer.order(deviceContext.getByteOrder());
    }

    public long getSize() {
        return bytes;
    }

    public void read() {
        read(null);
    }

    private void read(int[] events) {
        deviceContext.readBuffer(heapPointer() + offset, bytes, buffer.array(), 0, events);
    }

    public int getInt(int offset) {
        return buffer.getInt(offset);
    }

    public void dump(int width) {
        buffer.position(buffer.capacity());
        System.out.printf("Buffer  : capacity = %s, in use = %s, device = %s \n", RuntimeUtilities.humanReadableByteCount(bytes, true),
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

    protected long toAbsoluteAddress() {
        return deviceContext.getMemoryManager().toAbsoluteDeviceAddress(offset);
    }

    public void write() {
        write(null);
    }

    public void write(int[] events) {
        deviceContext.writeBuffer(heapPointer() + offset, bytes, buffer.array(), 0, events);
    }

    private long heapPointer() {
        return deviceContext.getMemoryManager().toBuffer();
    }

    public int enqueueWrite() {
        return enqueueWrite(null);
    }

    public int enqueueWrite(int[] events) {
        return deviceContext.enqueueWriteBuffer(heapPointer() + offset, bytes, buffer.array(), 0, events);
    }

    public ByteBuffer buffer() {
        return buffer;
    }
}
