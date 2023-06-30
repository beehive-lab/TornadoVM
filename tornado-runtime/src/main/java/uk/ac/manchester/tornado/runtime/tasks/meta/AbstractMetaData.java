/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020,2023 APT Group, Department of Computer Science,
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
 *
 */
package uk.ac.manchester.tornado.runtime.tasks.meta;

import static java.lang.Boolean.parseBoolean;
import static java.lang.Integer.parseInt;
import static uk.ac.manchester.tornado.runtime.tasks.meta.MetaDataUtils.resolveDevice;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.common.TornadoEvents;
import uk.ac.manchester.tornado.api.memory.TaskMetaDataInterface;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;
import uk.ac.manchester.tornado.runtime.TornadoAcceleratorDriver;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.common.TornadoAcceleratorDevice;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

public abstract class AbstractMetaData implements TaskMetaDataInterface {

    private static final long[] SEQUENTIAL_GLOBAL_WORK_GROUP = { 1, 1, 1 };
    private static final String TRUE = "True";
    private static final String FALSE = "False";
    private final boolean isDeviceDefined;
    private final HashSet<String> openCLBuiltOptions = new HashSet<>(Arrays.asList( //
            "-cl-single-precision-constant", //
            "-cl-denorms-are-zero", //
            "-cl-opt-disable", //
            "-cl-strict-aliasing", //
            "-cl-mad-enable", //
            "-cl-no-signed-zeros", //
            "-cl-unsafe-math-optimizations", //
            "-cl-finite-math-only", //
            "-cl-fast-relaxed-math", //
            "-w", //
            "-cl-std=CL2.0" //
    ));
    /*
     * Forces the executing kernel to output its arguments before execution
     */
    private final boolean threadInfo;
    private final boolean debug;
    private final boolean dumpEvents;
    private final boolean dumpProfiles;
    private final boolean debugKernelArgs;
    private final boolean printCompileTimes;
    private final boolean isOpenclGpuBlockXDefined;
    private final int openclGpuBlockX;
    private final boolean isOpenclGpuBlock2DXDefined;
    private final int openclGpuBlock2DX;
    private final boolean isOpenclGpuBlock2DYDefined;
    private final int openclGpuBlock2DY;
    private final boolean openclUseRelativeAddresses;
    private final boolean openclEnableBifs;
    private final boolean openclWaitActive;
    private final boolean vmWaitEvent;
    private final boolean enableExceptions;
    private final boolean enableProfiling;
    private final boolean enableOooExecution;
    private final boolean openclUseBlockingApiCalls;
    private final boolean enableParallelization;
    private final boolean enableVectors;
    private final boolean enableMemChecks;
    private final boolean useThreadCoarsener;
    private final boolean dumpTaskGraph;
    private final boolean coarsenWithCpuConfig;
    private final boolean isEnableParallelizationDefined;
    private final boolean isCpuConfigDefined;
    private final String cpuConfig;
    private String id;
    private TornadoAcceleratorDevice device;
    private int driverIndex;
    private int deviceIndex;
    private boolean deviceManuallySet;
    private long numThreads;
    private TornadoProfiler profiler;
    private GridScheduler gridScheduler;
    private long[] ptxBlockDim;
    private long[] ptxGridDim;
    private ResolvedJavaMethod graph;
    private boolean useGridScheduler;
    private boolean printKernelExecutionTime;
    private boolean isOpenclCompilerFlagsDefined;
    private String openclCompilerOptions;
    /*
     * Allows the OpenCL driver to select the size of local work groups
     */
    private boolean openclUseDriverScheduling;

