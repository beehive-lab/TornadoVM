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

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.options.ProfilerFlags;
import uk.ac.manchester.tornado.api.options.TcFlags;
import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;
import uk.ac.manchester.tornado.runtime.common.KernelAccess;
import uk.ac.manchester.tornado.runtime.common.TornadoInstalledCode;
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
     * With {@code -Dtornado.tc.compile.times=true} (and the profiler on), one line per compiled task:
     * TC's time and the driver's (NVRTC, clBuildProgram, Metal library) time.
     */
    private static final boolean REPORT_COMPILE_TIMES = TcFlags.COMPILE_TIMES.get();

    /** The initial values of the {@code AtomicInteger}s a kernel creates, by kernel method. */
    public static final Map<Object, ArrayList<Integer>> ATOMICS = Collections.synchronizedMap(new HashMap<>());

    /** For {@code AtomicInteger} parameters: parameter index to atomics slot, by kernel method. */
    public static final Map<Object, HashMap<Integer, Integer>> ATOMIC_PARAMETERS = Collections.synchronizedMap(new HashMap<>());

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
        profiler.start(ProfilerType.TASK_COMPILE_TC_TIME, taskMeta.getId());
        var kernel = TcHook.compile(method, executable.getArguments(), target, executable.getBatchThreads(), taskMeta.getNumThreads(),
                (long) executable.getBatchNumber() * executable.getBatchSize(), taskMeta.getId(), taskMeta.isStrictFloatingPoint(), taskMeta.isWorkerGridAvailable(),
                taskMeta.isDynamicShapes()).orElseThrow();
        profiler.stop(ProfilerType.TASK_COMPILE_TC_TIME, taskMeta.getId());
        if (ProfilerFlags.FEATURE_EXTRACTION.get() && deviceContext != null) {
            emitFeatures(method, kernel.features(), deviceContext);
        }
        profiler.sum(ProfilerType.TOTAL_TC_COMPILE_TIME, profiler.getTaskTimer(ProfilerType.TASK_COMPILE_TC_TIME, taskMeta.getId()));

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
        var shared = kernel;
        if (kernel.generic()) {
            kernel = TcHook.settled(method, kernel);
        }
        var installed = installer.install(kernel.entryName(), kernel.source());
        // virtual devices install nothing and return null
        int maxThreads = installed != null ? installed.maxThreadsPerBlock() : Integer.MAX_VALUE;
        if (kernel.generic() && maxThreads < target.maxWorkGroupSize() && TcHook.unsettled(method, shared)) {
            // Code shared across tasks can need more registers than a task's own specialisation, and so
            // allow smaller blocks; the task's block is only known at launch. Compare once with this
            // task's specialisation: when it allows larger blocks, bound the shared code to them, and
            // keep the specialisation only when the bound does not take.
            var own = TcHook.compileSpecialised(method, executable.getArguments(), target, executable.getBatchThreads(), taskMeta.getNumThreads(),
                    (long) executable.getBatchNumber() * executable.getBatchSize(), taskMeta.getId(), taskMeta.isStrictFloatingPoint(), taskMeta.isWorkerGridAvailable());
            if (own.isPresent()) {
                var ownInstalled = installer.install(own.get().entryName(), own.get().source());
                int ownThreads = ownInstalled.maxThreadsPerBlock();
                installed.invalidate();
                if (ownThreads <= maxThreads) {
                    // the task's code stays the shared kernel, installed again as the latest for its id
                    TcHook.settle(method, target, taskMeta.isStrictFloatingPoint(), kernel, false);
                    ownInstalled.invalidate();
                    installed = installer.install(kernel.entryName(), kernel.source());
                } else {
                    var bounded = TcHook.bounded(kernel, ownThreads);
                    var boundedInstalled = bounded.isPresent() ? installer.install(bounded.get().entryName(), bounded.get().source()) : null;
                    if (boundedInstalled != null && boundedInstalled.maxThreadsPerBlock() >= ownThreads) {
                        TcHook.settle(method, target, taskMeta.isStrictFloatingPoint(), kernel, false, ownThreads);
                        ownInstalled.invalidate();
                        kernel = bounded.get();
                        installed = boundedInstalled;
                    } else {
                        TcHook.settle(method, target, taskMeta.isStrictFloatingPoint(), kernel, true);
                        kernel = own.get();
                        kernel.domain().ifPresent(taskMeta::setDomain);
                        installed = ownInstalled;
                        if (boundedInstalled != null) {
                            // the specialisation installed again as the latest for its id
                            boundedInstalled.invalidate();
                            ownInstalled.invalidate();
                            installed = installer.install(kernel.entryName(), kernel.source());
                        }
                    }
                }
            }
        }
        profiler.stop(ProfilerType.TASK_COMPILE_DRIVER_TIME, taskMeta.getId());
        profiler.sum(ProfilerType.TOTAL_DRIVER_COMPILE_TIME, profiler.getTaskTimer(ProfilerType.TASK_COMPILE_DRIVER_TIME, taskMeta.getId()));
        if (REPORT_COMPILE_TIMES) {
            System.out.printf("[compile-times] compiler=tc method=%s.%s task=%s compilerNs=%d driverNs=%d%n", method.getDeclaringClass().getSimpleName(), method.getName(), taskMeta.getId(),
                    profiler.getTaskTimer(ProfilerType.TASK_COMPILE_TC_TIME, taskMeta.getId()), profiler.getTaskTimer(ProfilerType.TASK_COMPILE_DRIVER_TIME, taskMeta.getId()));
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
