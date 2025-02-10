/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, 2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.ptx.runtime;

import static uk.ac.manchester.tornado.drivers.ptx.graal.PTXCodeUtil.buildKernelName;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.TornadoTargetDevice;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.internal.annotations.Vector;
import uk.ac.manchester.tornado.api.memory.DeviceBufferState;
import uk.ac.manchester.tornado.api.memory.TornadoMemoryProvider;
import uk.ac.manchester.tornado.api.memory.XPUBuffer;
import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.CharArray;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;
import uk.ac.manchester.tornado.drivers.common.TornadoBufferProvider;
import uk.ac.manchester.tornado.drivers.ptx.PTX;
import uk.ac.manchester.tornado.drivers.ptx.PTXBackendImpl;
import uk.ac.manchester.tornado.drivers.ptx.PTXDevice;
import uk.ac.manchester.tornado.drivers.ptx.PTXDeviceContext;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXCodeUtil;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXProviders;
import uk.ac.manchester.tornado.drivers.ptx.graal.backend.PTXBackend;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXCompilationResult;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXCompiler;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.mm.CUDAXPUBuffer;
import uk.ac.manchester.tornado.drivers.ptx.mm.PTXByteArrayWrapper;
import uk.ac.manchester.tornado.drivers.ptx.mm.PTXCharArrayWrapper;
import uk.ac.manchester.tornado.drivers.ptx.mm.PTXDoubleArrayWrapper;
import uk.ac.manchester.tornado.drivers.ptx.mm.PTXFloatArrayWrapper;
import uk.ac.manchester.tornado.drivers.ptx.mm.PTXIntArrayWrapper;
import uk.ac.manchester.tornado.drivers.ptx.mm.PTXLongArrayWrapper;
import uk.ac.manchester.tornado.drivers.ptx.mm.PTXMemorySegmentWrapper;
import uk.ac.manchester.tornado.drivers.ptx.mm.PTXMultiDimArrayWrapper;
import uk.ac.manchester.tornado.drivers.ptx.mm.PTXShortArrayWrapper;
import uk.ac.manchester.tornado.drivers.ptx.mm.PTXVectorWrapper;
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

public class PTXTornadoDevice implements TornadoXPUDevice {

    private static PTXBackendImpl driver = null;
    private final PTXDevice device;
    private final int deviceIndex;
    private final TornadoLogger logger;

    public PTXTornadoDevice(final int deviceIndex) {
        this.deviceIndex = deviceIndex;
        driver = TornadoCoreRuntime.getTornadoRuntime().getBackend(PTXBackendImpl.class);
        if (driver == null) {
            throw new RuntimeException("TornadoVM PTX Driver not found");
        }
        device = PTX.getPlatform().getDevice(deviceIndex);
        this.logger = new TornadoLogger(this.getClass());
    }

    @Override
    public TornadoSchedulingStrategy getPreferredSchedule() {
        return TornadoSchedulingStrategy.PER_ACCELERATOR_ITERATION;
    }

    @Override
    public KernelStackFrame createKernelStackFrame(long executionPlanId, int numArgs, Access access) {
        return getDeviceContext().getMemoryManager().createCallWrapper(executionPlanId, numArgs);
    }

    @Override
    public XPUBuffer createOrReuseAtomicsBuffer(int[] arr, Access access) {
        return null;
    }

    @Override
    public int[] checkAtomicsForTask(SchedulableTask task) {
        logger.debug("[PTX] Atomics not implemented ! Returning null");
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
        return switch (task) {
            case CompilableTask _ -> compileTask(executionPlanId, task);
            case PrebuiltTask _ -> compilePreBuiltTask(executionPlanId, task);
            default -> throw new TornadoInternalError("task of unknown type: " + task.getClass().getSimpleName());
        };
    }

