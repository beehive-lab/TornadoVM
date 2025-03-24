/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020,2024 APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
 */
package uk.ac.manchester.tornado.drivers.opencl.mm;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getVMConfig;
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getVMRuntime;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.DEBUG;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jdk.vm.ci.hotspot.HotSpotResolvedJavaField;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaType;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.exceptions.TornadoMemoryException;
import uk.ac.manchester.tornado.api.exceptions.TornadoOutOfMemoryException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.internal.annotations.Vector;
import uk.ac.manchester.tornado.api.memory.XPUBuffer;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;
import uk.ac.manchester.tornado.drivers.common.mm.PrimitiveSerialiser;
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContext;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLKind;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.utils.TornadoUtils;

public class OCLFieldBuffer implements XPUBuffer {

    private static final long BYTES_OBJECT_REFERENCE = 8;
    private final HotSpotResolvedJavaType resolvedType;
    private final HotSpotResolvedJavaField[] fields;
    private final FieldBuffer[] wrappedFields;
    private final Class<?> objectType;
    private final int hubOffset;
    private final int fieldsOffset;
    private final OCLDeviceContext deviceContext;
    private long bufferId;
    private long bufferOffset;
    private ByteBuffer buffer;
    private long setSubRegionSize;
    private final TornadoLogger logger;
    private final Access access;

    public OCLFieldBuffer(final OCLDeviceContext device, Object object, Access access) {
        this.objectType = object.getClass();
        this.deviceContext = device;
        this.logger = new TornadoLogger(this.getClass());
        this.access = access;

        hubOffset = getVMConfig().hubOffset;
        fieldsOffset = getVMConfig().instanceKlassFieldsOffset();
        resolvedType = (HotSpotResolvedJavaType) getVMRuntime().getHostJVMCIBackend().getMetaAccess().lookupJavaType(objectType);

        fields = (HotSpotResolvedJavaField[]) resolvedType.getInstanceFields(false);
        sortFieldsByOffset();

        wrappedFields = new FieldBuffer[fields.length];

        for (int index = 0; index < fields.length; index++) {
            HotSpotResolvedJavaField field = fields[index];
            final Field reflectedField = getField(objectType, field.getName());
            final Class<?> type = reflectedField.getType();

            if (DEBUG) {
                logger.trace("field: name=%s, kind=%s, offset=%d", field.getName(), type.getName(), field.getOffset());
            }

            XPUBuffer wrappedField = null;
            if (type.isArray()) {
                Object objectFromField = TornadoUtils.getObjectFromField(reflectedField, object);
                if (type == int[].class) {
                    wrappedField = new OCLIntArrayWrapper((int[]) objectFromField, device, 0, access);
                } else if (type == float[].class) {
                    wrappedField = new OCLFloatArrayWrapper((float[]) objectFromField, device, 0, access);
                } else if (type == double[].class) {
                    wrappedField = new OCLDoubleArrayWrapper((double[]) objectFromField, device, 0, access);
                } else if (type == long[].class) {
                    wrappedField = new OCLLongArrayWrapper((long[]) objectFromField, device, 0, access);
                } else if (type == short[].class) {
                    wrappedField = new OCLShortArrayWrapper((short[]) objectFromField, device, 0, access);
                } else if (type == char[].class) {
                    wrappedField = new OCLCharArrayWrapper((char[]) objectFromField, device, 0, access);
                } else if (type == byte[].class) {
                    wrappedField = new OCLByteArrayWrapper((byte[]) objectFromField, device, 0, access);
                } else {
                    logger.warn("cannot wrap field: array type=%s", type.getName());
                }
            } else if (type == FloatArray.class) {
                Object objectFromField = TornadoUtils.getObjectFromField(reflectedField, object);
                long size = ((FloatArray) objectFromField).getSegmentWithHeader().byteSize();
                wrappedField = new OCLMemorySegmentWrapper(size, device, 0, access, OCLKind.FLOAT.getSizeInBytes());
            } else if (type == ByteArray.class) {
                Object objectFromField = TornadoUtils.getObjectFromField(reflectedField, object);
                long size = ((ByteArray) objectFromField).getSegmentWithHeader().byteSize();
                wrappedField = new OCLMemorySegmentWrapper(size, device, 0, access, OCLKind.CHAR.getSizeInBytes());
            } else if (type == DoubleArray.class) {
                Object objectFromField = TornadoUtils.getObjectFromField(reflectedField, object);
                long size = ((DoubleArray) objectFromField).getSegmentWithHeader().byteSize();
                wrappedField = new OCLMemorySegmentWrapper(size, device, 0, access, OCLKind.DOUBLE.getSizeInBytes());
            } else if (type == IntArray.class) {
                Object objectFromField = TornadoUtils.getObjectFromField(reflectedField, object);
                long size = ((IntArray) objectFromField).getSegmentWithHeader().byteSize();
                wrappedField = new OCLMemorySegmentWrapper(size, device, 0, access, OCLKind.INT.getSizeInBytes());
            } else if (type == ShortArray.class) {
                Object objectFromField = TornadoUtils.getObjectFromField(reflectedField, object);
                long size = ((ShortArray) objectFromField).getSegmentWithHeader().byteSize();
                wrappedField = new OCLMemorySegmentWrapper(size, device, 0, access, OCLKind.SHORT.getSizeInBytes());
            } else if (type == LongArray.class) {
                Object objectFromField = TornadoUtils.getObjectFromField(reflectedField, object);
                long size = ((LongArray) objectFromField).getSegmentWithHeader().byteSize();
                wrappedField = new OCLMemorySegmentWrapper(size, device, 0, access, OCLKind.LONG.getSizeInBytes());
            } else if (type == HalfFloatArray.class) {
                Object objectFromField = TornadoUtils.getObjectFromField(reflectedField, object);
                long size = ((HalfFloatArray) objectFromField).getSegmentWithHeader().byteSize();
                wrappedField = new OCLMemorySegmentWrapper(size, device, 0, access, OCLKind.SHORT.getSizeInBytes());
            } else if (object.getClass().getAnnotation(Vector.class) != null) {
                wrappedField = new OCLVectorWrapper(device, object, 0, access);
            } else if (field.getJavaKind().isObject()) {
                // We capture the field by the scope definition of the input
                // lambda expression
                wrappedField = new OCLFieldBuffer(device, TornadoUtils.getObjectFromField(reflectedField, object), access);
            }

            if (wrappedField != null) {
                wrappedFields[index] = new FieldBuffer(reflectedField, wrappedField);
            }
        }

        if (buffer == null) {
            buffer = ByteBuffer.allocate((int) getObjectSize());
            buffer.order(deviceContext.getByteOrder());
        }
    }

