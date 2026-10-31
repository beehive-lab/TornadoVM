/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, 2023-2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.runtime.tasks.meta;

import static uk.ac.manchester.tornado.runtime.tasks.meta.MetaDataUtils.resolveDevice;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.common.TornadoEvents;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.options.DebugFlags;
import uk.ac.manchester.tornado.api.options.MetalFlags;
import uk.ac.manchester.tornado.api.options.OpenCLFlags;
import uk.ac.manchester.tornado.api.options.RuntimeFlags;
import uk.ac.manchester.tornado.api.options.TaskFlags;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;
import uk.ac.manchester.tornado.api.runtime.TaskContextInterface;
import uk.ac.manchester.tornado.runtime.TornadoAcceleratorBackend;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.CompilerFlags;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.tasks.meta.MetaDataUtils.BackendSelectionContainer;

/**
 * Abstract Runtime (RT) Context: Class that maintains fields for deployment and compilation for the
 * supported drivers (e.g., device indexes, drivers, thread-block etc.)
 *
 * <p>
 * This class bridges the external (user requirements) from the internals (runtime and compiler parameters).
 * </p>
 */
public abstract class AbstractRTContext implements TaskContextInterface {

    private static final long[] SEQUENTIAL_GLOBAL_WORK_GROUP = { 1, 1, 1 };

    private final String id;
    private final boolean isDeviceDefined;
    private TornadoXPUDevice device;
    protected int backendIndex;
    protected int deviceIndex;
    private boolean deviceManuallySet;

    private boolean threadInfoEnabled;
    private boolean printKernel;

    /**
     * Whether this task must round floating-point arithmetic the way the host does.
     *
     * <p>
     * Not read from a system property, deliberately. The whole point of the switch is that it is a
     * property of one execution plan rather than of the JVM: a caller whose query needs the device
     * and the host to agree can ask for it, and a caller who does not care keeps the faster
     * arithmetic. A JVM-wide option cannot express that, and one read into a {@code static final}
     * cannot even be verified to have taken effect.
     */
    private boolean strictFloatingPoint;
    private boolean dynamicShapes;
    private boolean resetThreads;

    private final boolean isOpenclGpuBlockXDefined;
    private final int openclGpuBlockX;
    private final boolean isOpenclGpuBlock2DXDefined;
    private final int openclGpuBlock2DX;
    private final boolean isOpenclGpuBlock2DYDefined;
    private final int openclGpuBlock2DY;

    private long numThreads;

    private GridScheduler gridScheduler;

    private TornadoProfiler profiler;

    private Object graph;
    private boolean useGridScheduler;
    private final ConcurrentHashMap<TornadoVMBackendType, String> compilerOptionsPerBackend;

    private boolean openclUseDriverScheduling;
    private boolean metalUseDriverScheduling;

    AbstractRTContext(String id, AbstractRTContext parent) {
        this.id = id;

        Optional<String> device = TaskFlags.DEVICE.lookup(id);
        isDeviceDefined = device.isPresent();
        if (isDeviceDefined) {
            BackendSelectionContainer backendSelection = MetaDataUtils.resolveDriverDeviceIndexes(device.get());
            backendIndex = backendSelection.backendIndex();
            deviceIndex = backendSelection.deviceIndex();
        } else if (null != parent) {
            backendIndex = parent.getBackendIndex();
            deviceIndex = parent.getDeviceIndex();
        } else {
            backendIndex = RuntimeFlags.BACKEND_INDEX.get();
            deviceIndex = RuntimeFlags.DEVICE_INDEX.get();
        }

        threadInfoEnabled = DebugFlags.THREAD_INFO.get();
        printKernel = DebugFlags.PRINT_KERNEL.get();

        compilerOptionsPerBackend = new ConcurrentHashMap<>();
        compilerOptionsPerBackend.put(TornadoVMBackendType.METAL, MetalFlags.COMPILER_FLAGS.get());
        compilerOptionsPerBackend.put(TornadoVMBackendType.OPENCL, OpenCLFlags.COMPILER_FLAGS.get());
        compilerOptionsPerBackend.put(TornadoVMBackendType.CUDA, CompilerFlags.cudaDefaults());

        // Thread Configurations
        openclGpuBlockX = TaskFlags.OPENCL_GPU_BLOCK_X.forTask(id);
        isOpenclGpuBlockXDefined = TaskFlags.OPENCL_GPU_BLOCK_X.isSetFor(id);

        openclGpuBlock2DX = TaskFlags.OPENCL_GPU_BLOCK_2D_X.forTask(id);
        isOpenclGpuBlock2DXDefined = TaskFlags.OPENCL_GPU_BLOCK_2D_X.isSetFor(id);

        openclGpuBlock2DY = TaskFlags.OPENCL_GPU_BLOCK_2D_Y.forTask(id);
        isOpenclGpuBlock2DYDefined = TaskFlags.OPENCL_GPU_BLOCK_2D_Y.isSetFor(id);
    }

