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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.mm;

import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.OCL_CALL_STACK_LIMIT;

import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.exceptions.TornadoOutOfMemoryException;
import uk.ac.manchester.tornado.api.mm.ObjectBuffer;
import uk.ac.manchester.tornado.api.mm.TornadoMemoryProvider;
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContext;
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
    private long deviceBufferPosition;
    private long deviceBufferAddress;
    private final OCLDeviceContext deviceContext;
    private long deviceHeapPointer;
    private long constantPointer;
    private long atomicsRegion = -1;
    private long heapLimit;
    private long heapPosition;
    private boolean initialised;

    private static final int STACK_ALIGNMENT_SIZE = 128;

    private static final int MAX_NUMBER_OF_ATOMICS_PER_KERNEL = 128;
    private static final int INTEGER_BYTES_SIZE = 4;

    public OCLMemoryManager(final OCLDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
        callStackLimit = OCL_CALL_STACK_LIMIT;
        initialised = false;
        scheduleMeta = new ScheduleMetaData("mm-" + deviceContext.getDeviceId());
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

    @Override
    public final void reset() {
        callStackPosition = 0;
        deviceBufferPosition = 0;
        heapPosition = callStackLimit;
        Tornado.info("Reset heap @ 0x%x (%s) on %s", deviceBufferAddress, RuntimeUtilities.humanReadableByteCount(heapLimit, true), deviceContext.getDevice().getDeviceName());
    }

    @Override
    public long getHeapSize() {
        return heapLimit - callStackLimit;
    }

    private static long align(final long address, final long alignment) {
        return (address % alignment == 0) ? address : address + (alignment - address % alignment);
    }

    long tryAllocate(final long bytes, final int headerSize, int alignment) {
        final long alignedDataStart = align(heapPosition + headerSize, alignment);
        final long headerStart = alignedDataStart - headerSize;
        if (headerStart + bytes < heapLimit) {
            heapPosition = headerStart + bytes;
        } else {
            throw new TornadoOutOfMemoryException("Out of memory on the target device -> " + deviceContext.getDevice().getDeviceName() + ". [Heap Limit is: "
                    + RuntimeUtilities.humanReadableByteCount(heapLimit, true) + " and the application requires: " + RuntimeUtilities.humanReadableByteCount(headerStart + bytes, true)
                    + "]\nUse flag -Dtornado.heap.allocation=<XGB> to tune the device heap. E.g., -Dtornado.heap.allocation=2GB\n");
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

    public ObjectBuffer createDeviceBuffer(final int[] arr) {
        AtomicsBuffer atomicInteger = new AtomicsBuffer(arr, deviceContext);
        return atomicInteger;
    }

    public long getBytesRemaining() {
        return heapLimit - heapPosition;
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
     * @return {@link OCLByteBuffer}
     */
    public OCLByteBuffer getSubBuffer(final int offset, final int length) {
        return new OCLByteBuffer(deviceContext, offset, length);
    }

    /**
     * Allocate regions on the device.
     * 
     * @param numBytes
     *            Number of bytes to allocate in the global region.
     */
    public void allocateDeviceMemoryRegions(long numBytes) {
        this.heapLimit = numBytes;
        this.deviceHeapPointer = deviceContext.getPlatformContext().createBuffer(OCLMemFlags.CL_MEM_READ_WRITE | OCLMemFlags.CL_MEM_ALLOC_HOST_PTR, numBytes);
        this.constantPointer = deviceContext.getPlatformContext().createBuffer(OCLMemFlags.CL_MEM_READ_WRITE | OCLMemFlags.CL_MEM_ALLOC_HOST_PTR, 4);
        this.atomicsRegion = deviceContext.getPlatformContext().createBuffer(OCLMemFlags.CL_MEM_READ_WRITE | OCLMemFlags.CL_MEM_ALLOC_HOST_PTR, INTEGER_BYTES_SIZE * MAX_NUMBER_OF_ATOMICS_PER_KERNEL);
    }

    public void init(OCLBackend backend, long address) {
        deviceBufferAddress = address;
        initialised = true;
        info("Located heap @ 0x%x (%s) on %s", deviceBufferAddress, RuntimeUtilities.humanReadableByteCount(heapLimit, false), deviceContext.getDevice().getDeviceName());
        scheduleMeta.setDevice(backend.getDeviceContext().asMapping());
    }

    public long toAbsoluteDeviceAddress(final long address) {
        long result = address;

        TornadoInternalError.guarantee(address + deviceBufferAddress >= 0, "absolute address may have wrapped around: %d + %d = %d", address, deviceBufferAddress, address + deviceBufferAddress);
        result += deviceBufferAddress;

        return result;
    }

    long toBuffer() {
        return deviceHeapPointer;
    }

    long toConstantAddress() {
        return constantPointer;
    }

    long toAtomicAddress() {
        return atomicsRegion;
    }

    void allocateAtomicRegion() {
        if (this.atomicsRegion == -1) {
            this.atomicsRegion = deviceContext.getPlatformContext().createBuffer(OCLMemFlags.CL_MEM_READ_WRITE | OCLMemFlags.CL_MEM_ALLOC_HOST_PTR,
                    INTEGER_BYTES_SIZE * MAX_NUMBER_OF_ATOMICS_PER_KERNEL);
        }
    }

    public long toRelativeAddress() {
        return 0;
    }

    long toRelativeDeviceAddress(final long address) {
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
