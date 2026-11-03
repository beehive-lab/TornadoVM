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
package uk.ac.manchester.tornado.tc.runtime.tools;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import uk.ac.manchester.tornado.tc.tornado.ObjectLayout;
import uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation;

/**
 * The device layout of an object parameter from its class alone, for {@code tc emit}, which has the
 * kernel's classes but no argument values. The rule is the runtime's {@code FieldLayout}: a 16-byte
 * header, then each class's instance fields after its superclass's, largest first (ties by name),
 * each aligned to its size; a reference takes 4 bytes. A referenced object is described by its
 * declared class. Native arrays are laid out without their superclasses' fields.
 *
 * <p>
 * Without argument values only the layout is known: final fields and array lengths are left to the
 * device. Values come from {@code tc emit --arg name=value}, or are assumed when the kernel cannot be
 * compiled without them (see {@link #facts}).
 */
final class TypeLayouts {

    private static final int HEADER_BYTES = 16;
    private static final int REFERENCE_BYTES = 4;
    private static final String NATIVE_ARRAY = "uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray";
    private static final String TILE_CONTEXT = "uk.ac.manchester.tornado.api.tile.TileContext";
    private static final Set<String> NOT_OBJECTS = Set.of("uk.ac.manchester.tornado.api.KernelContext", TILE_CONTEXT);

    private TypeLayouts() {
    }

    /** A {@code --arg} value the parameter's type does not accept. */
    static final class BadArgument extends IllegalArgumentException {
        BadArgument(String name, String value, String expected) {
            super("--arg " + name + "=" + value + ": " + name + " takes " + expected);
        }
    }

    /** The size TornadoVM examples and tcirviz assume when a launch domain needs a value: integers and object sizes. */
    static final int ASSUMED = 256;

    /** The array length assumed, as tcirviz assumes it. */
    static final int ASSUMED_LENGTH = 1024;

    /**
     * One fact per parameter of {@code method}. A value given in {@code given} (by parameter name: a
     * scalar, an array length or the size an object is constructed with) specialises the kernel as the
     * runtime would for that argument. Otherwise an object parameter gets its layout and nothing else
     * is known; with {@code assume}, integer scalars and object sizes not given are taken as
     * {@link #ASSUMED} (not in TileContext kernels, whose integers are tensor extents) and array lengths
     * as {@link #ASSUMED_LENGTH}, and each assumption is added to {@code assumed}.
     */
    static List<ValueSpecialisation.Fact> facts(Method method, Map<String, String> given, boolean assume, List<String> assumed) {
        boolean tiles = Arrays.stream(method.getParameterTypes()).anyMatch(type -> type.getName().equals(TILE_CONTEXT));
        var facts = new ArrayList<ValueSpecialisation.Fact>();
        for (var parameter : method.getParameters()) {
            var type = parameter.getType();
            var value = given.get(parameter.getName());
            if (NOT_OBJECTS.contains(type.getName())) {
                facts.add(new ValueSpecialisation.Fact.Unknown());
            } else if (type.isPrimitive()) {
                if (value == null && assume && !tiles && isIntegral(type)) {
                    value = Integer.toString(ASSUMED);
                    assumed.add(parameter.getName() + "=" + value);
                }
                facts.add(value == null ? new ValueSpecialisation.Fact.Unknown() : new ValueSpecialisation.Fact.Scalar(scalar(parameter.getName(), type, value)));
            } else if (type.isArray() || isNativeArray(type)) {
                if (value == null && assume) {
                    value = Integer.toString(ASSUMED_LENGTH);
                    assumed.add(parameter.getName() + ".length=" + value);
                }
                facts.add(value == null ? new ValueSpecialisation.Fact.Unknown() : new ValueSpecialisation.Fact.Length(count(parameter.getName(), value, "a length")));
            } else if (isObject(type)) {
                if (value == null && assume) {
                    value = Integer.toString(ASSUMED);
                    assumed.add(parameter.getName() + "=" + value);
                }
                facts.add(value == null ? new ValueSpecialisation.Fact.Instance(Map.of(), describe(type)) : instance(type, (int) count(parameter.getName(), value, "a size")));
            } else {
                facts.add(new ValueSpecialisation.Fact.Unknown());
            }
        }
        return facts;
    }

    private static boolean isIntegral(Class<?> type) {
        return type == int.class || type == long.class || type == short.class || type == byte.class;
    }

    private static Number scalar(String name, Class<?> type, String text) {
        var value = text.trim();
        try {
            return switch (type.getName()) {
                case "long" -> Long.parseLong(value);
                case "float" -> Float.parseFloat(value);
                case "double" -> Double.parseDouble(value);
                case "boolean" -> switch (value) {
                    case "true" -> 1;
                    case "false" -> 0;
                    default -> throw new NumberFormatException(value);
                };
                case "char" -> {
                    if (value.length() != 1) {
                        throw new NumberFormatException(value);
                    }
                    yield (int) value.charAt(0);
                }
                case "short" -> (int) Short.parseShort(value);
                case "byte" -> (int) Byte.parseByte(value);
                default -> Integer.parseInt(value);
            };
        } catch (NumberFormatException e) {
            throw new BadArgument(name, text, expected(type.getName()));
        }
    }

    /** What a parameter of the type takes, as an error message says it: "an int", "true or false". */
    private static String expected(String type) {
        return switch (type) {
            case "boolean" -> "true or false";
            case "char" -> "one character";
            case "int" -> "an int";
            default -> "a " + type;
        };
    }

