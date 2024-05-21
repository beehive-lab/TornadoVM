/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021-2022, 2023, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.spirv.runtime;

import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.internal.annotations.Vector;
import uk.ac.manchester.tornado.api.memory.DeviceBufferState;
import uk.ac.manchester.tornado.api.memory.TornadoMemoryProvider;
import uk.ac.manchester.tornado.api.memory.XPUBuffer;
import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.drivers.common.TornadoBufferProvider;
import uk.ac.manchester.tornado.drivers.opencl.mm.AtomicsBuffer;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVBackend;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVBackendImpl;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDevice;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDeviceContext;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVProxy;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVProviders;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResult;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompiler;
import uk.ac.manchester.tornado.drivers.spirv.mm.SPIRVByteArrayWrapper;
import uk.ac.manchester.tornado.drivers.spirv.mm.SPIRVCharArrayWrapper;
import uk.ac.manchester.tornado.drivers.spirv.mm.SPIRVDoubleArrayWrapper;
import uk.ac.manchester.tornado.drivers.spirv.mm.SPIRVFloatArrayWrapper;
import uk.ac.manchester.tornado.drivers.spirv.mm.SPIRVIntArrayWrapper;
import uk.ac.manchester.tornado.drivers.spirv.mm.SPIRVLongArrayWrapper;
import uk.ac.manchester.tornado.drivers.spirv.mm.SPIRVMemorySegmentWrapper;
import uk.ac.manchester.tornado.drivers.spirv.mm.SPIRVMultiDimArrayWrapper;
import uk.ac.manchester.tornado.drivers.spirv.mm.SPIRVObjectWrapper;
import uk.ac.manchester.tornado.drivers.spirv.mm.SPIRVShortArrayWrapper;
import uk.ac.manchester.tornado.drivers.spirv.mm.SPIRVVectorWrapper;
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
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

/**
 * This is the core class for the actual runtime.
 */
public class SPIRVTornadoDevice implements TornadoXPUDevice {

    private static final boolean BENCHMARKING_MODE = Boolean.parseBoolean(System.getProperties().getProperty("tornado.benchmarking", "False"));
    private static SPIRVBackendImpl driver = null;
    private final SPIRVDevice device;
    private final int deviceIndex;
    private final int platformIndex;

    public SPIRVTornadoDevice(int platformIndex, int deviceIndex) {
        this.platformIndex = platformIndex;
        this.deviceIndex = deviceIndex;
        device = SPIRVProxy.getPlatform(platformIndex).getDevice(deviceIndex);
    }

    public SPIRVTornadoDevice(SPIRVDevice lowLevelDevice) {
        this.platformIndex = lowLevelDevice.getPlatformIndex();
        this.deviceIndex = lowLevelDevice.getDeviceIndex();
        device = lowLevelDevice;
    }

    public static SPIRVBackendImpl findDriver() {
        if (driver == null) {
            driver = TornadoCoreRuntime.getTornadoRuntime().getBackend(SPIRVBackendImpl.class);
            TornadoInternalError.guarantee(driver != null, "unable to find the SPIR-V driver");
        }
        return driver;
    }

    @Override
    public TornadoSchedulingStrategy getPreferredSchedule() {
        return null;
    }

    @Override
    public KernelStackFrame createKernelStackFrame(int numArgs) {
        return getDeviceContext().getMemoryManager().createKernelStackFrame(Thread.currentThread().threadId(), numArgs);
    }

    @Override
    public XPUBuffer createOrReuseAtomicsBuffer(int[] arr) {
        return null;
    }

    @Override
    public TornadoInstalledCode installCode(SchedulableTask task) {
        if (task instanceof CompilableTask) {
            return compileTask((CompilableTask) task);
        } else if (task instanceof PrebuiltTask) {
            return compilePreBuiltTask((PrebuiltTask) task);
        } else {
            throw new RuntimeException("SchedulableTask task not supported");
        }
    }

