/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.babylon.tc;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The memory layout of a HAT device type (HAT local or private memory), from its {@code deviceSchema}:
 * fields and arrays of primitives, half floats or nested structs, each nested struct described by
 * the schema's members or, failing that, by its own {@code deviceSchema}. Members are naturally
 * aligned. The memory is private to the kernel, so only the accessors must agree on it.
 * Read reflectively, so this class does not link against HAT.
 */
final class DeviceLayouts {

    private static final Map<Class<?>, GroupLayout> LAYOUTS = new ConcurrentHashMap<>();

    private DeviceLayouts() {
    }

    static GroupLayout of(Class<?> deviceType) {
        return LAYOUTS.computeIfAbsent(deviceType, t -> {
            try {
                var schemaField = t.getDeclaredField("deviceSchema");
                schemaField.setAccessible(true);
                var schema = schemaField.get(null);
                var root = schema.getClass().getField("root").get(schema);
                return struct(t, members(root));
            } catch (ReflectiveOperationException e) {
                throw new BabylonTranslator.Unsupported("TC-E1003", "HAT device type " + t.getSimpleName() + " has no readable deviceSchema: " + e);
            }
        });
    }

    private static List<?> members(Object node) throws ReflectiveOperationException {
        return (List<?>) node.getClass().getMethod("members").invoke(node);
    }

    private static GroupLayout struct(Class<?> type, List<?> members) throws ReflectiveOperationException {
        var layouts = new ArrayList<MemoryLayout>();
        long offset = 0;
        long alignment = 1;
        for (var member : members) {
            var name = (String) member.getClass().getMethod("name").invoke(member);
            var clazz = (Class<?>) member.getClass().getMethod("clazz").invoke(member);
            var nested = members(member);
            MemoryLayout element = element(clazz, nested);
            if (member.getClass().getSimpleName().equals("Array")) {
                int size = (int) member.getClass().getMethod("size").invoke(member);
                element = MemoryLayout.sequenceLayout(size, element);
            }
            long align = element.byteAlignment();
            if (offset % align != 0) {
                layouts.add(MemoryLayout.paddingLayout(align - offset % align));
                offset += align - offset % align;
            }
            layouts.add(element.withName(name));
            offset += element.byteSize();
            alignment = Math.max(alignment, align);
        }
        if (offset % alignment != 0) {
            layouts.add(MemoryLayout.paddingLayout(alignment - offset % alignment));
        }
        return MemoryLayout.structLayout(layouts.toArray(MemoryLayout[]::new)).withName(type.getSimpleName());
    }

    private static MemoryLayout element(Class<?> clazz, List<?> nested) throws ReflectiveOperationException {
        if (clazz == int.class) {
            return ValueLayout.JAVA_INT;
        } else if (clazz == long.class) {
            return ValueLayout.JAVA_LONG;
        } else if (clazz == float.class) {
            return ValueLayout.JAVA_FLOAT;
        } else if (clazz == double.class) {
            return ValueLayout.JAVA_DOUBLE;
        } else if (clazz == short.class) {
            return ValueLayout.JAVA_SHORT;
        } else if (clazz == char.class) {
            return ValueLayout.JAVA_CHAR;
        } else if (clazz == byte.class) {
            return ValueLayout.JAVA_BYTE;
        } else if (clazz == boolean.class) {
            return ValueLayout.JAVA_BOOLEAN;
        }
        if (clazz.getName().equals("hat.types.F16") || clazz.getName().equals("hat.types.BF16")) {
            return MemoryLayout.structLayout(ValueLayout.JAVA_SHORT.withName("value")).withName(clazz.getSimpleName());
        }
        if (!nested.isEmpty()) {
            return struct(clazz, nested);
        }
        return of(clazz);
    }
}
