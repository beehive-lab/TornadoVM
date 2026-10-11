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
package uk.ac.manchester.tornado.drivers.common.tc;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.runtime.common.FieldLayout;
import uk.ac.manchester.tornado.tc.tornado.ObjectLayout;
import uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation;

/**
 * What TC is told about a task's actual arguments: one {@link ValueSpecialisation.Fact} per parameter
 * (array lengths, scalar values, reduction arrays, and for an object its final fields and the device
 * layouts of it and the objects it references, read from the host objects by reflection).
 */
final class TcArguments {

    private TcArguments() {
    }

    /**
     * The facts for {@code arguments} of {@code method}; {@code batchThreads > 0} when the task runs in
     * batches (lengths are per chunk), {@code batchIndexOffset} the first index of this batch.
     */
    static List<ValueSpecialisation.Fact> facts(Method method, Object[] arguments, long batchThreads, long batchIndexOffset) {
        var facts = new ArrayList<ValueSpecialisation.Fact>();
        // An instance-method task: the runtime passes the receiver as the first argument.
        var parameterTypes = method.getParameterTypes();
        var declared = Modifier.isStatic(method.getModifiers()) ? parameterTypes : prepend(method.getDeclaringClass(), parameterTypes);
        var annotations = Modifier.isStatic(method.getModifiers()) ? method.getParameterAnnotations() : prepend(new java.lang.annotation.Annotation[0], method.getParameterAnnotations());
        for (int i = 0; i < arguments.length; i++) {
            var argument = arguments[i];
            boolean reduced = i < annotations.length && java.util.Arrays.stream(annotations[i]).anyMatch(Reduce.class::isInstance);
            if (reduced && argument instanceof TornadoNativeArray array) {
                // The runtime has already swapped in the per-work-group partial-results array.
                facts.add(new ValueSpecialisation.Fact.Reduce(array.getSizeLong()));
            } else if (reduced && argument != null && argument.getClass().isArray() && argument.getClass().getComponentType().isPrimitive()) {
                // The same for a Java array (float[], int[], ...).
                facts.add(new ValueSpecialisation.Fact.Reduce(java.lang.reflect.Array.getLength(argument)));
            } else if (argument instanceof TornadoNativeArray array) {
                // In a batch each device buffer holds one chunk of batchThreads elements (with its
                // header): lengths and indices are per chunk.
                facts.add(new ValueSpecialisation.Fact.Length(batchThreads > 0 ? batchThreads : array.getSizeLong()));
            } else if (batchThreads > 0 && argument != null && argument.getClass().isArray()) {
                facts.add(new ValueSpecialisation.Fact.Length(batchThreads)); // a Java array chunk, as for native arrays
            } else if (isInstance(argument) && i < declared.length) {
                facts.add(instanceFact(argument, declared[i]));
            } else {
                facts.add(ValueSpecialisation.Fact.of(argument));
            }
        }
        if (batchIndexOffset > 0) {
            facts.add(new ValueSpecialisation.Fact.IndexOffset(batchIndexOffset));
        }
        return facts;
    }

    /** An object passed by field copy (not an array, box, KernelContext or native array). */
    private static boolean isInstance(Object argument) {
        return argument != null && !argument.getClass().isArray() && !(argument instanceof Number) && !(argument instanceof Character) && !(argument instanceof Boolean)
                && !(argument instanceof KernelContext);
    }