    private TornadoInstalledCode compilePreBuiltTask(PrebuiltTask task) {
        final SPIRVDeviceContext deviceContext = getDeviceContext();
        if (deviceContext.isCached(task.getId(), task.getEntryPoint())) {
            return deviceContext.getInstalledCode(task.getId(), task.getEntryPoint());
        }
        final Path pathToSPIRVBin = Paths.get(task.getFilename());
        TornadoInternalError.guarantee(pathToSPIRVBin.toFile().exists(), "files does not exists %s", task.getFilename());
        return deviceContext.installBinary(task.meta(), task.getId(), task.getEntryPoint(), task.getFilename());
    }

    public SPIRVBackend getBackend() {
        return findDriver().getBackend(platformIndex, deviceIndex);
    }

    private TornadoInstalledCode compileTask(CompilableTask task) {
        TornadoProfiler profiler = task.getProfiler();
        final SPIRVDeviceContext deviceContext = getDeviceContext();

        final ResolvedJavaMethod resolvedMethod = TornadoCoreRuntime.getTornadoRuntime().resolveMethod(task.getMethod());
        final Sketch sketch = TornadoSketcher.lookup(resolvedMethod, task.meta().getBackendIndex(), task.meta().getDeviceIndex());

        // copy meta data into task
        final TaskMetaData taskMeta = task.meta();

        // Return the code from the cache
        if (!task.shouldCompile() && deviceContext.isCached(task.getId(), resolvedMethod.getName())) {
            return deviceContext.getInstalledCode(task.getId(), resolvedMethod.getName());
        }

        final Access[] sketchAccess = sketch.getArgumentsAccess();
        final Access[] taskAccess = taskMeta.getArgumentsAccess();

        System.arraycopy(sketchAccess, 0, taskAccess, 0, sketchAccess.length);

        try {
            SPIRVCompilationResult result;
            // Compile the code and insert the SPIR-V binary into the code cache
            SPIRVProviders providers = (SPIRVProviders) getBackend().getProviders();
            profiler.start(ProfilerType.TASK_COMPILE_GRAAL_TIME, taskMeta.getId());
            result = SPIRVCompiler.compileSketchForDevice(sketch, task, providers, getBackend(), task.getProfiler());
            profiler.stop(ProfilerType.TASK_COMPILE_GRAAL_TIME, taskMeta.getId());
            profiler.sum(ProfilerType.TOTAL_GRAAL_COMPILE_TIME, profiler.getTaskTimer(ProfilerType.TASK_COMPILE_GRAAL_TIME, taskMeta.getId()));

            profiler.start(ProfilerType.TASK_COMPILE_DRIVER_TIME, taskMeta.getId());
            TornadoInstalledCode installedCode = deviceContext.installBinary(result);
            profiler.stop(ProfilerType.TASK_COMPILE_DRIVER_TIME, taskMeta.getId());
            profiler.sum(ProfilerType.TOTAL_DRIVER_COMPILE_TIME, profiler.getTaskTimer(ProfilerType.TASK_COMPILE_DRIVER_TIME, taskMeta.getId()));
            return installedCode;
        } catch (Exception e) {
            TornadoLogger logger = new TornadoLogger(this.getClass());
            logger.fatal("Unable to compile %s for device %s\n", task.getId(), getDeviceName());
            logger.fatal("Exception occurred when compiling %s\n", task.getMethod().getName());
            if (TornadoOptions.RECOVER_BAILOUT) {
                throw new TornadoBailoutRuntimeException(STR."[Error During the Task Compilation]: \{e.getMessage()}");
            } else {
                throw e;
            }
        }
    }