    AbstractMetaData(String id, AbstractMetaData parent) {
        this.id = id;

        isDeviceDefined = getProperty(id + ".device") != null;
        if (isDeviceDefined) {
            int[] a = MetaDataUtils.resolveDriverDeviceIndexes(getProperty(id + ".device"));
            driverIndex = a[0];
            deviceIndex = a[1];
        } else if (null != parent) {
            driverIndex = parent.getDriverIndex();
            deviceIndex = parent.getDeviceIndex();
        } else {
            driverIndex = TornadoOptions.DEFAULT_DRIVER_INDEX;
            deviceIndex = TornadoOptions.DEFAULT_DEVICE_INDEX;
        }

        debugKernelArgs = parseBoolean(getDefault("debug.kernelargs", id, TRUE));
        printCompileTimes = parseBoolean(getDefault("debug.compiletimes", id, FALSE));
        printKernelExecutionTime = parseBoolean(getProperty("tornado.debug.executionTime"));
        openclUseRelativeAddresses = parseBoolean(getDefault("opencl.userelative", id, FALSE));
        openclWaitActive = parseBoolean(getDefault("opencl.wait.active", id, FALSE));
        coarsenWithCpuConfig = parseBoolean(getDefault("coarsener.ascpu", id, FALSE));

        /*
         * Allows the OpenCL driver to select the size of local work groups
         */
        openclUseDriverScheduling = parseBoolean(getDefault("opencl.usedriver.schedule", id, FALSE));
        vmWaitEvent = parseBoolean(getDefault("vm.waitevent", id, FALSE));
        enableExceptions = parseBoolean(getDefault("exceptions.enable", id, FALSE));
        enableProfiling = parseBoolean(getDefault("profiling.enable", id, FALSE));
        enableOooExecution = parseBoolean(getDefault("ooo-execution.enable", id, FALSE));
        openclUseBlockingApiCalls = parseBoolean(getDefault("opencl.blocking", id, FALSE));

        enableParallelization = parseBoolean(getDefault("parallelise", id, TRUE));
        isEnableParallelizationDefined = getProperty(id + ".parallelise") != null;

        enableVectors = parseBoolean(getDefault("vectors.enable", id, TRUE));
        openclEnableBifs = parseBoolean(getDefault("bifs.enable", id, FALSE));
        threadInfo = parseBoolean(getDefault("threadInfo", id, FALSE));
        debug = parseBoolean(getDefault("debug", id, FALSE));
        enableMemChecks = parseBoolean(getDefault("memory.check", id, FALSE));
        dumpEvents = parseBoolean(getDefault("events.dump", id, TRUE));
        dumpProfiles = parseBoolean(getDefault("profiles.print", id, FALSE));
        dumpTaskGraph = Boolean.parseBoolean(System.getProperty("dump.taskgraph", FALSE));

        openclCompilerOptions = (getProperty("tornado.opencl.compiler.options") == null) ? "-w" : getProperty("tornado.opencl.compiler.options");
        isOpenclCompilerFlagsDefined = getProperty("tornado.opencl.compiler.options") != null;

        openclGpuBlockX = parseInt(getDefault("opencl.gpu.block.x", id, "256"));
        isOpenclGpuBlockXDefined = getProperty(id + ".opencl.gpu.block.x") != null;

        openclGpuBlock2DX = parseInt(getDefault("opencl.gpu.block2d.x", id, "4"));
        isOpenclGpuBlock2DXDefined = getProperty(id + ".opencl.gpu.block2d.x") != null;

        openclGpuBlock2DY = parseInt(getDefault("opencl.gpu.block2d.y", id, "4"));
        isOpenclGpuBlock2DYDefined = getProperty(id + ".opencl.gpu.block2d.y") != null;

        cpuConfig = getDefault("cpu.config", id, null);
        isCpuConfigDefined = getProperty(id + ".cpu.config") != null;
        useThreadCoarsener = Boolean.parseBoolean(getDefault("coarsener", id, FALSE));
    }

    private static String getProperty(String key) {
        return System.getProperty(key);
    }

