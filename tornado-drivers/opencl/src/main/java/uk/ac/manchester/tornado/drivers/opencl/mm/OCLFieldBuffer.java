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
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getVMRuntime;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.DEBUG;

import java.lang.reflect.Field;
import java.util.*;

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
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContext;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLKind;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.utils.TornadoUtils;

public class OCLFieldBuffer implements XPUBuffer {

    private final HotSpotResolvedJavaType resolvedType;
    private final List<FieldBuffer> wrappedFields;
    private final Class<?> objectType;
    private final OCLDeviceContext deviceContext;
    private long setSubRegionSize;
    private final TornadoLogger logger;
    private final Access access;

    private OCLFieldBuffer(final OCLDeviceContext device, Object object, Access access, boolean includeSuperClasses) {
        this.objectType = object.getClass();
        this.deviceContext = device;
        this.logger = new TornadoLogger(this.getClass());
        this.access = access;

        resolvedType = (HotSpotResolvedJavaType) getVMRuntime().getHostJVMCIBackend().getMetaAccess().lookupJavaType(objectType);
        HotSpotResolvedJavaField[] fields = (HotSpotResolvedJavaField[]) resolvedType.getInstanceFields(includeSuperClasses);
        wrappedFields = new ArrayList<>();

        for (int index = 0; index < fields.length; index++) {
            HotSpotResolvedJavaField field = fields[index];
            final Field reflectedField = getField(findDeclaringClass(field), field.getName());
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
                wrappedField = new OCLFieldBuffer(device, TornadoUtils.getObjectFromField(reflectedField, object), access, includeSuperClasses);
            }

            if (wrappedField != null) {
                wrappedFields.add(new FieldBuffer(field, reflectedField, wrappedField, object));
            }
        }

