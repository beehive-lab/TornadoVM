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
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getVMConfig;
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getVMRuntime;
import static uk.ac.manchester.tornado.runtime.common.RuntimeUtilities.humanReadableByteCount;
import static uk.ac.manchester.tornado.runtime.common.Tornado.DEBUG;
import static uk.ac.manchester.tornado.runtime.common.Tornado.debug;
import static uk.ac.manchester.tornado.runtime.common.Tornado.trace;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jdk.vm.ci.hotspot.HotSpotResolvedJavaField;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaType;
import uk.ac.manchester.tornado.api.exceptions.TornadoMemoryException;
import uk.ac.manchester.tornado.api.exceptions.TornadoOutOfMemoryException;
import uk.ac.manchester.tornado.api.mm.ObjectBuffer;
import uk.ac.manchester.tornado.api.type.annotations.Payload;
import uk.ac.manchester.tornado.api.type.annotations.Vector;
import uk.ac.manchester.tornado.drivers.common.mm.PrimitiveSerialiser;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDeviceContext;

// FIXME <REFACTOR> This class can be common for the three backends. 
public class SPIRVObjectWrapper implements ObjectBuffer {

    private static final int SPIRV_OBJECT_ALIGNMENT = 64;

    private final Class<?> type;
    final SPIRVDeviceContext deviceContext;
    long batchSize;

    private boolean valid;
    private boolean isFinal;

    private int hubOffset;
    private int fieldsOffset;

    private final boolean vectorObject;
    private int vectorStorageIndex;
    private long bufferOffset;
    private long bytesToAllocate;
    private ByteBuffer buffer;
    private HotSpotResolvedJavaType resolvedType;
    private HotSpotResolvedJavaField[] fields;
    private FieldBuffer[] wrappedFields;

    private static final int BYTES_OBJECT_REFERENCE = 8;