    @Override
    public void allocate(Object reference, long batchSize, Access access) throws TornadoOutOfMemoryException, TornadoMemoryException {
        if (DEBUG) {
            logger.debug("object: object=0x%x, class=%s", reference.hashCode(), reference.getClass().getName());
        }

        this.bufferId = deviceContext.getBufferProvider().getOrAllocateBufferWithSize(size(), access);
        this.bufferOffset = 0;
        setBuffer(new XPUBufferWrapper(bufferId, bufferOffset));

        if (DEBUG) {
            logger.debug("object: object=0x%x @ bufferId 0x%x", reference.hashCode(), bufferId);
        }
    }

    @Override
    public void markAsFreeBuffer() throws TornadoMemoryException {
        deviceContext.getBufferProvider().markBufferReleased(this.bufferId, this.access);
        bufferId = -1;
    }

    private Field getField(Class<?> type, String name) {
        Field result = null;
        try {
            result = type.getDeclaredField(name);
            result.setAccessible(true);
        } catch (NoSuchFieldException | SecurityException e) {
            if (type.getSuperclass() != null) {
                result = getField(type.getSuperclass(), name);
            } else {
                shouldNotReachHere("unable to get field: class=%s, field=%s", type.getName(), name);
            }
        }
        return result;
    }

    private void writeFieldToBuffer(int index, Field field, Object obj) {
        Class<?> fieldType = field.getType();
        if (fieldType.isPrimitive()) {
            try {
                PrimitiveSerialiser.put(buffer, field.get(obj));
            } catch (IllegalArgumentException | IllegalAccessException e) {
                shouldNotReachHere("unable to write primitive to buffer: ", e.getMessage());
            }
        } else if (wrappedFields[index] != null) {
            buffer.putLong(wrappedFields[index].getBufferOffset());
        } else {
            unimplemented("field type %s", fieldType.getName());
        }
    }

