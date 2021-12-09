/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.spirv.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceFP64NotSupported;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.exceptions.TornadoMemoryException;
import uk.ac.manchester.tornado.api.exceptions.TornadoOutOfMemoryException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.mm.ObjectBuffer;
import uk.ac.manchester.tornado.api.mm.TornadoDeviceObjectState;
import uk.ac.manchester.tornado.api.mm.TornadoMemoryProvider;
import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;
import uk.ac.manchester.tornado.drivers.opencl.mm.AtomicsBuffer;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVBackend;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDevice;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDeviceContext;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDriver;
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
import uk.ac.manchester.tornado.drivers.spirv.mm.SPIRVMultiDimArrayWrapper;
import uk.ac.manchester.tornado.drivers.spirv.mm.SPIRVObjectWrapper;
import uk.ac.manchester.tornado.drivers.spirv.mm.SPIRVShortArrayWrapper;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.CallStack;
import uk.ac.manchester.tornado.runtime.common.DeviceObjectState;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoAcceleratorDevice;
import uk.ac.manchester.tornado.runtime.common.TornadoInstalledCode;
import uk.ac.manchester.tornado.runtime.common.TornadoSchedulingStrategy;
import uk.ac.manchester.tornado.runtime.sketcher.Sketch;
import uk.ac.manchester.tornado.runtime.sketcher.TornadoSketcher;
import uk.ac.manchester.tornado.runtime.tasks.CompilableTask;
import uk.ac.manchester.tornado.runtime.tasks.PrebuiltTask;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

/**
 * This is the core class for the actual runtime.
 */
public class SPIRVTornadoDevice implements TornadoAcceleratorDevice {

    private static final boolean BENCHMARKING_MODE = Boolean.parseBoolean(System.getProperties().getProperty("tornado.benchmarking", "False"));

    private SPIRVDevice device;
    private static SPIRVDriver driver = null;
    private int deviceIndex;
    private int platformIndex;

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

    @Override
    public TornadoSchedulingStrategy getPreferredSchedule() {
        return null;
    }

    @Override
    public CallStack createStack(int numArgs) {
        return getDeviceContext().getMemoryManager().createCallStack(numArgs);
    }

    @Override
    public ObjectBuffer createBuffer(int[] buffer) {
        return null;
    }

