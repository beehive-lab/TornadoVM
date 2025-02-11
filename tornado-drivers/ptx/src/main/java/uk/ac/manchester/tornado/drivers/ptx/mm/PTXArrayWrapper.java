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

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getVMConfig;
import static uk.ac.manchester.tornado.runtime.common.RuntimeUtilities.humanReadableByteCount;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.exceptions.TornadoMemoryException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.memory.XPUBuffer;
import uk.ac.manchester.tornado.drivers.ptx.PTXDeviceContext;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

public abstract class PTXArrayWrapper<T> implements XPUBuffer {

    private static final int INIT_VALUE = -1;
    protected PTXDeviceContext deviceContext;
    private int arrayHeaderSize;
    private int arrayLengthOffset;
    private long buffer;
    private long bufferSize;
    private JavaKind kind;
    private long setSubRegionSize;
    private final TornadoLogger logger;
    private Access access;

    public PTXArrayWrapper(PTXDeviceContext deviceContext, JavaKind kind, Access access) {
        this.deviceContext = deviceContext;
        this.kind = kind;
        this.buffer = INIT_VALUE;
        this.bufferSize = INIT_VALUE;
        this.access = access;

        arrayHeaderSize = getVMConfig().getArrayBaseOffset(kind);
        arrayLengthOffset = getVMConfig().arrayOopDescLengthOffset();
        logger = new TornadoLogger(this.getClass());
    }

    @SuppressWarnings("unchecked")
    private T cast(Object array) {
        try {
            return (T) array;
        } catch (Exception | Error e) {
            shouldNotReachHere("[ERROR] Unable to cast object: " + e.getMessage());
        }
        return null;
    }

    @Override
    public long toBuffer() {
        return buffer;
    }

    @Override
    public void setBuffer(XPUBufferWrapper bufferWrapper) {
        TornadoInternalError.shouldNotReachHere();
    }

    @Override
    public long getBufferOffset() {
        return 0;
    }

    @Override
    public void read(long executionPlanId, Object reference) {
        read(executionPlanId, reference, 0, 0, null, false);
    }

    @Override
    public int read(long executionPlanId, Object reference, long hostOffset, long partialReadSize, int[] events, boolean useDeps) {
        T array = cast(reference);
        if (array == null) {
            throw new TornadoRuntimeException("[ERROR] output data is NULL");
        }
        final long numBytes = getSizeSubRegionSize() > 0 ? getSizeSubRegionSize() : (bufferSize - arrayHeaderSize);
        return readArrayData(executionPlanId, toBuffer() + arrayHeaderSize, numBytes, array, hostOffset, (useDeps) ? events : null);
    }

    private boolean validateArrayHeader(long executionPlanId, T array) {
        final PTXByteBuffer header = prepareArrayHeader();
        header.read(executionPlanId);
        final int numElements = header.getInt(arrayLengthOffset);
        final boolean valid = numElements == Array.getLength(array);
        if (!valid) {
            logger.fatal("Array: expected=%d, got=%d", Array.getLength(array), numElements);
            header.dump(8);
        }
        return valid;
    }

    private PTXByteBuffer getArrayHeader() {
        final PTXByteBuffer header = new PTXByteBuffer(buffer, arrayHeaderSize, 0, deviceContext);
        header.buffer.clear();
        return header;
    }

    private PTXByteBuffer prepareArrayHeader() {
        final PTXByteBuffer header = getArrayHeader();
        header.buffer.clear();
        header.buffer.position(header.buffer.capacity());
        return header;
    }

    @Override
    public void write(long executionPlanId, Object reference) {
        final T array = cast(reference);
        if (array == null) {
            throw new TornadoRuntimeException("[ERROR] data is NULL");
        }
        buildArrayHeader(Array.getLength(array)).write(executionPlanId);
        // TODO: Writing with offset != 0
        writeArrayData(executionPlanId, toBuffer() + arrayHeaderSize, bufferSize - arrayHeaderSize, array, 0, null);
    }

    private PTXByteBuffer buildArrayHeader(int arraySize) {
        final PTXByteBuffer header = getArrayHeader();
        header.buffer.clear();
        int index = 0;
        while (index < arrayLengthOffset) {
            header.buffer.put((byte) 0);
            index++;
        }
        header.buffer.putInt(arraySize);
        return header;
    }

    @Override
    public int enqueueRead(long executionPlanId, final Object value, long hostOffset, final int[] events, boolean useDeps) {
        final T array = cast(value);
        if (array == null) {
            throw new TornadoRuntimeException("[ERROR] output data is NULL");
        }
        return enqueueReadArrayData(executionPlanId, toBuffer() + arrayHeaderSize, bufferSize - arrayHeaderSize, array, hostOffset, (useDeps) ? events : null);
    }

