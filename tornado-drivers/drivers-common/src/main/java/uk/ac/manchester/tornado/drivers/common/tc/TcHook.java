/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.common.tc;

import java.io.IOException;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceFP64NotSupported;
import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceMMANotSupported;
import uk.ac.manchester.tornado.runtime.domain.DomainTree;
import uk.ac.manchester.tornado.runtime.domain.IntDomain;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.tc.debug.HtmlReport;
import uk.ac.manchester.tornado.tc.frontend.Frontend;
import uk.ac.manchester.tornado.tc.ir.Diagnostic;
import uk.ac.manchester.tornado.tc.ir.ErrorCodes;
import uk.ac.manchester.tornado.tc.ir.Loc;
import uk.ac.manchester.tornado.tc.ir.analysis.KernelFeatures;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps;
import uk.ac.manchester.tornado.tc.runtime.TcCompiler;
import uk.ac.manchester.tornado.tc.runtime.TcOptions;
import uk.ac.manchester.tornado.tc.tornado.AtomicSlots;
import uk.ac.manchester.tornado.tc.tornado.ParallelLoopLowering;
import uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation;

/**
 * The runtime side of TC, TornadoVM's kernel compiler: compiles a task method, specialised on the
 * actual task arguments (array lengths, scalars), into the source, entry point and launch domain,
 * and records statistics. A kernel TC cannot compile raises the TornadoVM exception for the
 * diagnostic's category.
 *
 * <p>Options: {@code tornado.tc.verbose=true} (one line per kernel), {@code tornado.tc.stats=true}
 * (summary at exit), {@code tornado.tc.memoize=false} (compile every task, even when an earlier one
 * had the same method and facts), {@code tornado.tc.varyingScalars=N} (see {@link #VARYING_SCALARS})
 * and the {@code tornado.tc.*} dump options of {@link TcOptions}.</p>
 */
public final class TcHook {

    private static final boolean VERBOSE = Boolean.getBoolean("tornado.tc.verbose");
    private static final boolean STATS = Boolean.getBoolean("tornado.tc.stats");

    private static final boolean MEMOIZE = Boolean.parseBoolean(System.getProperty("tornado.tc.memoize", "true"));

    /**
     * A scalar argument that has taken this many different values across the tasks of one kernel
     * (same method, target, options and other facts) is compiled as a runtime value from then on; 0
     * specialises every value. A model's layers pass their layer index as a scalar: specialised on
     * it, each layer's attention was a kernel of its own. Values a kernel needs as constants (a
     * local array's size) keep it specialised: the generic compilation is refused and not retried.
     */
    private static final int VARYING_SCALARS = Integer.getInteger("tornado.tc.varyingScalars", 3);

    private static final AtomicInteger COMPILED = new AtomicInteger();
    private static final AtomicInteger REUSED = new AtomicInteger();

    /**
     * Everything a compilation reads: the method (its class and loader), the target, the options and
     * the facts TcArguments derives from the task's arguments (lengths, specialised scalars, object
     * layouts, batches, worker grids). The task id is left out: it reaches the code only through
     * device-launch labels, and an entry that printed one is reused only for the same task.
     */
    private record Key(Method method, TargetCaps target, TcOptions options, List<ValueSpecialisation.Fact> facts) {
    }

    /** A kernel's tasks with their scalar values left out. */
    private record Shape(Method method, TargetCaps target, TcOptions options, List<Object> facts) {
        private static final Object SCALAR = new Object();

        static Shape of(Key key) {
            return new Shape(key.method(), key.target(), key.options(), key.facts().stream().map(f -> f instanceof ValueSpecialisation.Fact.Scalar ? SCALAR : (Object) f).toList());
        }
    }

    /** The scalar values the tasks of one shape have had, and whether its kernel compiles without them. */
    private static final class Scalars {
        private final Map<Integer, java.util.Set<Number>> values = new java.util.HashMap<>();
        /** The launch domain of the first, specialised, compilation: generic code must launch the same. */
        private List<uk.ac.manchester.tornado.tc.tornado.ParallelLoopLowering.Domain> domain;
        private boolean specialiseAlways;