    public SPIRVObjectWrapper(final SPIRVDeviceContext deviceContext, Object object, long batchSize) {
        this.type = object.getClass();
        this.deviceContext = deviceContext;
        this.batchSize = batchSize;

        this.valid = false;
        this.isFinal = true;

        hubOffset = getVMConfig().hubOffset;
        fieldsOffset = getVMConfig().instanceKlassFieldsOffset;
        bufferOffset = -1;

        resolvedType = (HotSpotResolvedJavaType) getVMRuntime().getHostJVMCIBackend().getMetaAccess().lookupJavaType(object.getClass());

        vectorObject = resolvedType.getAnnotation(Vector.class) != null;

        vectorStorageIndex = -1;

        fields = (HotSpotResolvedJavaField[]) resolvedType.getInstanceFields(true);
        sortFieldsByOffset();

        wrappedFields = new FieldBuffer[fields.length];

        int index = 0;

        // calculate object size
        bytesToAllocate = (fields.length > 0) ? fields[0].getOffset() : fieldsOffset;
        for (HotSpotResolvedJavaField field : fields) {
            final Field reflectedField = getField(type, field.getName());
            final Class<?> type = reflectedField.getType();
            final boolean isFinal = Modifier.isFinal(reflectedField.getModifiers());

            if (vectorObject && field.getAnnotation(Payload.class) != null) {
                vectorStorageIndex = index;
            }

            if (DEBUG) {
                trace("field: name=%s, kind=%s, offset=%d", field.getName(), type.getName(), field.getOffset());
            }
            bytesToAllocate = field.getOffset();
            bytesToAllocate += (field.getJavaKind().isObject()) ? BYTES_OBJECT_REFERENCE : field.getJavaKind().getByteCount();

            ObjectBuffer wrappedField = null;
            if (type.isArray()) {
                if (type == int[].class) {
                    wrappedField = new SPIRVIntArrayWrapper(deviceContext, isFinal, batchSize);
                } else if (type == float[].class) {
                    wrappedField = new SPIRVFloatArrayWrapper(deviceContext, isFinal, batchSize);
                } else if (type == double[].class) {
                    wrappedField = new SPIRVDoubleArrayWrapper(deviceContext, isFinal, batchSize);
                } else if (type == long[].class) {
                    wrappedField = new SPIRVLongArrayWrapper(deviceContext, isFinal, batchSize);
                } else if (type == short[].class) {
                    wrappedField = new SPIRVShortArrayWrapper(deviceContext, isFinal, batchSize);
                } else if (type == byte[].class) {
                    wrappedField = new SPIRVByteArrayWrapper(deviceContext, isFinal, batchSize);
                } else {
                    throw new RuntimeException("Cannot wrap field array type: " + type.getName());
                }

            } else if (field.getJavaKind().isObject()) {
                // We capture the field by the scope definition of the input
                // lambda expression
                try {
                    wrappedField = new SPIRVObjectWrapper(deviceContext, reflectedField.get(object), batchSize);
                } catch (IllegalAccessException | IllegalArgumentException e) {
                    throw new RuntimeException(e);
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

    // FIXME <REFACTOR> Common method 3 backends
    private void sortFieldsByOffset() {
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

    // FIXME <REFACTOR> Common method 3 backends
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

    @Override
    public long toBuffer() {
        return deviceContext.getMemoryManager().toBuffer();
    }

    @Override
    public long getBufferOffset() {
        return bufferOffset;
    }

    @Override
    public long toAbsoluteAddress() {
        return (vectorObject) ? getVectorAddress(false) : deviceContext.getMemoryManager().toAbsoluteDeviceAddress(bufferOffset);
    }

    // FIXME <REFACTOR>
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

    @Override
    public void read(Object reference) {
        read(reference, 0, null, false);
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

    private void deserialise(Object object) {
        buffer.rewind();

        if (fields.length > 0) {
            buffer.position(fields[0].getOffset());

            for (int i = 0; i < fields.length; i++) {
                HotSpotResolvedJavaField field = fields[i];
                Field f = getField(type, field.getName());
                f.setAccessible(true);
                if (DEBUG) {
                    trace("reading field: name=%s, offset=%d", field.getName(), field.getOffset());
                }
                readFieldFromBuffer(i, f, object);
            }
        }
    }

    @Override
    public int read(Object reference, long hostOffset, int[] events, boolean useDeps) {
        int event;
        if (vectorObject) {
            final FieldBuffer fieldBuffer = wrappedFields[vectorStorageIndex];
            event = fieldBuffer.read(reference, events, useDeps);
        } else {
            buffer.position(buffer.capacity());
            event = deviceContext.readBuffer(toBuffer(), bufferOffset, bytesToAllocate, buffer.array(), hostOffset, (useDeps) ? events : null);
            for (int i = 0; i < fields.length; i++) {
                if (wrappedFields[i] != null) {
                    wrappedFields[i].read(reference);
                }
            }
            deserialise(reference);
        }
        return event;
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
            if (deviceContext.useRelativeAddresses()) {
                buffer.putLong(wrappedFields[index].toRelativeAddress());
            } else {
                buffer.putLong(wrappedFields[index].toAbsoluteAddress());
            }
        } else {
            unimplemented("field type %s", fieldType.getName());
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
                Field f = getField(type, field.getName());
                if (DEBUG) {
                    trace("writing field: name=%s, offset=%d", field.getName(), field.getOffset());
                }

                buffer.position(field.getOffset());
                writeFieldToBuffer(i, f, object);
            }
        }
    }

    @Override
    public void write(Object reference) {
        if (vectorObject) {
            final FieldBuffer fieldBuffer = wrappedFields[vectorStorageIndex];
            fieldBuffer.write(reference);
        } else {
            if (!valid) {
                serialise(reference);
                // XXX: Offset 0
                deviceContext.writeBuffer(toBuffer(), bufferOffset, bytesToAllocate, buffer.array(), 0, null);
            }
            for (int i = 0; i < fields.length; i++) {
                if (wrappedFields[i] != null) {
                    wrappedFields[i].write(reference);
                }
            }
        }
        valid = true;
    }

    @Override
    public int enqueueRead(Object reference, long hostOffset, int[] events, boolean useDeps) {
        final int returnEvent;
        if (vectorObject) {
            final FieldBuffer fieldBuffer = wrappedFields[vectorStorageIndex];
            returnEvent = fieldBuffer.enqueueRead(reference, (useDeps) ? events : null, useDeps);
        } else {
            int index = 0;
            int[] internalEvents = new int[fields.length];
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
                    returnEvent = deviceContext.enqueueMarker();
            }

        }
        return useDeps ? returnEvent : -1;
    }

    @Override
    public List<Integer> enqueueWrite(Object reference, long batchSize, long hostOffset, int[] events, boolean useDeps) {
        ArrayList<Integer> eventList = new ArrayList<>();

        if (vectorObject) {
            final FieldBuffer fieldBuffer = wrappedFields[vectorStorageIndex];
            if (!valid) {
                valid = true;
                eventList.addAll(fieldBuffer.enqueueWrite(reference, (useDeps) ? events : null, useDeps));
            } else {
                eventList = null;
            }
        } else {
            // TODO this needs to run asynchronously
            if (!valid || (valid && !isFinal)) {
                serialise(reference);
                eventList.add(deviceContext.enqueueWriteBuffer(toBuffer(), bufferOffset, bytesToAllocate, buffer.array(), hostOffset, (useDeps) ? events : null));
                valid = true;
            }
            for (final FieldBuffer field : wrappedFields) {
                if (field != null && field.needsWrite()) {
                    eventList.addAll(field.enqueueWrite(reference, (useDeps) ? events : null, useDeps));
                }
            }
        }
        return useDeps ? eventList : null;
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
            buffer.order(deviceContext.getDevice().getByteOrder());
        }

        if (bufferOffset == -1) {
            bufferOffset = deviceContext.getMemoryManager().tryAllocate(bytesToAllocate, 32, getAlignment());
        }

        if (DEBUG) {
            debug("object: object=0x%x @ 0x%x (0x%x)", reference.hashCode(), toAbsoluteAddress(), toRelativeAddress());
        }
        for (FieldBuffer buffer : wrappedFields) {
            if (buffer != null) {
                // TODO: support batch sizes for scope/field arguments
                if (batchSize > 0) {
                    throw new TornadoMemoryException("[ERROR] BatchSize Allocation currently not supported for Objects Fields. BatchSize = " + batchSize + " (bytes)");
                }
                buffer.allocate(reference, batchSize);
            }
        }
    }

    @Override
    public int getAlignment() {
        return SPIRV_OBJECT_ALIGNMENT;
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
        long size = bytesToAllocate;
        for (FieldBuffer wrappedField : wrappedFields) {
            if (wrappedField != null) {
                size += wrappedField.size();
            }
        }
        return size;
    }
}
