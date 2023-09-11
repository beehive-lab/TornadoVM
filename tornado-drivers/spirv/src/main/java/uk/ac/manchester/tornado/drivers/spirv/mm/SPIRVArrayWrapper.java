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

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.runtime.common.RuntimeUtilities.humanReadableByteCount;
import static uk.ac.manchester.tornado.runtime.common.Tornado.VALIDATE_ARRAY_HEADERS;
import static uk.ac.manchester.tornado.runtime.common.Tornado.fatal;
import static uk.ac.manchester.tornado.runtime.common.Tornado.info;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.exceptions.TornadoMemoryException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.memory.ObjectBuffer;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDeviceContext;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.Tornado;

public abstract class SPIRVArrayWrapper<T> implements ObjectBuffer {

    private static final int INIT_VALUE = -1;
    protected final SPIRVDeviceContext deviceContext;
    private final int arrayHeaderSize;
    private final int arrayLengthOffset;
    private final JavaKind kind;
    private final long batchSize;
    private long bufferId;
    private long bufferOffset;
    private long bufferSize;
    private long setSubRegionSize;

    public SPIRVArrayWrapper(SPIRVDeviceContext deviceContext, JavaKind javaKind, long batchSize) {
        this.deviceContext = deviceContext;
        this.kind = javaKind;
        this.batchSize = batchSize;
        this.bufferId = INIT_VALUE;
        this.bufferSize = INIT_VALUE;
        this.bufferOffset = 0;

        this.arrayLengthOffset = TornadoCoreRuntime.getVMConfig().arrayOopDescLengthOffset();
        this.arrayHeaderSize = TornadoCoreRuntime.getVMConfig().getArrayBaseOffset(kind);
    }

    protected SPIRVArrayWrapper(final T array, final SPIRVDeviceContext device, final JavaKind kind, long batchSize) {
        this(device, kind, batchSize);
        bufferSize = sizeOf(array);
    }

    public long getBatchSize() {
        return batchSize;
    }

    // FIXME <REFACTOR> <Common for ALl backends>
    private T cast(Object array) {
        try {
            return (T) array;
        } catch (Exception | Error e) {
            throw new RuntimeException("Unable to cast object: " + e.getMessage());
        }
    }

    protected abstract int readArrayData(long bufferId, long offset, long bytes, T value, long hostOffset, int[] waitEvents);

    protected abstract void writeArrayData(long bufferId, long offset, long bytes, T value, long hostOffset, int[] waitEvents);

    protected abstract int enqueueReadArrayData(long bufferId, long offset, long bytes, T value, long hostOffset, int[] waitEvents);

    protected abstract int enqueueWriteArrayData(long bufferId, long offset, long bytes, T value, long hostOffset, int[] waitEvents);

    @Override
    public long toBuffer() {
        return bufferId;
    }

    @Override
    public void setBuffer(ObjectBufferWrapper bufferWrapper) {
        this.bufferId = bufferWrapper.buffer;
        this.bufferOffset = bufferWrapper.bufferOffset;

        bufferWrapper.bufferOffset += size();
    }

    @Override
    public long getBufferOffset() {
        return bufferOffset;
    }

    @Override
    public void read(Object object) {
        read(object, 0, null, false);
    }

    /*
     * Retrieves a buffer that will contain the contents of the array header. This
     * also re-sizes the buffer.
     */
    private SPIRVByteBuffer prepareArrayHeader() {
        final SPIRVByteBuffer header = getArrayHeader();
        header.buffer.position(header.buffer.capacity());
        return header;
    }

    // FIXME <REFACTOR> This is common for all three backends.
    /*
     * Retrieves a buffer that will contain the contents of the array header. This
     * also re-sizes the buffer.
     */
    private boolean validateArrayHeader(final T array) {
        final SPIRVByteBuffer header = prepareArrayHeader();
        header.read();
        final int numElements = header.getInt(arrayLengthOffset);
        final boolean valid = numElements == Array.getLength(array);
        if (!valid) {
            fatal("Array: expected=%d, got=%d", Array.getLength(array), numElements);
            header.dump(8);
        }
        return valid;
    }

    @Override
    public int read(Object reference, long hostOffset, int[] events, boolean useDeps) {
        final T array = cast(reference);
        if (array == null) {
            throw new TornadoRuntimeException("[ERROR] output data is NULL");
        }

        if (VALIDATE_ARRAY_HEADERS) {
            if (validateArrayHeader(array)) {
                return readArrayData(toBuffer(), bufferOffset + arrayHeaderSize, bufferSize - arrayHeaderSize, array, hostOffset, (useDeps) ? events : null);
            } else {
                shouldNotReachHere("Array header is invalid");
            }
        } else {
            final long numBytes = getSizeSubRegion() > 0 ? getSizeSubRegion() : (bufferSize - arrayHeaderSize);
            return readArrayData(toBuffer(), bufferOffset + arrayHeaderSize, numBytes, array, hostOffset, (useDeps) ? events : null);
        }
        return -1;
    }