        /** Records the task's scalar values; returns the positions to compile as runtime values. */
        synchronized java.util.BitSet observe(List<ValueSpecialisation.Fact> facts) {
            var varying = new java.util.BitSet();
            for (int i = 0; i < facts.size(); i++) {
                if (facts.get(i) instanceof ValueSpecialisation.Fact.Scalar(var value)) {
                    var seen = values.computeIfAbsent(i, k -> new java.util.HashSet<>());
                    if (seen.size() < VARYING_SCALARS) {
                        seen.add(value);
                    }
                    if (seen.size() >= VARYING_SCALARS) {
                        varying.set(i);
                    }
                }
            }
            return specialiseAlways ? new java.util.BitSet() : varying;
        }
    }

    private static final Map<Shape, Scalars> SCALARS = new ConcurrentHashMap<>();
    private static final AtomicInteger GENERIC = new AtomicInteger();

    /** What a compilation leaves for later tasks (not its stages). */
    record Compiled(String source, String entryName, long nanos, List<ParallelLoopLowering.Domain> domain, AtomicSlots.Info atomics,
            KernelFeatures features, String task, boolean taskInSource) {
    }

    /** Compilations by key, finished or running; a model's layers share one entry per kernel. */
    private static final Map<Key, CompletableFuture<Compiled>> COMPILATIONS = new ConcurrentHashMap<>();

    private static final int MAX_COMPILATIONS = 4096;
    private static final Map<String, AtomicInteger> REFUSED = new ConcurrentHashMap<>();

    static {
        if (STATS) {
            Runtime.getRuntime().addShutdownHook(new Thread(TcHook::printStats));
        }
    }

    /** A kernel compiled by TC. */
    public record Kernel(byte[] source, String entryName, Optional<DomainTree> domain, long nanos, AtomicSlots.Info atomics, KernelFeatures features) {
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
        return compile(method, arguments, target, batchThreads, numThreads, batchIndexOffset, taskId, false);
    }

    /** As above, for a task of a plan with {@code withStrictFloatingPoint()} when {@code strictFloatingPoint}. */
    public static Optional<Kernel> compile(Method method, Object[] arguments, TargetCaps target, long batchThreads, long numThreads, long batchIndexOffset, String taskId,
            boolean strictFloatingPoint) {
        return compile(method, arguments, target, batchThreads, numThreads, batchIndexOffset, taskId, strictFloatingPoint, false);
    }

    /** As above; {@code workerGrid} when the task runs with a worker grid of the user's (no launch domain needed). */
    public static Optional<Kernel> compile(Method method, Object[] arguments, TargetCaps target, long batchThreads, long numThreads, long batchIndexOffset, String taskId,
            boolean strictFloatingPoint, boolean workerGrid) {
        var name = method.getDeclaringClass().getSimpleName() + "." + method.getName();
        var facts = TcArguments.facts(method, arguments, batchThreads, batchIndexOffset);
        if (workerGrid) {
            facts.add(new ValueSpecialisation.Fact.WorkerGrid());
        }
        var owner = method.getDeclaringClass();
        var loader = owner.getClassLoader() != null ? owner.getClassLoader() : ClassLoader.getSystemClassLoader();
        var descriptor = MethodType.methodType(method.getReturnType(), method.getParameterTypes()).toMethodDescriptorString();
        var options = TcOptions.fromSystemProperties().withTask(taskId).withStrictFloatingPoint(strictFloatingPoint);
        var key = new Key(method, target, options.withTask("?"), List.copyOf(facts));
        var scalars = MEMOIZE && VARYING_SCALARS > 0 ? SCALARS.computeIfAbsent(Shape.of(key), k -> new Scalars()) : null;
        var varying = scalars != null ? scalars.observe(key.facts()) : new java.util.BitSet();
        if (!varying.isEmpty()) {
            var generic = generic(name, owner, loader, descriptor, options, key, varying, scalars, taskId);
            if (generic.isPresent()) {
                return kernel(name, generic.get(), facts, numThreads);
            }
        }
        var result = compileOrReuse(name, owner, loader, descriptor, options, key, taskId);
        if (result.isEmpty()) {
            return Optional.empty();
        }
        if (scalars != null) {
            synchronized (scalars) {
                if (scalars.domain == null) {
                    scalars.domain = result.get().domain();
                }
            }
        }
        return kernel(name, result.get(), facts, numThreads);
    }

