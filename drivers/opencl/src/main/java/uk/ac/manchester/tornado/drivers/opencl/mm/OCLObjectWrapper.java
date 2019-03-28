/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2019, APT Group, School of Computer Science,
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
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.mm;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getVMConfig;
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getVMRuntime;
import static uk.ac.manchester.tornado.runtime.common.RuntimeUtilities.humanReadableByteCount;
import static uk.ac.manchester.tornado.runtime.common.Tornado.DEBUG;
import static uk.ac.manchester.tornado.runtime.common.Tornado.OPENCL_USE_RELATIVE_ADDRESSES;
import static uk.ac.manchester.tornado.runtime.common.Tornado.debug;
import static uk.ac.manchester.tornado.runtime.common.Tornado.trace;
import static uk.ac.manchester.tornado.runtime.common.Tornado.warn;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.Arrays;

import jdk.vm.ci.hotspot.HotSpotResolvedJavaField;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaType;
import uk.ac.manchester.tornado.api.exceptions.TornadoMemoryException;
import uk.ac.manchester.tornado.api.exceptions.TornadoOutOfMemoryException;
import uk.ac.manchester.tornado.api.mm.ObjectBuffer;
import uk.ac.manchester.tornado.api.type.annotations.Payload;
import uk.ac.manchester.tornado.api.type.annotations.Vector;
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContext;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;

public class OCLObjectWrapper implements ObjectBuffer {

    private final boolean vectorObject;
    private int vectorStorageIndex;
    private long bufferOffset;
    private long bytesToAllocate;
    private ByteBuffer buffer;
    private HotSpotResolvedJavaType resolvedType;
    private HotSpotResolvedJavaField[] fields;
    private FieldBuffer[] wrappedFields;

    private final Class<?> type;

    private int hubOffset;
    private int fieldsOffset;

    private final OCLDeviceContext deviceContext;
    private boolean valid;
    private boolean isFinal;
    private final int[] internalEvents;
    private long batchSize;

    public OCLObjectWrapper(final OCLDeviceContext device, Object object, long batchSize) {
        this.type = object.getClass();
        this.deviceContext = device;
        this.batchSize = batchSize;

        valid = false;
        isFinal = true;

        hubOffset = getVMConfig().hubOffset;
        fieldsOffset = getVMConfig().instanceKlassFieldsOffset;
        bufferOffset = -1;

        resolvedType = (HotSpotResolvedJavaType) getVMRuntime().getHostJVMCIBackend().getMetaAccess().lookupJavaType(object.getClass());

        if (resolvedType.getAnnotation(Vector.class) != null) {
            vectorObject = true;
        } else {
            vectorObject = false;
        }

        vectorStorageIndex = -1;

        fields = (HotSpotResolvedJavaField[]) resolvedType.getInstanceFields(true);
        sortFieldsByOffset();

        wrappedFields = new FieldBuffer[fields.length];
        internalEvents = new int[fields.length];

        int index = 0;

        // calculate object size
        bytesToAllocate = (fields.length > 0) ? fields[0].offset() : fieldsOffset;
        for (HotSpotResolvedJavaField field : fields) {
            final Field reflectedField = getField(type, field.getName());
            final Class<?> type = reflectedField.getType();
            final boolean isFinal = Modifier.isFinal(reflectedField.getModifiers());

            if (vectorObject) {
                if (field.getAnnotation(Payload.class) != null) {
                    vectorStorageIndex = index;
                }
            }

            if (DEBUG) {
                trace("field: name=%s, kind=%s, offset=%d", field.getName(), type.getName(), field.offset());
            }
            bytesToAllocate = field.offset();
            bytesToAllocate += (field.getJavaKind().isObject()) ? 8 : field.getJavaKind().getByteCount();

            ObjectBuffer wrappedField = null;
            if (type.isArray()) {
                if (type == int[].class) {
                    wrappedField = new OCLIntArrayWrapper(device, isFinal, batchSize);
                } else if (type == float[].class) {
                    wrappedField = new OCLFloatArrayWrapper(device, isFinal, batchSize);
                } else if (type == double[].class) {
                    wrappedField = new OCLDoubleArrayWrapper(device, isFinal, batchSize);
                } else if (type == long[].class) {
                    wrappedField = new OCLLongArrayWrapper(device, isFinal, batchSize);
                } else if (type == short[].class) {
                    wrappedField = new OCLShortArrayWrapper(device, isFinal, batchSize);
                } else if (type == byte[].class) {
                    wrappedField = new OCLByteArrayWrapper(device, isFinal, batchSize);
                } else {
                    warn("cannot wrap field: array type=%s", type.getName());
                }
            } else if (field.getJavaKind().isObject()) {
                // We capture the field by the scope definition of the input
                // lambda expression
                try {
                    wrappedField = new OCLObjectWrapper(device, reflectedField.get(object), batchSize);
                } catch (IllegalArgumentException | IllegalAccessException e) {
                    shouldNotReachHere();
                }
            } else {
                this.isFinal &= isFinal;
            }

            if (wrappedField != null) {
                wrappedFields[index] = new FieldBuffer(reflectedField, wrappedField);
            }
            index++;
        }

        if (DEBUG) {
            trace("object: type=%s, size=%s", resolvedType.getName(), humanReadableByteCount(bytesToAllocate, true));
        }
    }

