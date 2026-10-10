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

import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.domain.DomainTree;
import uk.ac.manchester.tornado.runtime.domain.IntDomain;
import uk.ac.manchester.tornado.tc.frontend.Frontend;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps;
import uk.ac.manchester.tornado.tc.runtime.TcCompiler;
import uk.ac.manchester.tornado.tc.runtime.TcOptions;
import uk.ac.manchester.tornado.tc.tornado.ObjectLayout;
import uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation;

/**
 * The runtime side of TC: compiles a task method with the Graal-free compiler instead of Graal when
 * {@code -Dtornado.compiler=tc}. Specialises on the actual task arguments (array lengths, scalars),
 * returns the source, entry point and launch domain, and records statistics. When TC cannot compile
 * a kernel the caller falls back to Graal unless {@code -Dtornado.compiler.fallback=false}.
 *
 * <p>Options: {@code tornado.compiler=graal|tc}, {@code tornado.compiler.fallback=true|false},
 * {@code tornado.tc.verbose=true} (one line per kernel), {@code tornado.tc.stats=true} (summary at
 * exit), and the {@code tornado.tc.*} dump options of {@link TcOptions}.</p>
 */
public final class TcHook {

    public static final boolean ENABLED = "tc".equals(System.getProperty("tornado.compiler", "graal"));
    public static final boolean FALLBACK = Boolean.parseBoolean(System.getProperty("tornado.compiler.fallback", "true"));
    private static final boolean VERBOSE = Boolean.getBoolean("tornado.tc.verbose");
    private static final boolean STATS = Boolean.getBoolean("tornado.tc.stats");

    private static final AtomicInteger COMPILED = new AtomicInteger();
    private static final Map<String, AtomicInteger> FALLBACKS = new ConcurrentHashMap<>();

    static {
        if (ENABLED && STATS) {
            Runtime.getRuntime().addShutdownHook(new Thread(TcHook::printStats));
        }
    }

    /** A kernel compiled by TC. */
    public record Kernel(byte[] source, String entryName, Optional<DomainTree> domain, long nanos) {
    }

    private TcHook() {
    }

    /**
     * Compiles {@code method} for the actual {@code arguments}. Returns empty when TC cannot compile
     * it; throws when fallback is disabled.
     */
    public static Optional<Kernel> compile(Method method, Object[] arguments, TargetCaps target) {
        return compile(method, arguments, target, 0);
    }

    /** As {@link #compile(Method, Object[], TargetCaps)}; {@code batchThreads > 0} when the task runs in batches. */
    public static Optional<Kernel> compile(Method method, Object[] arguments, TargetCaps target, long batchThreads) {
        var name = method.getDeclaringClass().getSimpleName() + "." + method.getName();
        // In a batch the device buffers hold one chunk: lengths and indices are per chunk, which TC
        // does not model yet (folding the full length would index past the chunk).
        if (batchThreads > 0) {
            return refuse(name, "TC-E3202", "batched execution (withBatch) is not compiled by TC yet");
        }
        // @Reduce parameters need the reduction rewrite (T-list item): until TC has it, they stay on Graal.
        for (var annotations : method.getParameterAnnotations()) {
            for (var annotation : annotations) {
                if (annotation instanceof Reduce) {
                    return refuse(name, "TC-E3201", "@Reduce reductions are not compiled by TC yet");
                }
            }
        }
        var facts = new ArrayList<ValueSpecialisation.Fact>();
        var declared = method.getParameterTypes();
        for (int i = 0; i < arguments.length; i++) {
            var argument = arguments[i];
            if (argument instanceof TornadoNativeArray array) {
                facts.add(new ValueSpecialisation.Fact.Length(array.getSizeLong()));
            } else if (isInstance(argument) && i < declared.length) {
                facts.add(instanceFact(argument, declared[i]));
            } else {
                facts.add(ValueSpecialisation.Fact.of(argument));
            }
        }
        var owner = method.getDeclaringClass();
        var loader = owner.getClassLoader() != null ? owner.getClassLoader() : ClassLoader.getSystemClassLoader();
        var descriptor = MethodType.methodType(method.getReturnType(), method.getParameterTypes()).toMethodDescriptorString();
        var c = TcCompiler.compileJava(Frontend.BytecodeSource.classLoader(loader), owner.getName(), method.getName(), descriptor, target, TcOptions.fromSystemProperties(), facts);
        if (!c.succeeded()) {
            var d = c.diagnostics().isEmpty() ? null : c.diagnostics().get(0);
            var reason = d == null ? "unknown" : d.code() + " " + d.message();
            FALLBACKS.computeIfAbsent(d == null ? "unknown" : d.code(), k -> new AtomicInteger()).incrementAndGet();
            if (!FALLBACK) {
                throw new IllegalStateException("[TC] cannot compile " + name + " and fallback is disabled: " + (d == null ? reason : d.render()));
            }
            if (VERBOSE) {
                System.out.println("[TC] fallback to Graal for " + name + ": " + reason);
            }
            return Optional.empty();
        }
        COMPILED.incrementAndGet();
        if (VERBOSE) {
            System.out.printf("[TC] compiled %s in %.2f ms%n", name, c.nanos() / 1e6);
        }
        Optional<DomainTree> domain = Optional.empty();
        if (!c.domain().isEmpty()) {
            var tree = new DomainTree(c.domain().size());
            for (var d : c.domain()) {
                tree.set(d.dim(), new IntDomain((int) d.offset(), (int) d.step(), (int) d.upper()));
            }
            domain = Optional.of(tree);
        }
        return Optional.of(new Kernel(c.source().getBytes(StandardCharsets.UTF_8), c.entryName(), domain, c.nanos()));
    }