    // FIXME <REFACTOR> <Same for all backends>
    private SPIRVByteBuffer getArrayHeader() {
        final SPIRVByteBuffer header = new SPIRVByteBuffer(deviceContext, bufferId, bufferOffset, arrayHeaderSize);
        header.buffer.clear();
        return header;
    }

    // FIXME <REFACTOR> <Same for all backends>
    private SPIRVByteBuffer buildArrayHeader(final int arraySize) {
        final SPIRVByteBuffer header = getArrayHeader();
        int index = 0;
        while (index < arrayLengthOffset) {
            header.buffer.put((byte) 0);
            index++;
        }
        header.buffer.putInt(arraySize);
        return header;
    }

    @Override
    public void write(final Object valueReference) {
        final T array = cast(valueReference);
        if (array == null) {
            throw new TornadoRuntimeException("[SPIRV][Error] data are NULL");
        }
        buildArrayHeader(Array.getLength(array));
        writeArrayData(toBuffer(), arrayHeaderSize + bufferOffset, bufferSize - arrayHeaderSize, array, 0, null);
    }

    // FIXME <REFACTOR> <S>
    @Override
    public int enqueueRead(Object objectReference, long hostOffset, int[] events, boolean useDeps) {
        final T array = cast(objectReference);
        if (array == null) {
            throw new TornadoRuntimeException("[ERROR] output data is NULL");
        }
        final int returnEvent;
        returnEvent = enqueueReadArrayData(toBuffer(), bufferOffset + arrayHeaderSize, bufferSize - arrayHeaderSize, array, hostOffset, (useDeps) ? events : null);
        return useDeps ? returnEvent : -1;
    }

    // FIXME <REFACTOR> <S>
    private SPIRVByteBuffer buildArrayHeaderBatch(final long arraySize) {
        final SPIRVByteBuffer header = getArrayHeader();
        int index = 0;
        while (index < arrayLengthOffset) {
            header.buffer.put((byte) 0);
            index++;
        }
        header.buffer.putInt((int) arraySize);
        return header;
    }

    // FIXME <REFACTOR> <S>
    @Override
    public List<Integer> enqueueWrite(Object reference, long batchSize, long hostOffset, int[] events, boolean useDeps) {
        final T array = cast(reference);
        ArrayList<Integer> listEvents = new ArrayList<>();

        if (array == null) {
            throw new TornadoRuntimeException("ERROR] Data to be copied is NULL");
        }
        final int returnEvent;
        // We first write the header for the object and then we write actual
        // buffer
        final int headerEvent;
        if (batchSize <= 0) {
            headerEvent = buildArrayHeader(Array.getLength(array)).enqueueWrite((useDeps) ? events : null);
        } else {
            headerEvent = buildArrayHeaderBatch(batchSize).enqueueWrite((useDeps) ? events : null);
        }
        returnEvent = enqueueWriteArrayData(toBuffer(), bufferOffset + arrayHeaderSize, bufferSize - arrayHeaderSize, array, hostOffset, (useDeps) ? events : null);

        listEvents.add(headerEvent);
        listEvents.add(returnEvent);
        return useDeps ? listEvents : null;
    }

    // FIXME <REFACTOR> <S>
    private long sizeOf(final T array) {
        return (long) arrayHeaderSize + ((long) Array.getLength(array) * (long) kind.getByteCount());
    }

    // FIXME <REFACTOR> <S>
    private long sizeOfBatch(long batchSize) {
        return (long) arrayHeaderSize + batchSize;
    }

    // FIXME <REFACTOR> <S>
    @Override
    public void allocate(Object objectReference, long batchSize) {
        final T hostArray = cast(objectReference);
        if (batchSize <= 0) {
            bufferSize = sizeOf(hostArray);
        } else {
            bufferSize = sizeOfBatch(batchSize);
        }

        if (bufferSize <= 0) {
            throw new TornadoMemoryException("[ERROR] Bytes Allocated <= 0: " + bufferSize);
        }

        this.bufferId = deviceContext.getBufferProvider().getBufferWithSize(bufferSize);

        if (Tornado.FULL_DEBUG) {
            info("allocated: array kind=%s, size=%s, length offset=%d, header size=%d", kind.getJavaName(), humanReadableByteCount(bufferSize, true), arrayLengthOffset, arrayHeaderSize);
            info("allocated: %s", toString());
        }

    }

    @Override
    public void deallocate() {
        TornadoInternalError.guarantee(bufferId != INIT_VALUE, "Fatal error: trying to deallocate an invalid buffer");

        deviceContext.getBufferProvider().markBufferReleased(bufferId, bufferSize);
        bufferId = INIT_VALUE;
        bufferSize = INIT_VALUE;

        if (Tornado.FULL_DEBUG) {
            info("deallocated: array kind=%s, size=%s, length offset=%d, header size=%d", kind.getJavaName(), humanReadableByteCount(bufferSize, true), arrayLengthOffset, arrayHeaderSize);
            info("deallocated: %s", toString());
        }
    }

    @Override
    public long size() {
        return bufferSize;
    }

    @Override
    public long getSizeSubRegion() {
        return setSubRegionSize;
    }

    @Override
    public void setSizeSubRegion(long batchSize) {
        this.setSubRegionSize = batchSize;
    }

}
