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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceFP64NotSupported;
import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceMMANotSupported;
import uk.ac.manchester.tornado.api.options.TcFlags;
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
import uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation;

/**
 * The runtime side of TC, TornadoVM's kernel compiler: compiles a task method, specialised on the
 * actual task arguments (array lengths, scalars), into the source, entry point and launch domain,
 * and records statistics. A kernel TC cannot compile raises the TornadoVM exception for the
 * diagnostic's category.
 *
 * <p>Options: the {@code tornado.tc.*} flags of {@link TcFlags}, read once and passed to TC as
 * {@link TcOptions}.</p>
 */
public final class TcHook {

    private static final boolean VERBOSE = TcFlags.VERBOSE.get();
    private static final boolean STATS = TcFlags.STATS.get();

    private static final boolean MEMOIZE = TcFlags.MEMOIZE.get();

    /** The compilation options the flags configure, before a task's own (its id, strict floating point). */
    private static final TcOptions CONFIGURED = new TcOptions(TcOptions.dumpStages(TcFlags.DUMP.get()), Path.of(TcFlags.DUMP_DIR.get()), //
            "each".equals(TcFlags.VERIFY.get()), Optional.of(TcFlags.ONLY.get()).filter(only -> !only.isEmpty()), "html".equals(TcFlags.REPORT.get()), //
            TcFlags.MATH_OPTIMIZATIONS.get(), "?", TcFlags.ENABLE_FMA.get(), TcFlags.NATIVE_HALF.get(), TcFlags.SIGNED_OVERFLOW_UNDEFINED.get());

    /**
     * A scalar argument that has taken this many different values across the tasks of one kernel
     * (same method, target, options and other facts) is compiled as a runtime value from then on; 0
     * specialises every value. A model's layers pass their layer index as a scalar: specialised on
     * it, each layer's attention was a kernel of its own. Values a kernel needs as constants (a
     * local array's size) keep it specialised: the generic compilation is refused and not retried.
     */
    private static final int VARYING_SCALARS = TcFlags.VARYING_SCALARS.get();

    /**
     * Dynamic shapes, for tasks of a plan with {@code withDynamicShapes()}, or every task when this
     * option is set: a kernel whose code does not depend on the lengths of its array arguments (it
     * never reads one, as with sizes passed as scalars) is memoised without them, so tasks over
     * arrays of other sizes reuse its compilation, and its scalar arguments count as varying across
     * all of them. Learned from the first compilation of each method, target and options: TC reports
     * whether the kernel, its callees inlined, reads any array length. Off by default: shared code can
     * be slower than code specialised on one size.
     */
    private static final boolean DYNAMIC_SHAPES = TcFlags.DYNAMIC_SHAPES.get();

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

    /**
     * For a kernel shared across lengths (dynamic shapes), the most positions that may change together between two tasks for
     * them to count as one varying group (a batch size and the tile count derived from it).
     */
    private static final int GROUP = 4;

    /** For a kernel shared across lengths (dynamic shapes), a scalar that takes this many values is compiled as a runtime value whatever the others do. */
    private static final int MANY_VALUES = 16;

    /** The scalar values the tasks of one shape have had, and whether its kernel compiles without them. */
    private static final class Scalars {
        private final Map<Integer, java.util.Set<Number>> values = new java.util.HashMap<>();
        /** The launch domain of the first, specialised, compilation: generic code must launch the same. */
        private List<uk.ac.manchester.tornado.tc.tornado.ParallelLoopLowering.Domain> domain;
        private boolean specialiseAlways;

        /**
         * A kernel shared across lengths's tasks span all the sizes and layers that call it, so a scalar that
         * differs between layers (a convolution's channels or stride) would soon count as varying and
         * lose its constant. Its scalars become runtime values in groups instead: the positions in which
         * a task differs from the nearest earlier one, once they have taken {@link #VARYING_SCALARS}
         * values with every other scalar the same (a batch size, not a layer).
         */
        private final boolean grouped;
        private final List<Number[]> tuples = new java.util.ArrayList<>();
        private final Map<List<Object>, java.util.Set<List<Number>>> groups = new java.util.HashMap<>();
        private final java.util.BitSet varying = new java.util.BitSet();