    /** A length or size: a non-negative whole number that fits an int. */
    private static long count(String name, String text, String what) {
        try {
            int value = Integer.parseInt(text.trim());
            if (value >= 0) {
                return value;
            }
        } catch (NumberFormatException e) {
            // Reported below.
        }
        throw new BadArgument(name, text, what + ": a non-negative whole number");
    }

    /**
     * An object built through its constructor taking only ints (each {@code size}), described as the
     * runtime describes an argument: its final primitive fields, the lengths of its native arrays and
     * its layout. Without such a constructor, the layout only.
     */
    private static ValueSpecialisation.Fact instance(Class<?> type, int size) {
        try {
            var constructor = Arrays.stream(type.getConstructors()).filter(c -> c.getParameterCount() > 0 && Arrays.stream(c.getParameterTypes()).allMatch(t -> t == int.class))
                    .min(Comparator.comparingInt(java.lang.reflect.Constructor::getParameterCount));
            if (constructor.isEmpty()) {
                return new ValueSpecialisation.Fact.Instance(Map.of(), describe(type));
            }
            var arguments = new Object[constructor.get().getParameterCount()];
            Arrays.fill(arguments, size);
            var object = constructor.get().newInstance(arguments);
            var finals = new HashMap<String, Number>();
            var lengths = new HashMap<String, Long>();
            for (var c = type; c != null; c = c.getSuperclass()) {
                for (var field : c.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    var fieldType = field.getType();
                    if (isNativeArray(fieldType)) {
                        field.setAccessible(true);
                        var array = field.get(object);
                        if (array != null) {
                            lengths.putIfAbsent(field.getName(), (Long) array.getClass().getMethod("getSizeLong").invoke(array));
                        }
                    } else if (Modifier.isFinal(field.getModifiers()) && fieldType.isPrimitive() && fieldType != boolean.class && fieldType != char.class) {
                        field.setAccessible(true);
                        finals.putIfAbsent(field.getName(), (Number) field.get(object));
                    }
                }
            }
            return new ValueSpecialisation.Fact.Instance(finals, describe(type), lengths);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return new ValueSpecialisation.Fact.Instance(Map.of(), describe(type));
        }
    }

    /** An object passed by field copy: not a primitive, an array, a native array or a context. */
    static boolean isObject(Class<?> type) {
        return !type.isPrimitive() && !type.isArray() && !type.isInterface() && !NOT_OBJECTS.contains(type.getName()) && !isNativeArray(type);
    }

    /** The layouts of {@code type} and of the classes its reference fields declare. */
    static List<ObjectLayout> describe(Class<?> type) {
        var out = new ArrayList<ObjectLayout>();
        describe(type, !isNativeArray(type), out, new HashSet<>());
        return out;
    }

    private static void describe(Class<?> type, boolean includeSuper, List<ObjectLayout> out, Set<Class<?>> seen) {
        if (!seen.add(type)) {
            return;
        }
        var offsets = new HashMap<String, Integer>();
        var references = new HashMap<String, String>();
        for (var entry : offsets(type, includeSuper).entrySet()) {
            var field = entry.getKey();
            offsets.putIfAbsent(field.getName(), entry.getValue());
            var fieldType = field.getType();
            if (!fieldType.isPrimitive() && !fieldType.isArray() && !isNativeArray(fieldType) && !fieldType.isInterface()) {
                references.put(field.getName(), fieldType.getName());
                describe(fieldType, includeSuper, out, seen);
            }
        }
        out.add(new ObjectLayout(type.getName(), Map.copyOf(offsets), Map.copyOf(references)));
    }

    /** Byte offset of each instance field of {@code type}: its own, or with its superclasses' too. */
    static Map<Field, Integer> offsets(Class<?> type, boolean includeSuper) {
        var all = new java.util.LinkedHashMap<Field, Integer>();
        layout(type, all);
        if (includeSuper) {
            return all;
        }
        var own = new java.util.LinkedHashMap<Field, Integer>();
        all.forEach((field, offset) -> {
            if (field.getDeclaringClass() == type) {
                own.put(field, offset);
            }
        });
        return own;
    }

    /** Lays out {@code type} and its superclasses into {@code offsets}; returns the end of the last field. */
    private static int layout(Class<?> type, Map<Field, Integer> offsets) {
        int position = type.getSuperclass() == null ? HEADER_BYTES : layout(type.getSuperclass(), offsets);
        var declared = Arrays.stream(type.getDeclaredFields()) //
                .filter(field -> !Modifier.isStatic(field.getModifiers())) //
                .sorted(Comparator.comparingInt(TypeLayouts::sizeOf).reversed().thenComparing(Field::getName)) //
                .toList();
        for (var field : declared) {
            int size = sizeOf(field);
            position = (position + size - 1) / size * size;
            offsets.put(field, position);
            position += size;
        }
        return position;
    }

    static int sizeOf(Field field) {
        var type = field.getType();
        if (!type.isPrimitive()) {
            return REFERENCE_BYTES;
        }
        return switch (type.getName()) {
            case "boolean", "byte" -> 1;
            case "char", "short" -> 2;
            case "int", "float" -> 4;
            default -> 8;
        };
    }

    private static boolean isNativeArray(Class<?> type) {
        for (var c = type; c != null; c = c.getSuperclass()) {
            if (c.getName().equals(NATIVE_ARRAY)) {
                return true;
            }
        }
        return false;
    }
}
