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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;
import uk.ac.manchester.tornado.runtime.common.KernelAccess;
import uk.ac.manchester.tornado.runtime.common.TornadoInstalledCode;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.profiler.FeatureExtractionUtilities;
import uk.ac.manchester.tornado.runtime.profiler.ProfilerCodeFeatures;
import uk.ac.manchester.tornado.runtime.tasks.CompilableTask;
import uk.ac.manchester.tornado.tc.ir.analysis.KernelFeatures;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps;

/**
 * Compiles a task with TC and installs it on a device: what every driver's {@code compileTask}
 * does. The driver supplies its target capabilities and how it installs source; a kernel TC cannot
 * compile becomes the TornadoVM exception for its kind ({@link TcHook}), and the runtime runs the
 * task on the host if bailouts are recoverable.
 */
public final class TcKernels {

    /**
     * With {@code -Dtornado.tc.compileTimes=true} (and the profiler on), one line per compiled task:
     * TC's time and the driver's (NVRTC, clBuildProgram, Metal library) time.
     */
    private static final boolean REPORT_COMPILE_TIMES = Boolean.getBoolean("tornado.tc.compileTimes");

    /** Wall-clock totals of the TC and driver phases, for -Dtornado.tc.compileTimes (printed at exit). */
    private static final AtomicLong TC_NANOS = new AtomicLong();
    private static final AtomicLong DRIVER_NANOS = new AtomicLong();
    private static final AtomicInteger TASKS = new AtomicInteger();

