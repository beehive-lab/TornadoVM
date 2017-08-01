/*
 * Copyright 2012 James Clarkson.
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
 */
package tornado.drivers.opencl.mm;

import java.lang.reflect.Array;
import jdk.vm.ci.meta.JavaKind;
import tornado.common.TornadoLogger;
import tornado.common.exceptions.TornadoOutOfMemoryException;
import tornado.runtime.cache.TornadoByteBuffer;
import tornado.runtime.cache.TornadoDataMover.AllocateOp;
import tornado.runtime.cache.TornadoDataMover.AsyncOp;
import tornado.runtime.cache.TornadoDataMover.BarrierOp;
import tornado.runtime.cache.TornadoDataMover.BufferOp;
import tornado.runtime.cache.TornadoDataMover.SyncOp;

import static tornado.common.RuntimeUtilities.humanReadableByteCount;
import static tornado.common.Tornado.*;
import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;
import static tornado.runtime.TornadoRuntime.getVMConfig;

public class OCLArraySerialiser<T> extends TornadoLogger implements OCLSerialiser {

    private static final int ARRAY_ALIGNMENT = Integer.parseInt(getProperty("tornado.opencl.array.align", "128"));

    private final int arrayHeaderSize;

    private static final int ARRAY_LENGTH_OFFSET = getVMConfig().arrayOopDescLengthOffset();

    private final JavaKind kind;

    // TODO remove this
    private final int[] internalEvents = new int[2];

    private final BufferOp buffer;
    private final AllocateOp allocator;
    private final BarrierOp barrier;

    private final SyncOp<T> syncWriter;
    private final AsyncOp<T> asyncWriter;
    private final SyncOp<T> syncReader;
    private final AsyncOp<T> asyncReader;