        sortFieldsByOffset();
    }

    public OCLFieldBuffer(final OCLDeviceContext device, Object object, Access access) {
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

    @Override
    public void allocate(Object reference, long batchSize, Access access) throws TornadoOutOfMemoryException, TornadoMemoryException {
        if (DEBUG) {
            logger.debug("object: object=0x%x, class=%s", reference.hashCode(), reference.getClass().getName());
        }

        for(FieldBuffer fb : wrappedFields){
            fb.getObjectBuffer().allocate(fb.getObjectField(), batchSize, access);
        }

        if (DEBUG) {
            logger.debug("object: object=0x%x", reference.hashCode());
        }
    }

    @Override
    public void markAsFreeBuffer() throws TornadoMemoryException {
        for(FieldBuffer fb : wrappedFields){
            fb.getObjectBuffer().markAsFreeBuffer();
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

    private void sortFieldsByOffset() {
        // TODO Replace bubble sort with Arrays.sort + comparator
        for (int i = 0; i < wrappedFields.size(); i++) {
            HotSpotResolvedJavaField field_i = wrappedFields.get(i).getResolvedField();
            for (int j = 0; j < wrappedFields.size(); j++) {
                HotSpotResolvedJavaField field_j = wrappedFields.get(j).getResolvedField();
                if (field_i.getOffset() < field_j.getOffset()) {
                    FieldBuffer tmp = wrappedFields.get(j);
                    wrappedFields.set(j, wrappedFields.get(i));
                    wrappedFields.set(i, tmp);
                }
            }
        }
    }

    private Class<?> findDeclaringClass(HotSpotResolvedJavaField field) {
        Class<?> objectTypeTemp = objectType;
        while (objectTypeTemp != null && !objectTypeTemp.getName().equals(field.getDeclaringClass().toJavaName())) {
            objectTypeTemp = objectTypeTemp.getSuperclass();
        }
        if (objectTypeTemp == null) {
            throw new TornadoRuntimeException(String.format("Cannot find declaring class %s in hierarchy of %s for field %s", field.getDeclaringClass().toJavaName(), objectType.getName(), field.getName()));
        }
        return objectTypeTemp;
    }

    @Override
    public void write(long executionPlanId, Object object) {
        // XXX: Offset 0
        for (int i = 0; i < wrappedFields.size(); i++) {
            if (wrappedFields.get(i) != null) {
                if (DEBUG) {
                    logger.trace("fieldBuffer: write - field=%s, parent=0x%x, child=0x%x", wrappedFields.get(i).getResolvedField().getName(), object.hashCode(), wrappedFields.get(i).getObjectField().hashCode());
                }
                wrappedFields.get(i).getObjectBuffer().write(executionPlanId, wrappedFields.get(i).getObjectField());
            }
        }
    }

    @Override
    public long toBuffer() {
        throw new TornadoRuntimeException("[ERROR] not supported");
    }

    @Override
    public void setBuffer(XPUBufferWrapper bufferWrapper) {
        throw new TornadoRuntimeException("[ERROR] not supported");
    }

    @Override
    public void read(long executionPlanId, Object object) {
        // XXX: offset 0
        for(FieldBuffer fb : wrappedFields){
            read(executionPlanId, fb.getObjectField(), 0, 0, null, false);
        }
    }

    @Override
    public int read(long executionPlanId, Object object, long hostOffset, long partialReadSize, int[] events, boolean useDeps) {
        final int returnEvent;
        int index = 0;
        int[] internalEvents = new int[wrappedFields.size()];
        Arrays.fill(internalEvents, -1);

        for (FieldBuffer fb : wrappedFields) {
            if (fb != null) {
                if (DEBUG) {
                    logger.debug("fieldBuffer: read - field=%s, parent=0x%x, child=0x%x", fb.getField(), object.hashCode(), fb.getObjectField().hashCode());
                }
                internalEvents[index] = fb.getObjectBuffer().read(executionPlanId, fb.getObjectField(), 0, 0, events, useDeps);
                index++;
            }
        }
        if (index == 1) {
            returnEvent = internalEvents[0];
        } else {
            returnEvent = deviceContext.enqueueMarker(executionPlanId, internalEvents);
        }
        return useDeps ? returnEvent : -1;
    }

    @Override
    public int enqueueRead(long executionPlanId, Object reference, long hostOffset, int[] events, boolean useDeps) {
        final int returnEvent;
        int index = 0;
        int[] internalEvents = new int[wrappedFields.size()];
        Arrays.fill(internalEvents, -1);

        for (FieldBuffer fb : wrappedFields) {
            if (fb != null) {
                if (DEBUG) {
                    logger.trace("fieldBuffer: enqueueRead* - field=%s, parent=0x%x, child=0x%x", fb.getField(), reference.hashCode(), fb.getObjectField().hashCode());
                }
                internalEvents[index] = (useDeps) ? fb.getObjectBuffer().enqueueRead(executionPlanId, fb.getObjectField(), 0, (useDeps) ? events : null, useDeps) : -1;
                index++;
            }
        }

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

        for (final FieldBuffer field : wrappedFields) {
            if (field != null) {
                if (DEBUG) {
                    logger.trace("fieldBuffer: enqueueWrite* - field=%s, parent=0x%x, child=0x%x", field.getField(), ref.hashCode(), field.getObjectField().hashCode());
                }
                eventList.addAll((useDeps) ? field.getObjectBuffer().enqueueWrite(executionPlanId, field.getObjectField(), 0, 0, (useDeps) ? events : null, useDeps) : null);
            }
        }
        return eventList;
    }

    @Override
    public String toString() {
        return String.format("object wrapper: type=%s, fields=%d\n", resolvedType.getName(), wrappedFields.size());
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

    @Override
    public long getBufferId() {
        throw new TornadoRuntimeException("[ERROR] not implemented");
    }

    @Override
    public long getBufferOffset() {
        throw new TornadoRuntimeException("[ERROR] not implemented");
    }

    @Override
    public long getBufferSize() {
        long size = 0;
        for (FieldBuffer wrappedField : wrappedFields) {
            if (wrappedField != null) {
                size += wrappedField.getObjectBuffer().getBufferSize();
            }
        }
        return size;
    }

    @Override
    public Access getBufferAccess() {
        return access;
    }
}
