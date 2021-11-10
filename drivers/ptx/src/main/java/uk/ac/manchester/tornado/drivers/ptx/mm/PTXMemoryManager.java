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

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.PTX_CALL_STACK_LIMIT;

import uk.ac.manchester.tornado.api.exceptions.TornadoOutOfMemoryException;
import uk.ac.manchester.tornado.api.mm.TornadoMemoryProvider;
import uk.ac.manchester.tornado.drivers.ptx.PTXDeviceContext;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.tasks.meta.ScheduleMetaData;

public class PTXMemoryManager extends TornadoLogger implements TornadoMemoryProvider {

    private static final int STACK_ALIGNMENT_SIZE = 128;

    private long heapPosition;
    private long heapLimit;
    private PTXDeviceContext deviceContext;
    private long callStackPosition;
    private long callStackLimit;
    private long deviceHeapPointer;
    private boolean initialised;
    private ScheduleMetaData scheduleMeta;

    public PTXMemoryManager(PTXDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
        scheduleMeta = new ScheduleMetaData("mm-" + deviceContext.getDevice().getDeviceIndex());
        callStackLimit = PTX_CALL_STACK_LIMIT;
        initialised = false;
        reset();
    }

    @Override
    public void reset() {
        callStackPosition = 0;
        heapPosition = callStackLimit;
        Tornado.info("Reset heap @ 0x%x (%s) on %s", deviceHeapPointer, RuntimeUtilities.humanReadableByteCount(heapLimit, true), deviceContext.getDevice().getDeviceName());
    }

    @Override
    public long getCallStackSize() {
        return callStackLimit;
    }

    @Override
    public long getCallStackAllocated() {
        return callStackPosition;
    }

    @Override
    public long getCallStackRemaining() {
        return callStackLimit - callStackPosition;
    }

    @Override
    public long getHeapSize() {
        return heapLimit - callStackLimit;
    }

    @Override
    public long getHeapRemaining() {
        return heapLimit - heapPosition;
    }

    @Override
    public long getHeapAllocated() {
        return heapPosition - callStackLimit;
    }

    @Override
    public boolean isInitialised() {
        return initialised;
    }

    public PTXCallStack createCallStack(final int maxArgs) {
        PTXCallStack callStack = new PTXCallStack(callStackPosition, maxArgs, deviceContext);

        if (callStackPosition + callStack.getSize() < callStackLimit) {
            callStackPosition = align(callStackPosition + callStack.getSize(), STACK_ALIGNMENT_SIZE);
        } else {
            callStack = null;
            fatal("Out of call-stack memory");
            System.exit(-1);
        }
        return callStack;
    }

    public long tryAllocate(long bytes, int headerSize, int alignment) {
        final long alignedDataStart = align(heapPosition + headerSize, alignment);
        final long headerStart = alignedDataStart - headerSize;
        if (headerStart + bytes < heapLimit) {
            heapPosition = headerStart + bytes;
        } else {
            throw new TornadoOutOfMemoryException("Out of memory on the target device -> " + deviceContext.getDevice().getDeviceName() + ". [Heap Limit is: "
                    + RuntimeUtilities.humanReadableByteCount(heapLimit, true) + " and the application requires: " + RuntimeUtilities.humanReadableByteCount(headerStart + bytes, true) + "]");
        }
        return headerStart;
    }

    /**
     * Allocate space on the device
     *
     * @param numBytes
     */
    public void allocateRegion(long numBytes) {
        this.heapLimit = numBytes;
        // FIXME <REFACTOR> This allocate memory directly in the deviceContext
        this.deviceHeapPointer = deviceContext.getDevice().getPTXContext().allocateMemory(numBytes);
    }

    private static long align(final long address, final long alignment) {
        return (address % alignment == 0) ? address : address + (alignment - address % alignment);
    }

    public long toBuffer() {
        return deviceHeapPointer;
    }

    public long toAbsoluteDeviceAddress(long address) {
        long result = address;

        guarantee(address + deviceHeapPointer >= 0, "absolute address may have wrapped around: %d + %d = %d", address, deviceHeapPointer, address + deviceHeapPointer);
        result += deviceHeapPointer;

        return result;
    }

    /**
     * * Returns sub-buffer that can be use to access a region managed by the memory
     * manager.
     *
     * @param offset
     *            offset within the memory managers heap
     * @param length
     *            size in bytes of the sub-buffer
     *
     * @return
     */
    public PTXByteBuffer getSubBuffer(int offset, int length) {
        return new PTXByteBuffer(length, offset, deviceContext);
    }
}