    static {
        if (REPORT_COMPILE_TIMES) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.printf("[compile-times] total tasks=%d tcMs=%.1f driverMs=%.1f%n", TASKS.get(), TC_NANOS.get() / 1e6,
                    DRIVER_NANOS.get() / 1e6)));
        }
    }

    /** The initial values of the {@code AtomicInteger}s a kernel creates, by kernel method. */
    public static final Map<Object, ArrayList<Integer>> ATOMICS = Collections.synchronizedMap(new HashMap<>());

    /** For {@code AtomicInteger} parameters: parameter index to atomics slot, by kernel method. */
    public static final Map<Object, HashMap<Integer, Integer>> ATOMIC_PARAMETERS = Collections.synchronizedMap(new HashMap<>());

    /** Background compilation threads (-Dtornado.parallel.compile.threads, default: cores, at most 8). */
    private static final int PREFETCH_THREADS = Integer.getInteger("tornado.parallel.compile.threads", Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors())));

    private static final AtomicInteger PREFETCH_THREAD_ID = new AtomicInteger();

    private static final ExecutorService PREFETCH = Executors.newFixedThreadPool(PREFETCH_THREADS, runnable -> {
        Thread thread = new Thread(runnable, "tornado-compile-" + PREFETCH_THREAD_ID.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    /** Compiles emitted source ahead of its installation (the driver's part of a prefetch). */
    public interface Prebuilder {
        void prebuild(String entryName, byte[] source);
    }

    /**
     * Compiles {@code executable} with TC on a background thread, then hands the source to
     * {@code prebuilder} (the driver compiler) on that thread. The task's later {@link #compile} finds
     * TC's result in TcHook's memo (or waits for it) and the driver finds its image. Failures are
     * dropped here: the in-order compilation repeats the work and reports them.
     */
    public static void prefetch(CompilableTask executable, TargetCaps target, Prebuilder prebuilder) {
        // Read the task's state on the calling thread, as compile() will see it.
        final Method method = executable.getMethod();
        final var taskMeta = executable.meta();
        final Object[] arguments = executable.getArguments();
        final long batchThreads = executable.getBatchThreads();
        final long numThreads = taskMeta.getNumThreads();
        final long batchIndexOffset = (long) executable.getBatchNumber() * executable.getBatchSize();
        final String taskId = taskMeta.getId();
        final boolean strict = taskMeta.isStrictFloatingPoint();
        final boolean workerGrid = taskMeta.isWorkerGridAvailable();
        // Decided here, in task order: the background compile must generate what the in-order one would.
        try {
            TcHook.decide(method, arguments, target, batchThreads, batchIndexOffset, taskId, strict, workerGrid);
        } catch (RuntimeException e) {
            return; // the in-order compilation reports it
        }
        PREFETCH.execute(() -> {
            try {
                var event = CompileEvent.begin("tc", method.getDeclaringClass().getSimpleName() + "." + method.getName(), taskId, true);
                var compiled = TcHook.compile(method, arguments, target, batchThreads, numThreads, batchIndexOffset, taskId, strict, workerGrid);
                event.finish();
                compiled.ifPresent(kernel -> prebuilder.prebuild(kernel.entryName(), kernel.source()));
            } catch (Throwable e) {
                // The in-order compilation repeats the work and reports its failures; one specific to
                // the background thread or to the driver's prebuild is visible with tornado.tc.verbose.
                if (Boolean.getBoolean("tornado.tc.verbose")) {
                    System.err.println("[TC] background compilation of " + taskId + " failed: " + e);
                }
            }
        });
    }

    /** Installs emitted source on a device. */
    public interface Installer {
        TornadoInstalledCode install(String entryName, byte[] source);
    }

    private TcKernels() {
    }

    public static TornadoInstalledCode compile(CompilableTask executable, TargetCaps target, Installer installer) {
        return compile(executable, target, null, installer);
    }

    /**
     * Compiles a task for {@code target} and installs it with {@code installer}. With
     * {@code -Dtornado.feature.extraction=true}, also reports the kernel's code features for
     * {@code deviceContext}.
     */
    public static TornadoInstalledCode compile(CompilableTask executable, TargetCaps target, TornadoDeviceContext deviceContext, Installer installer) {
        final Method method = executable.getMethod();
        final var taskMeta = executable.meta();
        final Access[] access = KernelAccess.of(method, taskMeta.getArgumentsAccess().length);
        System.arraycopy(access, 0, taskMeta.getArgumentsAccess(), 0, Math.min(access.length, taskMeta.getArgumentsAccess().length));

        TornadoProfiler profiler = executable.getProfiler();
        long tcStart = System.nanoTime();
        profiler.start(ProfilerType.TASK_COMPILE_TC_TIME, taskMeta.getId());
        var event = CompileEvent.begin("tc", method.getDeclaringClass().getSimpleName() + "." + method.getName(), taskMeta.getId(), false);
        var kernel = TcHook.compile(method, executable.getArguments(), target, executable.getBatchThreads(), taskMeta.getNumThreads(),
                (long) executable.getBatchNumber() * executable.getBatchSize(), taskMeta.getId(), taskMeta.isStrictFloatingPoint(), taskMeta.isWorkerGridAvailable()).orElseThrow();
        event.finish();
        profiler.stop(ProfilerType.TASK_COMPILE_TC_TIME, taskMeta.getId());
        long tcNanos = System.nanoTime() - tcStart;
        if (TornadoOptions.FEATURE_EXTRACTION && deviceContext != null) {
            emitFeatures(method, kernel.features(), deviceContext);
        }
        profiler.sum(ProfilerType.TOTAL_TC_COMPILE_TIME, profiler.getTaskTimer(ProfilerType.TASK_COMPILE_TC_TIME, taskMeta.getId()));

        long driverStart = System.nanoTime();
        profiler.start(ProfilerType.TASK_COMPILE_DRIVER_TIME, taskMeta.getId());
        kernel.domain().ifPresent(taskMeta::setDomain);
        // The runtime finds a task's atomics region through its compiled method.
        taskMeta.setCompiledGraph(method);
        var atomics = kernel.atomics();
        if (!atomics.isEmpty()) {
            ATOMICS.put(method, new ArrayList<>(atomics.initial()));
            if (!atomics.params().isEmpty()) {
                ATOMIC_PARAMETERS.put(method, new HashMap<>(atomics.params()));
            }
        }
        var installed = installer.install(kernel.entryName(), kernel.source());
        profiler.stop(ProfilerType.TASK_COMPILE_DRIVER_TIME, taskMeta.getId());
        profiler.sum(ProfilerType.TOTAL_DRIVER_COMPILE_TIME, profiler.getTaskTimer(ProfilerType.TASK_COMPILE_DRIVER_TIME, taskMeta.getId()));
        if (REPORT_COMPILE_TIMES) {
            long driverNanos = System.nanoTime() - driverStart;
            TC_NANOS.addAndGet(tcNanos);
            DRIVER_NANOS.addAndGet(driverNanos);
            TASKS.incrementAndGet();
            System.out.printf("[compile-times] compiler=tc method=%s.%s task=%s compilerNs=%d driverNs=%d%n", method.getDeclaringClass().getSimpleName(), method.getName(), taskMeta.getId(),
                    tcNanos, driverNanos);
        }
        return installed;
    }

    private static void emitFeatures(Method method, KernelFeatures features, TornadoDeviceContext deviceContext) {
        var map = FeatureExtractionUtilities.initializeFeatureMap();
        for (var feature : ProfilerCodeFeatures.values()) {
            map.put(feature, features.get(KernelFeatures.Feature.valueOf(feature.name())));
        }
        FeatureExtractionUtilities.emitFeatureProfileJsonFile(map, method.getDeclaringClass().getSimpleName() + "." + method.getName(), deviceContext);
    }
}
