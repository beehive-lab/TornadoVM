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

import java.io.IOException;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.domain.DomainTree;
import uk.ac.manchester.tornado.runtime.domain.IntDomain;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.tc.debug.HtmlReport;
import uk.ac.manchester.tornado.tc.frontend.Frontend;
import uk.ac.manchester.tornado.tc.ir.Diagnostic;
import uk.ac.manchester.tornado.tc.ir.ErrorCodes;
import uk.ac.manchester.tornado.tc.ir.Loc;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps;
import uk.ac.manchester.tornado.tc.runtime.TcCompiler;
import uk.ac.manchester.tornado.tc.runtime.TcOptions;
import uk.ac.manchester.tornado.tc.tornado.AtomicSlots;
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
    public record Kernel(byte[] source, String entryName, Optional<DomainTree> domain, long nanos, AtomicSlots.Info atomics) {
    }

    private TcHook() {
    }

    /**
     * Compiles {@code method} for the actual {@code arguments}. Returns empty when TC cannot compile
     * it; throws when fallback is disabled.
     */
    public static Optional<Kernel> compile(Method method, Object[] arguments, TargetCaps target) {
        return compile(method, arguments, target, 0, 0, 0);
    }

    /**
     * As {@link #compile(Method, Object[], TargetCaps)}; {@code batchThreads > 0} when the task runs in
     * batches; {@code numThreads > 0} when the runtime fixes the number of threads (a reduction over a
     * size that is not a power of two runs on the largest power-of-two prefix; the host folds the rest).
     */
    public static Optional<Kernel> compile(Method method, Object[] arguments, TargetCaps target, long batchThreads, long numThreads, long batchIndexOffset) {
        return compile(method, arguments, target, batchThreads, numThreads, batchIndexOffset, "?");
    }

    /** As above, for task {@code taskId} (named in device-launch reports). */
    public static Optional<Kernel> compile(Method method, Object[] arguments, TargetCaps target, long batchThreads, long numThreads, long batchIndexOffset, String taskId) {
        var name = method.getDeclaringClass().getSimpleName() + "." + method.getName();
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
            } else if (argument instanceof TornadoNativeArray array) {
                // In a batch each device buffer holds one chunk of batchThreads elements (with its
                // header): lengths and indices are per chunk, as on the Graal path.
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
        var owner = method.getDeclaringClass();
        var loader = owner.getClassLoader() != null ? owner.getClassLoader() : ClassLoader.getSystemClassLoader();
        var descriptor = MethodType.methodType(method.getReturnType(), method.getParameterTypes()).toMethodDescriptorString();
        TcCompiler.Compilation c;
        var options = TcOptions.fromSystemProperties().withTask(taskId);
        try {
            c = TcCompiler.compileJava(Frontend.BytecodeSource.classLoader(loader), owner.getName(), method.getName(), descriptor, target, options, facts);
        } catch (RuntimeException | StackOverflowError e) {
            // An internal TC failure must never take the application down: the kernel goes to Graal.
            return refuse(name, "TC-E9003", "internal compiler error: " + e);
        }
        if (options.htmlReport() && options.selects(name)) {
            writeReport(options.dumpDir().resolve(name.replace('.', '_') + ".report.html"), c);
        }
        if (!c.succeeded()) {
            var d = c.diagnostics().isEmpty() ? new Diagnostic("TC-E9003", "compilation failed without a diagnostic", "", Loc.UNKNOWN) : c.diagnostics().get(0);
            return reject(name, d);
        }
        COMPILED.incrementAndGet();
        if (VERBOSE) {
            System.out.printf("[TC] compiled %s in %.2f ms%n", name, c.nanos() / 1e6);
        }
        Optional<DomainTree> domain = Optional.empty();
        if (!c.domain().isEmpty()) {
            var tree = new DomainTree(c.domain().size());
            boolean reduction = facts.stream().anyMatch(f -> f instanceof ValueSpecialisation.Fact.Reduce);
            for (var d : c.domain()) {
                long upper = reduction && d.dim() == 0 && numThreads > 0 ? d.offset() + numThreads : d.upper();
                tree.set(d.dim(), new IntDomain((int) d.offset(), (int) d.step(), (int) upper));
            }
            domain = Optional.of(tree);
        }
        return Optional.of(new Kernel(c.source().getBytes(StandardCharsets.UTF_8), c.entryName(), domain, c.nanos(), c.atomics()));
    }

    /** -Dtornado.tc.report=html: every stage of one compilation, refused ones included, in one page. */
    private static void writeReport(Path file, TcCompiler.Compilation c) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, HtmlReport.render(c));
        } catch (IOException e) {
            System.err.println("[TC] cannot write " + file + ": " + e.getMessage());
        }
    }

    private static Optional<Kernel> refuse(String name, String code, String reason) {
        return reject(name, new Diagnostic(code, reason, "", Loc.UNKNOWN));
    }

    /**
     * TC cannot compile {@code name}. With fallback on, the Graal path compiles the kernel (an internal
     * TC error is still reported, so it does not go unnoticed). With fallback off, the diagnostic
     * becomes the TornadoVM exception for its category (ErrorCodes), as the Graal path would throw it.
     */
    private static Optional<Kernel> reject(String name, Diagnostic d) {
        FALLBACKS.computeIfAbsent(d.code(), k -> new AtomicInteger()).incrementAndGet();
        var category = ErrorCodes.category(d.code());
        if (FALLBACK) {
            if (category == ErrorCodes.Category.INTERNAL) {
                System.err.println("[TC] internal error compiling " + name + ", the Graal path compiles it instead (please report):\n" + message(name, d));
            } else if (VERBOSE) {
                System.out.println("[TC] fallback to Graal for " + name + ": " + d.code() + " " + d.message());
            }
            return Optional.empty();
        }
        var e = exception(name, d, category);
        if (e instanceof Error error) {
            throw error;
        }
        throw (RuntimeException) e;
    }

    /** The TornadoVM exception for a diagnostic, matching what the Graal path throws for the same kind of kernel. */
    static Throwable exception(String name, Diagnostic d, ErrorCodes.Category category) {
        var text = message(name, d);
        return switch (category) {
            // TornadoInternalError formats its message: pass the text as an argument, not as the format.
            case INTERNAL -> new TornadoInternalError("%s", "TC internal error (please report with the kernel): " + text);
            // Kernels the GPU cannot run as written: the Graal path raises an Error for these code shapes.
            case INVALID_KERNEL -> switch (d.code()) {
                case "TC-E1002", "TC-E1004", "TC-E2108" -> new TornadoInternalError("%s", text);
                default -> new TornadoBailoutRuntimeException(text);
            };
            case UNSUPPORTED, BAILOUT -> new TornadoBailoutRuntimeException(text);
        };
    }

    /** {@code [TC] cannot compile Class.method: TC-E1004 message / at File.java:49 (bci 7) / hint: ...}. */
    static String message(String name, Diagnostic d) {
        var sb = new StringBuilder("[TC] cannot compile ").append(name).append(": ").append(d.code()).append(' ').append(d.message());
        if (d.loc().isKnown()) {
            sb.append("\n    at ").append(d.loc().file()).append(':').append(d.loc().line());
            if (d.loc().bci() >= 0) {
                sb.append(" (bci ").append(d.loc().bci()).append(')');
            }
        }
        if (!d.where().isBlank()) {
            sb.append("\n    in ").append(d.where());
        }
        var hint = ErrorCodes.hint(d.code());
        if (!hint.isBlank()) {
            sb.append("\n    hint: ").append(hint);
        }
        return sb.toString();
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
        // A subclass of the declared type is fine for its fields (inherited ones keep their offsets);
        // calls on the object are resolved against the declared class, so it must override nothing.
        if (!declared.isAssignableFrom(argument.getClass()) || overridesAnything(argument.getClass(), declared) || !TornadoOptions.coopsUsed()) {
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
                        field.setAccessible(true);
                        if (field.get(argument) instanceof TornadoNativeArray array) {
                            lengths.putIfAbsent(field.getName(), array.getSizeLong());
                        }
                    }
                }
            }
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
        return new ValueSpecialisation.Fact.Instance(finals, layouts, lengths);
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

    /** The class name as TC's frontend writes it in obj<...>: the binary name after the package (Outer$Inner). */
    private static String tcClassName(Class<?> cls) {
        var name = cls.getName();
        return name.substring(name.lastIndexOf('.') + 1);
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