    protected OCLArraySerialiser(final JavaKind kind, AllocateOp allocator, BufferOp buffer, BarrierOp barrier, SyncOp<T> write, AsyncOp<T> enqueueWrite, SyncOp<T> read, AsyncOp<T> enqueueRead) {
        this.kind = kind;
        this.allocator = allocator;
        this.buffer = buffer;
        this.barrier = barrier;
        this.syncWriter = write;
        this.asyncWriter = enqueueWrite;
        this.syncReader = read;
        this.asyncReader = enqueueRead;
        arrayHeaderSize = getVMConfig().getArrayBaseOffset(kind);

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
    public OCLCachedObject allocate(long id, Object value) throws TornadoOutOfMemoryException {
        final T ref = cast(value);
        final int bytes = sizeOf(value);

        final long bufferOffset = allocator.allocate(ref.getClass(), bytes, arrayHeaderSize, getAlignment());

        if (DEBUG) {
            debug("allocated: array kind=%s, size=%s, length offset=%d, header size=%d, bo=0x%x",
                    kind.getJavaName(), humanReadableByteCount(bytes, true),
                    ARRAY_LENGTH_OFFSET, arrayHeaderSize, bufferOffset);
        }

        return new OCLCachedObject(id, bufferOffset, bytes);
    }

    /*
     * Retrieves a buffer that will contain the contents of the array header.
     * The header is also populated using the header from the given array.
     */
    private TornadoByteBuffer buildArrayHeader(final T array, final long bufferOffset) {
        final TornadoByteBuffer header = getArrayHeader(bufferOffset);
        int index = 0;
        while (index < ARRAY_LENGTH_OFFSET) {
            header.buffer().put((byte) 0);
            index++;
        }
        header.buffer().putInt(Array.getLength(array));
        // header.dump(8);
        return header;
    }

    @Override
    public int enqueueReadFromDevice(final Object value, final OCLCachedObject cachedObject, final boolean readObjectHeader, final int[] events, boolean useDeps) {
        final T array = cast(value);
        final int returnEvent;
        if (readObjectHeader) {
            returnEvent = asyncReader.apply(cachedObject.toBuffer(), cachedObject.getBufferOffset() + arrayHeaderSize, cachedObject.size()
                    - arrayHeaderSize, array, (useDeps) ? events : null);
        } else {
//            int index = 0;
            internalEvents[1] = -1;
//            internalEvents[0] = prepareArrayHeader().enqueueReadFromDevice(null);
            internalEvents[0] = asyncReader.apply(cachedObject.toBuffer(), cachedObject.getBufferOffset() + arrayHeaderSize, cachedObject.size()
                    - arrayHeaderSize, array, (useDeps) ? events : null);
            returnEvent = internalEvents[0];//(index == 0) ? internalEvents[0] : deviceContext.enqueueMarker(internalEvents);
        }
        return useDeps ? returnEvent : -1;
    }

    @Override
    public int enqueueWriteToDevice(final Object value, final OCLCachedObject cachedObject, final boolean writeObjectHeader, final int[] events, boolean useDeps) {
        final T array = cast(value);
        final int returnEvent;
        if (!writeObjectHeader) {
            returnEvent = asyncWriter.apply(cachedObject.toBuffer(), cachedObject.getBufferOffset() + arrayHeaderSize, cachedObject.size()
                    - arrayHeaderSize, array, (useDeps) ? events : null);
        } else {
            int index = 0;
            internalEvents[0] = -1;

            internalEvents[0] = buildArrayHeader(array, cachedObject.getBufferOffset())
                    .asyncWrite((useDeps) ? events : null);
            index++;

            internalEvents[index] = asyncWriter.apply(cachedObject.toBuffer(), cachedObject.getBufferOffset() + arrayHeaderSize, cachedObject.size()
                    - arrayHeaderSize, array, (useDeps) ? events : null);
            returnEvent = (index == 0) ? internalEvents[0] : barrier.insert(internalEvents);

        }
        return useDeps ? returnEvent : -1;
    }

    public int getAlignment() {
        return ARRAY_ALIGNMENT;
    }

    private TornadoByteBuffer getArrayHeader(final long bufferOffset) {
        final TornadoByteBuffer header = buffer.get(
                (int) bufferOffset, arrayHeaderSize);
        header.buffer().clear();
        return header;
    }

    /*
     * Retrieves a buffer that will contain the contents of the array header.
     * This also re-sizes the buffer.
     */
    private TornadoByteBuffer prepareArrayHeader(final long bufferOffset) {
        final TornadoByteBuffer header = getArrayHeader(bufferOffset);
        header.buffer().position(header.buffer().capacity());
        return header;
    }

    @Override
    public void readFromDevice(final Object value, final OCLCachedObject cachedObject) {
        readFromDevice(value, cachedObject, null, false);
    }

    @Override
    public void readFromDevice(final Object value, final OCLCachedObject cachedObject, int[] events, boolean useDeps) {
        final T array = cast(value);
        if (VALIDATE_ARRAY_HEADERS) {
            if (validateArrayHeader(array, cachedObject.getBufferOffset())) {
                syncReader.apply(cachedObject.toBuffer(), cachedObject.getBufferOffset() + arrayHeaderSize, cachedObject.size() - arrayHeaderSize,
                        array, (useDeps) ? events : null);
            } else {
                shouldNotReachHere("Array header is invalid");
            }
        } else {
            syncReader.apply(cachedObject.toBuffer(), cachedObject.getBufferOffset() + arrayHeaderSize, cachedObject.size() - arrayHeaderSize,
                    array, (useDeps) ? events : null);
        }
    }

    @Override
    public int sizeOf(final Object array) {
        return arrayHeaderSize + (Array.getLength(cast(array)) * kind.getByteCount());
    }

    @Override
    public String toString() {
        return "OCLArraySerialiser - " + kind.getJavaName();
    }

    /*
     * Retrieves a buffer that will contain the contents of the array header.
     * This also re-sizes the buffer.
     */
    private boolean validateArrayHeader(final T array, final long bufferOffset) {
        final TornadoByteBuffer header = prepareArrayHeader(bufferOffset);
        header.syncRead();
        // header.dump(8);
        final int numElements = header.getInt(ARRAY_LENGTH_OFFSET);
        final boolean valid = numElements == Array.getLength(array);
        if (!valid) {
            fatal("Array: expected=%d, got=%d", Array.getLength(array), numElements);
            header.dump(8);
        }
        return valid;
    }

    @Override
    public void writeToDevice(final Object value, final OCLCachedObject cachedObject) {
        final T array = cast(value);
        buildArrayHeader(array, cachedObject.getBufferOffset()).syncWrite();
        syncWriter.apply(cachedObject.toBuffer(),
                cachedObject.getBufferOffset() + arrayHeaderSize,
                cachedObject.size() - arrayHeaderSize, array,
                null);
    }

}
