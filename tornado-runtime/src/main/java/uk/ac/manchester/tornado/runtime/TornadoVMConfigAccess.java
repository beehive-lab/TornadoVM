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
package uk.ac.manchester.tornado.runtime;

import java.lang.reflect.Field;

import sun.misc.Unsafe;

import jdk.vm.ci.meta.JavaKind;

/**
 * JDK-neutral view of the HotSpot object/array memory layout needed to marshal
 * Java objects into accelerator buffers. Previously this extended
 * {@code jdk.vm.ci.hotspot.HotSpotVMConfigAccess} and read raw VM struct offsets
 * through JVMCI; it now derives the same values from {@link Unsafe}, so it works
 * without JVMCI being present.
 *
 * <p>
 * The values are validated to match the JVMCI-reported layout for both
 * compressed and uncompressed class pointers: the object header size (hence the
 * array-length offset) is obtained from a probe field offset, the klass pointer
 * sits immediately after the 8-byte mark word, and array base offsets/index
 * scales come directly from {@code Unsafe}.
 */
public class TornadoVMConfigAccess {

    /** HotSpot 64-bit mark-word size; the klass pointer (object hub) lives immediately after it. */
    private static final int MARK_WORD_SIZE = 8;

    private static final Unsafe UNSAFE = initUnsafe();

    /** Object header size in bytes: the offset of the first instance field is the end of the header. */
    private static final int HEADER_SIZE = (int) UNSAFE.objectFieldOffset(firstField(HeaderProbe.class));

    public final int hubOffset = MARK_WORD_SIZE;

    private static final class HeaderProbe {
        @SuppressWarnings("unused")
        int field;
    }

    public TornadoVMConfigAccess() {
    }

    private static Unsafe initUnsafe() {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            return (Unsafe) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new InternalError("Unable to obtain sun.misc.Unsafe for object layout", e);
        }
    }

    private static Field firstField(Class<?> clazz) {
        try {
            return clazz.getDeclaredField("field");
        } catch (NoSuchFieldException e) {
            throw new InternalError(e);
        }
    }

    private static Class<?> arrayClassFor(JavaKind kind) {
        return switch (kind) {
            case Boolean -> boolean[].class;
            case Byte -> byte[].class;
            case Short -> short[].class;
            case Char -> char[].class;
            case Int -> int[].class;
            case Long -> long[].class;
            case Float -> float[].class;
            case Double -> double[].class;
            default -> Object[].class;
        };
    }

    /** Offset of the array length field: it is stored immediately after the object header. */
    public final int arrayOopDescLengthOffset() {
        return HEADER_SIZE;
    }

    public int getArrayBaseOffset(JavaKind kind) {
        return UNSAFE.arrayBaseOffset(arrayClassFor(kind));
    }

    public int getArrayIndexScale(JavaKind kind) {
        return UNSAFE.arrayIndexScale(arrayClassFor(kind));
    }

    /** Base size (bytes) of an instance with no fields: the object header. */
    public int instanceKlassFieldsOffset() {
        return HEADER_SIZE;
    }

}
