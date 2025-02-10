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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * , Juan Fumero
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.mm;

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
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContext;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

public abstract class OCLArrayWrapper<T> implements XPUBuffer {

    private static final int INIT_VALUE = -1;
    protected final OCLDeviceContext deviceContext;
    private final int arrayHeaderSize;
    private final int arrayLengthOffset;
    private final JavaKind kind;
    private final long batchSize;
    private long bufferId;
    private long bufferOffset;
    private long bufferSize;
    private long setSubRegionSize;
    private TornadoLogger logger;
    private Access access;

    protected OCLArrayWrapper(final OCLDeviceContext device, final JavaKind kind, long batchSize, Access access) {
        this.deviceContext = device;
        this.kind = kind;
        this.batchSize = batchSize;
        this.bufferId = INIT_VALUE;
        this.bufferSize = INIT_VALUE;
        this.bufferOffset = 0;
        this.access = access;

        arrayLengthOffset = getVMConfig().arrayOopDescLengthOffset();
        arrayHeaderSize = getVMConfig().getArrayBaseOffset(kind);
        logger = new TornadoLogger(this.getClass());
    }

    protected OCLArrayWrapper(final T array, final OCLDeviceContext device, final JavaKind kind, long batchSize, Access access) {
        this(device, kind, batchSize, access);
        bufferSize = sizeOf(array);
    }

