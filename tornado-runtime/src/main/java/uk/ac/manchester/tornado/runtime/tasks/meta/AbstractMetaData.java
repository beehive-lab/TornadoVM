/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, 2023-2024, APT Group, Department of Computer Science,
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
import uk.ac.manchester.tornado.runtime.TornadoAcceleratorBackend;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;

public abstract class AbstractMetaData implements TaskMetaDataInterface {

    private static final long[] SEQUENTIAL_GLOBAL_WORK_GROUP = { 1, 1, 1 };
    private static final String TRUE = "True";
    private static final String FALSE = "False";
    private final boolean isDeviceDefined;

    private final HashSet<String> openCLBuiltOptions = new HashSet<>(Arrays.asList(//
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

    private boolean threadInfoEnabled;
    private final boolean debugMode;
    private final boolean dumpEvents;
    private final boolean dumpProfiles;
    private final boolean isOpenclGpuBlockXDefined;
    private final int openclGpuBlockX;
    private final boolean isOpenclGpuBlock2DXDefined;
    private final int openclGpuBlock2DX;
    private final boolean isOpenclGpuBlock2DYDefined;
    private final int openclGpuBlock2DY;
    private final boolean enableParallelization;
    private final boolean useThreadCoarsener;
    private final boolean dumpTaskGraph;
    private final boolean isEnableParallelizationDefined;
    private final boolean isCpuConfigDefined;
    private final String cpuConfig;
    private String id;
    private TornadoXPUDevice device;
    private int backendIndex;
    private int deviceIndex;
    private boolean deviceManuallySet;
    private long numThreads;
    private TornadoProfiler profiler;
    private GridScheduler gridScheduler;
    private long[] ptxBlockDim;
    private long[] ptxGridDim;
    private ResolvedJavaMethod graph;
    private boolean useGridScheduler;
    private boolean isOpenclCompilerFlagsDefined;
    private String openclCompilerOptions;

    private boolean openclUseDriverScheduling;
    private boolean printKernel;
    private boolean resetThreads;

    AbstractMetaData(String id, AbstractMetaData parent) {
        this.id = id;

        isDeviceDefined = getProperty(id + ".device") != null;
        if (isDeviceDefined) {
            int[] a = MetaDataUtils.resolveDriverDeviceIndexes(getProperty(id + ".device"));
            backendIndex = a[0];
            deviceIndex = a[1];
        } else if (null != parent) {
            backendIndex = parent.getBackendIndex();
            deviceIndex = parent.getDeviceIndex();
        } else {
            backendIndex = TornadoOptions.DEFAULT_BACKEND_INDEX;
            deviceIndex = TornadoOptions.DEFAULT_DEVICE_INDEX;
        }

        enableParallelization = parseBoolean(getDefault("parallelise", id, TRUE));
        isEnableParallelizationDefined = getProperty(id + ".parallelise") != null;

        threadInfoEnabled = TornadoOptions.THREAD_INFO;
        printKernel = TornadoOptions.PRINT_KERNEL_SOURCE;
        debugMode = parseBoolean(getDefault("debug", id, FALSE));
        dumpEvents = parseBoolean(getDefault("events.dump", id, TRUE));
        dumpProfiles = parseBoolean(getDefault("profiles.print", id, FALSE));
        dumpTaskGraph = Boolean.parseBoolean(System.getProperty("dump.taskgraph", FALSE));

        // Compilation flags - > only for OpenCL
        openclCompilerOptions = (getProperty("tornado.opencl.compiler.options") == null) ? "-w" : getProperty("tornado.opencl.compiler.options");
        isOpenclCompilerFlagsDefined = getProperty("tornado.opencl.compiler.options") != null;

        // Thread Configurations
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

    public TornadoXPUDevice getLogicDevice() {
        return device != null ? device : (device = resolveDevice(Tornado.getProperty(id + ".device", backendIndex + ":" + deviceIndex)));
    }

    private int getDeviceIndex(int driverIndex, TornadoDevice device) {
        TornadoAcceleratorBackend driver = TornadoCoreRuntime.getTornadoRuntime().getBackend(driverIndex);
        int devs = driver.getNumDevices();
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
     *     {@link TornadoDevice}
     */
    public void setDevice(TornadoDevice device) {
        this.backendIndex = device.getDriverIndex();
        this.deviceIndex = getDeviceIndex(backendIndex, device);
        if (device instanceof TornadoXPUDevice tornadoAcceleratorDevice) {
            this.device = tornadoAcceleratorDevice;
        }
        deviceManuallySet = true;
    }

    @Override
    public int getBackendIndex() {
        return backendIndex;
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
        return threadInfoEnabled;
    }

    public boolean isDebug() {
        return debugMode;
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

    public boolean shouldUseOpenCLDriverScheduling() {
        return openclUseDriverScheduling;
    }

    public boolean enableParallelization() {
        return enableParallelization;
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

    @Override
    public List<TornadoEvents> getProfiles(long executionPlanId) {
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

    public void enableThreadInfo() {
        this.threadInfoEnabled = true;
    }

    public void disableThreadInfo() {
        this.threadInfoEnabled = false;
    }

    @Override
    public boolean isPrintKernelEnabled() {
        return printKernel;
    }

    @Override
    public void setPrintKernelFlag(boolean printKernelEnabled) {
        this.printKernel = printKernelEnabled;
    }

    public void enablePrintKernel() {
        this.printKernel = true;
    }

    public void disablePrintKernel() {
        this.printKernel = false;
    }

    public void setThreadInfoEnabled(boolean threadInfoEnabled) {
        this.threadInfoEnabled = threadInfoEnabled;
    }

    public void resetThreadBlocks() {
        this.resetThreads = true;
    }

    public boolean shouldResetThreadsBlock() {
        return this.resetThreads;
    }

    public void disableResetThreadBlock() {
        this.resetThreads = false;
    }
}