    private void readFieldFromBuffer(int index, Field field, Object obj) {
        Class<?> fieldType = field.getType();
        if (fieldType.isPrimitive()) {
            try {
                if (fieldType == int.class) {
                    field.setInt(obj, buffer.getInt());
                } else if (fieldType == long.class) {
                    field.setLong(obj, buffer.getLong());
                } else if (fieldType == short.class) {
                    field.setShort(obj, buffer.getShort());
                } else if (fieldType == byte.class) {
                    field.set(obj, buffer.get());
                } else if (fieldType == float.class) {
                    field.setFloat(obj, buffer.getFloat());
                } else if (fieldType == double.class) {
                    field.setDouble(obj, buffer.getDouble());
                }
            } catch (IllegalAccessException e) {
                shouldNotReachHere("unable to read field: ", e.getMessage());
            }
        } else if (wrappedFields[index] != null) {
            buffer.getLong();
        } else {
            unimplemented("field type %s", fieldType.getName());
        }
    }

    private void sortFieldsByOffset() {
        // TODO Replace bubble sort with Arrays.sort + comparator
        for (int i = 0; i < fields.length; i++) {
            for (int j = 0; j < fields.length; j++) {
                if (fields[i].getOffset() < fields[j].getOffset()) {
                    final HotSpotResolvedJavaField tmp = fields[j];
                    fields[j] = fields[i];
                    fields[i] = tmp;
                }
            }
        }

    }

    private void serialise(Object object) {
        buffer.rewind();
        buffer.position(hubOffset);
        buffer.putLong(0);

        if (fields.length > 0) {
            buffer.position(fields[0].getOffset());
            for (int i = 0; i < fields.length; i++) {
                HotSpotResolvedJavaField field = fields[i];
                Field f = getField(objectType, field.getName());
                if (DEBUG) {
                    logger.trace("writing field: name=%s, offset=%d", field.getName(), field.getOffset());
                }

                buffer.position(field.getOffset());
                writeFieldToBuffer(i, f, object);
            }
        }
    }

    private void deserialise(Object object) {
        buffer.rewind();

        if (fields.length > 0) {
            buffer.position(fields[0].getOffset());

            for (int i = 0; i < fields.length; i++) {
                HotSpotResolvedJavaField field = fields[i];
                Field f = getField(objectType, field.getName());
                f.setAccessible(true);
                if (DEBUG) {
                    logger.trace("reading field: name=%s, offset=%d", field.getName(), field.getOffset());
                }
                readFieldFromBuffer(i, f, object);
            }
        }
    }

    @Override
    public void write(long executionPlanId, Object object) {
        serialise(object);
        // XXX: Offset 0
        deviceContext.writeBuffer(executionPlanId, toBuffer(), bufferOffset, getObjectSize(), buffer.array(), 0, null);
        for (int i = 0; i < fields.length; i++) {
            if (wrappedFields[i] != null) {
                wrappedFields[i].write(executionPlanId, object);
            }
        }
    }

    @Override
    public long toBuffer() {
        return bufferId;
    }

    @Override
    public void setBuffer(XPUBufferWrapper bufferWrapper) {
        this.bufferId = bufferWrapper.buffer;
        this.bufferOffset = bufferWrapper.bufferOffset;

        bufferWrapper.bufferOffset += getObjectSize();

        for (int i = 0; i < fields.length; i++) {
            FieldBuffer fieldBuffer = wrappedFields[i];
            if (fieldBuffer == null) {
                continue;
            }

            fieldBuffer.setBuffer(bufferWrapper);
        }
    }

    @Override
    public long getBufferOffset() {
        return bufferOffset;
    }

    @Override
    public void read(long executionPlanId, Object object) {
        // XXX: offset 0
        read(executionPlanId, object, 0, 0, null, false);
    }

