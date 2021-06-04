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
 * Authors: James Clarkson, Juan Fumero
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.mm;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getVMConfig;
import static uk.ac.manchester.tornado.runtime.common.RuntimeUtilities.humanReadableByteCount;
import static uk.ac.manchester.tornado.runtime.common.Tornado.VALIDATE_ARRAY_HEADERS;
import static uk.ac.manchester.tornado.runtime.common.Tornado.fatal;
import static uk.ac.manchester.tornado.runtime.common.Tornado.info;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.OPENCL_ARRAY_ALIGNMENT;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.api.exceptions.TornadoMemoryException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.mm.ObjectBuffer;
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContext;
import uk.ac.manchester.tornado.runtime.common.Tornado;

public abstract class OCLArrayWrapper<T> implements ObjectBuffer {

    private final int arrayHeaderSize;

    private final int arrayLengthOffset;

    private long bufferOffset;

    private long bytesToAllocate;

    protected final OCLDeviceContext deviceContext;

    private final JavaKind kind;
    private boolean onDevice;
    private boolean isFinal;
    private long batchSize;

    public OCLArrayWrapper(final OCLDeviceContext device, final JavaKind kind, long batchSize) {
        this(device, kind, false, batchSize);
    }

    public OCLArrayWrapper(final OCLDeviceContext device, final JavaKind kind, final boolean isFinal, long batchSize) {
        this.deviceContext = device;
        this.kind = kind;
        this.isFinal = isFinal;
        this.batchSize = batchSize;

        arrayLengthOffset = getVMConfig().arrayOopDescLengthOffset();
        arrayHeaderSize = getVMConfig().getArrayBaseOffset(kind);
        onDevice = false;
        bufferOffset = -1;
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
    public void allocate(Object value, long batchSize) {

        long newBufferSize = 0;
        if (batchSize > 0) {
            newBufferSize = sizeOfBatch(batchSize);
        }

        if ((batchSize > 0) && (bufferOffset != -1) && (newBufferSize < bytesToAllocate)) {
            bytesToAllocate = newBufferSize;
        }

        if (bufferOffset == -1) {
            final T hostArray = cast(value);
            if (batchSize <= 0) {
                bytesToAllocate = sizeOf(hostArray);
            } else {
                bytesToAllocate = sizeOfBatch(batchSize);
            }

            if (bytesToAllocate <= 0) {
                throw new TornadoMemoryException("[ERROR] Bytes Allocated <= 0: " + bytesToAllocate);
            }

            bufferOffset = deviceContext.getMemoryManager().tryAllocate(bytesToAllocate, arrayHeaderSize, getAlignment());

            if (Tornado.FULL_DEBUG) {
                info("allocated: array kind=%s, size=%s, length offset=%d, header size=%d, bo=0x%x", kind.getJavaName(), humanReadableByteCount(bytesToAllocate, true), arrayLengthOffset,
                        arrayHeaderSize, bufferOffset);
                info("allocated: %s", toString());
            }
        }

    }

    @Override
    public long size() {
        return bytesToAllocate;
    }

    /*
     * Retrieves a buffer that will contain the contents of the array header. The
     * header is also populated using the header from the given array.
     */
    private OCLByteBuffer buildArrayHeader(final int arraySize) {
        final OCLByteBuffer header = getArrayHeader();
        int index = 0;
        while (index < arrayLengthOffset) {
            header.buffer.put((byte) 0);
            index++;
        }
        header.buffer.putInt(arraySize);
        return header;
    }

    private OCLByteBuffer buildArrayHeaderBatch(final long arraySize) {
        final OCLByteBuffer header = getArrayHeader();
        int index = 0;
        while (index < arrayLengthOffset) {
            header.buffer.put((byte) 0);
            index++;
        }
        header.buffer.putLong(arraySize);
        return header;
    }

    @Override
    public int enqueueRead(final Object value, long hostOffset, final int[] events, boolean useDeps) {
        final T array = cast(value);
        if (array == null) {
            throw new TornadoRuntimeException("[ERROR] output data is NULL");
        }
        final int returnEvent;
        if (isFinal) {
            returnEvent = enqueueReadArrayData(toBuffer(), bufferOffset + arrayHeaderSize, bytesToAllocate - arrayHeaderSize, array, hostOffset, (useDeps) ? events : null);
        } else {
            returnEvent = enqueueReadArrayData(toBuffer(), bufferOffset + arrayHeaderSize, bytesToAllocate - arrayHeaderSize, array, hostOffset, (useDeps) ? events : null);
        }
        return useDeps ? returnEvent : -1;
    }

    /**
     * Copy data from the device to the main host.
     * 
     * @param bufferId
     *            Device Buffer ID
     * @param offset
     *            Offset within the device buffer
     * @param bytes
     *            Bytes to be copied back to the host
     * @param value
     *            Host array that resides the final data
     * @param waitEvents
     *            List of events to wait for.
     * @return Event information
     */
    abstract protected int enqueueReadArrayData(long bufferId, long offset, long bytes, T value, long hostOffset, int[] waitEvents);

    @Override
    public List<Integer> enqueueWrite(final Object value, long batchSize, long hostOffset, final int[] events, boolean useDeps) {
        final T array = cast(value);
        ArrayList<Integer> listEvents = new ArrayList<>();

        if (array == null) {
            throw new TornadoRuntimeException("ERROR] Data to be copied is NULL");
        }
        final int returnEvent;
        if (isFinal && onDevice) {
            returnEvent = enqueueWriteArrayData(toBuffer(), bufferOffset + arrayHeaderSize, bytesToAllocate - arrayHeaderSize, array, hostOffset, (useDeps) ? events : null);
        } else {
            // We first write the header for the object and then we write actual
            // buffer
            final int headerEvent;
            if (batchSize <= 0) {
                headerEvent = buildArrayHeader(Array.getLength(array)).enqueueWrite((useDeps) ? events : null);
            } else {
                headerEvent = buildArrayHeaderBatch(batchSize).enqueueWrite((useDeps) ? events : null);
            }
            returnEvent = enqueueWriteArrayData(toBuffer(), bufferOffset + arrayHeaderSize, bytesToAllocate - arrayHeaderSize, array, hostOffset, (useDeps) ? events : null);
            onDevice = true;
            // returnEvent = deviceContext.enqueueMarker(internalEvents);

            listEvents.add(headerEvent);
            listEvents.add(returnEvent);
        }
        return useDeps ? listEvents : null;
    }

    /**
     * Copy data that resides in the host to the target device.
     * 
     * @param bufferId
     *            Device Buffer ID
     * @param offset
     *            Offset within the device buffer
     * @param bytes
     *            Bytes to be copied
     * @param value
     *            Host array to be copied
     * 
     * @param waitEvents
     *            List of events to wait for.
     * @return Event information
     */
    abstract protected int enqueueWriteArrayData(long bufferId, long offset, long bytes, T value, long hostOffset, int[] waitEvents);

    @Override
    public int getAlignment() {
        return OPENCL_ARRAY_ALIGNMENT;
    }

    private OCLByteBuffer getArrayHeader() {
        final OCLByteBuffer header = deviceContext.getMemoryManager().getSubBuffer((int) getBufferOffset(), arrayHeaderSize);
        header.buffer.clear();
        return header;
    }

    @Override
    public long getBufferOffset() {
        return bufferOffset;
    }

    @Override
    public boolean isValid() {
        return onDevice;
    }

    @Override
    public void invalidate() {
        onDevice = false;
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
    public void read(final Object value) {
        // TODO: reading with offset != 0
        read(value, 0, null, false);
    }

    /**
     * Read an buffer from the target device to the host.
     * 
     * @param value
     *            in which the data are copied
     * @param hostOffset
     *            offset, in bytes, from the input value in which to perform the
     *            read.
     * @param events
     *            list of pending events.
     * @param useDeps
     *            flag to indicate dependencies should be carried for the next
     *            operation.
     */
    @Override
    public int read(final Object value, long hostOffset, int[] events, boolean useDeps) {
        final T array = cast(value);
        if (array == null) {
            throw new TornadoRuntimeException("[ERROR] output data is NULL");
        }

        if (VALIDATE_ARRAY_HEADERS) {
            if (validateArrayHeader(array)) {
                return readArrayData(toBuffer(), bufferOffset + arrayHeaderSize, bytesToAllocate - arrayHeaderSize, array, hostOffset, (useDeps) ? events : null);
            } else {
                shouldNotReachHere("Array header is invalid");
            }
        } else {
            return readArrayData(toBuffer(), bufferOffset + arrayHeaderSize, bytesToAllocate - arrayHeaderSize, array, hostOffset, (useDeps) ? events : null);
        }
        return -1;
    }

    abstract protected int readArrayData(long bufferId, long offset, long bytes, T value, long hostOffset, int[] waitEvents);

    private long sizeOf(final T array) {
        return (long) arrayHeaderSize + ((long) Array.getLength(array) * (long) kind.getByteCount());
    }

    private long sizeOfBatch(long batchSize) {
        return (long) arrayHeaderSize + batchSize;
    }

    @Override
    public long toAbsoluteAddress() {
        return deviceContext.getMemoryManager().toAbsoluteDeviceAddress(bufferOffset);
    }

    @Override
    public long toBuffer() {
        return deviceContext.getMemoryManager().toBuffer();
    }

    @Override
    public long toRelativeAddress() {
        return bufferOffset;
    }

    @Override
    public String toString() {
        return String.format("buffer<%s> %s @ 0x%x (0x%x)", kind.getJavaName(), humanReadableByteCount(bytesToAllocate, true), toAbsoluteAddress(), toRelativeAddress());
    }

    @Override
    public void printHeapTrace() {
        System.out.printf("0x%x\ttype=%s\n", toAbsoluteAddress(), kind.getJavaName());

    }

    /*
     * Retrieves a buffer that will contain the contents of the array header. This
     * also re-sizes the buffer.
     */
    private boolean validateArrayHeader(final T array) {
        final OCLByteBuffer header = prepareArrayHeader();
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
    public void write(final Object value) {
        final T array = cast(value);
        if (array == null) {
            throw new TornadoRuntimeException("[ERROR] data is NULL");
        }
        buildArrayHeader(Array.getLength(array)).write();
        // TODO: Writing with offset != 0
        writeArrayData(toBuffer(), bufferOffset + arrayHeaderSize, bytesToAllocate - arrayHeaderSize, array, 0, null);
        onDevice = true;
    }

    abstract protected void writeArrayData(long bufferId, long offset, long bytes, T value, long hostOffset, int[] waitEvents);

}
