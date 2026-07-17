/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.metal.mm;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getVMConfig;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.DEBUG;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import sun.misc.Unsafe;

import jdk.vm.ci.meta.JavaKind;
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
import uk.ac.manchester.tornado.api.types.arrays.Int8Array;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;
import uk.ac.manchester.tornado.drivers.common.mm.PrimitiveSerialiser;
import uk.ac.manchester.tornado.drivers.metal.MetalDeviceContext;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalKind;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.utils.TornadoUtils;

public class MetalFieldBuffer implements XPUBuffer {

    private static final long BYTES_OBJECT_REFERENCE_UNCOMPRESSED = 8;
    private static final Unsafe UNSAFE = initUnsafe();

    private final boolean areCoopsEnabled;
    private final long bytesObjectReference;
    private final Field[] fields;
    private final FieldBuffer[] wrappedFields;
    private final Class<?> objectType;
    private final int hubOffset;
    private final int fieldsOffset;
    private final MetalDeviceContext deviceContext;
    private long bufferId;
    private long bufferOffset;
    private ByteBuffer buffer;
    private long setSubRegionSize;
    private final TornadoLogger logger;
    private final Access access;

    private MetalFieldBuffer(final MetalDeviceContext device, Object object, Access access, boolean includeSuperClasses) {
        this.objectType = object.getClass();
        this.deviceContext = device;
        this.logger = new TornadoLogger(this.getClass());
        this.access = access;
        this.areCoopsEnabled = TornadoOptions.coopsUsed();
        this.bytesObjectReference = areCoopsEnabled ? 4 : BYTES_OBJECT_REFERENCE_UNCOMPRESSED;

        hubOffset = getVMConfig().hubOffset;
        fieldsOffset = getVMConfig().instanceKlassFieldsOffset();

        fields = gatherInstanceFields(objectType, includeSuperClasses);
        sortFieldsByOffset();

        wrappedFields = new FieldBuffer[fields.length];

        for (int index = 0; index < fields.length; index++) {
            final Field reflectedField = fields[index];
            final Class<?> type = reflectedField.getType();

            if (DEBUG) {
                logger.trace("field: name=%s, kind=%s, offset=%d", reflectedField.getName(), type.getName(), offsetOf(reflectedField));
            }

            XPUBuffer wrappedField = null;
            if (type.isArray()) {
                Object objectFromField = TornadoUtils.getObjectFromField(reflectedField, object);
                if (type == int[].class) {
                    wrappedField = new MetalIntArrayWrapper((int[]) objectFromField, device, 0, access);
                } else if (type == float[].class) {
                    wrappedField = new MetalFloatArrayWrapper((float[]) objectFromField, device, 0, access);
                } else if (type == double[].class) {
                    wrappedField = new MetalDoubleArrayWrapper((double[]) objectFromField, device, 0, access);
                } else if (type == long[].class) {
                    wrappedField = new MetalLongArrayWrapper((long[]) objectFromField, device, 0, access);
                } else if (type == short[].class) {
                    wrappedField = new MetalShortArrayWrapper((short[]) objectFromField, device, 0, access);
                } else if (type == char[].class) {
                    wrappedField = new MetalCharArrayWrapper((char[]) objectFromField, device, 0, access);
                } else if (type == byte[].class) {
                    wrappedField = new MetalByteArrayWrapper((byte[]) objectFromField, device, 0, access);
                } else {
                    logger.warn("cannot wrap field: array type=%s", type.getName());
                }
            } else if (type == FloatArray.class) {
                Object objectFromField = TornadoUtils.getObjectFromField(reflectedField, object);
                long size = ((FloatArray) objectFromField).getSegmentWithHeader().byteSize();
                wrappedField = new MetalMemorySegmentWrapper(size, device, 0, access, MetalKind.FLOAT.getSizeInBytes());
            } else if (type == ByteArray.class) {
                Object objectFromField = TornadoUtils.getObjectFromField(reflectedField, object);
                long size = ((ByteArray) objectFromField).getSegmentWithHeader().byteSize();
                wrappedField = new MetalMemorySegmentWrapper(size, device, 0, access, MetalKind.CHAR.getSizeInBytes());
            } else if (type == DoubleArray.class) {
                Object objectFromField = TornadoUtils.getObjectFromField(reflectedField, object);
                long size = ((DoubleArray) objectFromField).getSegmentWithHeader().byteSize();
                wrappedField = new MetalMemorySegmentWrapper(size, device, 0, access, MetalKind.DOUBLE.getSizeInBytes());
            } else if (type == IntArray.class) {
                Object objectFromField = TornadoUtils.getObjectFromField(reflectedField, object);
                long size = ((IntArray) objectFromField).getSegmentWithHeader().byteSize();
                wrappedField = new MetalMemorySegmentWrapper(size, device, 0, access, MetalKind.INT.getSizeInBytes());
            } else if (type == ShortArray.class) {
                Object objectFromField = TornadoUtils.getObjectFromField(reflectedField, object);
                long size = ((ShortArray) objectFromField).getSegmentWithHeader().byteSize();
                wrappedField = new MetalMemorySegmentWrapper(size, device, 0, access, MetalKind.SHORT.getSizeInBytes());
            } else if (type == LongArray.class) {
                Object objectFromField = TornadoUtils.getObjectFromField(reflectedField, object);
                long size = ((LongArray) objectFromField).getSegmentWithHeader().byteSize();
                wrappedField = new MetalMemorySegmentWrapper(size, device, 0, access, MetalKind.LONG.getSizeInBytes());
            } else if (type == HalfFloatArray.class) {
                Object objectFromField = TornadoUtils.getObjectFromField(reflectedField, object);
                long size = ((HalfFloatArray) objectFromField).getSegmentWithHeader().byteSize();
                wrappedField = new MetalMemorySegmentWrapper(size, device, 0, access, MetalKind.SHORT.getSizeInBytes());
            } else if (type == Int8Array.class) {
                Object objectFromField = TornadoUtils.getObjectFromField(reflectedField, object);
                long size = ((Int8Array) objectFromField).getSegmentWithHeader().byteSize();
                wrappedField = new MetalMemorySegmentWrapper(size, device, 0, access, MetalKind.CHAR.getSizeInBytes());
            } else if (object.getClass().getAnnotation(Vector.class) != null) {
                wrappedField = new MetalVectorWrapper(device, object, 0, access);
            } else if (!type.isPrimitive()) {
                // We capture the field by the scope definition of the input
                // lambda expression
                wrappedField = new MetalFieldBuffer(device, TornadoUtils.getObjectFromField(reflectedField, object), access, includeSuperClasses);
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

    public MetalFieldBuffer(final MetalDeviceContext device, Object object, Access access) {
        this(device, object, access, !isChildOfTornadoNativeArray(object));
    }

    private static boolean isChildOfTornadoNativeArray(Object object) {
        Class<?> objectType = object.getClass();
        while (objectType != null) {
            if (objectType.getName().equals(TornadoNativeArray.class.getName())) {
                return true;
            }
            objectType = objectType.getSuperclass();
        }
        return false;
    }

    private static Unsafe initUnsafe() {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            return (Unsafe) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new InternalError("Unable to obtain sun.misc.Unsafe for object marshalling", e);
        }
    }

    /**
     * Object field offset from {@code Unsafe} — the JDK-neutral equivalent of
     * {@code HotSpotResolvedJavaField.getOffset()} (identical value: the field's
     * location in the object layout).
     */
    private static int offsetOf(Field field) {
        return (int) UNSAFE.objectFieldOffset(field);
    }

    /** All non-static instance fields (optionally across the superclass chain), matching JVMCI's getInstanceFields. */
    private static Field[] gatherInstanceFields(Class<?> clazz, boolean includeSuperClasses) {
        List<Field> list = new ArrayList<>();
        for (Class<?> c = clazz; c != null; c = includeSuperClasses ? c.getSuperclass() : null) {
            for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    list.add(f);
                }
            }
        }
        return list.toArray(new Field[0]);
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

    private void writeFieldToBuffer(int index, Field field, Object obj) {
        Class<?> fieldType = field.getType();
        if (fieldType.isPrimitive()) {
            try {
                PrimitiveSerialiser.put(buffer, field.get(obj));
            } catch (IllegalArgumentException | IllegalAccessException e) {
                shouldNotReachHere("unable to write primitive to buffer: ", e.getMessage());
            }
        } else if (wrappedFields[index] != null) {
            if (areCoopsEnabled) {
                // Compressed oops must be relative to the flat buffer base (offset 0),
                // since the kernel always decompresses using ul_0 (the root object's buffer pointer).
                long relativeOffset = wrappedFields[index].getBufferOffset();
                int compressedOffset = (int) (relativeOffset / 8);
                buffer.putInt(compressedOffset);
            } else {
                buffer.putLong(wrappedFields[index].getBufferOffset());
            }
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
            if (areCoopsEnabled) {
                buffer.getInt();
            } else {
                buffer.getLong();
            }
        } else {
            unimplemented("field type %s", fieldType.getName());
        }
    }

    private void sortFieldsByOffset() {
        Arrays.sort(fields, (a, b) -> Integer.compare(offsetOf(a), offsetOf(b)));
    }

    private void serialise(Object object) {
        buffer.rewind();
        buffer.position(hubOffset);
        buffer.putLong(0);

        if (fields.length > 0) {
            buffer.position(offsetOf(fields[0]));
            for (int i = 0; i < fields.length; i++) {
                Field f = fields[i];
                if (DEBUG) {
                    logger.trace("writing field: name=%s, offset=%d", f.getName(), offsetOf(f));
                }

                buffer.position(offsetOf(f));
                writeFieldToBuffer(i, f, object);
            }
        }
    }

    private void deserialise(Object object) {
        buffer.rewind();

        if (fields.length > 0) {
            buffer.position(offsetOf(fields[0]));

            for (int i = 0; i < fields.length; i++) {
                Field f = fields[i];
                f.setAccessible(true);
                if (DEBUG) {
                    logger.trace("reading field: name=%s, offset=%d", f.getName(), offsetOf(f));
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
        return String.format("object wrapper: type=%s, fields=%d\n", objectType.getName(), wrappedFields.length);
    }

    private long getObjectSize() {
        long size = fieldsOffset;
        if (fields.length > 0) {
            Field field = fields[fields.length - 1];
            JavaKind kind = JavaKind.fromJavaClass(field.getType());
            size = offsetOf(field) + ((kind == JavaKind.Object) ? bytesObjectReference : kind.getByteCount());
        }
        // when coops are enabled, padding is required to ensure an 8-byte object alignment
        if (areCoopsEnabled && (size % 8 != 0)) {
            size = size + (8 - (size % 8));
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
