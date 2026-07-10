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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a constant-pool method/field reference (holder + name + JVM
 * descriptor) to the corresponding reflective {@link Executable}/{@link Field},
 * walking the class hierarchy and interfaces as the JVM resolution rules do.
 *
 * <p>
 * Resolution walks the super-chain + all interfaces (O(hierarchy) reflective
 * {@code getDeclared*} calls), so results are memoized process-wide keyed by
 * (holder, name, descriptor). {@link Optional#empty()} caches "not found".
 */
final class ReflectionMembers {

    private record MethodKey(Class<?> holder, String name, String descriptor) {
    }

    private record MemberKey(Class<?> holder, String name) {
    }

    private static final Map<MethodKey, Optional<Executable>> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<MemberKey, Optional<Executable>> POLYMORPHIC_CACHE = new ConcurrentHashMap<>();
    private static final Map<MemberKey, Optional<Field>> FIELD_CACHE = new ConcurrentHashMap<>();

    private ReflectionMembers() {
    }

    static Executable findMethod(Class<?> holder, String name, String descriptor, ClassLoader loader) {
        return METHOD_CACHE.computeIfAbsent(new MethodKey(holder, name, descriptor), k -> Optional.ofNullable(findMethod0(holder, name, descriptor, loader))).orElse(null);
    }

    private static Executable findMethod0(Class<?> holder, String name, String descriptor, ClassLoader loader) {
        Class<?>[] paramTypes = parameterTypes(descriptor, loader);
        if ("<init>".equals(name)) {
            try {
                return holder.getDeclaredConstructor(paramTypes);
            } catch (NoSuchMethodException e) {
                return null;
            }
        }
        for (Class<?> c = holder; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredMethod(name, paramTypes);
            } catch (NoSuchMethodException ignored) {
                // continue up the hierarchy
            }
        }
        for (Class<?> iface : allInterfaces(holder)) {
            try {
                return iface.getDeclaredMethod(name, paramTypes);
            } catch (NoSuchMethodException ignored) {
                // continue
            }
        }
        return null;
    }

    /**
     * Find a signature-polymorphic method ({@code VarHandle}/{@code MethodHandle}
     * intrinsics like {@code get}/{@code set}/{@code invoke}) by name: these are
     * declared as {@code native <ret> name(Object... args)} and matched by name
     * only, since the call site's real signature comes from the constant pool.
     */
    static Executable findPolymorphic(Class<?> holder, String name) {
        return POLYMORPHIC_CACHE.computeIfAbsent(new MemberKey(holder, name), k -> Optional.ofNullable(findPolymorphic0(holder, name))).orElse(null);
    }

    private static Executable findPolymorphic0(Class<?> holder, String name) {
        for (Class<?> c = holder; c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name, Object[].class);
                if (Modifier.isNative(m.getModifiers())) {
                    return m;
                }
            } catch (NoSuchMethodException ignored) {
                // continue
            }
        }
        return null;
    }

    static Field findField(Class<?> holder, String name) {
        return FIELD_CACHE.computeIfAbsent(new MemberKey(holder, name), k -> Optional.ofNullable(findField0(holder, name))).orElse(null);
    }

    private static Field findField0(Class<?> holder, String name) {
        for (Class<?> c = holder; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // continue up the hierarchy
            }
        }
        for (Class<?> iface : allInterfaces(holder)) {
            try {
                return iface.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // continue
            }
        }
        return null;
    }

    private static List<Class<?>> allInterfaces(Class<?> clazz) {
        List<Class<?>> result = new ArrayList<>();
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            collectInterfaces(c, result);
        }
        return result;
    }

    private static void collectInterfaces(Class<?> clazz, List<Class<?>> result) {
        for (Class<?> iface : clazz.getInterfaces()) {
            if (!result.contains(iface)) {
                result.add(iface);
                collectInterfaces(iface, result);
            }
        }
    }

    /** Parse the parameter portion of a method descriptor {@code (params)ret} into classes. */
    private static Class<?>[] parameterTypes(String descriptor, ClassLoader loader) {
        List<Class<?>> params = new ArrayList<>();
        int i = 1; // skip '('
        while (descriptor.charAt(i) != ')') {
            int start = i;
            while (descriptor.charAt(i) == '[') {
                i++;
            }
            if (descriptor.charAt(i) == 'L') {
                while (descriptor.charAt(i) != ';') {
                    i++;
                }
                i++;
            } else {
                i++;
            }
            try {
                params.add(ReflectionUniverse.classForDescriptor(descriptor.substring(start, i), loader));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Unable to resolve parameter type in " + descriptor, e);
            }
        }
        return params.toArray(new Class<?>[0]);
    }
}
