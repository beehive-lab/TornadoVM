/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2022,2024 APT Group, Department of Computer Science,
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
import uk.ac.manchester.tornado.api.internal.annotations.Payload;
import uk.ac.manchester.tornado.api.memory.XPUBuffer;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.CharArray;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.api.types.common.PrimitiveStorage;
import uk.ac.manchester.tornado.drivers.ptx.PTXDeviceContext;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.utils.TornadoUtils;

public class PTXVectorWrapper implements XPUBuffer {

    private static final int INIT_VALUE = -1;
    protected final PTXDeviceContext deviceContext;
    private final int arrayHeaderSize;
    private final int arrayLengthOffset;
    private final long batchSize;
    private final JavaKind kind;
    private final TornadoLogger logger;
    private long buffer;
    private long bufferSize;
    private long setSubRegionSize;
    private final Access access;

    public PTXVectorWrapper(final PTXDeviceContext device, final Object object, long batchSize, Access access) {
        TornadoInternalError.guarantee(object instanceof PrimitiveStorage, "Expecting a PrimitiveStorage type");
        this.deviceContext = device;
        this.batchSize = batchSize;
        this.buffer = INIT_VALUE;
        this.access = access;
        Object payload = TornadoUtils.getAnnotatedObjectFromField(object, Payload.class);
        this.kind = getJavaKind(payload.getClass());
        this.bufferSize = sizeOf(payload);
        this.arrayLengthOffset = getVMConfig().arrayOopDescLengthOffset();
        this.arrayHeaderSize = getVMConfig().getArrayBaseOffset(kind);
        this.logger = new TornadoLogger(this.getClass());
    }

    public long getBatchSize() {
        return batchSize;
    }