    public long getObjectBatchSize() {
        return this.batchSize;
    }

    @Override
    public void allocate(Object reference, long batchSize) throws TornadoOutOfMemoryException, TornadoMemoryException {
        if (DEBUG) {
            debug("object: object=0x%x, class=%s, size=%s", reference.hashCode(), reference.getClass().getName(), humanReadableByteCount(bytesToAllocate, true));
        }

        if (bytesToAllocate < 0) {
            throw new TornadoMemoryException("[ERROR] Bytes Allocated < 0: " + bytesToAllocate);
        } else if (bytesToAllocate > Integer.MAX_VALUE) {
            throw new TornadoOutOfMemoryException("[ERROR] Tornado cannot allocate: " + bytesToAllocate + " bytes");
        }

        if (buffer == null) {
            buffer = ByteBuffer.allocate((int) bytesToAllocate);
            buffer.order(deviceContext.getByteOrder());
        }

        if (bufferOffset == -1) {
            bufferOffset = deviceContext.getMemoryManager().tryAllocate(reference.getClass(), bytesToAllocate, 32, getAlignment());
        }

        if (DEBUG) {
            debug("object: object=0x%x @ 0x%x (0x%x)", reference.hashCode(), toAbsoluteAddress(), toRelativeAddress());
        }
        for (FieldBuffer buffer : wrappedFields) {
            if (buffer != null) {
                /// XXX: This is not clear to me - Maybe the field have to be
                /// broadcast => Passing 0 as batchSize? With 0 we broadcast the
                /// variable.
                if (batchSize > 0) {
                    throw new TornadoMemoryException("[ERROR] BatchSize Allocation currently not supported for Objects Fields. BatchSize = " + batchSize + " (bytes)");
                }
                buffer.allocate(reference, batchSize);
            }
        }
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
            if (OPENCL_USE_RELATIVE_ADDRESSES) {
                buffer.putLong(wrappedFields[index].toRelativeAddress());
            } else {
                buffer.putLong(wrappedFields[index].toAbsoluteAddress());
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
            buffer.getLong();
        } else {
            unimplemented("field type %s", fieldType.getName());
        }
    }