    /** The memoised compilation for {@code key}, or a new one. */
    private static Optional<Compiled> compileOrReuse(String name, Class<?> owner, ClassLoader loader, String descriptor, TcOptions options, Key key, String taskId) {
        Compiled compiled = null;
        CompletableFuture<Compiled> mine = null;
        if (MEMOIZE) {
            // An earlier task, or a prefetch on another thread, may have compiled (or be compiling)
            // the same key: wait for it. A failed compilation yields null and is redone here, so the
            // refusal is raised on this thread.
            mine = new CompletableFuture<>();
            var existing = COMPILATIONS.putIfAbsent(key, mine);
            if (existing != null) {
                mine = null;
                compiled = existing.join();
                if (compiled != null && compiled.taskInSource() && !compiled.task().equals(taskId)) {
                    compiled = null; // the source names its task (a device launch): compile for this one
                }
            }
        }
        if (compiled != null) {
            REUSED.incrementAndGet();
            if (VERBOSE) {
                System.out.printf("[TC] reused %s (same method and facts as an earlier task)%n", name);
            }
            return Optional.of(compiled);
        }
        Optional<Compiled> result = Optional.empty();
        try {
            result = compileNew(name, owner, loader, key.method().getName(), descriptor, key.target(), options, key.facts());
            return result;
        } finally {
            if (mine != null) {
                mine.complete(result.orElse(null));
                if (result.isEmpty()) {
                    COMPILATIONS.remove(key, mine);
                } else if (COMPILATIONS.size() > MAX_COMPILATIONS) {
                    // Bounded: drop finished entries (running ones are being waited for).
                    COMPILATIONS.values().removeIf(CompletableFuture::isDone);
                }
            }
        }
    }

    /**
     * The kernel compiled with the {@code varying} scalars as runtime values, shared by the tasks that
     * differ only in them; empty when it does not compile or would launch differently from the
     * specialised kernel, and the kernel is then specialised on every value.
     */
    private static Optional<Compiled> generic(String name, Class<?> owner, ClassLoader loader, String descriptor, TcOptions options, Key key, java.util.BitSet varying,
            Scalars scalars, String taskId) {
        var facts = new java.util.ArrayList<>(key.facts());
        varying.stream().forEach(i -> facts.set(i, new ValueSpecialisation.Fact.Unknown()));
        var genericKey = new Key(key.method(), key.target(), key.options(), List.copyOf(facts));
        boolean known = COMPILATIONS.containsKey(genericKey);
        Optional<Compiled> result;
        try {
            result = compileOrReuse(name, owner, loader, descriptor, options, genericKey, taskId);
        } catch (RuntimeException | Error e) {
            result = Optional.empty();
        }
        synchronized (scalars) {
            if (result.isEmpty() || (scalars.domain != null && !scalars.domain.equals(result.get().domain()))) {
                scalars.specialiseAlways = true;
                COMPILATIONS.remove(genericKey);
                if (VERBOSE) {
                    System.out.printf("[TC] %s stays specialised on its scalar arguments%n", name);
                }
                return Optional.empty();
            }
        }
        if (!known) {
            GENERIC.incrementAndGet();
            if (VERBOSE) {
                System.out.printf("[TC] %s: scalar arguments %s take %d or more values; compiled as runtime values%n", name, varying, VARYING_SCALARS);
            }
        }
        return result;
    }