    protected static String getDefault(String keySuffix, String id, String defaultValue) {
        String propertyValue = getProperty(id + "." + keySuffix);
        return (propertyValue != null) ? propertyValue : Tornado.getProperty("tornado" + "." + keySuffix, defaultValue);
    }

    public TornadoAcceleratorDevice getLogicDevice() {
        return device != null ? device : (device = resolveDevice(Tornado.getProperty(id + ".device", driverIndex + ":" + deviceIndex)));
    }

    private int getDeviceIndex(int driverIndex, TornadoDevice device) {
        TornadoAcceleratorDriver driver = TornadoCoreRuntime.getTornadoRuntime().getDriver(driverIndex);
        int devs = driver.getDeviceCount();
        int index = 0;
        for (int i = 0; i < devs; i++) {
            if (driver.getDevice(i).getPlatformName().equals(device.getPlatformName()) && (driver.getDevice(i).getDeviceName().equals(device.getDeviceName()))) {
                index = i;
                break;
            }
        }
        return index;
    }

    boolean isDeviceManuallySet() {
        return deviceManuallySet;
    }

    /**
     * Set a device in the default driver.
     *
     * @param device
     *            {@link TornadoDevice}
     */
    public void setDevice(TornadoDevice device) {
        this.driverIndex = device.getDriverIndex();
        this.deviceIndex = getDeviceIndex(driverIndex, device);
        if (device instanceof TornadoAcceleratorDevice) {
            this.device = (TornadoAcceleratorDevice) device;
        }
        deviceManuallySet = true;
    }

    /**
     * Set a device from a specific Tornado driver.
     *
     * @param driverIndex
     *            Driver Index
     * @param device
     *            {@link TornadoAcceleratorDevice}
     */
    public void setDriverDevice(int driverIndex, TornadoAcceleratorDevice device) {
        this.driverIndex = driverIndex;
        this.deviceIndex = getDeviceIndex(driverIndex, device);
        this.device = device;
    }

    @Override
    public int getDriverIndex() {
        return driverIndex;
    }

    @Override
    public int getDeviceIndex() {
        return deviceIndex;
    }

    public String getCpuConfig() {
        return cpuConfig;
    }

    public String getId() {
        return id;
    }

    public boolean isThreadInfoEnabled() {
        return threadInfo;
    }

    public boolean isDebug() {
        return debug;
    }

    public boolean shouldDumpEvents() {
        return dumpEvents;
    }

    public boolean shouldDumpProfiles() {
        return dumpProfiles;
    }

    public boolean shouldDumpTaskGraph() {
        return dumpTaskGraph;
    }

    public boolean shouldDebugKernelArgs() {
        return debugKernelArgs;
    }

    public boolean shouldPrintCompileTimes() {
        return printCompileTimes;
    }

    public boolean shouldPrintKernelExecutionTime() {
        return printKernelExecutionTime;
    }

    public String getCompilerFlags() {
        return composeBuiltOptions(openclCompilerOptions);
    }

    @Override
    public void setCompilerFlags(String value) {
        openclCompilerOptions = value;
        isOpenclCompilerFlagsDefined = true;
    }

    public int getOpenCLGpuBlockX() {
        return openclGpuBlockX;
    }

    public int getOpenCLGpuBlock2DX() {
        return openclGpuBlock2DX;
    }

    public int getOpenCLGpuBlock2DY() {
        return openclGpuBlock2DY;
    }

    public boolean shouldUseOpenCLRelativeAddresses() {
        return openclUseRelativeAddresses;
    }

    public boolean enableOpenCLBifs() {
        return openclEnableBifs;
    }

    public boolean shouldUseOpenCLDriverScheduling() {
        return openclUseDriverScheduling;
    }

    public boolean shouldUseOpenCLWaitActive() {
        return openclWaitActive;
    }

    public boolean shouldUseVmWaitEvent() {
        return vmWaitEvent;
    }

    public boolean enableExceptions() {
        return enableExceptions;
    }