    /** Whether {@link #getXPUDevice()} has resolved the device, which creates its backend. */
    public boolean isXPUDeviceResolved() {
        return device != null;
    }

    public TornadoXPUDevice getXPUDevice() {
        if (device == null) {
            device = resolveDevice(TaskFlags.DEVICE.lookup(id).orElse(backendIndex + ":" + deviceIndex));
        }
        return device;
    }

    private boolean hasSamePlatformName(TornadoAcceleratorBackend backend, TornadoDevice device, int index) {
        return backend.getDevice(index).getPlatformName().equals(device.getPlatformName());
    }

    private boolean hasSameDeviceName(TornadoAcceleratorBackend backend, TornadoDevice device, int index) {
        return backend.getDevice(index).getPhysicalDevice().getDeviceName().equals(device.getPhysicalDevice().getDeviceName());
    }

    private int getDeviceIndex(int driverIndex, TornadoDevice device) {
        TornadoAcceleratorBackend backend = TornadoCoreRuntime.getTornadoRuntime().getBackend(driverIndex);
        int totalNumDevices = backend.getNumDevices();
        // Identical GPUs share platform and device names: match the device itself first.
        for (int deviceIndex = 0; deviceIndex < totalNumDevices; deviceIndex++) {
            if (backend.getDevice(deviceIndex).equals(device)) {
                return deviceIndex;
            }
        }
        for (int deviceIndex = 0; deviceIndex < totalNumDevices; deviceIndex++) {
            if (hasSamePlatformName(backend, device, deviceIndex) && hasSameDeviceName(backend, device, deviceIndex)) {
                return deviceIndex;
            }
        }
        throw new TornadoRuntimeException("[ERROR] Device not found: " + device.getPhysicalDevice().getDeviceName());
    }

    boolean isDeviceManuallySet() {
        return deviceManuallySet;
    }

    /**
     * Select a device for the next execution.
     *
     * @param device
     *     {@link TornadoDevice}
     */
    public void setDevice(TornadoDevice device) {
        this.backendIndex = device.getBackendIndex();
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

    public String getId() {
        return id;
    }

    public boolean isThreadInfoEnabled() {
        return threadInfoEnabled;
    }

    public boolean isDebug() {
        return DebugFlags.DEBUG.get();
    }

    @Override
    public boolean isStrictFloatingPoint() {
        return strictFloatingPoint;
    }

    @Override
    public void setStrictFloatingPoint(boolean strict) {
        this.strictFloatingPoint = strict;
    }

    @Override
    public boolean isDynamicShapes() {
        return dynamicShapes;
    }

    @Override
    public void setDynamicShapes(boolean dynamicShapes) {
        this.dynamicShapes = dynamicShapes;
    }

    public String getCompilerFlags(TornadoVMBackendType backendType) {
        return compilerOptionsPerBackend.get(backendType);
    }

    @Override
    public void setCompilerFlags(TornadoVMBackendType backendType, String compilerFlags) {
        compilerOptionsPerBackend.put(backendType, compilerFlags);
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

    public boolean shouldUseMetalDriverScheduling() {
        return metalUseDriverScheduling;
    }

    public boolean isDeviceDefined() {
        return isDeviceDefined;
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

    @Override
    public void setCompiledGraph(Object graph) {
        this.graph = graph;
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
