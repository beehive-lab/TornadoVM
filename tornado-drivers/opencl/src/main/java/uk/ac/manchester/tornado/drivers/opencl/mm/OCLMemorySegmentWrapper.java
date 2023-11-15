/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2023, APT Group, Department of Computer Science,
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
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.mm;

import static uk.ac.manchester.tornado.runtime.common.Tornado.info;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

import uk.ac.manchester.tornado.api.types.collections.VectorDouble2;
import uk.ac.manchester.tornado.api.types.collections.VectorDouble3;
import uk.ac.manchester.tornado.api.types.collections.VectorDouble4;
import uk.ac.manchester.tornado.api.types.collections.VectorDouble8;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat2;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat3;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat4;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat8;
import uk.ac.manchester.tornado.api.types.collections.VectorInt2;
import uk.ac.manchester.tornado.api.types.collections.VectorInt3;
import uk.ac.manchester.tornado.api.types.collections.VectorInt4;
import uk.ac.manchester.tornado.api.types.collections.VectorInt8;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.CharArray;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.exceptions.TornadoMemoryException;
import uk.ac.manchester.tornado.api.exceptions.TornadoOutOfMemoryException;
import uk.ac.manchester.tornado.api.memory.ObjectBuffer;
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContext;
import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.common.exceptions.TornadoUnsupportedError;

public class OCLMemorySegmentWrapper implements ObjectBuffer {

    private static final int INIT_VALUE = -1;
    private final OCLDeviceContext deviceContext;
    private final long batchSize;
    private long bufferId;
    private long bufferOffset;
    private boolean onDevice;
    private long bufferSize;

    private long subregionSize;

    public OCLMemorySegmentWrapper(OCLDeviceContext deviceContext, long batchSize) {
        this.deviceContext = deviceContext;
        this.batchSize = batchSize;
        this.bufferSize = INIT_VALUE;
        this.bufferId = INIT_VALUE;
        this.bufferOffset = 0;
        onDevice = false;
    }

    public OCLMemorySegmentWrapper(long bufferSize, OCLDeviceContext deviceContext, long batchSize) {
        this.deviceContext = deviceContext;
        this.batchSize = batchSize;
        this.bufferSize = bufferSize;
        this.bufferId = INIT_VALUE;
        this.bufferOffset = 0;
        onDevice = false;
    }

    @Override
    public long toBuffer() {
        return this.bufferId;
    }

    @Override
    public void setBuffer(ObjectBufferWrapper bufferWrapper) {
        this.bufferId = bufferWrapper.buffer;
        this.bufferOffset = bufferWrapper.bufferOffset;

        bufferWrapper.bufferOffset += bufferSize;
    }

    @Override
    public long getBufferOffset() {
        return bufferOffset;
    }

    @Override
    public void read(final Object reference) {
        read(reference, 0, null, false);
    }

    private MemorySegment getSegment(final Object reference) {
        return switch (reference) {
            case IntArray intArray -> intArray.getSegment();
            case FloatArray floatArray -> floatArray.getSegment();
            case DoubleArray doubleArray -> doubleArray.getSegment();
            case LongArray longArray -> longArray.getSegment();
            case ShortArray shortArray -> shortArray.getSegment();
            case ByteArray byteArray -> byteArray.getSegment();
            case CharArray charArray -> charArray.getSegment();
            case VectorFloat2 vectorFloat2 -> vectorFloat2.getArray().getSegment();
            case VectorFloat3 vectorFloat3 -> vectorFloat3.getArray().getSegment();
            case VectorFloat4 vectorFloat4 -> vectorFloat4.getArray().getSegment();
            case VectorFloat8 vectorFloat8 -> vectorFloat8.getArray().getSegment();
            case VectorDouble2 vectorDouble2 -> vectorDouble2.getArray().getSegment();
            case VectorDouble3 vectorDouble3 -> vectorDouble3.getArray().getSegment();
            case VectorDouble4 vectorDouble4 -> vectorDouble4.getArray().getSegment();
            case VectorDouble8 vectorDouble8 -> vectorDouble8.getArray().getSegment();
            case VectorInt2 vectorInt2 -> vectorInt2.getArray().getSegment();
            case VectorInt3 vectorInt3 -> vectorInt3.getArray().getSegment();
            case VectorInt4 vectorInt4 -> vectorInt4.getArray().getSegment();
            case VectorInt8 vectorInt8 -> vectorInt8.getArray().getSegment();
            default -> (MemorySegment) reference;
        };
    }

