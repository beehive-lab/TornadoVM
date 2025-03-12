/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2023, 2024, APT Group, Department of Computer Science,
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
 */
package uk.ac.manchester.tornado.drivers.spirv.mm;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.exceptions.TornadoMemoryException;
import uk.ac.manchester.tornado.api.exceptions.TornadoOutOfMemoryException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.memory.XPUBuffer;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.api.types.collections.TornadoCollectionInterface;
import uk.ac.manchester.tornado.api.types.images.TornadoImagesInterface;
import uk.ac.manchester.tornado.api.types.matrix.TornadoMatrixInterface;
import uk.ac.manchester.tornado.api.types.volumes.TornadoVolumesInterface;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDeviceContext;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.common.exceptions.TornadoUnsupportedError;

public class SPIRVMemorySegmentWrapper implements XPUBuffer {

    private static final int INIT_VALUE = -1;

    private final SPIRVDeviceContext spirvDeviceContext;
    private final long batchSize;
    private long bufferId;
    private long bufferOffset;
    private long bufferSize;
    private long subregionSize;
    private final Access access;
    private final int sizeOfType;

    public SPIRVMemorySegmentWrapper(long bufferSize, SPIRVDeviceContext deviceContext, long batchSize, Access access, int sizeOfBytes) {
        this.spirvDeviceContext = deviceContext;
        this.batchSize = batchSize;
        this.bufferSize = bufferSize;
        this.bufferId = INIT_VALUE;
        this.bufferOffset = 0;
        this.access = access;
        this.sizeOfType = sizeOfBytes;
        if (sizeOfBytes <= 0) {
            throw new TornadoRuntimeException("Invalid size of type " + sizeOfBytes);
        }
    }

    public SPIRVMemorySegmentWrapper(SPIRVDeviceContext deviceContext, long batchSize, Access access, int sizeOfBytes) {
        this(INIT_VALUE, deviceContext, batchSize, access, sizeOfBytes);
    }

    @Override
    public long toBuffer() {
        return this.bufferId;
    }

    @Override
    public void setBuffer(XPUBufferWrapper bufferWrapper) {
        this.bufferId = bufferWrapper.buffer;
        this.bufferOffset = bufferWrapper.bufferOffset;
        bufferWrapper.bufferOffset += bufferSize;
    }

    @Override
    public long getBufferOffset() {
        return bufferOffset;
    }

    @Override
    public void read(long executionPlanId, Object reference) {
        read(executionPlanId, reference, 0, 0, null, false);
    }

    private MemorySegment getSegmentWithHeader(final Object reference) {
        return switch (reference) {
            case TornadoNativeArray tornadoNativeArray -> tornadoNativeArray.getSegmentWithHeader();
            case TornadoCollectionInterface<?> tornadoCollectionInterface -> tornadoCollectionInterface.getSegmentWithHeader();
            case TornadoImagesInterface<?> imagesInterface -> imagesInterface.getSegmentWithHeader();
            case TornadoMatrixInterface<?> matrixInterface -> matrixInterface.getSegmentWithHeader();
            case TornadoVolumesInterface<?> volumesInterface -> volumesInterface.getSegmentWithHeader();
            default -> throw new TornadoMemoryException("Memory Segment not supported: " + reference.getClass());
        };
    }

    @Override
    public int read(long executionPlanId, Object reference, long hostOffset, long partialReadSize, int[] waitEvents, boolean useDeps) {
        MemorySegment segment = getSegmentWithHeader(reference);
        final int returnEvent;
        final long numBytes = getSizeSubRegionSize() > 0 ? getSizeSubRegionSize() : bufferSize;

        if (partialReadSize != 0) {
            // Partial Copy Out due to a copy under demand copy by the user
            // in this case the host offset is equal to the device offset
            returnEvent = spirvDeviceContext.readBuffer(executionPlanId, toBuffer(), hostOffset, partialReadSize, segment.address(), hostOffset, waitEvents);
        } else if (batchSize <= 0) {
            // Partial Copy Out due to batch processing
            returnEvent = spirvDeviceContext.readBuffer(executionPlanId, toBuffer(), bufferOffset, numBytes, segment.address(), hostOffset, waitEvents);
        } else {
            // Full copy out (default)
            returnEvent = spirvDeviceContext.readBuffer(executionPlanId, toBuffer(), TornadoOptions.PANAMA_OBJECT_HEADER_SIZE, numBytes, segment.address(),
                    hostOffset + TornadoOptions.PANAMA_OBJECT_HEADER_SIZE, waitEvents);
        }
        return returnEvent;
    }

    @Override
    public void write(long executionPlanId, Object reference) {
        MemorySegment segment = getSegmentWithHeader(reference);
        if (batchSize <= 0) {
            spirvDeviceContext.writeBuffer(executionPlanId, toBuffer(), bufferOffset, bufferSize, segment.address(), 0, null);
        } else {
            throw new TornadoUnsupportedError("[UNSUPPORTED] batch processing for writeBuffer operation");
        }
    }

