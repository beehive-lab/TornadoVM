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

import java.io.IOException;
import java.io.InputStream;
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
 * metadata layer: type/method/field metadata is resolved via core reflection and
 * a classfile parser, so TornadoVM no longer depends on JVMCI on any JDK.
 */
public final class ReflectionUniverse {

    static final Unsafe UNSAFE = initUnsafe();

    private final Map<Class<?>, ReflectionResolvedJavaType> types = new ConcurrentHashMap<>();
    // Canonicalise methods/fields so a given Executable/Field always maps to the SAME
    // ResolvedJavaMethod/Field instance. Graal's InvocationPlugins keys resolved plugins by
    // ResolvedJavaMethod; without canonicalisation a fresh wrapper per lookup can miss the
    // plugin (segment-access intrinsics never fire), driving the sketcher into abstract
    // JDK-internal Panama methods that have no bytecode.
    private final Map<Executable, ReflectionResolvedJavaMethod> methods = new ConcurrentHashMap<>();
    // Signature-polymorphic methods carry a call-site descriptor, so they canonicalise on (Executable, descriptor):
    // the same descriptor must map to the SAME instance (same InvocationPlugin-matching guarantee as `methods`).
    private final Map<PolymorphicKey, ReflectionResolvedJavaMethod> polymorphicMethods = new ConcurrentHashMap<>();
    private final Map<Field, ReflectionResolvedJavaField> fields = new ConcurrentHashMap<>();

    private record PolymorphicKey(Executable executable, String callSiteDescriptor) {
    }
    // One classfile read per declaring class, shared by every method of that class and by both the Code-attribute
    // parse and the constant-pool parse. Without this a class with N methods reads+parses its classfile ~2N times.
    private final Map<Class<?>, byte[]> classfileBytes = new ConcurrentHashMap<>();

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
        return methods.computeIfAbsent(executable, e -> new ReflectionResolvedJavaMethod(this, e));
    }

    /** A signature-polymorphic method carrying the call-site descriptor as its signature. */
    ReflectionResolvedJavaMethod lookupPolymorphicMethod(Executable executable, String callSiteDescriptor) {
        return polymorphicMethods.computeIfAbsent(new PolymorphicKey(executable, callSiteDescriptor), k -> new ReflectionResolvedJavaMethod(this, executable, callSiteDescriptor));
    }

    public ReflectionResolvedJavaField lookupField(Field field) {
        return fields.computeIfAbsent(field, f -> new ReflectionResolvedJavaField(this, f));
    }

    /** Raw bytes of {@code declaring}'s classfile, read once and cached (shared across all its methods). */
    byte[] classfileBytes(Class<?> declaring) {
        return classfileBytes.computeIfAbsent(declaring, ReflectionUniverse::readClassfileBytes);
    }

    private static byte[] readClassfileBytes(Class<?> declaring) {
        String internalName = declaring.getName().replace('.', '/');
        ClassLoader loader = declaring.getClassLoader();
        if (loader == null) {
            loader = ClassLoader.getSystemClassLoader();
        }
        try (InputStream in = loader.getResourceAsStream(internalName + ".class")) {
            if (in == null) {
                throw new IllegalStateException("classfile not found for " + declaring.getName());
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("failed reading classfile for " + declaring.getName(), e);
        }
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