    @Override
    public ObjectBuffer createOrReuseBuffer(int[] arr) {
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
        try {
            final byte[] spirvBinary = Files.readAllBytes(pathToSPIRVBin);
            // return deviceContext.installCode(task.meta(), task.getId(),
            // task.getEntryPoint(), spirvBinary);
            return deviceContext.installBinary(task.meta(), task.getId(), task.getEntryPoint(), task.getFilename());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public SPIRVBackend getBackend() {
        return findDriver().getBackend(platformIndex, deviceIndex);
    }

    public static SPIRVDriver findDriver() {
        if (driver == null) {
            driver = TornadoCoreRuntime.getTornadoRuntime().getDriver(SPIRVDriver.class);
            TornadoInternalError.guarantee(driver != null, "unable to find the SPIR-V driver");
        }
        return driver;
    }

    private TornadoInstalledCode compileTask(CompilableTask task) {
        TornadoProfiler profiler = task.getProfiler();
        final SPIRVDeviceContext deviceContext = getDeviceContext();

        final CompilableTask executable = task;
        final ResolvedJavaMethod resolvedMethod = TornadoCoreRuntime.getTornadoRuntime().resolveMethod(executable.getMethod());
        final Sketch sketch = TornadoSketcher.lookup(resolvedMethod, task.meta().getDriverIndex(), task.meta().getDeviceIndex());

        // copy meta data into task
        final TaskMetaData taskMeta = executable.meta();

        // Return the code from the cache
        if (!task.shouldCompile() && deviceContext.isCached(task.getId(), resolvedMethod.getName())) {
            return deviceContext.getInstalledCode(task.getId(), resolvedMethod.getName());
        }

        final Access[] sketchAccess = sketch.getArgumentsAccess();
        final Access[] taskAccess = taskMeta.getArgumentsAccess();

        System.arraycopy(sketchAccess, 0, taskAccess, 0, sketchAccess.length);

        try {
            SPIRVCompilationResult result;
            // Compile the code and insert the SPIRV binary into the code cache
            SPIRVProviders providers = (SPIRVProviders) getBackend().getProviders();

            // Attach the profiler
            profiler.registerDeviceID(ProfilerType.DEVICE_ID, taskMeta.getId(), taskMeta.getLogicDevice().getDriverIndex() + ":" + taskMeta.getDeviceIndex());
            profiler.registerDeviceName(ProfilerType.DEVICE, taskMeta.getId(), taskMeta.getLogicDevice().getPhysicalDevice().getDeviceName());
            profiler.start(ProfilerType.TASK_COMPILE_GRAAL_TIME, taskMeta.getId());
            result = SPIRVCompiler.compileSketchForDevice(sketch, executable, providers, getBackend());
            profiler.stop(ProfilerType.TASK_COMPILE_GRAAL_TIME, taskMeta.getId());
            profiler.sum(ProfilerType.TOTAL_GRAAL_COMPILE_TIME, profiler.getTaskTimer(ProfilerType.TASK_COMPILE_GRAAL_TIME, taskMeta.getId()));

            profiler.start(ProfilerType.TASK_COMPILE_DRIVER_TIME, taskMeta.getId());
            TornadoInstalledCode installedCode = deviceContext.installBinary(result);
            profiler.stop(ProfilerType.TASK_COMPILE_DRIVER_TIME, taskMeta.getId());
            profiler.sum(ProfilerType.TOTAL_DRIVER_COMPILE_TIME, profiler.getTaskTimer(ProfilerType.TASK_COMPILE_DRIVER_TIME, taskMeta.getId()));
            return installedCode;
        } catch (TornadoBailoutRuntimeException e) {
            System.err.printf("Unable to compile %s for device %s\n", task.getId(), getDeviceName());
            System.err.printf("Exception occurred when compiling %s\n", task.getMethod().getName());
            e.printStackTrace();
            throw new TornadoBailoutRuntimeException("[Error During the Task Compilation] ", e);
        } catch (TornadoDeviceFP64NotSupported e) {
            throw new TornadoDeviceFP64NotSupported(e);
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
    public int[] updateAtomicRegionAndObjectState(SchedulableTask task, int[] array, int paramIndex, Object value, DeviceObjectState objectState) {
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
    public void setAtomicRegion(ObjectBuffer bufferAtomics) {
        throw new RuntimeException("Unsupported");
    }

    private ObjectBuffer createArrayWrapper(Class<?> klass, SPIRVDeviceContext device, long batchSize) {
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

    private ObjectBuffer createMultiArrayWrapper(Class<?> componentType, Class<?> type, SPIRVDeviceContext device, long batchSize) {
        ObjectBuffer result = null;

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

    private ObjectBuffer createDeviceBuffer(Class<?> type, Object object, SPIRVDeviceContext deviceContext, long batchSize) {
        if (type.isArray()) {
            if (!type.getComponentType().isArray()) {
                return createArrayWrapper(type, deviceContext, batchSize);
            } else {
                final Class<?> componentType = type.getComponentType();
                if (RuntimeUtilities.isPrimitiveArray(componentType)) {
                    return createMultiArrayWrapper(componentType, type, deviceContext, batchSize);
                } else {
                    throw new RuntimeException("Multi-dimensional array of type " + type.getName() + " not implemented");
                }
            }
        } else if (!type.isPrimitive()) {
            if (object instanceof AtomicInteger) {
                throw new RuntimeException("Atomic Integers not supported yet");
            } else {
                // Possible a vector type, we encapsulate in an object
                return new SPIRVObjectWrapper(deviceContext, object, batchSize);
            }
        }
        return null;
    }

    private void checkBatchSize(long batchSize) {
        if (batchSize > 0) {
            throw new TornadoRuntimeException("[ERROR] Batch computation with non-arrays not supported yet.");
        }
    }

    private void reserveMemory(Object object, long batchSize, TornadoDeviceObjectState state) {

        final ObjectBuffer buffer = createDeviceBuffer(object.getClass(), object, getDeviceContext(), batchSize);
        buffer.allocate(object, batchSize);
        state.setBuffer(buffer);

        if (buffer.getClass() == AtomicsBuffer.class) {
            state.setAtomicRegion();
        }

        final Class<?> type = object.getClass();
        if (!type.isArray()) {
            checkBatchSize(batchSize);
        }
        state.setValid(true);
    }

    // FIXME <REFACTOR> Common 3 backends
    private void checkForResizeBuffer(Object object, long batchSize, TornadoDeviceObjectState state) {
        // We re-allocate if the buffer size has changed
        final ObjectBuffer buffer = state.getBuffer();
        try {
            buffer.allocate(object, batchSize);
        } catch (TornadoOutOfMemoryException | TornadoMemoryException e) {
            e.printStackTrace();
        }
    }

    // FIXME <REFACTOR> <S>
    private void reAllocateInvalidBuffer(Object object, long batchSize, TornadoDeviceObjectState state) {
        try {
            state.getBuffer().allocate(object, batchSize);
            final Class<?> type = object.getClass();
            if (!type.isArray()) {
                checkBatchSize(batchSize);
                state.getBuffer().write(object);
            }
            state.setValid(true);
        } catch (TornadoOutOfMemoryException | TornadoMemoryException e) {
            e.printStackTrace();
        }
    }

    // FIXME <REFACTOR> <S>
    @Override
    public int ensureAllocated(Object object, long batchSize, TornadoDeviceObjectState state) {
        if (!state.hasBuffer()) {
            reserveMemory(object, batchSize, state);
        } else {
            checkForResizeBuffer(object, batchSize, state);
        }
        if (!state.isValid()) {
            reAllocateInvalidBuffer(object, batchSize, state);
        }
        return -1;
    }

    /**
     * It allocates and copy in the content of the object to the target device.
     *
     * @param object
     *            to be allocated
     * @param objectState
     *            state of the object in the target device
     *            {@link TornadoDeviceObjectState}
     * @param events
     *            list of pending events (dependencies)
     * @param batchSize
     *            size of the object to be allocated. If this value is <= 0, then it
     *            allocates the sizeof(object).
     * @param offset
     *            offset in bytes for the copy within the host input array (or
     *            object)
     * @return A list of event IDs
     */
    @Override
    public List<Integer> ensurePresent(Object object, TornadoDeviceObjectState objectState, int[] events, long batchSize, long offset) {
        if (!objectState.isValid()) {
            ensureAllocated(object, batchSize, objectState);
        }

        if (BENCHMARKING_MODE || !objectState.hasContents()) {
            objectState.setContents(true);
            return objectState.getBuffer().enqueueWrite(object, batchSize, offset, events, events == null);
        }
        return null;
    }

    @Override
    public List<Integer> streamIn(Object object, long batchSize, long hostOffset, TornadoDeviceObjectState objectState, int[] events) {
        if (batchSize > 0 || !objectState.isValid()) {
            ensureAllocated(object, batchSize, objectState);
        }
        objectState.setContents(true);
        return objectState.getBuffer().enqueueWrite(object, batchSize, hostOffset, events, events == null);
    }

    @Override
    public int streamOut(Object object, long hostOffset, TornadoDeviceObjectState objectState, int[] events) {
        TornadoInternalError.guarantee(objectState.isValid(), "invalid variable");
        int event = objectState.getBuffer().enqueueRead(object, hostOffset, events, events == null);
        if (events != null) {
            return event;
        }
        return -1;
    }

    @Override
    public int streamOutBlocking(Object object, long hostOffset, TornadoDeviceObjectState objectState, int[] events) {
        if (objectState.isAtomicRegionPresent()) {
            int eventID = objectState.getBuffer().enqueueRead(null, 0, null, false);
            if (object instanceof AtomicInteger) {
                throw new RuntimeException("Atomics Not supported yet");
            }
            return eventID;
        } else {
            TornadoInternalError.guarantee(objectState.isValid(), "invalid variable");
            int event = objectState.getBuffer().read(object, hostOffset, events, events == null);
            // We force a blocking copy -> we need to close the command list and command
            // queue
            flush();
            return event;
        }
    }

    @Override
    public Event resolveEvent(int event) {
        return getDeviceContext().resolveEvent(event);
    }

    @Override
    public void ensureLoaded() {

    }

    @Override
    public void flushEvents() {

    }

    @Override
    public int enqueueBarrier() {
        device.getDeviceContext().enqueueBarrier(deviceIndex);
        return 0;
    }

    @Override
    public int enqueueBarrier(int[] events) {
        return 0;
    }

    @Override
    public int enqueueMarker() {
        return 0;
    }

    @Override
    public int enqueueMarker(int[] events) {
        return 0;
    }

    @Override
    public void sync() {

    }

    @Override
    public void flush() {
        device.getDeviceContext().flush(deviceIndex);
    }

    @Override
    public void reset() {
        device.getDeviceContext().reset();
        // getBackend().reset();
    }

    @Override
    public void dumpEvents() {

    }

    @Override
    public void dumpMemory(String file) {

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
        return TornadoCoreRuntime.getTornadoRuntime().getDriverIndex(SPIRVDriver.class);
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
    public String toString() {
        return device.getName();
    }

    // FIXME <THis should be in the parent class> All backends
    public void sync(Object... objects) {
        for (Object obj : objects) {
            sync(obj);
        }
    }

    // FIXME <THis should be in the parent class> All backends
    public void sync(Object object) {
        final DeviceObjectState state = TornadoCoreRuntime.getTornadoRuntime().resolveObject(object).getDeviceState(this);
        resolveEvent(streamOut(object, 0, state, null)).waitOn();
    }

    /**
     * Move Data from the device region that corresponds to buffer A into buffer B.
     */
    public void moveDataFromDeviceBufferToHost(DeviceObjectState objectStateA, Object b) {
        objectStateA.getBuffer().read(b, 0, null, false);
    }
}
