/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020-2022, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.opencl.virtual;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.memory.DeviceBufferState;
import uk.ac.manchester.tornado.api.memory.TornadoMemoryProvider;
import uk.ac.manchester.tornado.api.memory.XPUBuffer;
import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;
import uk.ac.manchester.tornado.drivers.opencl.OCLBackendImpl;
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContextInterface;
import uk.ac.manchester.tornado.drivers.opencl.OCLTargetDevice;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLDeviceType;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLProviders;
import uk.ac.manchester.tornado.drivers.opencl.graal.backend.OCLBackend;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompilationResult;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompiler;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.KernelStackFrame;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoInstalledCode;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.common.TornadoSchedulingStrategy;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.common.XPUDeviceBufferState;
import uk.ac.manchester.tornado.runtime.sketcher.Sketch;
import uk.ac.manchester.tornado.runtime.sketcher.TornadoSketcher;
import uk.ac.manchester.tornado.runtime.tasks.CompilableTask;
import uk.ac.manchester.tornado.runtime.tasks.PrebuiltTask;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

public class VirtualOCLTornadoDevice implements TornadoXPUDevice {

    private static OCLBackendImpl driver = null;
    private final OCLTargetDevice device;
    private final int deviceIndex;
    private final int platformIndex;
    private final String platformName;

    public VirtualOCLTornadoDevice(final int platformIndex, final int deviceIndex) {
        this.platformIndex = platformIndex;
        this.deviceIndex = deviceIndex;

        platformName = findDriver().getPlatformContext(platformIndex).getPlatform().getName();
        device = findDriver().getPlatformContext(platformIndex).devices().get(deviceIndex);
    }

    private static OCLBackendImpl findDriver() {
        if (driver == null) {
            driver = TornadoCoreRuntime.getTornadoRuntime().getBackend(OCLBackendImpl.class);
            TornadoInternalError.guarantee(driver != null, "unable to find OpenCL driver");
        }
        return driver;
    }

    @Override
    public void dumpEvents(long executionPlanId) {
    }

    @Override
    public String getDescription() {
        final String availability = (device.isDeviceAvailable()) ? "available" : "not available";
        return String.format("%s %s (%s)", device.getDeviceName(), device.getDeviceType(), availability);
    }

    @Override
    public String getPlatformName() {
        return platformName;
    }

    @Override
    public OCLTargetDevice getPhysicalDevice() {
        return device;
    }

    @Override
    public TornadoMemoryProvider getMemoryProvider() {
        unimplemented();
        return getDeviceContext().getMemoryManager();
    }

    @Override
    public OCLDeviceContextInterface getDeviceContext() {
        return getBackend().getDeviceContext();
    }

    public OCLBackend getBackend() {
        return findDriver().getBackend(platformIndex, deviceIndex);
    }

    @Override
    public void clean() {
        Set<Long> ids = new HashSet<>(device.getDeviceContext().getRegisteredPlanIds());
        if (!ids.isEmpty()) {
            ids.forEach(id -> device.getDeviceContext().reset(id));
            ids.clear();
        }
    }

    @Override
    public String toString() {
        return getPlatformName() + " -- " + device.getDeviceName();
    }

    @Override
    public TornadoSchedulingStrategy getPreferredSchedule() {
        switch (Objects.requireNonNull(device.getDeviceType())) {
            case CL_DEVICE_TYPE_GPU, //
                    CL_DEVICE_TYPE_ACCELERATOR,//
                    CL_DEVICE_TYPE_CUSTOM,//
                    CL_DEVICE_TYPE_ALL -> {//
                return TornadoSchedulingStrategy.PER_ACCELERATOR_ITERATION;
            }
            case CL_DEVICE_TYPE_CPU -> {
                if (TornadoOptions.USE_BLOCK_SCHEDULER) {
                    return TornadoSchedulingStrategy.PER_CPU_BLOCK;
                } else {
                    return TornadoSchedulingStrategy.PER_ACCELERATOR_ITERATION;
                }
            }
            default -> {
                TornadoInternalError.shouldNotReachHere();
                return TornadoSchedulingStrategy.PER_ACCELERATOR_ITERATION;
            }
        }
    }

    @Override
    public void ensureLoaded(long executionPlanId) {
        final OCLBackend backend = getBackend();
        if (!backend.isInitialised()) {
            backend.init();
        }
    }

    @Override
    public KernelStackFrame createKernelStackFrame(long executionPlanId, int numArgs, Access access) {
        return null;
    }

    @Override
    public XPUBuffer createOrReuseAtomicsBuffer(int[] arr, Access access) {
        return null;
    }

