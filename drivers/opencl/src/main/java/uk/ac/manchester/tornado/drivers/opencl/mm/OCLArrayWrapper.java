/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.mm;

import static uk.ac.manchester.tornado.common.RuntimeUtilities.humanReadableByteCount;
import static uk.ac.manchester.tornado.common.Tornado.VALIDATE_ARRAY_HEADERS;
import static uk.ac.manchester.tornado.common.Tornado.fatal;
import static uk.ac.manchester.tornado.common.Tornado.getProperty;
import static uk.ac.manchester.tornado.common.Tornado.info;
import static uk.ac.manchester.tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;
import static tornado.runtime.TornadoRuntime.getVMConfig;

import java.lang.reflect.Array;

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.common.ObjectBuffer;
import uk.ac.manchester.tornado.common.exceptions.TornadoOutOfMemoryException;
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContext;

public abstract class OCLArrayWrapper<T> implements ObjectBuffer {

    private static final int ARRAY_ALIGNMENT = Integer.parseInt(getProperty("tornado.opencl.array.align", "128"));

    private final int arrayHeaderSize;

    private final int arrayLengthOffset;

    private long bufferOffset;

    private long bytes;

    protected final OCLDeviceContext deviceContext;

    private final JavaKind kind;
    private boolean onDevice;
    private boolean isFinal;

    // TODO remove this
    private final int[] internalEvents = new int[2];

    public OCLArrayWrapper(final OCLDeviceContext device, final JavaKind kind) {
        this(device, kind, false);
    }

    public OCLArrayWrapper(final OCLDeviceContext device, final JavaKind kind, final boolean isFinal) {
        this.deviceContext = device;
        this.kind = kind;
        this.isFinal = isFinal;

        arrayLengthOffset = getVMConfig().arrayOopDescLengthOffset();
        arrayHeaderSize = getVMConfig().getArrayBaseOffset(kind);
        onDevice = false;
        bufferOffset = -1;
    }

    @SuppressWarnings("unchecked")
    private T cast(Object array) {
        try {
            return (T) array;
        } catch (Exception | Error e) {
            shouldNotReachHere("Unable to cast object: " + e.getMessage());
        }
        return null;
    }

    @Override
    public void allocate(Object value) throws TornadoOutOfMemoryException {
        if (bufferOffset == -1) {
            final T ref = cast(value);
            bytes = sizeOf(ref);

            bufferOffset = deviceContext.getMemoryManager().tryAllocate(ref.getClass(), bytes, arrayHeaderSize, getAlignment());

            info("allocated: array kind=%s, size=%s, length offset=%d, header size=%d, bo=0x%x",
                    kind.getJavaName(), humanReadableByteCount(bytes, true),
                    arrayLengthOffset, arrayHeaderSize, bufferOffset);
            info("allocated: %s", toString());
        }
    }

    @Override
    public long size() {
        return bytes;
    }

    /*
     * Retrieves a buffer that will contain the contents of the array header.
     * The header is also populated using the header from the given array.
     */
    private OCLByteBuffer buildArrayHeader(final T array) {
        final OCLByteBuffer header = getArrayHeader();
        int index = 0;
        while (index < arrayLengthOffset) {
            header.buffer.put((byte) 0);
            index++;
        }
        header.buffer.putInt(Array.getLength(array));
        // header.dump(8);
        return header;
    }

    @Override
    public int enqueueRead(final Object value, final int[] events, boolean useDeps) {
        final T array = cast(value);
        final int returnEvent;
        if (isFinal) {
            returnEvent = enqueueReadArrayData(toBuffer(), bufferOffset + arrayHeaderSize, bytes
                    - arrayHeaderSize, array, (useDeps) ? events : null);
        } else {
//            int index = 0;
            internalEvents[1] = -1;
//            internalEvents[0] = prepareArrayHeader().enqueueRead(null);
            internalEvents[0] = enqueueReadArrayData(toBuffer(), bufferOffset + arrayHeaderSize, bytes
                    - arrayHeaderSize, array, (useDeps) ? events : null);
            returnEvent = internalEvents[0];//(index == 0) ? internalEvents[0] : deviceContext.enqueueMarker(internalEvents);
        }
        return useDeps ? returnEvent : -1;
    }

