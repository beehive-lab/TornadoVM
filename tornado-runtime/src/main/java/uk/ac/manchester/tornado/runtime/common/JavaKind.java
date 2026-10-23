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

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The kinds of Java values the runtime lays out in device memory: the eight primitives, references,
 * and {@code void}. Sizes are the Java language sizes; a reference's size depends on the VM and is
 * not given here.
 */
public enum JavaKind {

    Boolean(boolean.class, 'Z', 1),
    Byte(byte.class, 'B', 1),
    Short(short.class, 'S', 2),
    Char(char.class, 'C', 2),
    Int(int.class, 'I', 4),
    Float(float.class, 'F', 4),
    Long(long.class, 'J', 8),
    Double(double.class, 'D', 8),
    Object(Object.class, 'A', -1),
    Void(void.class, 'V', 0),
    Illegal(null, '-', 0);

    /** Primitive classes, {@code void} included, to their kind. */
    private static final Map<Class<?>, JavaKind> PRIMITIVES = Arrays.stream(values()) //
            .filter(kind -> kind.javaClass != null && kind.javaClass.isPrimitive()) //
            .collect(Collectors.toUnmodifiableMap(kind -> kind.javaClass, Function.identity()));

    private final Class<?> javaClass;
    private final char typeChar;
    private final int byteCount;

    JavaKind(Class<?> javaClass, char typeChar, int byteCount) {
        this.javaClass = javaClass;
        this.typeChar = typeChar;
        this.byteCount = byteCount;
    }

    /** The descriptor character: {@code 'I'} for int, {@code 'A'} for any reference. */
    public char getTypeChar() {
        return typeChar;
    }

    /** The Java source name: {@code "int"}, {@code "Object"}, {@code "illegal"}. */
    public String getJavaName() {
        return switch (this) {
            case Object -> "Object";
            case Illegal -> "illegal";
            default -> javaClass.getName();
        };
    }

    /** Size in bytes; -1 for {@link #Object}. */
    public int getByteCount() {
        return byteCount;
    }

    public int getBitCount() {
        return byteCount * java.lang.Byte.SIZE;
    }

    /** One of the eight primitive types ({@code void} is not). */
    public boolean isPrimitive() {
        return byteCount > 0;
    }

    public boolean isObject() {
        return this == Object;
    }

    public Class<?> toJavaClass() {
        return javaClass;
    }

    /** The kind of a value of type {@code type}: its primitive kind, else {@link #Object}. */
    public static JavaKind fromJavaClass(Class<?> type) {
        return PRIMITIVES.getOrDefault(type, Object);
    }
}
