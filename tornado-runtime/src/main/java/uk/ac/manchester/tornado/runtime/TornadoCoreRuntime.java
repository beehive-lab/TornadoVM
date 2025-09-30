/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2021, 2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.runtime;

import static org.graalvm.compiler.debug.GraalError.guarantee;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;

import java.lang.reflect.Method;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.StreamSupport;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.hotspot.HotSpotGraalOptionValues;
import org.graalvm.compiler.lir.constopt.ConstantLoadOptimization;
import org.graalvm.compiler.lir.phases.PostAllocationOptimizationStage;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.printer.GraalDebugHandlersFactory;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.runtime.JVMCI;
import jdk.vm.ci.runtime.JVMCIBackend;
import uk.ac.manchester.tornado.api.TornadoBackend;
import uk.ac.manchester.tornado.api.TornadoRuntime;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoBackendNotFound;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.common.UpsMeterReader;
import uk.ac.manchester.tornado.runtime.common.enums.TornadoBackends;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSnippetReflectionProvider;

public final class TornadoCoreRuntime implements TornadoRuntime {

    private static final ThreadFactory executorThreadFactory = new ThreadFactory() {
        private int threadId = 0;

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, String.format("TornadoExecutorThread - %d", threadId));
            thread.setDaemon(true);
            threadId++;
            return thread;
        }
    };
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(TornadoOptions.TORNADO_SKETCHER_THREADS, executorThreadFactory);
    private static final TornadoCoreRuntime runtime = new TornadoCoreRuntime();
    private static final JVMMapping JVM = new JVMMapping();
    private static final int DEFAULT_DRIVER = 0;

    private static Lock lock = new ReentrantLock();
    private static final int DEFAULT_BACKEND = 0;
    private static DebugContext debugContext = null;
    private static OptionValues options;

    private final JVMCIBackend vmBackend;
    private final HotSpotJVMCIRuntime vmRuntime;
    private final TornadoVMConfigAccess vmConfig;
    private final TornadoAcceleratorBackend[] tornadoVMBackends;
    private int backendCount;

    private TornadoCoreRuntime() {

        initOptions();
        guarantee(!GraalOptions.OmitHotExceptionStacktrace.getValue(options), "error");

        if (!(JVMCI.getRuntime() instanceof HotSpotJVMCIRuntime)) {
            shouldNotReachHere("Unsupported JVMCIRuntime: ", JVMCI.getRuntime().getClass().getName());
        }
        vmRuntime = (HotSpotJVMCIRuntime) JVMCI.getRuntime();
        vmBackend = vmRuntime.getHostJVMCIBackend();
        vmConfig = new TornadoVMConfigAccess(vmRuntime.getConfigStore(), vmBackend.getMetaAccess());
        tornadoVMBackends = loadBackends();
    }

    public static TornadoCoreRuntime getTornadoRuntime() {
        return runtime;
    }

    public static DebugContext getDebugContext() {
        lock.lock();
        if (debugContext == null) {
            debugContext = new DebugContext.Builder(getOptions(), new GraalDebugHandlersFactory(new TornadoSnippetReflectionProvider())).build();
        }
        lock.unlock();
        return debugContext;
    }

    public static ExecutorService getTornadoExecutor() {
        return EXECUTOR;
    }

    public static JVMCIBackend getVMBackend() {
        return runtime.vmBackend;
    }

    public static HotSpotJVMCIRuntime getVMRuntime() {
        return runtime.vmRuntime;
    }

    public static TornadoVMConfigAccess getVMConfig() {
        return runtime.vmConfig;
    }

    public static OptionValues getOptions() {
        return options;
    }

    private void initOptions() {
        EconomicMap<OptionKey<?>, Object> opts = OptionValues.newOptionMap();
        opts.putAll(HotSpotGraalOptionValues.defaultOptions().getMap());

        opts.put(GraalOptions.OmitHotExceptionStacktrace, false);

        opts.put(GraalOptions.MatchExpressions, true);
        opts.put(GraalOptions.RemoveNeverExecutedCode, false);
        opts.put(ConstantLoadOptimization.Options.LIROptConstantLoadOptimization, false);
        opts.put(PostAllocationOptimizationStage.Options.LIROptRedundantMoveElimination, false);
        opts.put(GraalOptions.OptConvertDeoptsToGuards, true);

        options = new OptionValues(opts);
    }

    private TornadoAcceleratorBackend[] loadBackends() {
        ServiceLoader<TornadoBackendProvider> loader = ServiceLoader.load(TornadoBackendProvider.class);
        for (TornadoBackendProvider p : loader) {
            System.out.println("provider = " + p.getClass());
        }
        List<TornadoBackendProvider> providerList = StreamSupport.stream(loader.spliterator(), false).sorted().toList();
        TornadoAcceleratorBackend[] tornadoAcceleratorBackends = new TornadoAcceleratorBackend[TornadoBackends.values().length];
        int index = 0;
        System.out.println("providerList size = " + providerList.size());
        for (TornadoBackendProvider provider : providerList) {
            if (TornadoOptions.FULL_DEBUG) {
                System.out.println("[INFO] TornadoVM Loading Backend: " + provider.getName());
            }
            TornadoAcceleratorBackend backend = provider.createBackend(options, vmRuntime, vmConfig);
            if (backend != null) {
                tornadoAcceleratorBackends[index] = backend;
                index++;
            }
        }
        backendCount = index;
        return tornadoAcceleratorBackends;
    }

    @Override
    public <D extends TornadoBackend> int getBackendIndex(Class<D> backendClass) {
        for (int backendIndex = 0; backendIndex < tornadoVMBackends.length; backendIndex++) {
            if (tornadoVMBackends[backendIndex] != null && tornadoVMBackends[backendIndex].getClass() == backendClass) {
                return backendIndex;
            }
        }
        throw shouldNotReachHere("Could not find index for backend: " + backendClass);
    }

    @Override
    public boolean isProfilerEnabled() {
        return TornadoOptions.PROFILER_LOGS_ACCUMULATE() && TornadoOptions.isProfilerEnabled();
    }

    @Override
    public boolean isPowerMonitoringEnabled() {
        return TornadoOptions.isUpsReaderEnabled();
    }

    @Override
    public long getPowerMetric() {
        return (UpsMeterReader.getOutputPowerMetric() != null) ? Long.parseLong(UpsMeterReader.getOutputPowerMetric()) : -1;
    }

    public MetaAccessProvider getMetaAccess() {
        return vmBackend.getMetaAccess();
    }

    public ResolvedJavaMethod resolveMethod(final Method method) {
        return getMetaAccess().lookupJavaMethod(method);
    }

    @Override
    public TornadoAcceleratorBackend getBackend(int index) {
        if (index > tornadoVMBackends.length) {
            throw new TornadoBackendNotFound("Tornado Backend Not Found");
        }
        return tornadoVMBackends[index];
    }

    @Override
    public void setDefaultBackend(int index) {
        TornadoAcceleratorBackend tmp = tornadoVMBackends[0];
        tornadoVMBackends[0] = tornadoVMBackends[index];
        tornadoVMBackends[index] = tmp;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <D extends TornadoBackend> D getBackend(Class<D> type) {
        for (TornadoAcceleratorBackend backend : tornadoVMBackends) {
            if (backend.getClass() == type) {
                return (D) backend;
            }
        }
        return null;
    }

    @Override
    public TornadoVMBackendType getBackendType(int index) {
        return tornadoVMBackends[index].getBackendType();
    }

    @Override
    public int getNumBackends() {
        return backendCount;
    }

    @Override
    public TornadoXPUDevice getDefaultDevice() {
        return (tornadoVMBackends == null || tornadoVMBackends[DEFAULT_BACKEND] == null) ? JVM : (TornadoXPUDevice) tornadoVMBackends[DEFAULT_BACKEND].getDefaultDevice();
    }

}
