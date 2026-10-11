/*
 * Copyright (c) 2018, 2021, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
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

/**
 * The kinds of Java values the runtime lays out in device memory: the eight primitives, references,
 * and {@code void}. Sizes are the Java language sizes; a reference's size depends on the VM and is
 * not given here.
 */
public enum JavaKind {
    Boolean('Z', "boolean", 1, boolean.class),
    Byte('B', "byte", 1, byte.class),
    Short('S', "short", 2, short.class),
    Char('C', "char", 2, char.class),
    Int('I', "int", 4, int.class),
    Float('F', "float", 4, float.class),
    Long('J', "long", 8, long.class),
    Double('D', "double", 8, double.class),
    Object('A', "Object", -1, Object.class),
    Void('V', "void", 0, void.class),
    Illegal('-', "illegal", 0, null);

    private final char typeChar;
    private final String javaName;
    private final int byteCount;
    private final Class<?> javaClass;

    JavaKind(char typeChar, String javaName, int byteCount, Class<?> javaClass) {
        this.typeChar = typeChar;
        this.javaName = javaName;
        this.byteCount = byteCount;
        this.javaClass = javaClass;
    }

    public char getTypeChar() {
        return typeChar;
    }

    public String getJavaName() {
        return javaName;
    }

    /** Size in bytes; -1 for {@link #Object}. */
    public int getByteCount() {
        return byteCount;
    }

    public int getBitCount() {
        return byteCount * 8;
    }

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
        if (type.isPrimitive()) {
            for (JavaKind kind : values()) {
                if (kind.javaClass == type) {
                    return kind;
                }
            }
        }
        return Object;
    }
}