    private TornadoInstalledCode compileTask(long executionPlanId, SchedulableTask task) {
        TornadoProfiler profiler = task.getProfiler();
        final PTXDeviceContext deviceContext = getDeviceContext();

        final CompilableTask executable = (CompilableTask) task;
        final ResolvedJavaMethod resolvedMethod = TornadoCoreRuntime.getTornadoRuntime().resolveMethod(executable.getMethod());
        final Sketch sketch = TornadoSketcher.lookup(resolvedMethod, task.meta().getBackendIndex(), task.meta().getDeviceIndex());

        // copy meta data into task
        final TaskDataContext taskMeta = executable.meta();
        final Access[] sketchAccess = sketch.getArgumentsAccess();
        final Access[] taskAccess = taskMeta.getArgumentsAccess();
        System.arraycopy(sketchAccess, 0, taskAccess, 0, sketchAccess.length);

        try {
            PTXCompilationResult result;
            if (!deviceContext.isCached(executionPlanId, resolvedMethod.getName(), executable)) {
                PTXProviders providers = (PTXProviders) getBackend().getProviders();
                profiler.start(ProfilerType.TASK_COMPILE_GRAAL_TIME, taskMeta.getId());
                result = PTXCompiler.compileSketchForDevice(sketch, executable, providers, getBackend(), executable.getProfiler());
                profiler.stop(ProfilerType.TASK_COMPILE_GRAAL_TIME, taskMeta.getId());
                profiler.sum(ProfilerType.TOTAL_GRAAL_COMPILE_TIME, profiler.getTaskTimer(ProfilerType.TASK_COMPILE_GRAAL_TIME, taskMeta.getId()));
            } else {
                result = new PTXCompilationResult(buildKernelName(resolvedMethod.getName(), executable), taskMeta);
            }

            profiler.start(ProfilerType.TASK_COMPILE_DRIVER_TIME, taskMeta.getId());
            TornadoInstalledCode installedCode = deviceContext.installCode(executionPlanId, result, resolvedMethod.getName());
            profiler.stop(ProfilerType.TASK_COMPILE_DRIVER_TIME, taskMeta.getId());
            profiler.sum(ProfilerType.TOTAL_DRIVER_COMPILE_TIME, profiler.getTaskTimer(ProfilerType.TASK_COMPILE_DRIVER_TIME, taskMeta.getId()));
            return installedCode;
        } catch (Exception e) {
            if (TornadoOptions.DEBUG) {
                System.err.println(e.getMessage());
            }
            logger.fatal("unable to compile %s for device %s\n", task.getId(), getDeviceName());
            logger.fatal("exception occurred when compiling %s\n", ((CompilableTask) task).getMethod().getName());
            throw new TornadoBailoutRuntimeException("[Error During the Task Compilation] ", e);
        }
    }