    private static Optional<Kernel> refuse(String name, String code, String reason) {
        FALLBACKS.computeIfAbsent(code, k -> new AtomicInteger()).incrementAndGet();
        if (!FALLBACK) {
            throw new IllegalStateException("[TC] cannot compile " + name + " and fallback is disabled: error " + code + ": " + reason);
        }
        if (VERBOSE) {
            System.out.println("[TC] fallback to Graal for " + name + ": " + code + " " + reason);
        }
        return Optional.empty();
    }

    /** An object passed by field copy (not an array, box, KernelContext or native array). */
    private static boolean isInstance(Object argument) {
        return argument != null && !argument.getClass().isArray() && !(argument instanceof Number) && !(argument instanceof Character) && !(argument instanceof Boolean)
                && !(argument instanceof KernelContext);
    }

    /**
     * The fact for an object argument: its final primitive field values and the device layouts of it
     * and the objects it references. Offsets come from the same JVMCI lookup the runtime's field
     * buffers serialise with, so they match by construction. Calls on the object are resolved
     * against its declared class, so only an argument of exactly that class is described.
     */
    private static ValueSpecialisation.Fact instanceFact(Object argument, Class<?> declared) {
        if (argument.getClass() != declared || !TornadoOptions.coopsUsed()) {
            return new ValueSpecialisation.Fact.Unknown();
        }
        var layouts = new ArrayList<ObjectLayout>();
        var finals = new java.util.HashMap<String, Number>();
        try {
            describe(argument, !isChildOfNativeArray(argument.getClass()), layouts, new java.util.HashSet<>());
            for (var c = argument.getClass(); c != null; c = c.getSuperclass()) {
                for (var field : c.getDeclaredFields()) {
                    if (Modifier.isFinal(field.getModifiers()) && !Modifier.isStatic(field.getModifiers()) && field.getType().isPrimitive() && field.getType() != boolean.class
                            && field.getType() != char.class) {
                        field.setAccessible(true);
                        finals.putIfAbsent(field.getName(), (Number) field.get(argument));
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return new ValueSpecialisation.Fact.Unknown();
        }
        return new ValueSpecialisation.Fact.Instance(finals, layouts);
    }

    private static void describe(Object object, boolean includeSuper, List<ObjectLayout> out, java.util.Set<Class<?>> seen) throws ReflectiveOperationException {
        var cls = object.getClass();
        if (!seen.add(cls)) {
            return;
        }
        var type = TornadoCoreRuntime.getTornadoRuntime().getMetaAccess().lookupJavaType(cls);
        var offsets = new java.util.HashMap<String, Integer>();
        var references = new java.util.HashMap<String, String>();
        for (var field : type.getInstanceFields(includeSuper)) {
            offsets.putIfAbsent(field.getName(), field.getOffset());
            var reflected = declaredField(cls, field.getName());
            var fieldType = reflected.getType();
            if (!fieldType.isPrimitive() && !fieldType.isArray() && !TornadoNativeArray.class.isAssignableFrom(fieldType)) {
                reflected.setAccessible(true);
                var value = reflected.get(object);
                if (value != null) {
                    references.put(field.getName(), value.getClass().getSimpleName());
                    describe(value, includeSuper, out, seen);
                }
            }
        }
        out.add(new ObjectLayout(cls.getSimpleName(), Map.copyOf(offsets), Map.copyOf(references)));
    }

    private static Field declaredField(Class<?> cls, String name) throws NoSuchFieldException {
        for (var c = cls; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                // declared higher up
            }
        }
        throw new NoSuchFieldException(cls.getName() + "." + name);
    }

    /** Native arrays are serialised without superclass fields (as the runtime's field buffers do). */
    private static boolean isChildOfNativeArray(Class<?> cls) {
        return TornadoNativeArray.class.isAssignableFrom(cls);
    }

    private static void printStats() {
        var reasons = new TreeMap<String, Integer>();
        FALLBACKS.forEach((k, v) -> reasons.put(k, v.get()));
        System.out.println("[TC] kernels compiled by TC: " + COMPILED.get() + ", fell back to Graal: " + reasons.values().stream().mapToInt(Integer::intValue).sum() + " " + reasons);
    }
}
