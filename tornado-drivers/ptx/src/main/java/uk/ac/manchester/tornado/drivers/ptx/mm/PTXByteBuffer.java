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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.ptx.mm;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

import uk.ac.manchester.tornado.drivers.ptx.PTXDeviceContext;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;

public class PTXByteBuffer {
    protected ByteBuffer buffer;
    private final long address;
    private final long bytes;
    private final long offset;
    protected final PTXDeviceContext deviceContext;

    /**
     * Native address of the host buffer when it is page-locked (pinned), or 0 when the
     * buffer is a plain heap {@link ByteBuffer}. When pinned, transfers use the direct
     * (host-pointer) async path and avoid the staged-copy stage-guard host sync that would
     * otherwise serialise small per-launch writes in multi-stream mode.
     */
    private final long pinnedHostAddress;

    public PTXByteBuffer(long address, long bytes, long offset, PTXDeviceContext deviceContext) {
        this(address, bytes, offset, deviceContext, false);
    }

    /**
     * @param pinHostBuffer when {@code true}, back the host buffer with off-heap memory that is
     *     registered as pinned ({@code cuMemHostRegister}) so async DMA is non-blocking. Use only
     *     for long-lived, frequently-reused buffers (e.g. the kernel stack frame) - registration is
     *     a one-off cost that must not be paid per transient buffer.
     */
    public PTXByteBuffer(long address, long bytes, long offset, PTXDeviceContext deviceContext, boolean pinHostBuffer) {
        this.address = address;
        this.bytes = bytes;
        this.offset = offset;
        this.deviceContext = deviceContext;

        if (pinHostBuffer) {
            this.buffer = ByteBuffer.allocateDirect((int) bytes);
            this.buffer.order(deviceContext.getByteOrder());
            this.pinnedHostAddress = MemorySegment.ofBuffer(this.buffer).address();
            deviceContext.getDevice().getPTXContext().registerHostMemory(this.pinnedHostAddress, bytes);
        } else {
            this.buffer = ByteBuffer.allocate((int) bytes);
            this.buffer.order(deviceContext.getByteOrder());
            this.pinnedHostAddress = 0L;
        }
    }

    /** Unregisters the pinned host buffer, if any. Call before the buffer is discarded. */
    protected void unpinHostBuffer() {
        if (pinnedHostAddress != 0L) {
            deviceContext.getDevice().getPTXContext().unregisterHostMemory(pinnedHostAddress);
        }
    }

    public long getSize() {
        return bytes;
    }

    public void read(long executionPlanId) {
        read(executionPlanId, null);
    }

    private void read(long executionPlanId, int[] events) {
        if (pinnedHostAddress != 0L) {
            deviceContext.readBuffer(executionPlanId, getAddress() + offset, bytes, pinnedHostAddress, 0, events);
        } else {
            deviceContext.readBuffer(executionPlanId, getAddress() + offset, bytes, buffer.array(), 0, events);
        }
    }

    public int getInt(int offset) {
        return buffer.getInt(offset);
    }

    public void dump(int width) {
        buffer.position(buffer.capacity());
        System.out.printf("Buffer  : capacity = %s, in use = %s, device = %s \n", RuntimeUtilities.humanReadableByteCount(bytes, true), RuntimeUtilities.humanReadableByteCount(buffer.position(),
                true), deviceContext.getDevice().getDeviceName());
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

    public long toAbsoluteAddress() {
        return getAddress() + offset;
    }

    public void write(long executionPlanId) {
        write(executionPlanId, null);
    }

    public void write(long executionPlanId, int[] events) {
        if (pinnedHostAddress != 0L) {
            deviceContext.writeBuffer(executionPlanId, getAddress() + offset, bytes, pinnedHostAddress, 0, events);
        } else {
            deviceContext.writeBuffer(executionPlanId, getAddress() + offset, bytes, buffer.array(), 0, events);
        }
    }

    protected long getAddress() {
        return address;
    }

    public int enqueueWrite(long executionPlanId) {
        return enqueueWrite(executionPlanId, null);
    }

    public int enqueueWrite(long executionPlanId, int[] events) {
        if (pinnedHostAddress != 0L) {
            return deviceContext.enqueueWriteBuffer(executionPlanId, getAddress() + offset, bytes, pinnedHostAddress, 0, events);
        }
        return deviceContext.enqueueWriteBuffer(executionPlanId, getAddress() + offset, bytes, buffer.array(), 0, events);
    }
}