    @Override
    public List<Integer> enqueueWrite(long executionPlanId, Object reference, long batchSize, long hostOffset, int[] events, boolean useDeps) {
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
            headerEvent = buildArrayHeader(Array.getLength(array)).enqueueWrite(executionPlanId, (useDeps) ? events : null);
        } else {
            headerEvent = buildArrayHeaderBatch(batchSize).enqueueWrite(executionPlanId, (useDeps) ? events : null);
        }
        returnEvent = enqueueWriteArrayData(executionPlanId, toBuffer() + arrayHeaderSize, bufferSize - arrayHeaderSize, array, hostOffset, (useDeps) ? events : null);

        listEvents.add(headerEvent);
        listEvents.add(returnEvent);
        return listEvents;
    }

    private PTXByteBuffer buildArrayHeaderBatch(long arraySize) {
        final PTXByteBuffer header = getArrayHeader();
        header.buffer.clear();
        int index = 0;
        while (index < arrayLengthOffset) {
            header.buffer.put((byte) 0);
            index++;
        }
        header.buffer.putInt((int) arraySize);
        return header;
    }

    @Override
    public void allocate(Object value, long batchSize, Access access) {
        final T hostArray = cast(value);
        if (batchSize <= 0) {
            bufferSize = sizeOf(hostArray);
        } else {
            bufferSize = arrayHeaderSize + batchSize;
        }

        if (bufferSize <= 0) {
            throw new TornadoMemoryException("[ERROR] Bytes Allocated <= 0: " + bufferSize);
        }

        this.buffer = deviceContext.getBufferProvider().getOrAllocateBufferWithSize(bufferSize, access);

        if (TornadoOptions.FULL_DEBUG) {
            logger.info("allocated: array kind=%s, size=%s, length offset=%d, header size=%d", kind.getJavaName(), humanReadableByteCount(bufferSize, true), arrayLengthOffset, arrayHeaderSize);
            logger.info("allocated: %s", toString());
        }
    }

    @Override
    public void markAsFreeBuffer() throws TornadoMemoryException {
        TornadoInternalError.guarantee(buffer != INIT_VALUE, "Fatal error: trying to deallocate an invalid buffer");

        deviceContext.getBufferProvider().markBufferReleased(buffer, access);
        buffer = INIT_VALUE;
        bufferSize = INIT_VALUE;

        if (TornadoOptions.FULL_DEBUG) {
            logger.info("deallocated: array kind=%s, size=%s, length offset=%d, header size=%d", kind.getJavaName(), humanReadableByteCount(bufferSize, true), arrayLengthOffset, arrayHeaderSize);
            logger.info("deallocated: %s", toString());
        }
    }

    @Override
    public long deallocate() {
        return deviceContext.getBufferProvider().deallocate(access);
    }

    private long sizeOf(final T array) {
        return (long) arrayHeaderSize + ((long) Array.getLength(array) * (long) kind.getByteCount());
    }

    @Override
    public long size() {
        return bufferSize;
    }

    /**
     * Copy data from the device to the main host.
     *
     * @param address
     *     Device Buffer address
     * @param bytes
     *     Bytes to be copied back to the host
     * @param value
     *     Host array that resides the final data
     * @param waitEvents
     *     List of events to wait for.
     * @return Event information
     */
    protected abstract int enqueueReadArrayData(long executionPlanId, long address, long bytes, T value, long hostOffset, int[] waitEvents);

    protected abstract int readArrayData(long executionPlanId, long address, long bytes, T value, long hostOffset, int[] waitEvents);

    /**
     * Copy data that resides in the host to the target device.
     *
     * @param address
     *     Device Buffer address
     * @param bytes
     *     Bytes to be copied
     * @param value
     *     Host array to be copied
     * @param waitEvents
     *     List of events to wait for.
     * @return Event information
     */
    protected abstract int enqueueWriteArrayData(long executionPlanId, long address, long bytes, T value, long hostOffset, int[] waitEvents);

    protected abstract void writeArrayData(long executionPlanId, long address, long bytes, T value, int hostOffset, int[] waitEvents);

    @Override
    public long getSizeSubRegionSize() {
        return setSubRegionSize;
    }

    @Override
    public void setSizeSubRegion(long batchSize) {
        this.setSubRegionSize = batchSize;
    }

    @Override
    public void mapOnDeviceMemoryRegion(long executionPlanId, XPUBuffer srcPointer, long offset) {
        throw new TornadoRuntimeException("[ERROR] not implemented");
    }
}
