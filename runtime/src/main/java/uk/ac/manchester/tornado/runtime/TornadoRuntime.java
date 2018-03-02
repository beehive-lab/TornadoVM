/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
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

import java.lang.reflect.Method;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.runtime.JVMCI;
import jdk.vm.ci.runtime.JVMCIBackend;

import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.hotspot.HotSpotGraalOptionValues;
import org.graalvm.compiler.lir.constopt.ConstantLoadOptimization;
import org.graalvm.compiler.lir.phases.PostAllocationOptimizationStage;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.util.EconomicMap;

import uk.ac.manchester.tornado.common.TornadoDevice;
import uk.ac.manchester.tornado.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.api.GlobalObjectState;

import static org.graalvm.compiler.debug.GraalError.guarantee;
import static uk.ac.manchester.tornado.common.Tornado.SHOULD_LOAD_RMI;
import static uk.ac.manchester.tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;

public class TornadoRuntime extends TornadoLogger {

    private static final Executor EXECUTOR = Executors.newCachedThreadPool();

    private static final TornadoRuntime runtime = new TornadoRuntime();

    private static final JVMMapping JVM = new JVMMapping();

    public static TornadoRuntime getTornadoRuntime() {
        return runtime;
    }

    public static Executor getTornadoExecutor() {
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

    private final Map<Object, GlobalObjectState> objectMappings;
    private TornadoDriver[] drivers;
    private int driverCount;
    private final JVMCIBackend vmBackend;
    private final HotSpotJVMCIRuntime vmRuntime;
    private final TornadoVMConfig vmConfig;
    private final int defaultDriver = 0;
    private final OptionValues options;

    public TornadoRuntime() {
        objectMappings = new WeakHashMap<>();

        EconomicMap<OptionKey<?>, Object> opts = OptionValues.newOptionMap();
        opts.putAll(HotSpotGraalOptionValues.HOTSPOT_OPTIONS.getMap());

        opts.put(GraalOptions.OmitHotExceptionStacktrace, false);

        opts.put(GraalOptions.MatchExpressions, true);
        opts.put(GraalOptions.RemoveNeverExecutedCode, false);
        opts.put(ConstantLoadOptimization.Options.LIROptConstantLoadOptimization, false);
        opts.put(PostAllocationOptimizationStage.Options.LIROptRedundantMoveElimination, false);
//        opts.put(BytecodeParserOptions.TraceParserPlugins, true);
//        opts.put(BytecodeParserOptions.InlineDuringParsing, false);

        options = new OptionValues(opts);
        guarantee(GraalOptions.OmitHotExceptionStacktrace.getValue(options) == false, "error");

        if (!(JVMCI.getRuntime() instanceof HotSpotJVMCIRuntime)) {
            shouldNotReachHere("Unsupported JVMCIRuntime: ", JVMCI.getRuntime().getClass().getName());
        }
        vmRuntime = (HotSpotJVMCIRuntime) JVMCI.getRuntime();

        vmBackend = vmRuntime.getHostJVMCIBackend();
        vmConfig = new TornadoVMConfig(vmRuntime.getConfigStore());

        drivers = loadDrivers();
    }

    public void clearObjectState() {
        for (GlobalObjectState gs : objectMappings.values()) {
            gs.clear();
        }
        objectMappings.clear();
    }

    private TornadoDriver[] loadDrivers() {
        ServiceLoader<TornadoDriverProvider> loader = ServiceLoader.load(TornadoDriverProvider.class);
        drivers = new TornadoDriver[2];
        int index = 0;
        for (TornadoDriverProvider provider : loader) {
            boolean isRMI = provider.getName().equalsIgnoreCase("RMI Driver");
            if ((!isRMI) || (isRMI && SHOULD_LOAD_RMI)) {
                drivers[index] = provider.createDriver(options, vmRuntime, vmConfig);
                if (drivers[index] != null) {
                    index++;
                }
            }
        }

        driverCount = index;

        return drivers;
    }

    public OptionValues getOptions() {
        return options;
    }

    public GlobalObjectState resolveObject(Object object) {
        if (!objectMappings.containsKey(object)) {
            final GlobalObjectState state = new GlobalObjectState();
            objectMappings.put(object, state);
        }
        return objectMappings.get(object);
    }

    public MetaAccessProvider getMetaAccess() {
        return vmBackend.getMetaAccess();
    }

    public ResolvedJavaMethod resolveMethod(final Method method) {
        return getMetaAccess().lookupJavaMethod(method);
    }

    public TornadoDriver getDriver(int index) {
        return drivers[index];
    }

    public <D extends TornadoDriver> D getDriver(Class<D> type) {
        for (TornadoDriver driver : drivers) {
            if (driver.getClass() == type) {
                return (D) driver;
            }
        }
        return null;
    }

    public int getNumDrivers() {
        return driverCount;
    }

    public TornadoDevice getDefaultDevice() {
        return (drivers == null || drivers[defaultDriver] == null) ? JVM : drivers[defaultDriver].getDefaultDevice();
    }
}