    private TornadoInstalledCode compileTask(SchedulableTask task) {
        final CompilableTask executable = (CompilableTask) task;
        final ResolvedJavaMethod resolvedMethod = TornadoCoreRuntime.getTornadoRuntime().resolveMethod(executable.getMethod());
        final Sketch sketch = TornadoSketcher.lookup(resolvedMethod, task.meta().getBackendIndex(), task.meta().getDeviceIndex());

        // copy meta data into task
        final TaskDataContext taskMeta = executable.meta();
        final Access[] sketchAccess = sketch.getArgumentsAccess();
        final Access[] taskAccess = taskMeta.getArgumentsAccess();
        System.arraycopy(sketchAccess, 0, taskAccess, 0, sketchAccess.length);

        try {
            OCLProviders providers = (OCLProviders) getBackend().getProviders();
            TornadoProfiler profiler = task.getProfiler();
            profiler.start(ProfilerType.TASK_COMPILE_GRAAL_TIME, taskMeta.getId());
            final OCLCompilationResult result = OCLCompiler.compileSketchForDevice(sketch, executable, providers, getBackend(), executable.getProfiler());
            profiler.stop(ProfilerType.TASK_COMPILE_GRAAL_TIME, taskMeta.getId());
            profiler.sum(ProfilerType.TOTAL_GRAAL_COMPILE_TIME, profiler.getTaskTimer(ProfilerType.TASK_COMPILE_GRAAL_TIME, taskMeta.getId()));

            if (taskMeta.isPrintKernelEnabled()) {
                RuntimeUtilities.dumpKernel(result.getTargetCode());
            }

            return null;
        } catch (Exception e) {
            TornadoLogger tornadoLogger = new TornadoLogger();
            tornadoLogger.fatal("unable to compile %s for device %s", task.getId(), getDeviceName());
            tornadoLogger.fatal("exception occurred when compiling %s", ((CompilableTask) task).getMethod().getName());
            tornadoLogger.fatal("exception: %s", e.toString());
            throw new TornadoBailoutRuntimeException("[Error During the Task Compilation] ", e);
        }
    }