    private static Optional<Compiled> compileNew(String name, Class<?> owner, ClassLoader loader, String methodName, String descriptor, TargetCaps target, TcOptions options,
            List<ValueSpecialisation.Fact> facts) {
        TcCompiler.Compilation c;
        try {
            c = TcCompiler.compileJava(Frontend.BytecodeSource.classLoader(loader), owner.getName(), methodName, descriptor, target, options, facts);
        } catch (RuntimeException | StackOverflowError e) {
            // An internal TC failure becomes a diagnostic, reported like any other.
            if (VERBOSE) {
                e.printStackTrace();
            }
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
        boolean taskInSource = c.source().contains(uk.ac.manchester.tornado.tc.codegen.CEmitter.DEVICE_LAUNCH_LABEL);
        return Optional.of(new Compiled(c.source(), c.entryName(), c.nanos(), c.domain(), c.atomics(), c.features(), options.task(), taskInSource));
    }

    /** The kernel for one task: the launch domain depends on the task's thread count, not only on the code. */
    private static Optional<Kernel> kernel(String name, Compiled c, List<ValueSpecialisation.Fact> facts, long numThreads) {
        Optional<DomainTree> domain = Optional.empty();
        if (!c.domain().isEmpty()) {
            var tree = new DomainTree(c.domain().size());
            boolean reduction = facts.stream().anyMatch(f -> f instanceof ValueSpecialisation.Fact.Reduce);
            for (var d : c.domain()) {
                // IntDomain's third value is the number of threads. A reduction launches the runtime's count
                // (a power of two; its partial slots are sized for it), whatever the loop's start.
                long upper = reduction && d.dim() == 0 && numThreads > 0 ? numThreads : d.upper();
                // The runtime's launch domains are int: a wider one would wrap.
                if (d.offset() != (int) d.offset() || d.step() != (int) d.step() || upper != (int) upper) {
                    var why = "the launch domain of dimension " + d.dim() + " (" + d.offset() + " to " + upper + ") does not fit the runtime's int domains";
                    return reject(name, new Diagnostic("TC-E3209", why, "", Loc.UNKNOWN));
                }
                tree.set(d.dim(), new IntDomain((int) d.offset(), (int) d.step(), (int) upper));
            }
            domain = Optional.of(tree);
        }
        return Optional.of(new Kernel(c.source().getBytes(StandardCharsets.UTF_8), c.entryName(), domain, c.nanos(), c.atomics(), c.features()));
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

    private static <T> Optional<T> refuse(String name, String code, String reason) {
        return reject(name, new Diagnostic(code, reason, "", Loc.UNKNOWN));
    }

    /** TC cannot compile {@code name}: the diagnostic becomes the TornadoVM exception for its category (ErrorCodes). */
    private static <T> Optional<T> reject(String name, Diagnostic d) {
        REFUSED.computeIfAbsent(d.code(), k -> new AtomicInteger()).incrementAndGet();
        var category = ErrorCodes.category(d.code());
        var e = exception(name, d, category);
        if (e instanceof Error error) {
            throw error;
        }
        throw (RuntimeException) e;
    }

    /** The TornadoVM exception for a diagnostic, the one TornadoVM has always thrown for that kind of kernel. */
    static Throwable exception(String name, Diagnostic d, ErrorCodes.Category category) {
        var text = message(name, d);
        if (d.code().equals("TC-E4012")) {
            // A device without fp64 (Metal; OpenCL without cl_khr_fp64).
            return new TornadoDeviceFP64NotSupported(text);
        }
        if (d.code().equals("TC-E4017") || d.code().equals("TC-E4018")) {
            // Tensor cores the device (or its NVRTC) lacks: what KernelContext.mma* document.
            return new TornadoDeviceMMANotSupported(text);
        }
        return switch (category) {
            // TornadoInternalError formats its message: pass the text as an argument, not as the format.
            case INTERNAL -> new TornadoInternalError("%s", "TC internal error (please report with the kernel): " + text);
            // Kernels the GPU cannot run as written raise an Error.
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

    private static void printStats() {
        var reasons = new TreeMap<String, Integer>();
        REFUSED.forEach((k, v) -> reasons.put(k, v.get()));
        int refused = reasons.values().stream().mapToInt(Integer::intValue).sum();
        System.out.println("[TC] kernels compiled by TC: " + COMPILED.get() + ", reused: " + REUSED.get() + ", refused: " + refused + " " + reasons + ", generic in varying scalars: " + GENERIC.get());
    }
}