        Scalars(boolean grouped) {
            this.grouped = grouped;
        }

        /** Records the task's scalar values; returns the positions to compile as runtime values. */
        synchronized java.util.BitSet observe(List<ValueSpecialisation.Fact> facts) {
            if (grouped) {
                return observeGrouped(facts);
            }
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

        private java.util.BitSet observeGrouped(List<ValueSpecialisation.Fact> facts) {
            var tuple = new Number[facts.size()];
            for (int i = 0; i < facts.size(); i++) {
                if (facts.get(i) instanceof ValueSpecialisation.Fact.Scalar(var value)) {
                    tuple[i] = value;
                    var seen = values.computeIfAbsent(i, k -> new java.util.HashSet<>());
                    if (seen.size() < MANY_VALUES) {
                        seen.add(value);
                    }
                    if (seen.size() >= MANY_VALUES) {
                        varying.set(i);
                    }
                }
            }
            if (tuples.stream().noneMatch(t -> java.util.Arrays.equals(t, tuple))) {
                Number[] nearest = null;
                java.util.BitSet differ = null;
                for (var t : tuples) {
                    var d = new java.util.BitSet();
                    for (int i = 0; i < tuple.length; i++) {
                        if (!java.util.Objects.equals(t[i], tuple[i])) {
                            d.set(i);
                        }
                    }
                    d.andNot(varying);
                    if (differ == null || d.cardinality() < differ.cardinality()) {
                        nearest = t;
                        differ = d;
                    }
                }
                if (tuples.size() < 1024) {
                    tuples.add(tuple);
                }
                if (differ != null && !differ.isEmpty() && differ.cardinality() <= GROUP) {
                    var fixed = (java.util.BitSet) varying.clone();
                    fixed.or(differ);
                    var rest = new java.util.ArrayList<Object>();
                    rest.add(differ.toString());
                    for (int i = 0; i < tuple.length; i++) {
                        rest.add(fixed.get(i) ? null : tuple[i]);
                    }
                    var seen = groups.computeIfAbsent(rest, k -> new java.util.HashSet<>());
                    seen.add(project(tuple, differ));
                    seen.add(project(nearest, differ));
                    if (seen.size() >= VARYING_SCALARS) {
                        varying.or(differ);
                    }
                }
            }
            return specialiseAlways ? new java.util.BitSet() : (java.util.BitSet) varying.clone();
        }

        private static List<Number> project(Number[] tuple, java.util.BitSet positions) {
            var out = new java.util.ArrayList<Number>();
            positions.stream().forEach(i -> out.add(tuple[i]));
            return out;
        }
    }

    private static final Map<Shape, Scalars> SCALARS = new ConcurrentHashMap<>();

    /** A method compiled for one target with some options, whatever its arguments. */
    private record MethodKey(Method method, TargetCaps target, TcOptions options) {
    }

    /** Per method, whether its code reads the lengths of its array arguments (see dynamic shapes (see {@link #DYNAMIC_SHAPES})). */
    private static final Map<MethodKey, Boolean> READS_LENGTHS = new ConcurrentHashMap<>();
    private static final AtomicInteger LENGTH_FREE = new AtomicInteger();
    private static final AtomicInteger BOUNDED = new AtomicInteger();
    private static final AtomicInteger GENERIC = new AtomicInteger();

    /** What a compilation leaves for later tasks (not its stages). */
    private record Compiled(String source, String entryName, long nanos, List<uk.ac.manchester.tornado.tc.tornado.ParallelLoopLowering.Domain> domain, AtomicSlots.Info atomics,
            KernelFeatures features, String task, boolean taskInSource, boolean readsLengths) {
    }

