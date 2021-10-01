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

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;

import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoOutOfMemoryException;
import uk.ac.manchester.tornado.api.mm.TornadoMemoryProvider;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVBackend;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDeviceContext;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.tasks.meta.ScheduleMetaData;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

// FIXME <REFACTOR> This class can be almost common for all three backends
public class SPIRVMemoryManager implements TornadoMemoryProvider {

    private static final int STACK_ALIGNMENT_SIZE = 128;

    private long heapPosition;
    private long heapLimit;
    private SPIRVDeviceContext deviceContext;
    private long callStackPosition;
    private long callStackLimit;
    private long deviceHeapPointer; // Device pointer
    private long deviceBufferAddress; // Given by the loopUkBuffer
    private boolean initialized;
    private ScheduleMetaData scheduleMetadata;

    public SPIRVMemoryManager(SPIRVDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
        this.scheduleMetadata = new ScheduleMetaData("mm-" + deviceContext.getDevice().getDeviceIndex());
        callStackLimit = TornadoOptions.SPIRV_CALL_STACK_LIMIT;
        this.initialized = false;
        reset();
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
        return initialized;
    }

    @Override
    public void reset() {
        callStackPosition = 0;
        heapPosition = callStackLimit;
        Tornado.info("Reset heap @ 0x%x (%s) on %s", deviceHeapPointer, RuntimeUtilities.humanReadableByteCount(heapLimit, true), deviceContext.getDevice().getDeviceName());
    }

    public SPIRVCallStack createCallStack(final int maxArgs) {
        SPIRVCallStack callStack = new SPIRVCallStack(callStackPosition, maxArgs, deviceContext);
        if (callStackPosition + callStack.getSize() < callStackLimit) {
            callStackPosition = align(callStackPosition + callStack.getSize(), STACK_ALIGNMENT_SIZE);
        } else {
            throw new TornadoBailoutRuntimeException("[Deoptimizing] Out of call-stack memory");
        }
        return callStack;
    }

    private static long align(final long address, final long alignment) {
        return (address % alignment == 0) ? address : address + (alignment - address % alignment);
    }

    public long toBuffer() {
        return deviceHeapPointer;
    }

    public long toAbsoluteDeviceAddress(final long address) {
        long result = address;
        int compare = Long.compareUnsigned(address + deviceBufferAddress, 0);
        guarantee(compare >= 0, "absolute address may have wrapped around: %d + %d = %d", Long.toUnsignedString(address), Long.toUnsignedString(deviceHeapPointer),
                Long.toUnsignedString(address + deviceHeapPointer));
        result += deviceBufferAddress;
        return result;
    }

    public void allocateDeviceMemoryRegions(long numBytes) {
        this.heapLimit = numBytes;
        this.deviceHeapPointer = deviceContext.getSpirvContext().allocateMemory(deviceContext.getDevice().getDeviceIndex(), numBytes);
    }

    // FIXME <REFACTOR> <S>
    public SPIRVByteBuffer getSubBuffer(int bufferOffset, int numBytes) {
        return new SPIRVByteBuffer(deviceContext, bufferOffset, numBytes);
    }

    // FIXME <REFACTOR> <S>
    public long tryAllocate(long bytesToAllocate, int headerSize, int alignment) {
        final long alignedDataStart = align(heapPosition + headerSize, alignment);
        final long headerStart = alignedDataStart - headerSize;
        if (headerStart + bytesToAllocate < heapLimit) {
            heapPosition = headerStart + bytesToAllocate;
        } else {
            throw new TornadoOutOfMemoryException("Out of memory on the target device -> " + deviceContext.getDevice().getDeviceName() + ". [Heap Limit is: "
                    + RuntimeUtilities.humanReadableByteCount(heapLimit, true) + " and the application requires: " + RuntimeUtilities.humanReadableByteCount(headerStart + bytesToAllocate, true)
                    + "]\nUse flag -Dtornado.heap.allocation=<XGB> to tune the device heap. E.g., -Dtornado.heap.allocation=2GB\n");
        }
        return headerStart;
    }

    // FIXME <REFACTOR> same as OCL and SPIR-V backends
    public void init(SPIRVBackend spirvBackend, long baseHeapAddress) {
        this.deviceBufferAddress = baseHeapAddress;
        this.initialized = true;
        TornadoLogger.info("Located heap @ 0x%x (%s) on %s", deviceBufferAddress, RuntimeUtilities.humanReadableByteCount(heapLimit, false), deviceContext.getDevice().getDeviceName());
        scheduleMetadata.setDevice(spirvBackend.getDeviceContext().asMapping());
    }

    public long launchAndReadLookupBufferAddress(TaskMetaData meta) {
        return deviceContext.getSpirvContext().executeAndReadLookupBufferAddressKernel(meta);
    }

    public long toRelativeDeviceAddress(long address) {
        long result = address;
        if (!(Long.compareUnsigned(address, deviceBufferAddress) < 0 || Long.compareUnsigned(address, (deviceBufferAddress + heapLimit)) > 0)) {
            result -= deviceBufferAddress;
        }
        return result;
    }
}
