/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2019, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
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
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.mm;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;

import uk.ac.manchester.tornado.api.exceptions.TornadoOutOfMemoryException;
import uk.ac.manchester.tornado.api.mm.TornadoMemoryProvider;
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContext;
import uk.ac.manchester.tornado.drivers.opencl.OpenCL;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLMemFlags;
import uk.ac.manchester.tornado.drivers.opencl.graal.backend.OCLBackend;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.tasks.meta.ScheduleMetaData;

public class OCLMemoryManager extends TornadoLogger implements TornadoMemoryProvider {

    private final ScheduleMetaData scheduleMeta;
    private final long callStackLimit;
    private long callStackPosition;
    private long deviceBufferAddress;
    private final OCLDeviceContext deviceContext;
    private long deviceHeapPointer;
    private long heapLimit;
    private long heapPosition;
    private boolean initialised;

    public static final int STACK_ALIGNMENT_SIZE = 32;

    public OCLMemoryManager(final OCLDeviceContext device) {
        deviceContext = device;
        callStackLimit = OpenCL.OCL_CALL_STACK_LIMIT;
        initialised = false;
        scheduleMeta = new ScheduleMetaData("mm-" + device.getDeviceId());
        reset();
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
    public long getCallStackSize() {
        return callStackLimit;
    }

    @Override
    public long getHeapAllocated() {
        return heapPosition - callStackLimit;
    }

    @Override
    public long getHeapRemaining() {
        return heapLimit - heapPosition;
    }

    public final void reset() {
        callStackPosition = 0;
        heapPosition = callStackLimit;
        Tornado.info("Reset heap @ 0x%x (%s) on %s", deviceBufferAddress, RuntimeUtilities.humanReadableByteCount(heapLimit, true), deviceContext.getDevice().getDeviceName());
    }

    @Override
    public long getHeapSize() {
        return heapLimit - callStackLimit;
    }

    private static long align(final long address, final long alignment) {
        long newAddress = (address % alignment == 0) ? address : address + (alignment - address % alignment);
        return newAddress;
    }

    public long tryAllocate(final Class<?> type, final long bytes, final int headerSize, int alignment) throws TornadoOutOfMemoryException {
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

    public OCLCallStack createCallStack(final int maxArgs) {

        OCLCallStack callStack = new OCLCallStack(callStackPosition, maxArgs, deviceContext);

        if (callStackPosition + callStack.getSize() < callStackLimit) {
            callStackPosition = align(callStackPosition + callStack.getSize(), STACK_ALIGNMENT_SIZE);
        } else {
            callStack = null;
            fatal("Out of call-stack memory");
            System.exit(-1);
        }

        return callStack;
    }

    public long getBytesRemaining() {
        return heapLimit - heapPosition;
    }

    /**
     * * Returns sub-buffer that can be use to access a region managed by the
     * memory manager.
     *
     * @param offset
     *            offset within the memory managers heap
     * @param length
     *            size in bytes of the sub-buffer
     *
     * @return
     */
    public OCLByteBuffer getSubBuffer(final int offset, final int length) {
        return new OCLByteBuffer(deviceContext, offset, length);
    }

    /**
     * Allocate space on the device
     * 
     * @param numBytes
     */
    public void allocateRegion(long numBytes) {
        heapLimit = numBytes;
        // deviceHeapPointer =
        // deviceContext.getPlatformContext().createBuffer(OCLMemFlags.CL_MEM_READ_WRITE
        // | OCLMemFlags.CL_MEM_ALLOC_HOST_PTR, numBytes);
        deviceHeapPointer = deviceContext.getPlatformContext().createBuffer(OCLMemFlags.CL_MEM_READ_WRITE, numBytes);
    }

    public void init(OCLBackend backend, long address) {
        deviceBufferAddress = address;
        initialised = true;
        info("Located heap @ 0x%x (%s) on %s", deviceBufferAddress, RuntimeUtilities.humanReadableByteCount(heapLimit, false), deviceContext.getDevice().getDeviceName());
        scheduleMeta.setDevice(backend.getDeviceContext().asMapping());
    }

    public long toAbsoluteAddress() {
        return deviceBufferAddress;
    }

    public long toAbsoluteDeviceAddress(final long address) {
        long result = address;

        guarantee(address + deviceBufferAddress >= 0, "absolute address may have wrapped arround: %d + %d = %d", address, deviceBufferAddress, address + deviceBufferAddress);
        result += deviceBufferAddress;

        return result;
    }

    public long toBuffer() {
        return deviceHeapPointer;
    }

    public long toRelativeAddress() {
        return 0;
    }

    public long toRelativeDeviceAddress(final long address) {
        long result = address;
        if (!(Long.compareUnsigned(address, deviceBufferAddress) < 0 || Long.compareUnsigned(address, (deviceBufferAddress + heapLimit)) > 0)) {
            result -= deviceBufferAddress;
        }
        return result;
    }

    public boolean isInitialised() {
        return initialised;
    }
}
