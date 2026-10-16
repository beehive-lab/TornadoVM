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