    /** Most recent compilations; a model's layers share one entry per kernel. */
    private static final Map<Key, Compiled> COMPILATIONS = java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Compiled> eldest) {
            return size() > 2048;
        }
    });
    private static final Map<String, AtomicInteger> REFUSED = new ConcurrentHashMap<>();

    static {
        if (STATS) {
            Runtime.getRuntime().addShutdownHook(new Thread(TcHook::printStats));
        }
    }

    /**
     * A kernel compiled by TC. {@code generic} when its code was shared across tasks: compiled
     * without the array lengths (see dynamic shapes (see {@link #DYNAMIC_SHAPES})) or with scalars as runtime values. Such
     * code can need more registers than the task's own specialisation; see {@link #compileSpecialised}.
     */
    public record Kernel(byte[] source, String entryName, Optional<DomainTree> domain, long nanos, AtomicSlots.Info atomics, KernelFeatures features, boolean generic) {
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
        return compile(method, arguments, target, batchThreads, numThreads, batchIndexOffset, taskId, strictFloatingPoint, workerGrid, false);
    }

    /** As above, for a task of a plan with {@code withDynamicShapes()} when {@code dynamicShapes} (see {@link #DYNAMIC_SHAPES}). */
    public static Optional<Kernel> compile(Method method, Object[] arguments, TargetCaps target, long batchThreads, long numThreads, long batchIndexOffset, String taskId,
            boolean strictFloatingPoint, boolean workerGrid, boolean dynamicShapes) {
        var name = method.getDeclaringClass().getSimpleName() + "." + method.getName();
        var facts = TcArguments.facts(method, arguments, batchThreads, batchIndexOffset);
        if (workerGrid) {
            facts.add(new ValueSpecialisation.Fact.WorkerGrid());
        }
        var owner = method.getDeclaringClass();
        var loader = owner.getClassLoader() != null ? owner.getClassLoader() : ClassLoader.getSystemClassLoader();
        var descriptor = MethodType.methodType(method.getReturnType(), method.getParameterTypes()).toMethodDescriptorString();
        var options = CONFIGURED.withTask(taskId).withStrictFloatingPoint(strictFloatingPoint);
        var key = new Key(method, target, options.withTask("?"), List.copyOf(facts));
        boolean shared = (DYNAMIC_SHAPES || dynamicShapes) && MEMOIZE && !SPECIALISED.contains(new MethodKey(method, target, options.withTask("?")))
                && !readsLengths(name, owner, loader, descriptor, options, key, taskId);
        if (shared) {
            key = withoutLengths(key);
        }
        var scalars = MEMOIZE && VARYING_SCALARS > 0 ? SCALARS.computeIfAbsent(Shape.of(key), k -> new Scalars(shared)) : null;
        var varying = scalars != null ? scalars.observe(key.facts()) : new java.util.BitSet();
        if (!varying.isEmpty()) {
            var generic = generic(name, owner, loader, descriptor, options, key, varying, scalars, taskId);
            if (generic.isPresent()) {
                return kernel(name, generic.get(), facts, numThreads, true);
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
        return kernel(name, result.get(), facts, numThreads, shared);
    }

    /**
     * Methods whose shared (generic) code allowed smaller blocks than a task's own specialisation
     * (it needs more registers): from then on they are compiled as without dynamic shapes (see {@link #DYNAMIC_SHAPES}).
     */
    private static final java.util.Set<MethodKey> SPECIALISED = ConcurrentHashMap.newKeySet();

    /**
     * Shared kernels (by method and source) already compared with a specialisation (see {@link #settle}),
     * with the block size their code is bounded to, or 0 when it needs no bound.
     */
    private static final Map<List<Object>, Integer> SETTLED = new ConcurrentHashMap<>();

    /**
     * As {@link #compile(Method, Object[], TargetCaps, long, long, long, String, boolean, boolean)}, but
     * specialised on every argument value, never shared with other tasks, to compare with a generic
     * kernel that needs many registers (see {@link #settle}).
     */
    public static Optional<Kernel> compileSpecialised(Method method, Object[] arguments, TargetCaps target, long batchThreads, long numThreads, long batchIndexOffset, String taskId,
            boolean strictFloatingPoint, boolean workerGrid) {
        var name = method.getDeclaringClass().getSimpleName() + "." + method.getName();
        var facts = TcArguments.facts(method, arguments, batchThreads, batchIndexOffset);
        if (workerGrid) {
            facts.add(new ValueSpecialisation.Fact.WorkerGrid());
        }
        var owner = method.getDeclaringClass();
        var loader = owner.getClassLoader() != null ? owner.getClassLoader() : ClassLoader.getSystemClassLoader();
        var descriptor = MethodType.methodType(method.getReturnType(), method.getParameterTypes()).toMethodDescriptorString();
        var options = CONFIGURED.withTask(taskId).withStrictFloatingPoint(strictFloatingPoint);
        var result = compileOrReuse(name, owner, loader, descriptor, options, new Key(method, target, options.withTask("?"), List.copyOf(facts)), taskId);
        return result.isEmpty() ? Optional.empty() : kernel(name, result.get(), facts, numThreads, false);
    }

    /** True when the generic {@code kernel} of {@code method} has not been compared with a specialisation yet. */
    public static boolean unsettled(Method method, Kernel kernel) {
        return !SETTLED.containsKey(List.of(method, java.util.Arrays.hashCode(kernel.source())));
    }

    /**
     * The generic {@code kernel} as later tasks must launch it: bounded to the block size found by
     * {@link #settle}, or unchanged.
     */
    public static Kernel settled(Method method, Kernel kernel) {
        Integer bound = SETTLED.get(List.of(method, java.util.Arrays.hashCode(kernel.source())));
        return bound == null || bound == 0 ? kernel : bounded(kernel, bound).orElse(kernel);
    }

    /**
     * {@code kernel} with {@code __launch_bounds__(maxThreads, 1)} on its entry point: the compiler
     * then keeps to the registers that let a block of {@code maxThreads} threads launch, so code
     * shared across tasks launches every block a task's own specialisation could. One block per
     * multiprocessor is the minimum asked for; without it the compiler cuts registers further than
     * the block needs. Empty when the source has no CUDA entry point of that name.
     */
    public static Optional<Kernel> bounded(Kernel kernel, int maxThreads) {
        var source = new String(kernel.source(), StandardCharsets.UTF_8);
        var entry = "__global__ void " + kernel.entryName() + "(";
        int at = source.indexOf(entry);
        if (at < 0 || source.indexOf(entry, at + 1) >= 0) {
            return Optional.empty();
        }
        var withBound = source.substring(0, at) + "__global__ void __launch_bounds__(" + maxThreads + ", 1) " + kernel.entryName() + "(" + source.substring(at + entry.length());
        return Optional.of(new Kernel(withBound.getBytes(StandardCharsets.UTF_8), kernel.entryName(), kernel.domain(), kernel.nanos(), kernel.atomics(), kernel.features(), true));
    }

    /**
     * Records the comparison of the generic {@code kernel} with a task's own specialisation: when the
     * specialisation allows larger blocks ({@code specialise}), the method is compiled from then on
     * as without dynamic shapes (see {@link #DYNAMIC_SHAPES}), since the block its tasks launch with is only known later.
     */
    public static void settle(Method method, TargetCaps target, boolean strictFloatingPoint, Kernel kernel, boolean specialise) {
        settle(method, target, strictFloatingPoint, kernel, specialise, 0);
    }

    /**
     * As above; {@code bound > 0} when the generic {@code kernel} is kept, bounded to blocks of
     * {@code bound} threads (see {@link #bounded}), for this and every later task sharing it.
     */
    public static void settle(Method method, TargetCaps target, boolean strictFloatingPoint, Kernel kernel, boolean specialise, int bound) {
        SETTLED.put(List.of(method, java.util.Arrays.hashCode(kernel.source())), specialise ? 0 : bound);
        if (bound > 0 && !specialise) {
            BOUNDED.incrementAndGet();
            if (VERBOSE) {
                System.out.printf("[TC] %s: shared code bounded to blocks of %d threads, as its specialisation allows%n", method.getDeclaringClass().getSimpleName() + "." + method.getName(), bound);
            }
        }
        if (specialise) {
            SPECIALISED.add(new MethodKey(method, target, CONFIGURED.withTask("?").withStrictFloatingPoint(strictFloatingPoint)));
            if (VERBOSE) {
                System.out.printf("[TC] %s: shared code needs more registers than its specialisation; specialised from now on%n", method.getDeclaringClass().getSimpleName() + "." + method.getName());
            }
        }
    }

    /** {@code key} with every array length unknown. */
    private static Key withoutLengths(Key key) {
        return new Key(key.method(), key.target(), key.options(),
                key.facts().stream().map(f -> f instanceof ValueSpecialisation.Fact.Length ? (ValueSpecialisation.Fact) new ValueSpecialisation.Fact.Unknown() : f).toList());
    }

    /**
     * Whether the kernel's code reads the lengths of its array arguments, from the first compilation
     * of the method (memoised also under the key without lengths when it reads none).
     */
    private static boolean readsLengths(String name, Class<?> owner, ClassLoader loader, String descriptor, TcOptions options, Key key, String taskId) {
        if (key.facts().stream().noneMatch(f -> f instanceof ValueSpecialisation.Fact.Length)) {
            return true;
        }
        var methodKey = new MethodKey(key.method(), key.target(), key.options());
        Boolean known = READS_LENGTHS.get(methodKey);
        if (known != null) {
            return known;
        }
        boolean reads = true;
        try {
            var with = compileOrReuse(name, owner, loader, descriptor, options, key, taskId);
            if (with.isPresent() && !with.get().readsLengths()) {
                reads = false;
                COMPILATIONS.putIfAbsent(withoutLengths(key), with.get());
            }
        } catch (RuntimeException | Error e) {
            // left to the normal path, which reports the failure
        }
        if (READS_LENGTHS.putIfAbsent(methodKey, reads) == null && !reads) {
            LENGTH_FREE.incrementAndGet();
            if (VERBOSE) {
                System.out.printf("[TC] %s reads no array length: memoised whatever the lengths%n", name);
            }
        }
        return reads;
    }

    /** The memoised compilation for {@code key}, or a new one. */
    private static Optional<Compiled> compileOrReuse(String name, Class<?> owner, ClassLoader loader, String descriptor, TcOptions options, Key key, String taskId) {
        var compiled = MEMOIZE ? COMPILATIONS.get(key) : null;
        if (compiled != null && (!compiled.taskInSource() || compiled.task().equals(taskId))) {
            REUSED.incrementAndGet();
            if (VERBOSE) {
                System.out.printf("[TC] reused %s (same method and facts as an earlier task)%n", name);
            }
            return Optional.of(compiled);
        }
        var result = compileNew(name, owner, loader, key.method().getName(), descriptor, key.target(), options, key.facts());
        if (result.isPresent() && MEMOIZE) {
            COMPILATIONS.put(key, result.get());
        }
        return result;
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
        return Optional.of(new Compiled(c.source(), c.entryName(), c.nanos(), c.domain(), c.atomics(), c.features(), options.task(), taskInSource, c.readsLengths()));
    }

    /** The kernel for one task: the launch domain depends on the task's thread count, not only on the code. */
    private static Optional<Kernel> kernel(String name, Compiled c, List<ValueSpecialisation.Fact> facts, long numThreads, boolean generic) {
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
        return Optional.of(new Kernel(c.source().getBytes(StandardCharsets.UTF_8), c.entryName(), domain, c.nanos(), c.atomics(), c.features(), generic));
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
        System.out.println("[TC] kernels compiled by TC: " + COMPILED.get() + ", reused: " + REUSED.get() + ", refused: " + refused + " " + reasons + ", generic in varying scalars: " + GENERIC.get()
                + ", methods reading no array length: " + LENGTH_FREE.get() + ", shared kernels bounded: " + BOUNDED.get() + ", specialised: " + SPECIALISED.size());
    }
}