    @Override
    public boolean isFullJITMode(SchedulableTask task) {
        return false;
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
    public int[] updateAtomicRegionAndObjectState(SchedulableTask task, int[] array, int paramIndex, Object value, XPUDeviceBufferState objectState) {
        return null;
    }

    @Override
    public int getAtomicsGlobalIndexForTask(SchedulableTask task, int paramIndex) {
        return 0;
    }

    @Override
    public boolean checkAtomicsParametersForTask(SchedulableTask task) {
        return false;
    }

    @Override
    public void enableThreadSharing() {
        // empty method
    }

    @Override
    public void setAtomicRegion(XPUBuffer bufferAtomics) {
        throw new RuntimeException("Unsupported");
    }

    @Override
    public boolean loopIndexInWrite(SchedulableTask task) {
        if (task instanceof CompilableTask) {
            final CompilableTask executable = (CompilableTask) task;
            final ResolvedJavaMethod resolvedMethod = TornadoCoreRuntime.getTornadoRuntime().resolveMethod(executable.getMethod());
            final Sketch sketch = TornadoSketcher.lookup(resolvedMethod, task.meta().getBackendIndex(), task.meta().getDeviceIndex());
            return sketch.getBatchWriteThreadIndex();
        } else {
            return false;
        }
    }

    private XPUBuffer createArrayWrapper(Class<?> klass, SPIRVDeviceContext device, long batchSize) {
        if (klass == int[].class) {
            return new SPIRVIntArrayWrapper(device, batchSize);
        } else if (klass == float[].class) {
            return new SPIRVFloatArrayWrapper(device, batchSize);
        } else if (klass == double[].class) {
            return new SPIRVDoubleArrayWrapper(device, batchSize);
        } else if (klass == short[].class) {
            return new SPIRVShortArrayWrapper(device, batchSize);
        } else if (klass == byte[].class) {
            return new SPIRVByteArrayWrapper(device, batchSize);
        } else if (klass == long[].class) {
            return new SPIRVLongArrayWrapper(device, batchSize);
        } else if (klass == char[].class) {
            return new SPIRVCharArrayWrapper(device, batchSize);
        }
        throw new RuntimeException("[SPIRV] Array Wrapper Not Implemented yet: " + klass);
    }

    private XPUBuffer createMultiArrayWrapper(Class<?> componentType, Class<?> type, SPIRVDeviceContext device, long batchSize) {
        XPUBuffer result = null;

        if (componentType == int[].class) {
            result = new SPIRVMultiDimArrayWrapper<>(device, (SPIRVDeviceContext context) -> new SPIRVIntArrayWrapper(context, batchSize), batchSize);
        } else if (componentType == short[].class) {
            result = new SPIRVMultiDimArrayWrapper<>(device, (SPIRVDeviceContext context) -> new SPIRVShortArrayWrapper(context, batchSize), batchSize);
        } else if (componentType == char[].class) {
            result = new SPIRVMultiDimArrayWrapper<>(device, (SPIRVDeviceContext context) -> new SPIRVCharArrayWrapper(context, batchSize), batchSize);
        } else if (componentType == byte[].class) {
            result = new SPIRVMultiDimArrayWrapper<>(device, (SPIRVDeviceContext context) -> new SPIRVByteArrayWrapper(context, batchSize), batchSize);
        } else if (componentType == float[].class) {
            result = new SPIRVMultiDimArrayWrapper<>(device, (SPIRVDeviceContext context) -> new SPIRVFloatArrayWrapper(context, batchSize), batchSize);
        } else if (componentType == double[].class) {
            result = new SPIRVMultiDimArrayWrapper<>(device, (SPIRVDeviceContext context) -> new SPIRVDoubleArrayWrapper(context, batchSize), batchSize);
        } else if (componentType == long[].class) {
            result = new SPIRVMultiDimArrayWrapper<>(device, (SPIRVDeviceContext context) -> new SPIRVLongArrayWrapper(context, batchSize), batchSize);
        } else {
            TornadoInternalError.unimplemented("array of type %s", type.getName());
        }
        return result;
    }

    private XPUBuffer createDeviceBuffer(Class<?> type, Object object, SPIRVDeviceContext deviceContext, long batchSize) {
        if (type.isArray()) {
            if (!type.getComponentType().isArray()) {
                return createArrayWrapper(type, deviceContext, batchSize);
            } else {
                final Class<?> componentType = type.getComponentType();
                if (RuntimeUtilities.isPrimitiveArray(componentType)) {
                    return createMultiArrayWrapper(componentType, type, deviceContext, batchSize);
                } else {
                    throw new TornadoRuntimeException(STR."Multi-dimensional array of type \{type.getName()} not implemented.");
                }
            }
        } else if (!type.isPrimitive()) {
            if (object instanceof AtomicInteger) {
                throw new TornadoRuntimeException("[ERROR] AtomicInteger types are not supported yet.");
            } else if (object.getClass().getAnnotation(Vector.class) != null) {
                return new SPIRVVectorWrapper(deviceContext, object, batchSize);
            } else if (object instanceof MemorySegment) {
                return new SPIRVMemorySegmentWrapper(deviceContext, batchSize);
            } else if (object instanceof TornadoNativeArray) {
                return new SPIRVMemorySegmentWrapper(deviceContext, batchSize);
            } else {
                // Possible a vector type, we encapsulate in an object
                return new SPIRVObjectWrapper(deviceContext, object);
            }
        }
        return null;
    }

    @Override
    public synchronized int allocateObjects(Object[] objects, long batchSize, DeviceBufferState[] states) {
        TornadoBufferProvider bufferProvider = getDeviceContext().getBufferProvider();
        if (!bufferProvider.checkBufferAvailability(objects.length)) {
            bufferProvider.resetBuffers();
        }
        for (int i = 0; i < objects.length; i++) {
            allocate(objects[i], batchSize, states[i]);
        }
        return -1;
    }

    private XPUBuffer createNewBufferAllocation(Object object, long batchSize, DeviceBufferState state) {
        final XPUBuffer buffer;
        TornadoInternalError.guarantee(state.isAtomicRegionPresent() || !state.hasObjectBuffer(), "A device memory leak might be occurring.");
        buffer = createDeviceBuffer(object.getClass(), object, getDeviceContext(), batchSize);
        state.setXPUBuffer(buffer);
        buffer.allocate(object, batchSize);
        return buffer;
    }

    @Override
    public int allocate(Object object, long batchSize, DeviceBufferState state) {
        final XPUBuffer buffer;
        if (state.hasObjectBuffer() && state.isLockedBuffer()) {
            buffer = state.getXPUBuffer();
            if (batchSize != 0) {
                buffer.setSizeSubRegion(batchSize);
            }
        } else {
            buffer = createNewBufferAllocation(object, batchSize, state);
        }

        if (buffer.getClass() == AtomicsBuffer.class) {
            state.setAtomicRegion();
        }
        return -1;
    }

    @Override
    public synchronized int deallocate(DeviceBufferState state) {
        if (state.isLockedBuffer()) {
            return -1;
        }

        state.getXPUBuffer().deallocate();
        state.setContents(false);
        state.setXPUBuffer(null);
        return -1;
    }

    /**
     * It allocates and copy in the content of the object to the target device.
     *
     * @param object
     *     to be allocated
     * @param objectState
     *     state of the object in the target device
     *     {@link DeviceBufferState}
     * @param events
     *     list of pending events (dependencies)
     * @param batchSize
     *     size of the object to be allocated. If this value is <= 0, then it
     *     allocates the sizeof(object).
     * @param offset
     *     offset in bytes for the copy within the host input array (or
     *     object)
     * @return A list of event IDs
     */
    @Override
    public List<Integer> ensurePresent(long executionPlanId, Object object, DeviceBufferState objectState, int[] events, long batchSize, long offset) {
        if (!objectState.hasContent() || BENCHMARKING_MODE) {
            objectState.setContents(true);
            return objectState.getXPUBuffer().enqueueWrite(executionPlanId, object, batchSize, offset, events, events == null);
        }
        return null;
    }

    @Override
    public List<Integer> streamIn(long executionPlanId, Object object, long batchSize, long hostOffset, DeviceBufferState objectState, int[] events) {
        objectState.setContents(true);
        return objectState.getXPUBuffer().enqueueWrite(executionPlanId, object, batchSize, hostOffset, events, events == null);
    }

    @Override
    public int streamOut(long executionPlanId, Object object, long hostOffset, DeviceBufferState objectState, int[] events) {
        TornadoInternalError.guarantee(objectState.hasObjectBuffer(), "invalid variable");
        int event = objectState.getXPUBuffer().enqueueRead(executionPlanId, object, hostOffset, events, events == null);
        if (events != null) {
            return event;
        }
        return -1;
    }

    @Override
    public int streamOutBlocking(long executionPlanId, Object object, long hostOffset, DeviceBufferState objectState, int[] events) {
        long partialSize = objectState.getPartialCopySize();
        if (objectState.isAtomicRegionPresent()) {
            int eventID = objectState.getXPUBuffer().enqueueRead(executionPlanId, null, 0, null, false);
            if (object instanceof AtomicInteger) {
                throw new RuntimeException("Atomics Not supported yet");
            }
            return eventID;
        } else {
            TornadoInternalError.guarantee(objectState.hasObjectBuffer(), "invalid variable");
            int event = objectState.getXPUBuffer().read(executionPlanId, object, hostOffset, partialSize, events, events == null);
            // We force a blocking copy -> we need to close the command list and command queue
            flush(executionPlanId);
            return event;
        }
    }

    @Override
    public Event resolveEvent(long executionPlanId, int event) {
        return getDeviceContext().resolveEvent(executionPlanId, event);
    }

    @Override
    public void ensureLoaded(long executionPlanId) {
    }

    @Override
    public void flushEvents(long executionPlanId) {

    }

    @Override
    public int enqueueBarrier(long executionPlanId) {
        device.getDeviceContext().enqueueBarrier(executionPlanId, deviceIndex);
        return 0;
    }

    @Override
    public int enqueueBarrier(long executionPlanId, int[] events) {
        return 0;
    }

    @Override
    public int enqueueMarker(long executionPlanId) {
        return 0;
    }

    @Override
    public int enqueueMarker(long executionPlanId, int[] events) {
        return 0;
    }

    @Override
    public void sync(long executionPlanId) {

    }

    @Override
    public void flush(long executionPlanId) {
        device.getDeviceContext().flush(executionPlanId, deviceIndex);
    }

    private void disableProfilerOptions() {
        TornadoOptions.TORNADO_PROFILER_LOG = false;
        TornadoOptions.TORNADO_PROFILER = false;
    }

    @Override
    public void clean() {
        Set<Long> ids = device.getDeviceContext().getRegisteredPlanIds();
        ids.forEach(id -> device.getDeviceContext().reset(id));
        ids.clear();
        disableProfilerOptions();
    }

    @Override
    public void dumpEvents(long executionPlanId) {

    }

    @Override
    public String getDeviceName() {
        return "spirv-" + device.getDeviceIndex();
    }

    @Override
    public String getDescription() {
        return String.format("%s %s", device.getName(), device.getTornadoDeviceType());
    }

    @Override
    public String getPlatformName() {
        return device.getPlatformName();
    }

    @Override
    public SPIRVDeviceContext getDeviceContext() {
        return device.getDeviceContext();
    }

    @Override
    public SPIRVDevice getPhysicalDevice() {
        return device;
    }

    @Override
    public TornadoMemoryProvider getMemoryProvider() {
        return null;
    }

    @Override
    public TornadoDeviceType getDeviceType() {
        return device.getTornadoDeviceType();
    }

    @Override
    public long getMaxAllocMemory() {
        return device.getMaxAllocMemory();
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
        return device.getDeviceMaxWorkgroupDimensions();
    }

    @Override
    public String getDeviceOpenCLCVersion() {
        return device.getDeviceOpenCLCVersion();
    }

    @Override
    public Object getDeviceInfo() {
        return null;
    }

    @Override
    public int getDriverIndex() {
        return TornadoCoreRuntime.getTornadoRuntime().getBackendIndex(SPIRVBackendImpl.class);
    }

    @Override
    public Object getAtomic() {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public void setAtomicsMapping(ConcurrentHashMap<Object, Integer> mappingAtomics) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public TornadoVMBackendType getTornadoVMBackend() {
        return TornadoVMBackendType.SPIRV;
    }

    @Override
    public boolean isSPIRVSupported() {
        return true;
    }

    @Override
    public String toString() {
        return device.getName();
    }

    /**
     * Move Data from the device region that corresponds to buffer A into buffer B.
     */
    public void moveDataFromDeviceBufferToHost(long executionPlanId, XPUDeviceBufferState objectStateA, Object b) {
        objectStateA.getXPUBuffer().read(executionPlanId, b, 0, 0, null, false);
    }
}