    public boolean enableProfiling() {
        return enableProfiling;
    }

    public boolean enableOooExecution() {
        return enableOooExecution;
    }

    public boolean shouldUseOpenCLBlockingApiCalls() {
        return openclUseBlockingApiCalls;
    }

    public boolean enableParallelization() {
        return enableParallelization;
    }

    public boolean enableVectors() {
        return enableVectors;
    }

    public boolean enableMemChecks() {
        return enableMemChecks;
    }

    public boolean enableThreadCoarsener() {
        return useThreadCoarsener;
    }

    public boolean isDeviceDefined() {
        return isDeviceDefined;
    }

    boolean isEnableParallelizationDefined() {
        return isEnableParallelizationDefined;
    }

    public boolean isOpenclCompilerFlagsDefined() {
        return isOpenclCompilerFlagsDefined;
    }

    public String composeBuiltOptions(String rawFlags) {
        rawFlags = rawFlags.replace(",", " ");
        for (String str : rawFlags.split(" ")) {
            if (!openCLBuiltOptions.contains(str)) {
                rawFlags = " ";
                break;
            }
        }
        return rawFlags;
    }

    public boolean isOpenclGpuBlockXDefined() {
        return isOpenclGpuBlockXDefined;
    }

    public boolean isOpenclGpuBlock2DXDefined() {
        return isOpenclGpuBlock2DXDefined;
    }

    public boolean isOpenclGpuBlock2DYDefined() {
        return isOpenclGpuBlock2DYDefined;
    }

    public boolean isCpuConfigDefined() {
        return isCpuConfigDefined;
    }

    public boolean shouldCoarsenWithCpuConfig() {
        return coarsenWithCpuConfig;
    }

    @Override
    public List<TornadoEvents> getProfiles() {
        return null;
    }

    @Override
    public long[] getGlobalWork() {
        return null;
    }

    @Override
    public void setGlobalWork(long[] global) {

    }

    @Override
    public long[] getLocalWork() {
        return null;
    }

    @Override
    public void setLocalWork(long[] local) {

    }

    @Override
    public long getNumThreads() {
        return numThreads;
    }

    @Override
    public void setNumThreads(long threads) {
        this.numThreads = threads;
    }

    public void attachProfiler(TornadoProfiler profiler) {
        this.profiler = profiler;
    }

    public TornadoProfiler getProfiler() {
        return this.profiler;
    }

    public void enableDefaultThreadScheduler(boolean use) {
        openclUseDriverScheduling = use;
    }

    public void setGridScheduler(GridScheduler gridScheduler) {
        this.gridScheduler = gridScheduler;
    }

    public boolean isWorkerGridAvailable() {
        return (gridScheduler != null && gridScheduler.get(getId()) != null);
    }

    public boolean isGridSequential() {
        return Arrays.equals(getWorkerGrid(getId()).getGlobalWork(), SEQUENTIAL_GLOBAL_WORK_GROUP);
    }

    public WorkerGrid getWorkerGrid(String taskName) {
        return gridScheduler.get(taskName);
    }

    public long[] getPTXBlockDim() {
        return ptxBlockDim;
    }

    public long[] getPTXGridDim() {
        return ptxGridDim;
    }

    public void setPtxBlockDim(long[] blockDim) {
        this.ptxBlockDim = blockDim;
    }

    public void setPtxGridDim(long[] gridDim) {
        this.ptxGridDim = gridDim;
    }

    @Override
    public void setCompiledGraph(Object graph) {
        if (graph instanceof ResolvedJavaMethod) {
            this.graph = (ResolvedJavaMethod) graph;
        }
    }

    @Override
    public Object getCompiledResolvedJavaMethod() {
        return graph;
    }

    public void setUseGridScheduler(boolean use) {
        this.useGridScheduler = use;
    }

    public boolean isGridSchedulerEnabled() {
        return this.useGridScheduler;
    }
}