    abstract protected int enqueueReadArrayData(long bufferId, long offset, long bytes,
            T value, int[] waitEvents);

    @Override
    public int enqueueWrite(final Object value, final int[] events, boolean useDeps) {
        final T array = cast(value);
        final int returnEvent;
        if (isFinal && onDevice) {
            returnEvent = enqueueWriteArrayData(toBuffer(), bufferOffset + arrayHeaderSize, bytes
                    - arrayHeaderSize, array, (useDeps) ? events : null);
        } else {
            int index = 0;
            internalEvents[0] = -1;
            if (!onDevice || !isFinal) {
                internalEvents[0] = buildArrayHeader(array).enqueueWrite(
                        (useDeps) ? events : null);
                index++;
            }
            internalEvents[index] = enqueueWriteArrayData(toBuffer(), bufferOffset + arrayHeaderSize, bytes
                    - arrayHeaderSize, array, (useDeps) ? events : null);
            onDevice = true;
            returnEvent = (index == 0) ? internalEvents[0] : deviceContext.enqueueMarker(internalEvents);

        }
        return useDeps ? returnEvent : -1;
    }

    abstract protected int enqueueWriteArrayData(long bufferId, long offset, long bytes,
            T value, int[] waitEvents);

    @Override
    public int getAlignment() {
        return ARRAY_ALIGNMENT;
    }

    private OCLByteBuffer getArrayHeader() {
        final OCLByteBuffer header = deviceContext.getMemoryManager().getSubBuffer(
                (int) bufferOffset, arrayHeaderSize);
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
     * Retrieves a buffer that will contain the contents of the array header.
     * This also re-sizes the buffer.
     */
    private OCLByteBuffer prepareArrayHeader() {
        final OCLByteBuffer header = getArrayHeader();
        header.buffer.position(header.buffer.capacity());
        return header;
    }

    @Override
    public void read(final Object value) {
        read(value, null, false);
    }

    @Override
    public void read(final Object value, int[] events, boolean useDeps) {
        final T array = cast(value);

        if (VALIDATE_ARRAY_HEADERS) {
            if (validateArrayHeader(array)) {
                readArrayData(toBuffer(), bufferOffset + arrayHeaderSize, bytes - arrayHeaderSize,
                        array, (useDeps) ? events : null);
            } else {
                shouldNotReachHere("Array header is invalid");
            }
        } else {
            readArrayData(toBuffer(), bufferOffset + arrayHeaderSize, bytes - arrayHeaderSize,
                    array, (useDeps) ? events : null);
        }
    }

    abstract protected void readArrayData(long bufferId, long offset, long bytes, T value,
            int[] waitEvents);

    private int sizeOf(final T array) {
        return arrayHeaderSize + (Array.getLength(array) * kind.getByteCount());
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
        return String.format("buffer<%s> %s @ 0x%x (0x%x)", kind.getJavaName(),
                humanReadableByteCount(bytes, true), toAbsoluteAddress(),
                toRelativeAddress());
    }

    @Override
    public void printHeapTrace() {
        System.out.printf("0x%x\ttype=%s\n", toAbsoluteAddress(), kind.getJavaName());

    }

    /*
     * Retrieves a buffer that will contain the contents of the array header.
     * This also re-sizes the buffer.
     */
    private boolean validateArrayHeader(final T array) {
        final OCLByteBuffer header = prepareArrayHeader();
        header.read();
        // header.dump(8);
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
        buildArrayHeader(array).write();
        writeArrayData(toBuffer(), bufferOffset + arrayHeaderSize, bytes - arrayHeaderSize, array,
                null);
        onDevice = true;

    }

    abstract protected void writeArrayData(long bufferId, long offset, long bytes, T value,
            int[] waitEvents);

}
