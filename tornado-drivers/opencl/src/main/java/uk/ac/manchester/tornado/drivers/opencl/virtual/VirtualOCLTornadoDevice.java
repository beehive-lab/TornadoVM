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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
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
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.memory.ObjectBuffer;
import uk.ac.manchester.tornado.api.memory.TornadoDeviceObjectState;
import uk.ac.manchester.tornado.api.memory.TornadoMemoryProvider;
import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContextInterface;
import uk.ac.manchester.tornado.drivers.opencl.OCLDriver;
import uk.ac.manchester.tornado.drivers.opencl.OCLTargetDevice;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLDeviceType;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLProviders;
import uk.ac.manchester.tornado.drivers.opencl.graal.backend.OCLBackend;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompilationResult;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompiler;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.DeviceObjectState;
import uk.ac.manchester.tornado.runtime.common.KernelArgs;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.common.TornadoAcceleratorDevice;
import uk.ac.manchester.tornado.runtime.common.TornadoInstalledCode;
import uk.ac.manchester.tornado.runtime.common.TornadoSchedulingStrategy;
import uk.ac.manchester.tornado.runtime.sketcher.Sketch;
import uk.ac.manchester.tornado.runtime.sketcher.TornadoSketcher;
import uk.ac.manchester.tornado.runtime.tasks.CompilableTask;
import uk.ac.manchester.tornado.runtime.tasks.PrebuiltTask;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class VirtualOCLTornadoDevice implements TornadoAcceleratorDevice {

    private static OCLDriver driver = null;
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

    private static OCLDriver findDriver() {
        if (driver == null) {
            driver = TornadoCoreRuntime.getTornadoRuntime().getDriver(OCLDriver.class);
            TornadoInternalError.guarantee(driver != null, "unable to find OpenCL driver");
        }
        return driver;
    }

    @Override
    public void dumpEvents() {
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
    public void reset() {
        device.getDeviceContext().reset();
    }

    @Override
    public String toString() {
        return getPlatformName() + " -- " + device.getDeviceName();
    }

    @Override
    public TornadoSchedulingStrategy getPreferredSchedule() {
        if (null != device.getDeviceType()) {

            if (Tornado.FORCE_ALL_TO_GPU) {
                return TornadoSchedulingStrategy.PER_ITERATION;
            }

            if (device.getDeviceType() == OCLDeviceType.CL_DEVICE_TYPE_CPU) {
                return TornadoSchedulingStrategy.PER_BLOCK;
            }
            return TornadoSchedulingStrategy.PER_ITERATION;
        }
        TornadoInternalError.shouldNotReachHere();
        return TornadoSchedulingStrategy.PER_ITERATION;
    }

    @Override
    public void ensureLoaded() {
        final OCLBackend backend = getBackend();
        if (!backend.isInitialised()) {
            backend.init();
        }
    }

    @Override
    public KernelArgs createCallWrapper(int numArgs) {
        return null;
    }

    @Override
    public ObjectBuffer createOrReuseAtomicsBuffer(int[] arr) {
        return null;
    }

    private TornadoInstalledCode compileTask(SchedulableTask task) {
        final CompilableTask executable = (CompilableTask) task;
        final ResolvedJavaMethod resolvedMethod = TornadoCoreRuntime.getTornadoRuntime().resolveMethod(executable.getMethod());
        final Sketch sketch = TornadoSketcher.lookup(resolvedMethod, task.meta().getDriverIndex(), task.meta().getDeviceIndex());

        // copy meta data into task
        final TaskMetaData taskMeta = executable.meta();
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

            RuntimeUtilities.maybePrintSource(result.getTargetCode());

            return null;
        } catch (Exception e) {
            driver.fatal("unable to compile %s for device %s", task.getId(), getDeviceName());
            driver.fatal("exception occurred when compiling %s", ((CompilableTask) task).getMethod().getName());
            driver.fatal("exception: %s", e.toString());
            throw new TornadoBailoutRuntimeException("[Error During the Task Compilation] ", e);
        }
    }

    private TornadoInstalledCode compilePreBuiltTask(SchedulableTask task) {
        final PrebuiltTask executable = (PrebuiltTask) task;

        final Path path = Paths.get(executable.getFilename());
        TornadoInternalError.guarantee(path.toFile().exists(), "file does not exist: %s", executable.getFilename());
        try {
            final byte[] source = Files.readAllBytes(path);

            RuntimeUtilities.maybePrintSource(source);
        } catch (IOException e) {
            e.printStackTrace();
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
    public boolean isFullJITMode(SchedulableTask task) {
        return true;
    }

    @Override
    public TornadoInstalledCode getCodeFromCache(SchedulableTask task) {
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
    public int[] updateAtomicRegionAndObjectState(SchedulableTask task, int[] array, int paramIndex, Object value, DeviceObjectState objectState) {
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
    public TornadoInstalledCode installCode(SchedulableTask task) {
        return compileJavaToAccelerator(task);
    }

    @Override
    public int allocate(Object object, long batchSize, TornadoDeviceObjectState state) {
        unimplemented();
        return -1;
    }

    @Override
    public int allocateObjects(Object[] objects, long batchSize, TornadoDeviceObjectState[] states) {
        unimplemented();
        return -1;
    }

    @Override
    public int deallocate(TornadoDeviceObjectState state) {
        unimplemented();
        return -1;
    }

    @Override
    public List<Integer> ensurePresent(Object object, TornadoDeviceObjectState state, int[] events, long batchSize, long offset) {
        unimplemented();
        return null;
    }

    @Override
    public List<Integer> streamIn(Object object, long batchSize, long offset, TornadoDeviceObjectState state, int[] events) {
        unimplemented();
        return null;
    }

    @Override
    public int streamOut(Object object, long offset, TornadoDeviceObjectState state, int[] events) {
        unimplemented();
        return -1;
    }

    @Override
    public int streamOutBlocking(Object object, long hostOffset, TornadoDeviceObjectState state, int[] events) {
        unimplemented();
        return -1;
    }

    @Override
    public void flush() {
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
    public void sync() {
    }

    @Override
    public int enqueueBarrier() {
        unimplemented();
        return getDeviceContext().enqueueBarrier();
    }

    @Override
    public int enqueueBarrier(int[] events) {
        unimplemented();
        return getDeviceContext().enqueueBarrier(events);
    }

    @Override
    public int enqueueMarker() {
        return getDeviceContext().enqueueMarker();
    }

    @Override
    public int enqueueMarker(int[] events) {
        return getDeviceContext().enqueueMarker(events);
    }

    @Override
    public Event resolveEvent(int event) {
        return getDeviceContext().resolveEvent(event);
    }

    @Override
    public void flushEvents() {
        getDeviceContext().flushEvents();
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
    public int getDriverIndex() {
        return TornadoCoreRuntime.getTornadoRuntime().getDriverIndex(OCLDriver.class);
    }

    @Override
    public void enableThreadSharing() {
        // OpenCL device context is shared by different threads, by default
    }

    @Override
    public void setAtomicRegion(ObjectBuffer bufferAtomics) {

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

}