    private void sortFieldsByOffset() {
        for (int i = 0; i < fields.length; i++) {
            for (int j = 0; j < fields.length; j++) {
                if (fields[i].offset() < fields[j].offset()) {
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
            buffer.position(fields[0].offset());
            for (int i = 0; i < fields.length; i++) {
                HotSpotResolvedJavaField field = fields[i];
                Field f = getField(type, field.getName());
                if (DEBUG) {
                    trace("writing field: name=%s, offset=%d", field.getName(), field.offset());
                }

                buffer.position(field.offset());
                writeFieldToBuffer(i, f, object);
            }
        }
    }

    private void deserialise(Object object) {
        buffer.rewind();

        if (fields.length > 0) {
            buffer.position(fields[0].offset());

            for (int i = 0; i < fields.length; i++) {
                HotSpotResolvedJavaField field = fields[i];
                Field f = getField(type, field.getName());
                f.setAccessible(true);
                if (DEBUG) {
                    trace("reading field: name=%s, offset=%d", field.getName(), field.offset());
                }
                readFieldFromBuffer(i, f, object);
            }
        }
    }

    @Override
    public long toBuffer() {
        return deviceContext.getMemoryManager().toBuffer();
    }

    @Override
    public long getBufferOffset() {
        return bufferOffset;
    }

    @Override
    public void write(Object object) {
        if (vectorObject) {
            final FieldBuffer fieldBuffer = wrappedFields[vectorStorageIndex];
            fieldBuffer.write(object);
        } else {
            if (!valid) {
                serialise(object);
                // XXX: Offset 0
                deviceContext.writeBuffer(toBuffer(), bufferOffset, bytesToAllocate, buffer.array(), 0, null);
            }
            for (int i = 0; i < fields.length; i++) {
                if (wrappedFields[i] != null) {
                    wrappedFields[i].write(object);
                }
            }
        }
        valid = true;
    }

    @Override
    public void read(Object object) {
        // XXX: offset 0
        read(object, 0, null, false);
    }

    @Override
    public void read(Object object, long hostOffset, int[] events, boolean useDeps) {
        if (vectorObject) {
            final FieldBuffer fieldBuffer = wrappedFields[vectorStorageIndex];
            fieldBuffer.read(object, events, useDeps);
        } else {
            buffer.position(buffer.capacity());
            deviceContext.readBuffer(toBuffer(), bufferOffset, bytesToAllocate, buffer.array(), hostOffset, (useDeps) ? events : null);
            for (int i = 0; i < fields.length; i++) {
                if (wrappedFields[i] != null) {
                    wrappedFields[i].read(object);
                }
            }
            deserialise(object);
        }
    }

    @Override
    public long toAbsoluteAddress() {
        return (vectorObject) ? getVectorAddress(false) : deviceContext.getMemoryManager().toAbsoluteDeviceAddress(bufferOffset);
    }

    private long getVectorAddress(boolean relative) {
        final HotSpotResolvedJavaField resolvedField = fields[vectorStorageIndex];
        final FieldBuffer fieldBuffer = wrappedFields[vectorStorageIndex];
        final long address = (relative) ? fieldBuffer.toRelativeAddress() : fieldBuffer.toAbsoluteAddress();

        final long arrayBaseOffset = getVMConfig().getArrayBaseOffset(resolvedField.getJavaKind());
        return address + arrayBaseOffset;
    }

    @Override
    public long toRelativeAddress() {
        return (vectorObject) ? getVectorAddress(true) : bufferOffset;
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
        System.out.printf("Buffer  : capacity = %s, in use = %s, device = %s \n", RuntimeUtilities.humanReadableByteCount(bytesToAllocate, true),
                RuntimeUtilities.humanReadableByteCount(buffer.position(), true), deviceContext.getDevice().getDeviceName());
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
    public int enqueueRead(Object reference, long hostOffset, int[] events, boolean useDeps) {
        final int returnEvent;
        if (vectorObject) {
            final FieldBuffer fieldBuffer = wrappedFields[vectorStorageIndex];
            returnEvent = fieldBuffer.enqueueRead(reference, (useDeps) ? events : null, useDeps);
        } else {
            int index = 0;
            Arrays.fill(internalEvents, -1);

            for (FieldBuffer fb : wrappedFields) {
                if (fb != null) {
                    internalEvents[index] = fb.enqueueRead(reference, (useDeps) ? events : null, useDeps);
                    index++;
                }
            }

            if (!isFinal) {
                internalEvents[index] = deviceContext.enqueueReadBuffer(toBuffer(), bufferOffset, bytesToAllocate, buffer.array(), hostOffset, (useDeps) ? events : null);
                index++;

                // TODO this needs to run asynchronously
                deserialise(reference);
            }

            switch (index) {
                case 0:
                    returnEvent = -1;
                    break;
                case 1:
                    returnEvent = internalEvents[0];
                    break;
                default:
                    returnEvent = deviceContext.enqueueMarker(internalEvents);

            }

        }
        return useDeps ? returnEvent : -1;
    }

    @Override
    public int enqueueWrite(Object ref, long batchSize, long hostOffset, int[] events, boolean useDeps) {
        final int returnEvent;
        if (vectorObject) {
            final FieldBuffer fieldBuffer = wrappedFields[vectorStorageIndex];
            if (!valid) {
                valid = true;
                returnEvent = fieldBuffer.enqueueWrite(ref, (useDeps) ? events : null, useDeps);
            } else {
                returnEvent = -1;
            }

        } else {
            Arrays.fill(internalEvents, -1);
            int index = 0;

            // TODO this needs to run asynchronously
            if (!valid || (valid && !isFinal)) {
                serialise(ref);

                internalEvents[index] = deviceContext.enqueueWriteBuffer(toBuffer(), bufferOffset, bytesToAllocate, buffer.array(), hostOffset, (useDeps) ? events : null);
                index++;

                valid = true;
            }

            for (final FieldBuffer fb : wrappedFields) {
                if (fb != null && fb.needsWrite()) {
                    internalEvents[index] = fb.enqueueWrite(ref, (useDeps) ? events : null, useDeps);
                    index++;
                }
            }

            switch (index) {
                case 0:
                    returnEvent = -1;
                    break;
                case 1:
                    returnEvent = internalEvents[0];
                    break;
                default:
                    returnEvent = deviceContext.enqueueMarker(internalEvents);

            }
        }
        return useDeps ? returnEvent : -1;
    }

    @Override
    public int getAlignment() {
        return 64;
    }

    public FieldBuffer getField(String name) {
        int index = 0;
        for (HotSpotResolvedJavaField field : fields) {
            if (field.getName().equalsIgnoreCase(name)) {
                return wrappedFields[index];
            }
            break;
        }
        return null;
    }

    @Override
    public boolean isValid() {
        return valid;
    }

    @Override
    public void invalidate() {
        valid = false;

    }

    @Override
    public String toString() {
        return String.format("object wrapper: type=%s, fields=%d, valid=%s\n", resolvedType.getName(), wrappedFields.length, valid);
    }

    @Override
    public void printHeapTrace() {
        System.out.printf("0x%x:\ttype=%s, num fields=%d (%d)\n", toAbsoluteAddress(), type.getName(), fields.length, wrappedFields.length);
        for (FieldBuffer fb : wrappedFields) {
            if (fb != null) {
                System.out.printf("\t0x%x\tname=%s\n", fb.toAbsoluteAddress(), fb.getFieldName());
            }
        }
    }

    @Override
    public long size() {
        return bytesToAllocate;
    }

}
