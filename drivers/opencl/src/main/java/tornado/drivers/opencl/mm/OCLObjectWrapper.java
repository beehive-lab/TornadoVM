package tornado.drivers.opencl.mm;

import com.oracle.graal.api.meta.PrimitiveConstant;
import com.oracle.graal.hotspot.HotSpotGraalRuntimeProvider;
import com.oracle.graal.hotspot.meta.HotSpotResolvedJavaField;
import com.oracle.graal.hotspot.meta.HotSpotResolvedJavaType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.Arrays;
import tornado.api.Payload;
import tornado.api.Vector;
import tornado.common.ObjectBuffer;
import tornado.common.RuntimeUtilities;
import tornado.common.Tornado;
import tornado.common.exceptions.TornadoOutOfMemoryException;
import tornado.drivers.opencl.OCLDeviceContext;
import tornado.runtime.TornadoRuntime;

public class OCLObjectWrapper implements ObjectBuffer {

    private final boolean vectorObject;
    private int vectorStorageIndex;
    private long bufferOffset;
    private long bytes;
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

    private final HotSpotGraalRuntimeProvider runtime;

    public OCLObjectWrapper(final OCLDeviceContext device, Object object) {
        this.type = object.getClass();
        this.deviceContext = device;

        valid = false;
        isFinal = true;

        runtime = TornadoRuntime.getVMRuntimeProvider();
        hubOffset = runtime.getConfig().hubOffset;
        fieldsOffset = runtime.getConfig().instanceKlassFieldsOffset;

        resolvedType = (HotSpotResolvedJavaType) TornadoRuntime
                .getVMProviders().getMetaAccess()
                .lookupJavaType(object.getClass());

        if (resolvedType.getAnnotation(Vector.class) != null) {
            vectorObject = true;
        } else {
            vectorObject = false;
        }

        vectorStorageIndex = -1;

        fields = (HotSpotResolvedJavaField[]) resolvedType
                .getInstanceFields(true);
        sortFieldsByOffset();

        wrappedFields = new FieldBuffer[fields.length];
        internalEvents = new int[fields.length];

        int index = 0;
        // calculate object size
        bytes = (fields.length > 0) ? fields[0].offset() : fieldsOffset;
        for (HotSpotResolvedJavaField field : fields) {
            final Field reflectedField = getField(type, field.getName());
            final Class<?> type = reflectedField.getType();
            final boolean isFinal = Modifier.isFinal(reflectedField
                    .getModifiers());

            if (vectorObject) {
                if (field.getAnnotation(Payload.class) != null) {
                    vectorStorageIndex = index;
                }
            }

            if (Tornado.DEBUG) {
                Tornado.trace("field: name=%s, kind=%s, offset=%d",
                        field.getName(), type.getName(), field.offset());
            }
            bytes = field.offset();
            bytes += (field.getKind().isObject()) ? 8 : field.getKind()
                    .getByteCount();

            ObjectBuffer wrappedField = null;
            if (type.isArray()) {
                if (type == int[].class) {
                    wrappedField = new OCLIntArrayWrapper(device, isFinal);
                } else if (type == float[].class) {
                    wrappedField = new OCLFloatArrayWrapper(device, isFinal);

                } else if (type == double[].class) {
                    wrappedField = new OCLDoubleArrayWrapper(device, isFinal);

                } else if (type == long[].class) {
                    wrappedField = new OCLLongArrayWrapper(device, isFinal);

                } else if (type == short[].class) {
                    wrappedField = new OCLShortArrayWrapper(device, isFinal);
                } else if (type == byte[].class) {
                    wrappedField = new OCLByteArrayWrapper(device, isFinal);
                } else {
                    Tornado.warn("cannot wrap field: array type=%s",
                            type.getName());

                }
            } else if (field.getKind().isObject()) {
                try {
                    wrappedField = new OCLObjectWrapper(device,
                            reflectedField.get(object));
                } catch (IllegalArgumentException | IllegalAccessException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            } else {
                this.isFinal &= isFinal;
            }

            if (wrappedField != null) {
                wrappedFields[index] = new FieldBuffer(reflectedField,
                        wrappedField);
            }
            index++;
        }

        if (Tornado.DEBUG) {
            Tornado.trace("object: type=%s, size=%s", resolvedType.getName(),
                    RuntimeUtilities.humanReadableByteCount(bytes, true));
        }

    }

    @Override
    public void allocate(Object ref) throws TornadoOutOfMemoryException {
        if (Tornado.DEBUG) {
            Tornado.debug("object: object=0x%x, class=%s, size=%s",
                    ref.hashCode(), ref.getClass().getName(),
                    RuntimeUtilities.humanReadableByteCount(bytes, true));
        }
        buffer = ByteBuffer.allocate((int) bytes);
        buffer.order(deviceContext.getByteOrder());
        bufferOffset = deviceContext.getMemoryManager().tryAllocate(ref.getClass(), bytes,
                32, getAlignment());

        if (Tornado.DEBUG) {
            Tornado.debug("object: object=0x%x @ 0x%x (0x%x)", ref.hashCode(),
                    toAbsoluteAddress(), toRelativeAddress());
        }
        for (FieldBuffer buffer : wrappedFields) {
            if (buffer != null) {
                buffer.allocate(ref);
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
                Tornado.fatal("unable to get field");
                e.printStackTrace();
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
                Tornado.fatal("unable to write primitive to buffer");
            }
        } else if (wrappedFields[index] != null) {
            if (Tornado.OPENCL_USE_RELATIVE_ADDRESSES) {
                buffer.putLong(wrappedFields[index].toRelativeAddress());
            } else {
                buffer.putLong(wrappedFields[index].toAbsoluteAddress());
            }
        } else {
            Tornado.fatal("unimplemented - field type %s",
                    fieldType.getName());
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
                e.printStackTrace();
            }
        } else if (wrappedFields[index] != null) {
            buffer.getLong();
        } else {
            Tornado.fatal("unimplemented - field type %s",
                    fieldType.getName());
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
        if (Tornado.DEBUG) {
            Tornado.trace("object: hub offset=%d, value=0x%x", hubOffset,
                    ((PrimitiveConstant) resolvedType.getObjectHub()).asLong());
        }
        buffer.putLong(((PrimitiveConstant) resolvedType.getObjectHub())
                .asLong());

        if (fields.length > 0) {
            buffer.position(fields[0].offset());

            for (int i = 0; i < fields.length; i++) {
                HotSpotResolvedJavaField field = fields[i];
                Field f = getField(type, field.getName());
                if (Tornado.DEBUG) {
                    Tornado.trace("writing field: name=%s, offset=%d",
                            field.getName(), field.offset());
                }

                buffer.position(field.offset());
                writeFieldToBuffer(i, f, object);
            }

        }
//         dump();
    }

    private void deserialise(Object object) {
        buffer.rewind();

        if (fields.length > 0) {
            buffer.position(fields[0].offset());

            for (int i = 0; i < fields.length; i++) {
                HotSpotResolvedJavaField field = fields[i];
                Field f = getField(type, field.getName());
                f.setAccessible(true);
                if (Tornado.DEBUG) {
                    Tornado.trace("reading field: name=%s, offset=%d",
                            field.getName(), field.offset());
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
                deviceContext.writeBuffer(toBuffer(), bufferOffset, bytes,
                        buffer.array(), null);
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
        if (vectorObject) {
            final FieldBuffer fieldBuffer = wrappedFields[vectorStorageIndex];
            fieldBuffer.read(object);
        } else {
            buffer.position(buffer.capacity());
            deviceContext.readBuffer(toBuffer(), bufferOffset, bytes,
                    buffer.array(), null);
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
        return (vectorObject) ? getVectorAddress(false) : deviceContext
                .getMemoryManager().toAbsoluteDeviceAddress(bufferOffset);
    }

    private long getVectorAddress(boolean relative) {
        final HotSpotResolvedJavaField resolvedField = fields[vectorStorageIndex];
        final FieldBuffer fieldBuffer = wrappedFields[vectorStorageIndex];
        final long address = (relative) ? fieldBuffer.toRelativeAddress()
                : fieldBuffer.toAbsoluteAddress();

        final long arrayBaseOffset = runtime.getArrayBaseOffset(resolvedField
                .getKind());
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
        System.out
                .printf("Buffer  : capacity = %s, in use = %s, device = %s \n",
                        RuntimeUtilities.humanReadableByteCount(bytes, true),
                        RuntimeUtilities.humanReadableByteCount(
                                buffer.position(), true), deviceContext
                        .getDevice().getName());
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
    public int enqueueRead(Object ref, int[] events) {
        if (vectorObject) {
            final FieldBuffer fieldBuffer = wrappedFields[vectorStorageIndex];
            return fieldBuffer.enqueueRead(ref, events);
        } else {
            int index = 0;
            Arrays.fill(internalEvents, -1);

            for (FieldBuffer fb : wrappedFields) {
                if (fb != null) {
                    internalEvents[index] = fb.enqueueRead(ref, events);
                    index++;
                }
            }

            if (!isFinal) {
                internalEvents[index] = deviceContext.enqueueReadBuffer(toBuffer(),
                        bufferOffset, bytes, buffer.array(), events);
                index++;

                // TODO this needs to run asynchronously
                deserialise(ref);
            }

            switch (index) {
                case 0:
                    return -1;
                case 1:
                    return internalEvents[0];
                default:
                    return deviceContext
                            .enqueueMarker(internalEvents);
            }

        }
    }

    @Override
    public int enqueueWrite(Object ref, int[] events) {

        if (vectorObject) {
            final FieldBuffer fieldBuffer = wrappedFields[vectorStorageIndex];
            if (!valid) {
                valid = true;
                return fieldBuffer.enqueueWrite(ref, events);
            } else {
                return -1;
            }

        } else {
            Arrays.fill(internalEvents, -1);
            int index = 0;

            // TODO this needs to run asynchronously
            if (!valid || (valid && !isFinal)) {
                serialise(ref);

                internalEvents[index] = deviceContext.enqueueWriteBuffer(toBuffer(),
                        bufferOffset, bytes, buffer.array(), events);
                index++;

                valid = true;
            }

            for (final FieldBuffer fb : wrappedFields) {
                if (fb != null && fb.needsWrite()) {
//                     System.out.printf("field: write %s onDevice=%s, isFinal=%s\n",fb.getFieldName(), fb.onDevice(), fb.isFinal());
                    internalEvents[index] = fb.enqueueWrite(ref, events);
                    index++;
                }
            }

            switch (index) {
                case 0:
                    return -1;
                case 1:
                    return internalEvents[0];
                default:
                    return deviceContext
                            .enqueueMarker(internalEvents);
            }
        }
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
        return String.format("object wrapper: type=%s, fields=%d, valid=%s\n",
                resolvedType.getName(), wrappedFields.length, valid);
    }

    @Override
    public void printHeapTrace() {
        System.out.printf("0x%x:\ttype=%s, num fields=%d (%d)\n",
                toAbsoluteAddress(), type.getName(), fields.length,
                wrappedFields.length);
        for (FieldBuffer fb : wrappedFields) {
            if (fb != null) {
                System.out.printf("\t0x%x\tname=%s\n", fb.toAbsoluteAddress(),
                        fb.getFieldName());
            }
        }
    }

    @Override
    public long size() {
        return bytes;
    }

}