    private TornadoInstalledCode compilePreBuiltTask(long executionPlanId, SchedulableTask task) {
        final PTXDeviceContext deviceContext = getDeviceContext();
        final PrebuiltTask executable = (PrebuiltTask) task;
        String functionName = buildKernelName(executable.getEntryPoint(), executable);
        if (deviceContext.isCached(executionPlanId, executable.getEntryPoint(), executable)) {
            return deviceContext.getInstalledCode(executionPlanId, functionName);
        }

        final Path path = Paths.get(executable.getFilename());
        TornadoInternalError.guarantee(path.toFile().exists(), "file does not exist: %s", executable.getFilename());
        try {
            byte[] source = Files.readAllBytes(path);
            source = PTXCodeUtil.getCodeWithAttachedPTXHeader(source, getBackend());
            return deviceContext.installCode(executionPlanId, functionName, source, executable.getEntryPoint(), task.meta().isPrintKernelEnabled());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public boolean isFullJITMode(long executionPlanId, SchedulableTask task) {
        return true;
    }

    @Override
    public TornadoInstalledCode getCodeFromCache(long executionPlanId, SchedulableTask task) {
        String methodName;
        if (task instanceof PrebuiltTask) {
            PrebuiltTask prebuiltTask = (PrebuiltTask) task;
            methodName = prebuiltTask.getEntryPoint();
        } else {
            CompilableTask compilableTask = (CompilableTask) task;
            ResolvedJavaMethod resolvedMethod = TornadoCoreRuntime.getTornadoRuntime().resolveMethod(compilableTask.getMethod());
            methodName = resolvedMethod.getName();
        }
        String functionName = buildKernelName(methodName, task);
        return getDeviceContext().getInstalledCode(executionPlanId, functionName);
    }

    private XPUBuffer createDeviceBuffer(Class<?> type, Object object, long batchSize, Access access) {
        XPUBuffer result = null;
        if (type.isArray()) {

            if (!type.getComponentType().isArray()) {
                result = createArrayWrapper(type, getDeviceContext(), batchSize, access);
            } else {
                final Class<?> componentType = type.getComponentType();
                if (RuntimeUtilities.isPrimitiveArray(componentType)) {
                    result = createMultiArrayWrapper(componentType, type, batchSize, access);
                } else {
                    TornadoInternalError.unimplemented("multi-dimensional array of type %s", type.getName());
                }
            }
        } else if (!type.isPrimitive()) {
            if (object.getClass().getAnnotation(Vector.class) != null) {
                result = new PTXVectorWrapper(getDeviceContext(), object, batchSize, access);
            } else if (object instanceof MemorySegment) {
                result = new PTXMemorySegmentWrapper(getDeviceContext(), batchSize, access, 0);
            } else if (object instanceof IntArray) {
                result = new PTXMemorySegmentWrapper(getDeviceContext(), batchSize, access, PTXKind.S32.getSizeInBytes());
            } else if (object instanceof FloatArray) {
                result = new PTXMemorySegmentWrapper(getDeviceContext(), batchSize, access, PTXKind.F32.getSizeInBytes());
            } else if (object instanceof DoubleArray) {
                result = new PTXMemorySegmentWrapper(getDeviceContext(), batchSize, access, PTXKind.F64.getSizeInBytes());
            } else if (object instanceof LongArray) {
                result = new PTXMemorySegmentWrapper(getDeviceContext(), batchSize, access, PTXKind.S64.getSizeInBytes());
            } else if (object instanceof ShortArray) {
                result = new PTXMemorySegmentWrapper(getDeviceContext(), batchSize, access, PTXKind.S16.getSizeInBytes());
            } else if (object instanceof ByteArray) {
                result = new PTXMemorySegmentWrapper(getDeviceContext(), batchSize, access, PTXKind.S8.getSizeInBytes());
            } else if (object instanceof CharArray) {
                result = new PTXMemorySegmentWrapper(getDeviceContext(), batchSize, access, PTXKind.S8.getSizeInBytes());
            } else if (object instanceof HalfFloatArray) {
                result = new PTXMemorySegmentWrapper(getDeviceContext(), batchSize, access, PTXKind.S16.getSizeInBytes());
            } else {
                result = new CUDAXPUBuffer(getDeviceContext(), object, access);
            }
        }

        TornadoInternalError.guarantee(result != null, "Unable to create buffer for object: " + type);
        return result;
    }

    @Override
    public synchronized long allocateObjects(Object[] objects, long batchSize, DeviceBufferState[] states, Access[] accesses) {
        TornadoBufferProvider bufferProvider = getDeviceContext().getBufferProvider();
        for (Access access : accesses) {
            if (!bufferProvider.isNumFreeBuffersAvailable(objects.length, access)) {
                bufferProvider.resetBuffers(access);
            }
        }
        long allocatedSpace = 0;
        for (int i = 0; i < objects.length; i++) {
            logger.debug("Allocate object %s with access: %s", objects[i], accesses[i]);
            allocatedSpace += allocate(objects[i], batchSize, states[i], accesses[i]);
        }
        return allocatedSpace;
    }

    @Override
    public long allocate(Object object, long batchSize, DeviceBufferState state, Access access) {
        final XPUBuffer buffer;
        if (!state.hasObjectBuffer() || !state.isLockedBuffer()) {
            TornadoInternalError.guarantee(state.isAtomicRegionPresent() || !state.hasObjectBuffer(), "A device memory leak might be occurring.");
            buffer = createDeviceBuffer(object.getClass(), object, batchSize, access);
            state.setXPUBuffer(buffer);
            buffer.allocate(object, batchSize, access);
        } else {
            buffer = state.getXPUBuffer();
            if (batchSize != 0) {
                buffer.setSizeSubRegion(batchSize);
            }
        }
        return state.getXPUBuffer().size();
    }

    @Override
    public synchronized long deallocate(DeviceBufferState deviceBufferState) {
        long deallocatedSpace = 0;
        if (deviceBufferState.isLockedBuffer()) {
            return deallocatedSpace;
        }

        deviceBufferState.getXPUBuffer().markAsFreeBuffer();
        if (!TornadoOptions.isReusedBuffersEnabled()) {
            deallocatedSpace = deviceBufferState.getXPUBuffer().deallocate();
        }
        deviceBufferState.setContents(false);
        deviceBufferState.setXPUBuffer(null);
        return deallocatedSpace;
    }

    private XPUBuffer createArrayWrapper(Class<?> type, PTXDeviceContext deviceContext, long batchSize, Access access) {
        XPUBuffer result = null;
        if (type == int[].class) {
            result = new PTXIntArrayWrapper(deviceContext, access);
        } else if (type == short[].class) {
            result = new PTXShortArrayWrapper(deviceContext, access);
        } else if (type == byte[].class) {
            result = new PTXByteArrayWrapper(deviceContext, access);
        } else if (type == float[].class) {
            result = new PTXFloatArrayWrapper(deviceContext, access);
        } else if (type == double[].class) {
            result = new PTXDoubleArrayWrapper(deviceContext, access);
        } else if (type == long[].class) {
            result = new PTXLongArrayWrapper(deviceContext, access);
        } else if (type == char[].class) {
            result = new PTXCharArrayWrapper(deviceContext, access);
        } else {
            TornadoInternalError.unimplemented("array of type %s", type.getName());
        }
        return result;
    }

    private XPUBuffer createMultiArrayWrapper(Class<?> componentType, Class<?> type, long batchSize, Access access) {
        XPUBuffer result = null;
        PTXDeviceContext deviceContext = getDeviceContext();

        if (componentType == int[].class) {
            result = new PTXMultiDimArrayWrapper<>(deviceContext, (context) -> new PTXIntArrayWrapper(context, access), batchSize, access);
        } else if (componentType == short[].class) {
            result = new PTXMultiDimArrayWrapper<>(deviceContext, (context) -> new PTXShortArrayWrapper(context, access), batchSize, access);
        } else if (componentType == char[].class) {
            result = new PTXMultiDimArrayWrapper<>(deviceContext, (context) -> new PTXCharArrayWrapper(context, access), batchSize, access);
        } else if (componentType == byte[].class) {
            result = new PTXMultiDimArrayWrapper<>(deviceContext, (context) -> new PTXByteArrayWrapper(context, access), batchSize, access);
        } else if (componentType == float[].class) {
            result = new PTXMultiDimArrayWrapper<>(deviceContext, (context) -> new PTXFloatArrayWrapper(context, access), batchSize, access);
        } else if (componentType == double[].class) {
            result = new PTXMultiDimArrayWrapper<>(deviceContext, (context) -> new PTXDoubleArrayWrapper(context, access), batchSize, access);
        } else if (componentType == long[].class) {
            result = new PTXMultiDimArrayWrapper<>(deviceContext, (context) -> new PTXLongArrayWrapper(context, access), batchSize, access);
        } else {
            TornadoInternalError.unimplemented("array of type %s", type.getName());
        }
        return result;
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
     * @param hostOffset
     *     offset in bytes for the copy within the host input array (or
     *     object)
     * @return an event ID
     */
    @Override
    public List<Integer> ensurePresent(long executionPlanId, Object object, DeviceBufferState objectState, int[] events, long batchSize, long hostOffset) {
        if (!objectState.hasContent()) {
            objectState.setContents(true);
            return objectState.getXPUBuffer().enqueueWrite(executionPlanId, object, batchSize, hostOffset, events, events != null);
        }
        return null;
    }

    /**
     * It always copies in the input data (object) from the host to the target
     * device.
     *
     * @param object
     *     to be copied
     * @param batchSize
     *     size of the object to be allocated. If this value is <= 0, then it
     *     allocates the sizeof(object).
     * @param hostOffset
     *     offset in bytes for the copy within the host input array (or
     *     object)
     * @param objectState
     *     state of the object in the target device
     *     {@link DeviceBufferState}
     * @param events
     *     list of previous events
     * @return and event ID
     */
    @Override
    public List<Integer> streamIn(long executionPlanId, Object object, long batchSize, long hostOffset, DeviceBufferState objectState, int[] events) {
        objectState.setContents(true);
        return objectState.getXPUBuffer().enqueueWrite(executionPlanId, object, batchSize, hostOffset, events, events != null);
    }

    /**
     * It copies a device buffer from the target device to the host. Copies are
     * non-blocking
     *
     * @param object
     *     to be copied.
     * @param hostOffset
     *     offset in bytes for the copy within the host input array (or
     *     object)
     * @param objectState
     *     state of the object in the target device
     *     {@link DeviceBufferState}
     * @param events
     *     of pending events
     * @return and event ID
     */
    @Override
    public int streamOut(long executionPlanId, Object object, long hostOffset, DeviceBufferState objectState, int[] events) {
        TornadoInternalError.guarantee(objectState.hasObjectBuffer(), "invalid variable");
        int event = objectState.getXPUBuffer().enqueueRead(executionPlanId, object, hostOffset, events, events != null);
        if (events != null) {
            return event;
        }
        return -1;
    }

    /**
     * It copies a device buffer from the target device to the host. Copies are
     * blocking between the device and the host.
     *
     * @param object
     *     to be copied.
     * @param hostOffset
     *     offset in bytes for the copy within the host input array (or
     *     object)
     * @param objectState
     *     state of the object in the target device
     *     {@link DeviceBufferState}
     * @param events
     *     of pending events
     * @return and event ID
     */
    @Override
    public int streamOutBlocking(long executionPlanId, Object object, long hostOffset, DeviceBufferState objectState, int[] events) {
        TornadoInternalError.guarantee(objectState.hasObjectBuffer(), "invalid variable");
        return objectState.getXPUBuffer().read(executionPlanId, object, hostOffset, objectState.getPartialCopySize(), events, events != null);
    }

    /**
     * It resolves an pending event.
     *
     * @param event
     *     ID
     * @return an object of type {@link Event}
     */
    @Override
    public Event resolveEvent(long executionPlanId, int event) {
        return getDeviceContext().resolveEvent(executionPlanId, event);
    }

    @Override
    public void ensureLoaded(long executionPlanId) {
        // Sync the CUDA Stream only if the Stream Exists
        getDeviceContext().flushEventsIfNeeded(executionPlanId);
    }

    @Override
    public void flushEvents(long executionPlanId) {
        getDeviceContext().flushEvents(executionPlanId);
    }

    @Override
    public int enqueueBarrier(long executionPlanId) {
        return getDeviceContext().enqueueBarrier(executionPlanId);
    }

    @Override
    public int enqueueBarrier(long executionPlanId, int[] events) {
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
    public void sync(long executionPlanId) {
        getDeviceContext().sync(executionPlanId);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PTXTornadoDevice) {
            final PTXTornadoDevice other = (PTXTornadoDevice) obj;
            return (other.deviceIndex == deviceIndex);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 89 * hash + this.deviceIndex;
        return hash;
    }

    @Override
    public void flush(long executionPlanId) {
        getDeviceContext().flush(executionPlanId);
    }

    private void disableProfilerOptions() {
        TornadoOptions.TORNADO_PROFILER_LOG = false;
        TornadoOptions.TORNADO_PROFILER = false;
    }

    @Override
    public void clean() {
        // Reset only the execution plans attached to the PTX backend.
        Set<Long> ids = new HashSet<>(device.getPTXContext().getDeviceContext().getRegisteredPlanIds());
        ids.forEach(id -> device.getPTXContext().getDeviceContext().reset(id));
        ids.clear();
        disableProfilerOptions();
    }

    @Override
    public void dumpEvents(long executionPlanId) {
        getDeviceContext().dumpEvents(executionPlanId);
    }

    @Override
    public String getDeviceName() {
        return "cuda-" + device.getDeviceIndex();
    }

    @Override
    public String getDescription() {
        return String.format("%s %s", device.getDeviceName(), device.getDeviceType());
    }

    @Override
    public String getPlatformName() {
        return PTX.getPlatform().getName();
    }

    @Override
    public PTXDeviceContext getDeviceContext() {
        return getBackend().getDeviceContext();
    }

    public PTXBackend getBackend() {
        return driver.getBackend(device.getDeviceIndex());
    }

    @Override
    public TornadoTargetDevice getPhysicalDevice() {
        return device;
    }

    @Override
    public TornadoMemoryProvider getMemoryProvider() {
        return getDeviceContext().getMemoryManager();
    }

    @Override
    public TornadoDeviceType getDeviceType() {
        return TornadoDeviceType.GPU;
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
        return "N/A";
    }

    @Override
    public Object getDeviceInfo() {
        return device.getDeviceInfo();
    }

    @Override
    public int getBackendIndex() {
        return TornadoCoreRuntime.getTornadoRuntime().getBackendIndex(PTXBackendImpl.class);
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
        return TornadoVMBackendType.PTX;
    }

    @Override
    public boolean isSPIRVSupported() {
        return false;
    }

    @Override
    public void mapDeviceRegion(long executionPlanId, Object destArray, Object srcArray, DeviceBufferState deviceStateSrc, DeviceBufferState deviceStateDest, long offset) {
        XPUBuffer devicePointer = deviceStateDest.getXPUBuffer();
        XPUBuffer srcPointer = deviceStateSrc.getXPUBuffer();
        devicePointer.mapOnDeviceMemoryRegion(executionPlanId, srcPointer, offset);
    }

    /**
     * In CUDA the context is not attached to the whole process, but to individual
     * threads Therefore, in the case of new threads executing a task schedule, we
     * must make sure that the context is set for that thread.
     */
    @Override
    public void enableThreadSharing() {
        device.getPTXContext().enablePTXContext();
    }

    @Override
    public void setAtomicRegion(XPUBuffer bufferAtomics) {

    }

    @Override
    public String toString() {
        return getPlatformName() + " -- " + device.getDeviceName();
    }

}
