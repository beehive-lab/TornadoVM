/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2021, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.runtime;

import static org.graalvm.compiler.debug.GraalError.guarantee;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.runtime.common.Tornado.SHOULD_LOAD_RMI;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Collectors;
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
import uk.ac.manchester.tornado.api.TornadoDriver;
import uk.ac.manchester.tornado.api.TornadoRuntimeInterface;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.common.TornadoAcceleratorDevice;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.common.enums.TornadoDrivers;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSnippetReflectionProvider;
import uk.ac.manchester.tornado.runtime.tasks.GlobalObjectState;

public class TornadoCoreRuntime extends TornadoLogger implements TornadoRuntimeInterface {

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
    private static DebugContext debugContext = null;
    private static OptionValues options;
    private final Map<Object, GlobalObjectState> objectMappings;
    private final JVMCIBackend vmBackend;
    private final HotSpotJVMCIRuntime vmRuntime;
    private final TornadoVMConfig vmConfig;
    private TornadoAcceleratorDriver[] tornadoVMDrivers;
    private int driverCount;

    private TornadoCoreRuntime() {
        objectMappings = new WeakHashMap<>();

        initOptions();
        guarantee(!GraalOptions.OmitHotExceptionStacktrace.getValue(options), "error");

        if (!(JVMCI.getRuntime() instanceof HotSpotJVMCIRuntime)) {
            shouldNotReachHere("Unsupported JVMCIRuntime: ", JVMCI.getRuntime().getClass().getName());
        }
        vmRuntime = (HotSpotJVMCIRuntime) JVMCI.getRuntime();
        vmBackend = vmRuntime.getHostJVMCIBackend();
        vmConfig = new TornadoVMConfig(vmRuntime.getConfigStore(), vmBackend.getMetaAccess());
        tornadoVMDrivers = loadDrivers();
    }

    public static TornadoCoreRuntime getTornadoRuntime() {
        return runtime;
    }

    public static DebugContext getDebugContext() {
        if (debugContext == null) {
            debugContext = new DebugContext.Builder(getOptions(), new GraalDebugHandlersFactory(new TornadoSnippetReflectionProvider())).build();
        }
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

    public static TornadoVMConfig getVMConfig() {
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

    public void clearObjectState() {
        for (GlobalObjectState gs : objectMappings.values()) {
            gs.clear();
        }
        objectMappings.clear();
    }

    private TornadoAcceleratorDriver[] loadDrivers() {
        ServiceLoader<TornadoDriverProvider> loader = ServiceLoader.load(TornadoDriverProvider.class);
        List<TornadoDriverProvider> providerList = StreamSupport.stream(loader.spliterator(), false).sorted().collect(Collectors.toList());
        TornadoAcceleratorDriver[] tornadoAcceleratorDrivers = new TornadoAcceleratorDriver[TornadoDrivers.values().length];
        int index = 0;
        for (TornadoDriverProvider provider : providerList) {
            if (Tornado.FULL_DEBUG) {
                System.out.println("Loading DRIVER: " + provider);
            }
            boolean isRMI = provider.getName().equalsIgnoreCase("RMI Driver");
            if ((!isRMI) || (isRMI && SHOULD_LOAD_RMI)) {
                TornadoAcceleratorDriver driver = provider.createDriver(options, vmRuntime, vmConfig);
                if (driver != null) {
                    tornadoAcceleratorDrivers[index] = driver;
                    index++;
                }
            }
        }
        driverCount = index;
        return tornadoAcceleratorDrivers;
    }

    public GlobalObjectState resolveObject(Object object) {
        if (!objectMappings.containsKey(object)) {
            final GlobalObjectState state = new GlobalObjectState();
            objectMappings.put(object, state);
        }
        return objectMappings.get(object);
    }

    @Override
    public <D extends TornadoDriver> int getDriverIndex(Class<D> driverClass) {
        for (int driverIndex = 0; driverIndex < tornadoVMDrivers.length; driverIndex++) {
            if (tornadoVMDrivers[driverIndex] != null && tornadoVMDrivers[driverIndex].getClass() == driverClass) {
                return driverIndex;
            }
        }
        throw shouldNotReachHere("Could not find index for driver: " + driverClass);
    }

    @Override
    public boolean isProfilerEnabled() {
        return TornadoOptions.PROFILER_LOGS_ACCUMULATE() && TornadoOptions.isProfilerEnabled();
    }

    public MetaAccessProvider getMetaAccess() {
        return vmBackend.getMetaAccess();
    }

    public ResolvedJavaMethod resolveMethod(final Method method) {
        return getMetaAccess().lookupJavaMethod(method);
    }

    @Override
    public TornadoAcceleratorDriver getDriver(int index) {
        return tornadoVMDrivers[index];
    }

    @Override
    public void setDefaultDriver(int index) {
        TornadoAcceleratorDriver tmp = tornadoVMDrivers[0];
        tornadoVMDrivers[0] = tornadoVMDrivers[index];
        tornadoVMDrivers[index] = tmp;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <D extends TornadoDriver> D getDriver(Class<D> type) {
        for (TornadoAcceleratorDriver driver : tornadoVMDrivers) {
            if (driver.getClass() == type) {
                return (D) driver;
            }
        }
        return null;
    }

    @Override
    public TornadoVMBackendType getBackendType(int index) {
        return tornadoVMDrivers[index].getBackendType();
    }

    @Override
    public int getNumDrivers() {
        return driverCount;
    }

    @Override
    public TornadoAcceleratorDevice getDefaultDevice() {
        return (tornadoVMDrivers == null || tornadoVMDrivers[DEFAULT_DRIVER] == null) ? JVM : (TornadoAcceleratorDevice) tornadoVMDrivers[DEFAULT_DRIVER].getDefaultDevice();
    }

}
