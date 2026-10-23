/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.runtime.common;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;

/**
 * The layout of an object's copy in device memory. TornadoVM owns it: it does not follow the JVM's
 * object layout, so it needs neither JVMCI nor {@code Unsafe}, and it is the same on every JDK and VM.
 *
 * <ul>
 * <li>A {@value #HEADER_BYTES}-byte header; the runtime zeroes the 8 bytes at {@value #HUB_OFFSET}.</li>
 * <li>Then the superclass's fields, then the class's own: each class's fields in decreasing size,
 * then by name, each aligned to its size.</li>
 * <li>A reference takes {@value #REFERENCE_BYTES} bytes: the offset of the referenced object's copy
 * from this object's copy, divided by {@value #REFERENCE_SCALE}.</li>
 * <li>The object's size is the end of its last field, rounded up to 8 bytes.</li>
 * </ul>
 *
 * Field values are read and written through {@link VarHandle}s.
 */
public final class FieldLayout {

    public static final int HEADER_BYTES = 16;
    public static final int HUB_OFFSET = 8;
    public static final int REFERENCE_BYTES = 4;
    public static final int REFERENCE_SCALE = 8;

    private static final ClassValue<FieldLayout> LAYOUTS = new ClassValue<>() {
        @Override
        protected FieldLayout computeValue(Class<?> type) {
            return new FieldLayout(type);
        }
    };

    private static final Map<Field, VarHandle> HANDLES = new ConcurrentHashMap<>();

    private final Field[] all;
    private final Field[] own;
    private final Map<Field, Integer> offsets;
    private final int end;

    private FieldLayout(Class<?> type) {
        FieldLayout parent = type.getSuperclass() == null ? null : of(type.getSuperclass());
        offsets = new HashMap<>();
        List<Field> fields = new ArrayList<>();
        int position = HEADER_BYTES;
        if (parent != null) {
            offsets.putAll(parent.offsets);
            fields.addAll(Arrays.asList(parent.all));
            position = parent.end;
        }
        List<Field> declared = Arrays.stream(type.getDeclaredFields()) //
                .filter(field -> !Modifier.isStatic(field.getModifiers())) //
                .sorted(Comparator.comparingInt(FieldLayout::sizeOf).reversed().thenComparing(Field::getName)) //
                .toList();
        for (Field field : declared) {
            int size = sizeOf(field);
            position = (position + size - 1) / size * size;
            offsets.put(field, position);
            position += size;
        }
        fields.addAll(declared);
        all = fields.toArray(Field[]::new);
        own = declared.toArray(Field[]::new);
        end = position;
    }

    public static FieldLayout of(Class<?> type) {
        return LAYOUTS.get(type);
    }

    /** Instance fields in layout order: with the superclasses' fields first, or only the class's own. */
    public Field[] fields(boolean includeSuperClasses) {
        return (includeSuperClasses ? all : own).clone();
    }

    /** Offset of {@code field} (declared by this class or a superclass) in the device copy. */
    public int offset(Field field) {
        if (offsets.get(field) instanceof Integer offset) {
            return offset;
        }
        throw new TornadoRuntimeException("field " + field + " is not in the layout");
    }

    /** Size of the device copy in bytes. */
    public int size() {
        return (end + 7) & ~7;
    }

    /** Bytes {@code field} takes in a device copy. */
    public static int sizeOf(Field field) {
        Class<?> type = field.getType();
        return type.isPrimitive() ? JavaKind.fromJavaClass(type).getByteCount() : REFERENCE_BYTES;
    }

    /** Reads {@code field} of {@code object}. */
    public static Object get(Field field, Object object) {
        return handle(field).get(object);
    }

    /** Writes {@code value}, boxed, to {@code field} of {@code object}. */
    public static void set(Field field, Object object, Object value) {
        if (Modifier.isFinal(field.getModifiers())) {
            // A VarHandle cannot write a final field; deep reflection still can (not in a record or hidden class).
            try {
                field.setAccessible(true);
                field.set(object, value);
            } catch (IllegalAccessException | RuntimeException e) {
                throw new TornadoRuntimeException("cannot write final field " + field + ": " + e);
            }
            return;
        }
        handle(field).set(object, value);
    }

    private static VarHandle handle(Field field) {
        return HANDLES.computeIfAbsent(field, f -> {
            Class<?> owner = f.getDeclaringClass();
            // This module must read the owner's module (an application class is usually unnamed); the
            // owner's package must be open to it, as for reflection.
            FieldLayout.class.getModule().addReads(owner.getModule());
            try {
                return MethodHandles.privateLookupIn(owner, MethodHandles.lookup()).unreflectVarHandle(f).withInvokeBehavior();
            } catch (IllegalAccessException e) {
                throw new TornadoRuntimeException("cannot access field " + f + " (open its package to tornado.runtime): " + e);
            }
        });
    }
}