    @Override
    public int enqueueRead(long executionPlanId, Object reference, long hostOffset, int[] waitEvents, boolean useDeps) {
        MemorySegment segment = getSegmentWithHeader(reference);
        final int returnEvent;
        final long numBytes = getSizeSubRegionSize() > 0 ? getSizeSubRegionSize() : bufferSize;
        if (batchSize <= 0) {
            returnEvent = spirvDeviceContext.enqueueReadBuffer(executionPlanId, toBuffer(), bufferOffset, numBytes, segment.address(), hostOffset, waitEvents);
        } else {
            throw new TornadoUnsupportedError("[UNSUPPORTED] batch processing for enqueueReadBuffer operation");
        }
        return returnEvent;
    }

    @Override
    public List<Integer> enqueueWrite(long executionPlanId, Object reference, long batchSize, long hostOffset, int[] events, boolean useDeps) {
        List<Integer> returnEvents = new ArrayList<>();
        MemorySegment segment = getSegmentWithHeader(reference);
        int internalEvent;
        if (batchSize <= 0) {
            internalEvent = spirvDeviceContext.enqueueWriteBuffer(executionPlanId, toBuffer(), bufferOffset, bufferSize, segment.address(), hostOffset, (useDeps) ? events : null);
        } else {
            internalEvent = spirvDeviceContext.enqueueWriteBuffer(executionPlanId, toBuffer(), 0, TornadoOptions.PANAMA_OBJECT_HEADER_SIZE, segment.address(), 0, (useDeps) ? events : null);
            returnEvents.add(internalEvent);
            internalEvent = spirvDeviceContext.enqueueWriteBuffer(executionPlanId, toBuffer(), bufferOffset + TornadoNativeArray.ARRAY_HEADER, bufferSize, segment.address(),
                    hostOffset + TornadoOptions.PANAMA_OBJECT_HEADER_SIZE, (useDeps) ? events : null);

        }
        returnEvents.add(internalEvent);
        return returnEvents;
    }

    @Override
    public void allocate(Object reference, long batchSize, Access access) throws TornadoOutOfMemoryException, TornadoMemoryException {
        MemorySegment memorySegment = getSegmentWithHeader(reference);
        if (batchSize <= 0 && memorySegment != null) {
            bufferSize = memorySegment.byteSize();
            bufferId = spirvDeviceContext.getBufferProvider().getOrAllocateBufferWithSize(bufferSize, access);
        } else {
            bufferSize = batchSize;
            bufferId = spirvDeviceContext.getBufferProvider().getOrAllocateBufferWithSize(bufferSize + TornadoNativeArray.ARRAY_HEADER, access);
        }

        if (bufferSize <= 0) {
            throw new TornadoMemoryException("[ERROR] Bytes Allocated <= 0: " + bufferSize);
        }

        if (TornadoOptions.FULL_DEBUG) {
            new TornadoLogger().info("allocated: %s", toString());
        }
    }

    @Override
    public void markAsFreeBuffer() throws TornadoMemoryException {
        TornadoInternalError.guarantee(bufferId != INIT_VALUE, "Fatal error: trying to deallocate an invalid buffer");
        spirvDeviceContext.getBufferProvider().markBufferReleased(bufferId, access);
        bufferId = INIT_VALUE;
        bufferSize = INIT_VALUE;
        if (TornadoOptions.FULL_DEBUG) {
            new TornadoLogger().info("allocated: %s", toString());
        }
    }

    @Override
    public long size() {
        return bufferSize;
    }

    @Override
    public void setSizeSubRegion(long batchSize) {
        this.subregionSize = batchSize;
    }

    @Override
    public long getSizeSubRegionSize() {
        return subregionSize;
    }

    @Override
    public long deallocate() {
        return spirvDeviceContext.getBufferProvider().deallocate(access);
    }

    @Override
    public void mapOnDeviceMemoryRegion(long executionPlanId, XPUBuffer srcPointer, long offset) {
        if (!(srcPointer instanceof SPIRVMemorySegmentWrapper spirvMemorySegmentWrapper)) {
            throw new TornadoRuntimeException("[ERROR] copy pointer must be an instance of OCLMemorySegmentWrapper: " + srcPointer);
        }
        final long sizeSource = spirvMemorySegmentWrapper.bufferSize;
        final long sizeDest = bufferSize;
        this.bufferId = spirvDeviceContext.mapOnDeviceMemoryRegion(executionPlanId, this.bufferId, spirvMemorySegmentWrapper.bufferId, offset, sizeOfType, sizeSource, sizeDest);
    }

    @Override
    public int getSizeOfType() {
        return sizeOfType;
    }

}