    /**
     * The fact for an object argument: its final primitive field values and the device layouts of it
     * and the objects it references. Offsets come from {@link FieldLayout}, which the runtime's field
     * buffers serialise with, so they match by construction. Calls on the object are resolved
     * against its declared class, so only an argument of exactly that class is described.
     */
    private static ValueSpecialisation.Fact instanceFact(Object argument, Class<?> declared) {
        // A subclass of the declared type is fine for its fields (inherited ones keep their offsets);
        // calls on the object are resolved against the declared class, so it must override nothing.
        if (!declared.isAssignableFrom(argument.getClass()) || overridesAnything(argument.getClass(), declared)) {
            return new ValueSpecialisation.Fact.Unknown();
        }
        var lengths = new java.util.HashMap<String, Long>();
        var layouts = new ArrayList<ObjectLayout>();
        var finals = new java.util.HashMap<String, Number>();
        try {
            describe(argument, !isChildOfNativeArray(argument.getClass()), layouts, new java.util.HashSet<>());
            if (argument.getClass() != declared) {
                // Registered under the declared class too: that is the name in the kernel's obj<...>.
                var own = layouts.get(layouts.size() - 1);
                layouts.add(new ObjectLayout(tcClassName(declared), own.offsets(), own.referenceClasses()));
            }
            for (var c = argument.getClass(); c != null; c = c.getSuperclass()) {
                for (var field : c.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers()) && TornadoNativeArray.class.isAssignableFrom(field.getType())) {
                        if (FieldLayout.get(field, argument) instanceof TornadoNativeArray array) {
                            lengths.putIfAbsent(field.getName(), array.getSizeLong());
                        }
                    }
                }
            }
            for (var c = argument.getClass(); c != null; c = c.getSuperclass()) {
                for (var field : c.getDeclaredFields()) {
                    if (Modifier.isFinal(field.getModifiers()) && !Modifier.isStatic(field.getModifiers()) && field.getType().isPrimitive() && field.getType() != boolean.class
                            && field.getType() != char.class) {
                        finals.putIfAbsent(field.getName(), (Number) FieldLayout.get(field, argument));
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return new ValueSpecialisation.Fact.Unknown();
        }
        return new ValueSpecialisation.Fact.Instance(finals, layouts, lengths);
    }

    private static void describe(Object object, boolean includeSuper, List<ObjectLayout> out, java.util.Set<Class<?>> seen) throws ReflectiveOperationException {
        var cls = object.getClass();
        if (!seen.add(cls)) {
            return;
        }
        var layout = FieldLayout.of(cls);
        var offsets = new java.util.HashMap<String, Integer>();
        var references = new java.util.HashMap<String, String>();
        for (var field : layout.fields(includeSuper)) {
            offsets.putIfAbsent(field.getName(), layout.offset(field));
            var fieldType = field.getType();
            if (!fieldType.isPrimitive() && !fieldType.isArray() && !TornadoNativeArray.class.isAssignableFrom(fieldType)) {
                var value = FieldLayout.get(field, object);
                if (value != null) {
                    references.put(field.getName(), tcClassName(value.getClass()));
                    describe(value, includeSuper, out, seen);
                }
            }
        }
        out.add(new ObjectLayout(tcClassName(cls), Map.copyOf(offsets), Map.copyOf(references)));
    }

    /** Whether {@code actual} (or a class between it and {@code declared}) overrides a method of {@code declared}. */
    private static boolean overridesAnything(Class<?> actual, Class<?> declared) {
        for (var c = actual; c != null && c != declared; c = c.getSuperclass()) {
            for (var m : c.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers()) || Modifier.isPrivate(m.getModifiers())) {
                    continue;
                }
                try {
                    declared.getMethod(m.getName(), m.getParameterTypes());
                    return true;
                } catch (NoSuchMethodException e) {
                    // not an override of a public method; check non-public declared methods too
                    for (var d = declared; d != null; d = d.getSuperclass()) {
                        try {
                            var dm = d.getDeclaredMethod(m.getName(), m.getParameterTypes());
                            if (!Modifier.isPrivate(dm.getModifiers())) {
                                return true;
                            }
                        } catch (NoSuchMethodException ignored) {
                            // keep looking up the hierarchy
                        }
                    }
                }
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static <T> T[] prepend(T first, T[] rest) {
        var all = (T[]) java.lang.reflect.Array.newInstance(rest.getClass().getComponentType(), rest.length + 1);
        all[0] = first;
        System.arraycopy(rest, 0, all, 1, rest.length);
        return all;
    }

    /** The class as TC names object types: its fully qualified binary name ({@code pkg.Outer$Inner}). */
    private static String tcClassName(Class<?> cls) {
        return cls.getName();
    }

    /** Native arrays are serialised without superclass fields (as the runtime's field buffers do). */
    private static boolean isChildOfNativeArray(Class<?> cls) {
        return TornadoNativeArray.class.isAssignableFrom(cls);
    }
}