    private TornadoInstalledCode compilePreBuiltTask(SchedulableTask task) {
        final PrebuiltTask executable = (PrebuiltTask) task;
        final Path path = Paths.get(executable.getFilename());
        TornadoInternalError.guarantee(path.toFile().exists(), "file does not exist: %s", executable.getFilename());
        try {
            final byte[] source = Files.readAllBytes(path);
            if (task.meta().isPrintKernelEnabled()) {
                RuntimeUtilities.dumpKernel(source);
            }
        } catch (IOException e) {
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
        return null;
    }

    private TornadoInstalledCode compileJavaToAccelerator(SchedulableTask task) {
        if (task instanceof CompilableTask) {
            return compileTask(task);
        } else if (task instanceof PrebuiltTask) {
            return compilePreBuiltTask(task);
        }
        TornadoInternalError.shouldNotReachHere("task of unknown type: " + task.getClass().getSimpleName());
        return null;
    }

    @Override
    public boolean isFullJITMode(long executionPlanId, SchedulableTask task) {
        return true;
    }

    @Override
    public TornadoInstalledCode getCodeFromCache(long executionPlanId, SchedulableTask task) {
        return null;
    }

    @Override
    public int[] checkAtomicsForTask(SchedulableTask task) {
        return null;
    }

    @Override
    public int[] checkAtomicsForTask(SchedulableTask task, int[] array, int paramIndex, Object value) {
        return null;
    }

    @Override
    public int[] updateAtomicRegionAndObjectState(SchedulableTask task, int[] array, int paramIndex, Object value, XPUDeviceBufferState objectState) {
        return null;
    }

    @Override
    public int getAtomicsGlobalIndexForTask(SchedulableTask task, int paramIndex) {
        return -1;
    }

    @Override
    public boolean checkAtomicsParametersForTask(SchedulableTask task) {
        return false;
    }

    @Override
    public TornadoInstalledCode installCode(long executionPlanId, SchedulableTask task) {
        return compileJavaToAccelerator(task);
    }

    @Override
    public long allocate(Object object, long batchSize, DeviceBufferState state, Access access) {
        unimplemented();
        return -1;
    }

    @Override
    public synchronized long allocateObjects(Object[] objects, long batchSize, DeviceBufferState[] states, Access[] accesses) {
        unimplemented();
        return -1;
    }

    @Override
    public synchronized long deallocate(DeviceBufferState state) {
        unimplemented();
        return -1;
    }

    @Override
    public List<Integer> ensurePresent(long executionPlanId, Object object, DeviceBufferState state, int[] events, long batchSize, long offset) {
        unimplemented();
        return null;
    }

    @Override
    public List<Integer> streamIn(long executionPlanId, Object object, long batchSize, long offset, DeviceBufferState state, int[] events) {
        unimplemented();
        return null;
    }

    @Override
    public int streamOut(long executionPlanId, Object object, long offset, DeviceBufferState state, int[] events) {
        unimplemented();
        return -1;
    }

    @Override
    public int streamOutBlocking(long executionPlanId, Object object, long hostOffset, DeviceBufferState state, int[] events) {
        unimplemented();
        return -1;
    }

    @Override
    public void flush(long executionPlanId) {
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof VirtualOCLTornadoDevice) {
            final VirtualOCLTornadoDevice other = (VirtualOCLTornadoDevice) obj;
            return (other.deviceIndex == deviceIndex && other.platformIndex == platformIndex);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 89 * hash + this.deviceIndex;
        hash = 89 * hash + this.platformIndex;
        return hash;
    }

    @Override
    public void sync(long executionPlanId) {
    }

    @Override
    public int enqueueBarrier(long executionPlanId) {
        unimplemented();
        return getDeviceContext().enqueueBarrier(executionPlanId);
    }

    @Override
    public int enqueueBarrier(long executionPlanId, int[] events) {
        unimplemented();
        return getDeviceContext().enqueueBarrier(executionPlanId, events);
    }

    @Override
    public int enqueueMarker(long executionPlanId) {
        return getDeviceContext().enqueueMarker(executionPlanId);
    }

    @Override
    public int enqueueMarker(long executionPlanId, int[] events) {
        return getDeviceContext().enqueueMarker(executionPlanId, events);
    }

    @Override
    public Event resolveEvent(long executionPlanId, int event) {
        return getDeviceContext().resolveEvent(executionPlanId, event);
    }

    @Override
    public void flushEvents(long executionPlanId) {
        getDeviceContext().flushEvents(executionPlanId);
    }

    @Override
    public String getDeviceName() {
        return String.format("virtualOpencl-%d-%d", platformIndex, deviceIndex);
    }

    @Override
    public TornadoDeviceType getDeviceType() {
        OCLDeviceType deviceType = device.getDeviceType();
        switch (deviceType) {
            case CL_DEVICE_TYPE_CPU:
                return TornadoDeviceType.CPU;
            case CL_DEVICE_TYPE_GPU:
                return TornadoDeviceType.GPU;
            case CL_DEVICE_TYPE_ACCELERATOR:
                return TornadoDeviceType.ACCELERATOR;
            case CL_DEVICE_TYPE_CUSTOM:
                return TornadoDeviceType.CUSTOM;
            case CL_DEVICE_TYPE_ALL:
                return TornadoDeviceType.ALL;
            case CL_DEVICE_TYPE_DEFAULT:
                return TornadoDeviceType.DEFAULT;
            default:
                throw new RuntimeException("Device not supported");
        }
    }

    @Override
    public long getMaxAllocMemory() {
        return device.getDeviceMaxAllocationSize();
    }

    @Override
    public long getMaxGlobalMemory() {
        return device.getDeviceGlobalMemorySize();
    }

    @Override
    public long getDeviceLocalMemorySize() {
        return device.getDeviceLocalMemorySize();
    }

    @Override
    public long[] getDeviceMaxWorkgroupDimensions() {
        return device.getDeviceMaxWorkItemSizes();
    }

    @Override
    public String getDeviceOpenCLCVersion() {
        return device.getDeviceOpenCLCVersion();
    }

    @Override
    public Object getDeviceInfo() {
        return device.getDeviceInfo();
    }

    @Override
    public int getBackendIndex() {
        return TornadoCoreRuntime.getTornadoRuntime().getBackendIndex(OCLBackendImpl.class);
    }

    @Override
    public void enableThreadSharing() {
        // OpenCL device context is shared by different threads, by default
    }

    @Override
    public void setAtomicRegion(XPUBuffer bufferAtomics) {

    }

    @Override
    public int getAvailableProcessors() {
        return ((VirtualOCLDevice) device).getAvailableProcessors();
    }

    @Override
    public Object getAtomic() {
        return null;
    }

    @Override
    public void setAtomicsMapping(ConcurrentHashMap<Object, Integer> mappingAtomics) {

    }

    @Override
    public TornadoVMBackendType getTornadoVMBackend() {
        return TornadoVMBackendType.VIRTUAL;
    }

    @Override
    public boolean isSPIRVSupported() {
        return false;
    }

    @Override
    public void mapDeviceRegion(long executionPlanId, Object destArray, Object srcArray, DeviceBufferState deviceStateSrc, DeviceBufferState deviceStateDest, long offset) {
        throw new UnsupportedOperationException();
    }

}