    public long getBatchSize() {
        return batchSize;
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
    public void allocate(Object value, long batchSize, Access access) {
        final T hostArray = cast(value);
        if (batchSize <= 0) {
            bufferSize = sizeOf(hostArray);
        } else {
            bufferSize = sizeOfBatch(batchSize);
        }

        if (bufferSize <= 0) {
            throw new TornadoMemoryException("[ERROR] Bytes Allocated <= 0: " + bufferSize);
        }

        this.bufferId = deviceContext.getBufferProvider().getOrAllocateBufferWithSize(bufferSize, access);

        if (TornadoOptions.FULL_DEBUG) {
            logger.info("allocated: array kind=%s, size=%s, length offset=%d, header size=%d", kind.getJavaName(), humanReadableByteCount(bufferSize, true), arrayLengthOffset, arrayHeaderSize);
            logger.info("allocated: %s", toString());
        }

    }

    @Override
    public void markAsFreeBuffer() {
        TornadoInternalError.guarantee(bufferId != INIT_VALUE, "Fatal error: trying to deallocate an invalid buffer");

        deviceContext.getBufferProvider().markBufferReleased(bufferId, access);
        bufferId = INIT_VALUE;
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

    @Override
    public long size() {
        return bufferSize;
    }

    /*
     * Retrieves a buffer that will contain the contents of the array header. The
     * header is also populated using the header from the given array.
     */
    private OCLByteBuffer buildArrayHeader(final int arraySize) {
        final OCLByteBuffer header = getArrayHeader();
        for (int i = 0; i < arrayLengthOffset; i++) {
            header.buffer.put((byte) 0);
        }
        header.buffer.putInt(arraySize);
        return header;
    }

    private OCLByteBuffer buildArrayHeaderBatch(final long arraySize) {
        final OCLByteBuffer header = getArrayHeader();
        for (int i = 0; i < arrayLengthOffset; i++) {
            header.buffer.put((byte) 0);
        }
        header.buffer.putInt((int) arraySize);
        return header;
    }

    @Override
    public int enqueueRead(long executionPlanId, final Object value, long hostOffset, final int[] events, boolean useDeps) {
        final T array = cast(value);
        if (array == null) {
            throw new TornadoRuntimeException("[ERROR] output data is NULL");
        }
        final int returnEvent;
        // FIXME: <REFACTOR>
        returnEvent = enqueueReadArrayData(executionPlanId, toBuffer(), arrayHeaderSize + bufferOffset, bufferSize - arrayHeaderSize, array, hostOffset, (useDeps) ? events : null);
        return useDeps ? returnEvent : -1;
    }

    /**
     * Copy data from the device to the main host.
     *
     * @param bufferId
     *     Device Buffer ID
     * @param offset
     *     Offset within the device buffer
     * @param bytes
     *     Bytes to be copied back to the host
     * @param value
     *     Host array that resides the final data
     * @param waitEvents
     *     List of events to wait for.
     * @return Event information
     */
    protected abstract int enqueueReadArrayData(long executionPlanId, long bufferId, long offset, long bytes, T value, long hostOffset, int[] waitEvents);

    @Override
    public List<Integer> enqueueWrite(long executionPlanId, final Object value, long batchSize, long hostOffset, final int[] events, boolean useDeps) {
        final T array = cast(value);
        if (array == null) {
            throw new TornadoRuntimeException("ERROR] Data to be copied is NULL");
        }

        ArrayList<Integer> listEvents = new ArrayList<>();
        // We first write the header for the object, and then we write actual buffer
        final int headerEvent;
        if (batchSize <= 0) {
            headerEvent = buildArrayHeader(Array.getLength(array)).enqueueWrite(executionPlanId, (useDeps) ? events : null);
        } else {
            headerEvent = buildArrayHeaderBatch(batchSize).enqueueWrite(executionPlanId, (useDeps) ? events : null);
        }
        final int returnEvent = enqueueWriteArrayData(executionPlanId, toBuffer(), arrayHeaderSize + bufferOffset, bufferSize - arrayHeaderSize, array, hostOffset, (useDeps) ? events : null);

        listEvents.add(headerEvent);
        listEvents.add(returnEvent);
        return listEvents;
    }

    /**
     * Copy data that resides in the host to the target device.
     *
     * @param bufferId
     *     Device Buffer ID
     * @param offset
     *     Offset within the device buffer
     * @param bytes
     *     Bytes to be copied
     * @param value
     *     Host array to be copied
     *
     * @param waitEvents
     *     List of events to wait for.
     * @return Event information
     */
    protected abstract int enqueueWriteArrayData(long executionPlanId, long bufferId, long offset, long bytes, T value, long hostOffset, int[] waitEvents);

    private OCLByteBuffer getArrayHeader() {
        final OCLByteBuffer header = new OCLByteBuffer(deviceContext, bufferId, bufferOffset, arrayHeaderSize);
        header.buffer.clear();
        return header;
    }

    /*
     * Retrieves a buffer that will contain the contents of the array header. This
     * also re-sizes the buffer.
     */
    private OCLByteBuffer prepareArrayHeader() {
        final OCLByteBuffer header = getArrayHeader();
        header.buffer.position(header.buffer.capacity());
        return header;
    }

    @Override
    public void read(long executionPlanId, final Object value) {
        // TODO: reading with offset != 0
        read(executionPlanId, value, 0, 0, null, false);
    }

    /**
     * Read an buffer from the target device to the host.
     *
     * @param value
     *     in which the data are copied
     * @param hostOffset
     *     offset, in bytes, from the input value in which to perform the
     *     read.
     * @param events
     *     list of pending events.
     * @param useDeps
     *     flag to indicate dependencies should be carried for the next
     *     operation.
     */
    @Override
    public int read(long executionPlanId, final Object value, long hostOffset, long partialReadSize, int[] events, boolean useDeps) {
        final T array = cast(value);
        if (array == null) {
            throw new TornadoRuntimeException("[ERROR] output data is NULL");
        }
        final long numBytes = getSizeSubRegionSize() > 0 ? getSizeSubRegionSize() : (bufferSize - arrayHeaderSize);
        return readArrayData(executionPlanId, toBuffer(), arrayHeaderSize + bufferOffset, numBytes, array, hostOffset, (useDeps) ? events : null);
    }

    protected abstract int readArrayData(long executionPlanId, long bufferId, long offset, long bytes, T value, long hostOffset, int[] waitEvents);

    public long sizeOf(final T array) {
        return arrayHeaderSize + ((long) Array.getLength(array) * (long) kind.getByteCount());
    }

    private long sizeOfBatch(long batchSize) {
        return arrayHeaderSize + batchSize;
    }

    @Override
    public long toBuffer() {
        return bufferId;
    }

    @Override
    public void setBuffer(XPUBufferWrapper bufferWrapper) {
        this.bufferId = bufferWrapper.buffer;
        this.bufferOffset = bufferWrapper.bufferOffset;

        bufferWrapper.bufferOffset += size();
    }

    @Override
    public long getBufferOffset() {
        return bufferOffset;
    }

    @Override
    public String toString() {
        return String.format("buffer<%s> %s", kind.getJavaName(), humanReadableByteCount(bufferSize, true));
    }

    /*
     * Retrieves a buffer that will contain the contents of the array header. This
     * also re-sizes the buffer.
     */
    private boolean validateArrayHeader(long executionPlanId, final T array) {
        final OCLByteBuffer header = prepareArrayHeader();
        header.read(executionPlanId);
        final int numElements = header.getInt(arrayLengthOffset);
        final boolean valid = numElements == Array.getLength(array);
        if (!valid) {
            logger.fatal("Array: expected=%d, got=%d", Array.getLength(array), numElements);
            header.dump(8);
        }
        return valid;
    }

    @Override
    public void write(long executionPlanId, final Object value) {
        final T array = cast(value);
        if (array == null) {
            throw new TornadoRuntimeException("[ERROR] data is NULL");
        }
        buildArrayHeader(Array.getLength(array)).write(executionPlanId);
        // TODO: Writing with offset != 0
        writeArrayData(executionPlanId, toBuffer(), arrayHeaderSize + bufferOffset, bufferSize - arrayHeaderSize, array, 0, null);
    }

    protected abstract void writeArrayData(long executionPlanId, long bufferId, long offset, long bytes, T value, long hostOffset, int[] waitEvents);

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