    @Override
    public int read(long executionPlanId, Object object, long hostOffset, long partialReadSize, int[] events, boolean useDeps) {
        buffer.position(buffer.capacity());
        int event = deviceContext.readBuffer(executionPlanId, toBuffer(), bufferOffset, getObjectSize(), buffer.array(), hostOffset, (useDeps) ? events : null);
        for (int i = 0; i < fields.length; i++) {
            if (wrappedFields[i] != null) {
                wrappedFields[i].read(executionPlanId, object);
            }
        }
        deserialise(object);
        return event;
    }

    public void clear() {
        buffer.rewind();
        while (buffer.hasRemaining()) {
            buffer.put((byte) 0);
        }
        buffer.rewind();
    }

    public void dump() {
        dump(8);
    }

    protected void dump(int width) {
        System.out.printf("Buffer  : capacity = %s, in use = %s, device = %s \n", RuntimeUtilities.humanReadableByteCount(getObjectSize(), true), RuntimeUtilities.humanReadableByteCount(buffer
                .position(), true), deviceContext.getDevice().getDeviceName());
        for (int i = 0; i < buffer.position(); i += width) {
            System.out.printf("[0x%04x]: ", i);
            for (int j = 0; j < Math.min(buffer.capacity() - i, width); j++) {
                if (j % 2 == 0) {
                    System.out.printf(" ");
                }
                if (j < buffer.position() - i) {
                    System.out.printf("%02x", buffer.get(i + j));
                } else {
                    System.out.printf("..");
                }
            }
            System.out.println();
        }
    }

    @Override
    public int enqueueRead(long executionPlanId, Object reference, long hostOffset, int[] events, boolean useDeps) {
        final int returnEvent;
        int index = 0;
        int[] internalEvents = new int[fields.length];
        Arrays.fill(internalEvents, -1);

        for (FieldBuffer fb : wrappedFields) {
            if (fb != null) {
                internalEvents[index] = fb.enqueueRead(executionPlanId, reference, (useDeps) ? events : null, useDeps);
                index++;
            }
        }

        internalEvents[index] = deviceContext.enqueueReadBuffer(executionPlanId, toBuffer(), bufferOffset, getObjectSize(), buffer.array(), hostOffset, (useDeps) ? events : null);
        index++;

        deserialise(reference);

        if (index == 1) {
            returnEvent = internalEvents[0];
        } else {
            returnEvent = deviceContext.enqueueMarker(executionPlanId, internalEvents);
        }
        return useDeps ? returnEvent : -1;
    }

    @Override
    public List<Integer> enqueueWrite(long executionPlanId, Object ref, long batchSize, long hostOffset, int[] events, boolean useDeps) {
        ArrayList<Integer> eventList = new ArrayList<>();

        serialise(ref);
        eventList.add(deviceContext.enqueueWriteBuffer(executionPlanId, toBuffer(), bufferOffset, getObjectSize(), buffer.array(), hostOffset, (useDeps) ? events : null));
        for (final FieldBuffer field : wrappedFields) {
            if (field != null) {
                eventList.addAll(field.enqueueWrite(executionPlanId, ref, (useDeps) ? events : null, useDeps));
            }
        }
        return eventList;
    }

    @Override
    public String toString() {
        return String.format("object wrapper: type=%s, fields=%d\n", resolvedType.getName(), wrappedFields.length);
    }

    private long getObjectSize() {
        long size = fieldsOffset;
        if (fields.length > 0) {
            HotSpotResolvedJavaField field = fields[fields.length - 1];
            size = field.getOffset() + ((field.getJavaKind().isObject()) ? BYTES_OBJECT_REFERENCE : field.getJavaKind().getByteCount());
        }
        return size;
    }

    @Override
    public long size() {
        long size = getObjectSize();
        for (FieldBuffer wrappedField : wrappedFields) {
            if (wrappedField != null) {
                size += wrappedField.size();
            }
        }
        return size;
    }

    @Override
    public void setSizeSubRegion(long batchSize) {
        this.setSubRegionSize = batchSize;
    }

    @Override
    public long getSizeSubRegionSize() {
        return setSubRegionSize;
    }

    @Override
    public long deallocate() {
        return deviceContext.getBufferProvider().deallocate(access);
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
