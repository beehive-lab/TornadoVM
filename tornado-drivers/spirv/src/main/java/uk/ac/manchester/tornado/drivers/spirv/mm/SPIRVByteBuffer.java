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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
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

    private static final int BYTES_PER_INTEGER = 4;
    protected ByteBuffer buffer;
    private final long bufferId;
    protected final long bytes;
    private final long offset;
    protected SPIRVDeviceContext deviceContext;

    public SPIRVByteBuffer(final SPIRVDeviceContext deviceContext, final long bufferId, final long offset, final long numBytes) {
        this.deviceContext = deviceContext;
        this.bufferId = bufferId;
        this.bytes = numBytes;
        this.offset = offset;
        buffer = ByteBuffer.allocate((int) numBytes);
        buffer.order(this.deviceContext.getDevice().getByteOrder());
    }

    public long getSize() {
        return this.bytes;
    }

    public void read(long executionPlanId) {
        read(executionPlanId, null);
    }

    private void read(long executionPlanId, int[] events) {
        deviceContext.readBuffer(executionPlanId, toBuffer(), offset, bytes, buffer.array(), 0, events);
    }

    public int getInt(int offset) {
        return buffer.getInt(offset);
    }

    public long toBuffer() {
        return bufferId;
    }

    public long getOffset() {
        return offset;
    }

    public void write(long executionPlanId) {
        write(executionPlanId, null);
    }

    public void write(long executionPlanId, int[] events) {
        deviceContext.enqueueWriteBuffer(executionPlanId, toBuffer(), offset, bytes, buffer.array(), 0, events);
    }

    // FIXME <PENDING> enqueueWrite

    public ByteBuffer buffer() {
        return buffer;
    }

    // FIXME <REFACTOR> This method is common with the 3 backends
    public void dump(int width) {
        buffer.position(buffer.capacity());
        System.out.printf("Buffer  : capacity = %s, in use = %s, device = %s \n", RuntimeUtilities.humanReadableByteCount(bytes, true), RuntimeUtilities.humanReadableByteCount(buffer.position(),
                true), deviceContext.getDevice().getDeviceName());
        for (int i = 0; i < buffer.position(); i += width) {
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

    public int enqueueRead(long executionPlanId) {
        return enqueueRead(executionPlanId, null);
    }

    public int enqueueRead(long executionPlanId, final int[] events) {
        return deviceContext.enqueueReadBuffer(executionPlanId, toBuffer(), offset, bytes, buffer.array(), 0, events);
    }

    public int enqueueWrite(long executionPlanId) {
        return enqueueWrite(executionPlanId, null);
    }

    public int enqueueWrite(long executionPlanId, int[] events) {
        // XXX: offset 0
        return deviceContext.enqueueWriteBuffer(executionPlanId, toBuffer(), offset, bytes, buffer.array(), 0, events);
    }
}