    @Override
    public void allocate(Object value, long batchSize, Access access) {
        TornadoInternalError.guarantee(value instanceof PrimitiveStorage, "Expecting a PrimitiveStorage type");
        final Object hostArray = TornadoUtils.getAnnotatedObjectFromField(value, Payload.class);
        if (batchSize <= 0) {
            bufferSize = sizeOf(hostArray);
        } else {
            bufferSize = batchSize;
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
    public void markAsFreeBuffer() {
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

    @Override
    public long size() {
        return bufferSize;
    }

    @Override
    public void setSizeSubRegion(long batchSize) {
        this.setSubRegionSize = batchSize;
    }

    @Override
    public int enqueueRead(long executionPlanId, final Object value, long hostOffset, final int[] events, boolean useDeps) {
        TornadoInternalError.guarantee(value instanceof PrimitiveStorage, "Expecting a PrimitiveStorage type");
        final Object actualValue = TornadoUtils.getAnnotatedObjectFromField(value, Payload.class);
        if (actualValue == null) {
            throw new TornadoRuntimeException("[ERROR] output data is NULL");
        }
        final int returnEvent = enqueueReadArrayData(executionPlanId, toBuffer(), bufferSize, actualValue, hostOffset, (useDeps) ? events : null);
        return useDeps ? returnEvent : -1;
    }

    /**
     * Copy data from the device to the main host.
     *
     * @param address
     *     Device Buffer ID
     * @param bytes
     *     Bytes to be copied back to the host
     * @param value
     *     Host array that resides the final data
     * @param waitEvents
     *     List of events to wait for.
     * @return Event information
     */
    private int enqueueReadArrayData(long executionPlanId, long address, long bytes, Object value, long hostOffset, int[] waitEvents) {
        return switch (kind) {
            case JavaKind.Int -> deviceContext.enqueueReadBuffer(executionPlanId, address, bytes, (int[]) value, hostOffset, waitEvents);
            case JavaKind.Float -> deviceContext.enqueueReadBuffer(executionPlanId, address, bytes, (float[]) value, hostOffset, waitEvents);
            case JavaKind.Double -> deviceContext.enqueueReadBuffer(executionPlanId, address, bytes, (double[]) value, hostOffset, waitEvents);
            case JavaKind.Long -> deviceContext.enqueueReadBuffer(executionPlanId, address, bytes, (long[]) value, hostOffset, waitEvents);
            case JavaKind.Short -> deviceContext.enqueueReadBuffer(executionPlanId, address, bytes, (short[]) value, hostOffset, waitEvents);
            case JavaKind.Byte -> deviceContext.enqueueReadBuffer(executionPlanId, address, bytes, (byte[]) value, hostOffset, waitEvents);
            case JavaKind.Object -> deviceContext.enqueueReadBuffer(executionPlanId, address, bytes, ((TornadoNativeArray) value).getSegmentWithHeader().address(), hostOffset, waitEvents);
            default -> throw new TornadoRuntimeException("Type not supported: " + value.getClass());
        };
    }

    @Override
    public List<Integer> enqueueWrite(long executionPlanId, final Object value, long batchSize, long hostOffset, final int[] events, boolean useDeps) {
        TornadoInternalError.guarantee(value instanceof PrimitiveStorage, "Expecting a PrimitiveStorage type");
        final Object array = TornadoUtils.getAnnotatedObjectFromField(value, Payload.class);
        ArrayList<Integer> listEvents = new ArrayList<>();

        if (array == null) {
            throw new TornadoRuntimeException("ERROR] Data to be copied is NULL");
        }
        final int returnEvent = enqueueWriteArrayData(executionPlanId, toBuffer(), bufferSize, array, hostOffset, (useDeps) ? events : null);
        listEvents.add(returnEvent);
        return listEvents;
    }

    private int enqueueWriteArrayData(long executionPlanId, long address, long bytes, Object value, long hostOffset, int[] waitEvents) {
        return switch (kind) {
            case JavaKind.Int -> deviceContext.enqueueWriteBuffer(executionPlanId, address, bytes, (int[]) value, hostOffset, waitEvents);
            case JavaKind.Float -> deviceContext.enqueueWriteBuffer(executionPlanId, address, bytes, (float[]) value, hostOffset, waitEvents);
            case JavaKind.Double -> deviceContext.enqueueWriteBuffer(executionPlanId, address, bytes, (double[]) value, hostOffset, waitEvents);
            case JavaKind.Long -> deviceContext.enqueueWriteBuffer(executionPlanId, address, bytes, (long[]) value, hostOffset, waitEvents);
            case JavaKind.Short -> deviceContext.enqueueWriteBuffer(executionPlanId, address, bytes, (short[]) value, hostOffset, waitEvents);
            case JavaKind.Byte -> deviceContext.enqueueWriteBuffer(executionPlanId, address, bytes, (byte[]) value, hostOffset, waitEvents);
            case JavaKind.Object -> deviceContext.enqueueWriteBuffer(executionPlanId, address, bytes, ((TornadoNativeArray) value).getSegmentWithHeader().address(), hostOffset, waitEvents);
            default -> throw new TornadoRuntimeException("Type not supported: " + value.getClass());
        };
    }

    @Override
    public void read(long executionPlanId, final Object value) {
        // TODO: reading with offset != 0
        read(executionPlanId, value, 0, 0, null, false);
    }

    @Override
    public int read(long executionPlanId, final Object value, long hostOffset, long partialReadSize, int[] events, boolean useDeps) {
        TornadoInternalError.guarantee(value instanceof PrimitiveStorage, "Expecting a PrimitiveStorage type");
        final Object array = TornadoUtils.getAnnotatedObjectFromField(value, Payload.class);
        if (array == null) {
            throw new TornadoRuntimeException("[ERROR] output data is NULL");
        }

        return readArrayData(executionPlanId, toBuffer(), bufferSize, array, hostOffset, (useDeps) ? events : null);
    }

    private int readArrayData(long executionPlanId, long address, long bytes, Object value, long hostOffset, int[] waitEvents) {
        return switch (kind) {
            case JavaKind.Int -> deviceContext.readBuffer(executionPlanId, address, bytes, (int[]) value, hostOffset, waitEvents);
            case JavaKind.Float -> deviceContext.readBuffer(executionPlanId, address, bytes, (float[]) value, hostOffset, waitEvents);
            case JavaKind.Double -> deviceContext.readBuffer(executionPlanId, address, bytes, (double[]) value, hostOffset, waitEvents);
            case JavaKind.Long -> deviceContext.readBuffer(executionPlanId, address, bytes, (long[]) value, hostOffset, waitEvents);
            case JavaKind.Short -> deviceContext.readBuffer(executionPlanId, address, bytes, (short[]) value, hostOffset, waitEvents);
            case JavaKind.Byte -> deviceContext.readBuffer(executionPlanId, address, bytes, (byte[]) value, hostOffset, waitEvents);
            case JavaKind.Object -> deviceContext.readBuffer(executionPlanId, address, bytes, ((TornadoNativeArray) value).getSegmentWithHeader().address(), hostOffset, waitEvents);
            default -> throw new TornadoRuntimeException("Type not supported: " + value.getClass());
        };
    }

    private long sizeOf(final Object array) {
        long size;
        if (array instanceof TornadoNativeArray nativeArray) {
            size = nativeArray.getNumBytesOfSegment();
        } else if (array.getClass() == HalfFloat[].class) {
            size = (long) Array.getLength(array) * 2; // the size of half floats is two bytes
        } else {
            size = (long) Array.getLength(array) * kind.getByteCount();
        }
        return size;
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
    public String toString() {
        return String.format("buffer<%s> %s", kind.getJavaName(), humanReadableByteCount(bufferSize, true));
    }

    @Override
    public void write(long executionPlanId, final Object value) {
        TornadoInternalError.guarantee(value instanceof PrimitiveStorage, "Expecting a PrimitiveStorage type");
        final Object array = TornadoUtils.getAnnotatedObjectFromField(value, Payload.class);
        if (array == null) {
            throw new TornadoRuntimeException("[ERROR] data is NULL");
        }
        // TODO: Writing with offset != 0
        writeArrayData(executionPlanId, toBuffer(), bufferSize, array, 0, null);
    }

    private void writeArrayData(long executionPlanId, long address, long bytes, Object value, long hostOffset, int[] waitEvents) {
        switch (kind) {
            case JavaKind.Int -> deviceContext.writeBuffer(executionPlanId, address, bytes, (int[]) value, (int) hostOffset, waitEvents);
            case JavaKind.Float -> deviceContext.writeBuffer(executionPlanId, address, bytes, (float[]) value, (int) hostOffset, waitEvents);
            case JavaKind.Double -> deviceContext.writeBuffer(executionPlanId, address, bytes, (double[]) value, (int) hostOffset, waitEvents);
            case JavaKind.Long -> deviceContext.writeBuffer(executionPlanId, address, bytes, (long[]) value, (int) hostOffset, waitEvents);
            case JavaKind.Short -> deviceContext.writeBuffer(executionPlanId, address, bytes, (short[]) value, hostOffset, waitEvents);
            case JavaKind.Byte -> deviceContext.writeBuffer(executionPlanId, address, bytes, (byte[]) value, hostOffset, waitEvents);
            case JavaKind.Object -> deviceContext.writeBuffer(executionPlanId, address, bytes, ((TornadoNativeArray) value).getSegmentWithHeader().address(), hostOffset, waitEvents);
            default -> throw new TornadoRuntimeException("Type not supported: " + value.getClass());
        }
    }

    private JavaKind getJavaKind(Class<?> type) {
        if (type.isArray()) {
            if (type == int[].class) {
                return JavaKind.Int;
            } else if (type == float[].class) {
                return JavaKind.Float;
            } else if (type == double[].class) {
                return JavaKind.Double;
            } else if (type == long[].class) {
                return JavaKind.Long;
            } else if (type == short[].class) {
                return JavaKind.Short;
            } else if (type == byte[].class) {
                return JavaKind.Byte;
            } else if (type == HalfFloat[].class) {
                return JavaKind.Object;
            } else {
                logger.warn("cannot wrap field: array type=%s", type.getName());
            }
        } else if (type == FloatArray.class || type == IntArray.class || type == DoubleArray.class || type == LongArray.class || type == ShortArray.class || type == CharArray.class || type == ByteArray.class || type == HalfFloatArray.class) {
            return JavaKind.Object;
        } else {
            TornadoInternalError.shouldNotReachHere("The type should be an array");
        }
        return null;
    }

    @Override
    public long getSizeSubRegionSize() {
        return setSubRegionSize;
    }

    @Override
    public int[] getIntBuffer() {
        return XPUBuffer.super.getIntBuffer();
    }

    @Override
    public void setIntBuffer(int[] arr) {
        XPUBuffer.super.setIntBuffer(arr);
    }

    @Override
    public void mapOnDeviceMemoryRegion(long executionPlanId, XPUBuffer srcPointer, long offset) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getSizeOfType() {
        throw new TornadoRuntimeException("[ERROR] not implemented");
    }

}
