/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.runtime.jvmci.reflection;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import sun.misc.Unsafe;

import jdk.vm.ci.meta.JavaKind;

/**
 * Registry + factory for the reflection/ASM/{@code Unsafe}-backed JVMCI
 * metadata objects. It caches one {@link ReflectionResolvedJavaType} per
 * {@link Class} so type identity is stable (Graal stores types in maps and
 * compares them), and provides the shared {@code Unsafe} handle plus small
 * helpers (JVM type descriptors, {@link JavaKind} mapping) used across the
 * reflection SPI implementation.
 *
 * <p>
 * This is the JDK-neutral replacement for {@code HotSpotJVMCIRuntime}'s
 * metadata layer (Phase 4). It is reached only when
 * {@code -Dtornado.jvmci.reflection=true}; the default path stays on the
 * verified delegating + {@code Unsafe} seam.
 */
public final class ReflectionUniverse {

    static final Unsafe UNSAFE = initUnsafe();

    private final Map<Class<?>, ReflectionResolvedJavaType> types = new ConcurrentHashMap<>();

    private static Unsafe initUnsafe() {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            return (Unsafe) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new InternalError("Unable to obtain sun.misc.Unsafe for reflection JVMCI provider", e);
        }
    }

    public ReflectionResolvedJavaType lookupType(Class<?> clazz) {
        if (clazz == null) {
            return null;
        }
        return types.computeIfAbsent(clazz, c -> new ReflectionResolvedJavaType(this, c));
    }

    public ReflectionResolvedJavaMethod lookupMethod(Executable executable) {
        return new ReflectionResolvedJavaMethod(this, executable);
    }

    /** A signature-polymorphic method carrying the call-site descriptor as its signature. */
    ReflectionResolvedJavaMethod lookupPolymorphicMethod(Executable executable, String callSiteDescriptor) {
        return new ReflectionResolvedJavaMethod(this, executable, callSiteDescriptor);
    }

    public ReflectionResolvedJavaField lookupField(Field field) {
        return new ReflectionResolvedJavaField(this, field);
    }

    /** JVM type descriptor, e.g. {@code int -> "I"}, {@code Object -> "Ljava/lang/Object;"}, {@code int[] -> "[I"}. */
    static String descriptor(Class<?> c) {
        if (c.isPrimitive()) {
            return String.valueOf(JavaKind.fromJavaClass(c).getTypeChar());
        }
        if (c.isArray()) {
            return c.getName().replace('.', '/');
        }
        return "L" + c.getName().replace('.', '/') + ";";
    }

    static long fieldOffset(Field f) {
        return Modifier.isStatic(f.getModifiers()) ? UNSAFE.staticFieldOffset(f) : UNSAFE.objectFieldOffset(f);
    }

    /**
     * Resolve a JVM type descriptor (e.g. {@code I}, {@code Ljava/lang/Object;},
     * {@code [I}) back to a {@link Class}. Used to answer queries such as
     * {@code isAssignableFrom} when the other operand is not a reflection type
     * (e.g. a HotSpot type flowing in from a snippet or stamp).
     */
    static Class<?> classForDescriptor(String descriptor, ClassLoader loader) throws ClassNotFoundException {
        ClassLoader cl = loader != null ? loader : ClassLoader.getSystemClassLoader();
        return switch (descriptor.charAt(0)) {
            case 'Z' -> boolean.class;
            case 'B' -> byte.class;
            case 'C' -> char.class;
            case 'S' -> short.class;
            case 'I' -> int.class;
            case 'J' -> long.class;
            case 'F' -> float.class;
            case 'D' -> double.class;
            case 'V' -> void.class;
            case 'L' -> Class.forName(descriptor.substring(1, descriptor.length() - 1).replace('/', '.'), false, cl);
            case '[' -> Class.forName(descriptor.replace('/', '.'), false, cl);
            default -> throw new ClassNotFoundException("Unrecognised type descriptor: " + descriptor);
        };
    }

    static UnsupportedOperationException todo(String method) {
        return new UnsupportedOperationException("REFLECTION-TODO: " + method);
    }
}
