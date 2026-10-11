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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;
import uk.ac.manchester.tornado.runtime.common.KernelAccess;
import uk.ac.manchester.tornado.runtime.common.TornadoInstalledCode;
import uk.ac.manchester.tornado.runtime.tasks.CompilableTask;
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
        final Method method = executable.getMethod();
        final var taskMeta = executable.meta();
        final Access[] access = KernelAccess.of(method, taskMeta.getArgumentsAccess().length);
        System.arraycopy(access, 0, taskMeta.getArgumentsAccess(), 0, Math.min(access.length, taskMeta.getArgumentsAccess().length));

        TornadoProfiler profiler = executable.getProfiler();
        profiler.start(ProfilerType.TASK_COMPILE_TC_TIME, taskMeta.getId());
        var kernel = TcHook.compile(method, executable.getArguments(), target, executable.getBatchThreads(), taskMeta.getNumThreads(),
                (long) executable.getBatchNumber() * executable.getBatchSize(), taskMeta.getId(), taskMeta.isStrictFloatingPoint()).orElseThrow();
        profiler.stop(ProfilerType.TASK_COMPILE_TC_TIME, taskMeta.getId());
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
        var installed = installer.install(kernel.entryName(), kernel.source());
        profiler.stop(ProfilerType.TASK_COMPILE_DRIVER_TIME, taskMeta.getId());
        profiler.sum(ProfilerType.TOTAL_DRIVER_COMPILE_TIME, profiler.getTaskTimer(ProfilerType.TASK_COMPILE_DRIVER_TIME, taskMeta.getId()));
        if (REPORT_COMPILE_TIMES) {
            System.out.printf("[compile-times] compiler=tc method=%s.%s task=%s compilerNs=%d driverNs=%d%n", method.getDeclaringClass().getSimpleName(), method.getName(), taskMeta.getId(),
                    profiler.getTaskTimer(ProfilerType.TASK_COMPILE_TC_TIME, taskMeta.getId()), profiler.getTaskTimer(ProfilerType.TASK_COMPILE_DRIVER_TIME, taskMeta.getId()));
        }
        return installed;
    }
}