    @Override
    public int read(final Object reference, long hostOffset, int[] events, boolean useDeps) {
        MemorySegment segment;
        segment = getSegment(reference);

        final int returnEvent;
        final long numBytes = getSizeSubRegionSize() > 0 ? getSizeSubRegionSize() : bufferSize;
        if (batchSize <= 0) {
            returnEvent = deviceContext.readBuffer(toBuffer(), bufferOffset, numBytes, segment.address(), hostOffset, (useDeps) ? events : null);
        } else {
            returnEvent = deviceContext.readBuffer(toBuffer(), TornadoNativeArray.ARRAY_HEADER, numBytes, segment.address(), hostOffset + TornadoNativeArray.ARRAY_HEADER, (useDeps) ? events : null);
        }

        return useDeps ? returnEvent : -1;
    }

    @Override

    public void write(Object reference) {
        MemorySegment segment;
        segment = getSegment(reference);
        if (batchSize <= 0) {
            deviceContext.writeBuffer(toBuffer(), bufferOffset, bufferSize, segment.address(), 0, null);
        } else {
            throw new TornadoUnsupportedError("[UNSUPPORTED] batch processing for writeBuffer operation");
        }
        onDevice = true;
    }

    @Override
    public int enqueueRead(Object reference, long hostOffset, int[] events, boolean useDeps) {
        MemorySegment segment;
        segment = getSegment(reference);

        final int returnEvent;
        if (batchSize <= 0) {
            returnEvent = deviceContext.enqueueReadBuffer(toBuffer(), bufferOffset, bufferSize, segment.address(), hostOffset, (useDeps) ? events : null);
        } else {
            throw new TornadoUnsupportedError("[UNSUPPORTED] batch processing for enqueueReadBuffer operation");
        }
        return useDeps ? returnEvent : -1;
    }

    @Override
    public List<Integer> enqueueWrite(Object reference, long batchSize, long hostOffset, int[] events, boolean useDeps) {
        List<Integer> returnEvents = new ArrayList<>();
        MemorySegment segment;
        segment = getSegment(reference);

        int internalEvent;
        if (batchSize <= 0) {
            internalEvent = deviceContext.enqueueWriteBuffer(toBuffer(), bufferOffset, bufferSize, segment.address(), hostOffset, (useDeps) ? events : null);
        } else {
            internalEvent = deviceContext.enqueueWriteBuffer(toBuffer(), 0, TornadoNativeArray.ARRAY_HEADER, segment.address(), 0, (useDeps) ? events : null);
            returnEvents.add(internalEvent);
            internalEvent = deviceContext.enqueueWriteBuffer(toBuffer(), bufferOffset + TornadoNativeArray.ARRAY_HEADER, bufferSize, segment.address(), hostOffset + TornadoNativeArray.ARRAY_HEADER,
                    (useDeps) ? events : null);
        }
        returnEvents.add(internalEvent);
        onDevice = true;
        return useDeps ? returnEvents : null;
    }

    @Override
    public void allocate(Object reference, long batchSize) throws TornadoOutOfMemoryException, TornadoMemoryException {
        MemorySegment segment;
        segment = getSegment(reference);

        if (batchSize <= 0) {
            bufferSize = segment.byteSize();
            bufferId = deviceContext.getBufferProvider().getBufferWithSize(bufferSize);
        } else {
            bufferSize = batchSize;
            bufferId = deviceContext.getBufferProvider().getBufferWithSize(bufferSize + TornadoNativeArray.ARRAY_HEADER);
        }

        if (bufferSize <= 0) {
            throw new TornadoMemoryException("[ERROR] Bytes Allocated <= 0: " + bufferSize);
        }

        if (Tornado.FULL_DEBUG) {
            info("allocated: %s", toString());
        }
    }

    @Override
    public void deallocate() throws TornadoMemoryException {
        TornadoInternalError.guarantee(bufferId != INIT_VALUE, "Fatal error: trying to deallocate an invalid buffer");
        deviceContext.getBufferProvider().markBufferReleased(bufferId, bufferSize);
        bufferId = INIT_VALUE;
        bufferSize = INIT_VALUE;

        if (Tornado.FULL_DEBUG) {
            info("deallocated: %s", toString());
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

    public long getBatchSize() {
        return batchSize;
    }
}
