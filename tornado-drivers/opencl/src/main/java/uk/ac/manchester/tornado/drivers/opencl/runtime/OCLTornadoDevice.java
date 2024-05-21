/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, 2023, 2024, APT Group, Department of Computer Science,
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
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.runtime;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.internal.annotations.Vector;
import uk.ac.manchester.tornado.api.memory.DeviceBufferState;
import uk.ac.manchester.tornado.api.memory.TaskMetaDataInterface;
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
import uk.ac.manchester.tornado.drivers.opencl.OCLBackendImpl;
import uk.ac.manchester.tornado.drivers.opencl.OCLCodeCache;
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContext;
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContextInterface;
import uk.ac.manchester.tornado.drivers.opencl.OCLTargetDevice;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLDeviceType;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLInstalledCode;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLProviders;
import uk.ac.manchester.tornado.drivers.opencl.graal.backend.OCLBackend;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompilationResult;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompiler;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.TornadoAtomicIntegerNode;
import uk.ac.manchester.tornado.drivers.opencl.mm.AtomicsBuffer;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLByteArrayWrapper;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLCharArrayWrapper;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLDoubleArrayWrapper;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLFloatArrayWrapper;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLIntArrayWrapper;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLLongArrayWrapper;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLMemorySegmentWrapper;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLMultiDimArrayWrapper;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLShortArrayWrapper;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLVectorWrapper;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLXPUBuffer;
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

public class OCLTornadoDevice implements TornadoXPUDevice {

    private static OCLBackendImpl driver = null;
    private static boolean BENCHMARKING_MODE = Boolean.parseBoolean(System.getProperties().getProperty("tornado.benchmarking", "False"));
    private static final Pattern NAME_PATTERN = Pattern.compile("^OpenCL (\\d)\\.(\\d).*");
    private final OCLTargetDevice device;
    private final int deviceIndex;
    private final int platformIndex;
    private final String platformName;
    private XPUBuffer reuseBuffer;
    private ConcurrentHashMap<Object, Integer> mappingAtomics;

    /**
     * Constructor used also in SLAMBench/KFusion
     *
     * @param platformIndex
     *     OpenCL Platform index
     * @param deviceIndex
     *     OpenCL Device Index
     */
    public OCLTornadoDevice(final int platformIndex, final int deviceIndex) {
        this.platformIndex = platformIndex;
        this.deviceIndex = deviceIndex;
        driver = TornadoCoreRuntime.getTornadoRuntime().getBackend(OCLBackendImpl.class);

        if (driver == null) {
            throw new RuntimeException("TornadoVM OpenCL Driver not found");
        }

        platformName = driver.getPlatformContext(platformIndex).getPlatform().getName();
        device = driver.getPlatformContext(platformIndex).devices().get(deviceIndex);
        mappingAtomics = new ConcurrentHashMap<>();
    }

    @Override
    public void dumpEvents(long executionPlanId) {
        getDeviceContext().dumpEvents();
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

    public int getDeviceIndex() {
        return deviceIndex;
    }

    @Override
    public TornadoMemoryProvider getMemoryProvider() {
        return getDeviceContext().getMemoryManager();
    }

    public int getPlatformIndex() {
        return platformIndex;
    }

    @Override
    public OCLDeviceContextInterface getDeviceContext() {
        return getBackend().getDeviceContext();
    }

    public OCLBackend getBackend() {
        return driver.getBackend(platformIndex, deviceIndex);
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
    public String toString() {
        return String.format(" [" + getPlatformName() + "] -- " + device.getDeviceName());
    }

    @Override
    public TornadoSchedulingStrategy getPreferredSchedule() {
        switch (Objects.requireNonNull(device.getDeviceType())) {
            case CL_DEVICE_TYPE_GPU, CL_DEVICE_TYPE_ACCELERATOR, CL_DEVICE_TYPE_CUSTOM, CL_DEVICE_TYPE_ALL -> {
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
    public KernelStackFrame createKernelStackFrame(int numArgs) {
        return getDeviceContext().getMemoryManager().createKernelStackFrame(Thread.currentThread().threadId(), numArgs);
    }

    @Override
    public XPUBuffer createOrReuseAtomicsBuffer(int[] array) {
        if (reuseBuffer == null) {
            reuseBuffer = getDeviceContext().getMemoryManager().createAtomicsBuffer(array);
        }
        reuseBuffer.setIntBuffer(array);
        return reuseBuffer;
    }

    private boolean isOpenCLPreLoadBinary(OCLDeviceContextInterface deviceContext, String deviceInfo) {
        OCLCodeCache installedCode = deviceContext.getCodeCache();
        return (installedCode.isLoadBinaryOptionEnabled() && (installedCode.getOpenCLBinary(deviceInfo) != null));
    }

    private TornadoInstalledCode compileTask(SchedulableTask task) {
        final OCLDeviceContextInterface deviceContext = getDeviceContext();
        final CompilableTask executable = (CompilableTask) task;
        final ResolvedJavaMethod resolvedMethod = TornadoCoreRuntime.getTornadoRuntime().resolveMethod(executable.getMethod());
        final Sketch sketch = TornadoSketcher.lookup(resolvedMethod, task.meta().getBackendIndex(), task.meta().getDeviceIndex());

        // Return the code from the cache
        if (!task.shouldCompile() && deviceContext.isCached(task.getId(), resolvedMethod.getName())) {
            return deviceContext.getInstalledCode(task.getId(), resolvedMethod.getName());
        }

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

            // Update atomics buffer for inner methods that are not inlined
            ResolvedJavaMethod[] methods = result.getMethods();
            if (methods.length > 1) {
                HashMap<Integer, Integer> mapping;
                for (ResolvedJavaMethod m : methods) {
                    if (TornadoAtomicIntegerNode.globalAtomicsParameters.containsKey(m)) {
                        mapping = TornadoAtomicIntegerNode.globalAtomicsParameters.get(m);
                        for (ResolvedJavaMethod mInternal : methods) {
                            // RE-MAP position
                            TornadoAtomicIntegerNode.globalAtomicsParameters.put(mInternal, mapping);
                        }
                    }
                }
            }

            profiler.stop(ProfilerType.TASK_COMPILE_GRAAL_TIME, taskMeta.getId());
            profiler.sum(ProfilerType.TOTAL_GRAAL_COMPILE_TIME, profiler.getTaskTimer(ProfilerType.TASK_COMPILE_GRAAL_TIME, taskMeta.getId()));

            profiler.start(ProfilerType.TASK_COMPILE_DRIVER_TIME, taskMeta.getId());
            // Compile the code
            OCLInstalledCode installedCode;
            if (OCLBackend.isDeviceAnFPGAAccelerator(deviceContext)) {
                // A) for FPGA
                installedCode = deviceContext.installCode(result.getId(), result.getName(), result.getTargetCode(), task.meta().isPrintKernelEnabled());
            } else {
                // B) for CPU multi-core or GPU
                installedCode = deviceContext.installCode(result);
            }
            profiler.stop(ProfilerType.TASK_COMPILE_DRIVER_TIME, taskMeta.getId());
            profiler.sum(ProfilerType.TOTAL_DRIVER_COMPILE_TIME, profiler.getTaskTimer(ProfilerType.TASK_COMPILE_DRIVER_TIME, taskMeta.getId()));

            return installedCode;
        } catch (Exception e) {
            TornadoLogger logger = new TornadoLogger();
            logger.fatal("Unable to compile %s for device %s\n", task.getId(), getDeviceName());
            logger.fatal("Exception occurred when compiling %s\n", ((CompilableTask) task).getMethod().getName());
            if (TornadoOptions.RECOVER_BAILOUT) {
                throw new TornadoBailoutRuntimeException("[Error during the Task Compilation]: " + e.getMessage());
            } else {
                throw e;
            }
        }
    }

    private TornadoInstalledCode compilePreBuiltTask(SchedulableTask task) {
        final OCLDeviceContextInterface deviceContext = getDeviceContext();
        final PrebuiltTask executable = (PrebuiltTask) task;
        if (deviceContext.isCached(task.getId(), executable.getEntryPoint())) {
            return deviceContext.getInstalledCode(task.getId(), executable.getEntryPoint());
        }

        final Path path = Paths.get(executable.getFilename());
        TornadoInternalError.guarantee(path.toFile().exists(), "file does not exist: %s", executable.getFilename());
        try {
            final byte[] source = Files.readAllBytes(path);

            OCLInstalledCode installedCode;
            if (OCLBackend.isDeviceAnFPGAAccelerator(deviceContext)) {
                // A) for FPGA
                installedCode = deviceContext.installCode(task.getId(), executable.getEntryPoint(), source, task.meta().isPrintKernelEnabled());
            } else {
                // B) for CPU multi-core or GPU
                installedCode = deviceContext.installCode(executable.meta(), task.getId(), executable.getEntryPoint(), source);
            }
            return installedCode;
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

    private String getTaskEntryName(SchedulableTask task) {
        return task.getTaskName();
    }

    private TornadoInstalledCode loadPreCompiledBinaryForTask(SchedulableTask task) {
        final OCLDeviceContextInterface deviceContext = getDeviceContext();
        final OCLCodeCache codeCache = deviceContext.getCodeCache();
        final String deviceFullName = getFullTaskIdDevice(task);
        final Path lookupPath = Paths.get(codeCache.getOpenCLBinary(deviceFullName));
        String entry = getTaskEntryName(task);

        if (deviceContext.getInstalledCode(task.getId(), entry) != null) {
            return deviceContext.getInstalledCode(task.getId(), entry);
        } else {
            return codeCache.installEntryPointForBinaryForFPGAs(task.getId(), lookupPath, entry);
        }
    }

    private String getFullTaskIdDevice(SchedulableTask task) {
        TaskMetaDataInterface meta = task.meta();
        if (meta instanceof TaskMetaData) {
            TaskMetaData metaData = (TaskMetaData) task.meta();
            return task.getId() + ".device=" + metaData.getBackendIndex() + ":" + metaData.getDeviceIndex();
        } else {
            throw new RuntimeException("[ERROR] TaskMetadata expected");
        }
    }

    @Override
    public boolean isFullJITMode(SchedulableTask task) {
        final OCLDeviceContextInterface deviceContext = getDeviceContext();
        final String deviceFullName = getFullTaskIdDevice(task);
        return (!isOpenCLPreLoadBinary(deviceContext, deviceFullName) && deviceContext.isPlatformFPGA());
    }

    @Override
    public TornadoInstalledCode getCodeFromCache(SchedulableTask task) {
        String entry = getTaskEntryName(task);
        return getDeviceContext().getInstalledCode(task.getId(), entry);
    }

    @Override
    public int[] checkAtomicsForTask(SchedulableTask task) {
        if (TornadoAtomicIntegerNode.globalAtomics.containsKey(task.meta().getCompiledResolvedJavaMethod())) {
            ArrayList<Integer> values = TornadoAtomicIntegerNode.globalAtomics.get(task.meta().getCompiledResolvedJavaMethod());
            int[] atomicsArray = new int[values.size()];
            int j = 0;
            for (Integer i : values) {
                atomicsArray[j++] = i;
            }
            return atomicsArray;
        } else {
            return null;
        }
    }

    @Override
    public int[] checkAtomicsForTask(SchedulableTask task, int[] array, int paramIndex, Object value) {
        if (value instanceof AtomicInteger) {
            AtomicInteger ai = (AtomicInteger) value;
            if (TornadoAtomicIntegerNode.globalAtomicsParameters.containsKey(task.meta().getCompiledResolvedJavaMethod())) {
                HashMap<Integer, Integer> values = TornadoAtomicIntegerNode.globalAtomicsParameters.get(task.meta().getCompiledResolvedJavaMethod());
                int index = values.get(paramIndex);
                array[index] = ai.get();
            }
        }
        return array;
    }

    @Override
    public int[] updateAtomicRegionAndObjectState(SchedulableTask task, int[] array, int paramIndex, Object value, XPUDeviceBufferState objectState) {
        int[] atomicsArray = checkAtomicsForTask(task, array, paramIndex, value);
        mappingAtomics.put(value, getAtomicsGlobalIndexForTask(task, paramIndex));
        XPUBuffer bufferAtomics = objectState.getXPUBuffer();
        bufferAtomics.setIntBuffer(atomicsArray);
        setAtomicRegion(bufferAtomics);
        objectState.setAtomicRegion(bufferAtomics);
        return atomicsArray;
    }

    @Override
    public int getAtomicsGlobalIndexForTask(SchedulableTask task, int paramIndex) {
        if (TornadoAtomicIntegerNode.globalAtomicsParameters.containsKey(task.meta().getCompiledResolvedJavaMethod())) {
            HashMap<Integer, Integer> values = TornadoAtomicIntegerNode.globalAtomicsParameters.get(task.meta().getCompiledResolvedJavaMethod());
            return values.get(paramIndex);
        }
        return -1;
    }

    @Override
    public boolean checkAtomicsParametersForTask(SchedulableTask task) {
        return TornadoAtomicIntegerNode.globalAtomicsParameters.containsKey(task.meta().getCompiledResolvedJavaMethod());
    }

    private boolean isJITTaskForFGPA(SchedulableTask task) {
        final OCLDeviceContextInterface deviceContext = getDeviceContext();
        final String deviceFullName = getFullTaskIdDevice(task);
        return !isOpenCLPreLoadBinary(deviceContext, deviceFullName) && deviceContext.isPlatformFPGA();
    }

    private boolean isJITTaskForGPUsAndCPUs(SchedulableTask task) {
        final OCLDeviceContextInterface deviceContext = getDeviceContext();
        final String deviceFullName = getFullTaskIdDevice(task);
        return !isOpenCLPreLoadBinary(deviceContext, deviceFullName) && !deviceContext.isPlatformFPGA();
    }

    private TornadoInstalledCode compileJavaForFPGAs(SchedulableTask task) {
        TornadoInstalledCode tornadoInstalledCode = compileJavaToAccelerator(task);
        if (tornadoInstalledCode != null) {
            return loadPreCompiledBinaryForTask(task);
        }
        return null;
    }

    @Override
    public TornadoInstalledCode installCode(SchedulableTask task) {
        if (isJITTaskForFGPA(task)) {
            return compileJavaForFPGAs(task);
        } else if (isJITTaskForGPUsAndCPUs(task)) {
            return compileJavaToAccelerator(task);
        }
        return loadPreCompiledBinaryForTask(task);
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

    private XPUBuffer createArrayWrapper(Class<?> type, OCLDeviceContext device, long batchSize) {
        XPUBuffer result = null;
        if (type == float[].class) {
            result = new OCLFloatArrayWrapper(device, batchSize);
        } else if (type == int[].class) {
            result = new OCLIntArrayWrapper(device, batchSize);
        } else if (type == double[].class) {
            result = new OCLDoubleArrayWrapper(device, batchSize);
        } else if (type == short[].class) {
            result = new OCLShortArrayWrapper(device, batchSize);
        } else if (type == byte[].class) {
            result = new OCLByteArrayWrapper(device, batchSize);
        } else if (type == long[].class) {
            result = new OCLLongArrayWrapper(device, batchSize);
        } else if (type == char[].class) {
            result = new OCLCharArrayWrapper(device, batchSize);
        } else {
            TornadoInternalError.unimplemented("array of type %s", type.getName());
        }
        return result;
    }

    private XPUBuffer createMultiArrayWrapper(Class<?> componentType, Class<?> type, OCLDeviceContext device, long batchSize) {
        XPUBuffer result = null;

        if (componentType == int[].class) {
            result = new OCLMultiDimArrayWrapper<>(device, (OCLDeviceContext context) -> new OCLIntArrayWrapper(context, batchSize), batchSize);
        } else if (componentType == short[].class) {
            result = new OCLMultiDimArrayWrapper<>(device, (OCLDeviceContext context) -> new OCLShortArrayWrapper(context, batchSize), batchSize);
        } else if (componentType == char[].class) {
            result = new OCLMultiDimArrayWrapper<>(device, (OCLDeviceContext context) -> new OCLCharArrayWrapper(context, batchSize), batchSize);
        } else if (componentType == byte[].class) {
            result = new OCLMultiDimArrayWrapper<>(device, (OCLDeviceContext context) -> new OCLByteArrayWrapper(context, batchSize), batchSize);
        } else if (componentType == float[].class) {
            result = new OCLMultiDimArrayWrapper<>(device, (OCLDeviceContext context) -> new OCLFloatArrayWrapper(context, batchSize), batchSize);
        } else if (componentType == double[].class) {
            result = new OCLMultiDimArrayWrapper<>(device, (OCLDeviceContext context) -> new OCLDoubleArrayWrapper(context, batchSize), batchSize);
        } else if (componentType == long[].class) {
            result = new OCLMultiDimArrayWrapper<>(device, (OCLDeviceContext context) -> new OCLLongArrayWrapper(context, batchSize), batchSize);
        } else {
            TornadoInternalError.unimplemented("array of type %s", type.getName());
        }
        return result;
    }

    private XPUBuffer createDeviceBuffer(Class<?> type, Object object, OCLDeviceContext deviceContext, long batchSize) {
        XPUBuffer result = null;
        if (type.isArray()) {
            if (!type.getComponentType().isArray()) {
                result = createArrayWrapper(type, deviceContext, batchSize);
            } else {
                final Class<?> componentType = type.getComponentType();
                if (RuntimeUtilities.isPrimitiveArray(componentType)) {
                    result = createMultiArrayWrapper(componentType, type, deviceContext, batchSize);
                } else {
                    TornadoInternalError.unimplemented("multi-dimensional array of type %s", type.getName());
                }
            }
        } else if (!type.isPrimitive()) {
            if (object instanceof AtomicInteger) {
                result = new AtomicsBuffer(new int[] {}, deviceContext);
            } else if (object.getClass().getAnnotation(Vector.class) != null) {
                result = new OCLVectorWrapper(deviceContext, object, batchSize);
            } else if (object instanceof MemorySegment) {
                result = new OCLMemorySegmentWrapper(deviceContext, batchSize);
            } else if (object instanceof IntArray) {
                result = new OCLMemorySegmentWrapper(deviceContext, batchSize);
            } else if (object instanceof FloatArray) {
                result = new OCLMemorySegmentWrapper(deviceContext, batchSize);
            } else if (object instanceof DoubleArray) {
                result = new OCLMemorySegmentWrapper(deviceContext, batchSize);
            } else if (object instanceof LongArray) {
                result = new OCLMemorySegmentWrapper(deviceContext, batchSize);
            } else if (object instanceof ShortArray) {
                result = new OCLMemorySegmentWrapper(deviceContext, batchSize);
            } else if (object instanceof ByteArray) {
                result = new OCLMemorySegmentWrapper(deviceContext, batchSize);
            } else if (object instanceof CharArray) {
                result = new OCLMemorySegmentWrapper(deviceContext, batchSize);
            } else if (object instanceof HalfFloatArray) {
                result = new OCLMemorySegmentWrapper(deviceContext, batchSize);
            } else {
                result = new OCLXPUBuffer(deviceContext, object);
            }
        }

        TornadoInternalError.guarantee(result != null, "Unable to create buffer for object: " + type);
        return result;
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

    private XPUBuffer newDeviceBufferAllocation(Object object, long batchSize, DeviceBufferState deviceObjectState) {
        final XPUBuffer buffer;
        TornadoInternalError.guarantee(deviceObjectState.isAtomicRegionPresent() || !deviceObjectState.hasObjectBuffer(), "A device memory leak might be occurring.");
        buffer = createDeviceBuffer(object.getClass(), object, (OCLDeviceContext) getDeviceContext(), batchSize);
        deviceObjectState.setXPUBuffer(buffer);
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
            buffer = newDeviceBufferAllocation(object, batchSize, state);
        }

        if (buffer.getClass() == AtomicsBuffer.class) {
            state.setAtomicRegion();
        }
        return -1;
    }

    @Override
    public synchronized int deallocate(DeviceBufferState deviceBufferState) {
        if (deviceBufferState.isLockedBuffer()) {
            return -1;
        }
        deviceBufferState.getXPUBuffer().deallocate();
        deviceBufferState.setContents(false);
        deviceBufferState.setXPUBuffer(null);
        return -1;
    }

    @Override
    public List<Integer> ensurePresent(long executionPlanId, Object object, DeviceBufferState state, int[] events, long batchSize, long offset) {
        if (!state.hasContent() || BENCHMARKING_MODE) {
            state.setContents(true);
            return state.getXPUBuffer().enqueueWrite(executionPlanId, object, batchSize, offset, events, events == null);
        }
        return null;
    }

    @Override
    public List<Integer> streamIn(long executionPlanId, Object object, long batchSize, long offset, DeviceBufferState state, int[] events) {
        state.setContents(true);
        return state.getXPUBuffer().enqueueWrite(executionPlanId, object, batchSize, offset, events, events == null);
    }

    @Override
    public int streamOut(long executionPlanId, Object object, long offset, DeviceBufferState state, int[] events) {
        TornadoInternalError.guarantee(state.hasObjectBuffer(), "invalid variable");
        int event = state.getXPUBuffer().enqueueRead(executionPlanId, object, offset, events, events == null);
        if (events != null) {
            return event;
        }
        return -1;
    }

    @Override
    public int streamOutBlocking(long executionPlanId, Object object, long hostOffset, DeviceBufferState state, int[] events) {
        long partialCopySize = state.getPartialCopySize();
        if (state.isAtomicRegionPresent()) {
            // Read for Atomics
            int eventID = state.getXPUBuffer().enqueueRead(executionPlanId, null, 0, null, false);
            if (object instanceof AtomicInteger) {
                int[] arr = getAtomic().getIntBuffer();
                int indexFromGlobalRegion = mappingAtomics.get(object);
                ((AtomicInteger) object).set(arr[indexFromGlobalRegion]);
            }
            return eventID;
        } else {
            // Read for any other buffer that is not an atomic buffer
            TornadoInternalError.guarantee(state.hasObjectBuffer(), "invalid variable");
            return state.getXPUBuffer().read(executionPlanId, object, hostOffset, partialCopySize, events, events == null);
        }
    }

    @Override
    public void flush(long executionPlanId) {
        this.getDeviceContext().flush(executionPlanId);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof OCLTornadoDevice other) {
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
        getDeviceContext().sync(executionPlanId);
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
    public Event resolveEvent(long executionPlanId, int event) {
        return getDeviceContext().resolveEvent(executionPlanId, event);
    }

    @Override
    public void flushEvents(long executionPlanId) {
        getDeviceContext().flushEvents(executionPlanId);
    }

    @Override
    public String getDeviceName() {
        return String.format("opencl-%d-%d", platformIndex, deviceIndex);
    }

    @Override
    public TornadoDeviceType getDeviceType() {
        OCLDeviceType deviceType = device.getDeviceType();
        return switch (deviceType) {
            case CL_DEVICE_TYPE_CPU -> TornadoDeviceType.CPU;
            case CL_DEVICE_TYPE_GPU -> TornadoDeviceType.GPU;
            case CL_DEVICE_TYPE_ACCELERATOR -> TornadoDeviceType.ACCELERATOR;
            case CL_DEVICE_TYPE_CUSTOM -> TornadoDeviceType.CUSTOM;
            case CL_DEVICE_TYPE_ALL -> TornadoDeviceType.ALL;
            case CL_DEVICE_TYPE_DEFAULT -> TornadoDeviceType.DEFAULT;
            default -> throw new RuntimeException("Device not supported");
        };
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
        return TornadoCoreRuntime.getTornadoRuntime().getBackendIndex(OCLBackendImpl.class);
    }

    @Override
    public XPUBuffer getAtomic() {
        return reuseBuffer;
    }

    @Override
    public void setAtomicsMapping(ConcurrentHashMap<Object, Integer> mappingAtomics) {
        this.mappingAtomics = mappingAtomics;
    }

    @Override
    public void enableThreadSharing() {
        // OpenCL device context is shared by different threads, by default
    }

    @Override
    public void setAtomicRegion(XPUBuffer bufferAtomics) {
        reuseBuffer = bufferAtomics;
    }

    @Override
    public TornadoVMBackendType getTornadoVMBackend() {
        return TornadoVMBackendType.OPENCL;
    }

    @Override
    public boolean isSPIRVSupported() {
        // An OpenCL device supports SPIR-V if the version is >= 2.1
        String version = device.getDeviceContext().getPlatformContext().getPlatform().getVersion();

        if (version.contains("CUDA")) {
            // Currently, the CUDA platform does not allow dispatching SPIR-V kernels
            return false;
        }

        Matcher matcher = NAME_PATTERN.matcher(version);
        int major = 0;
        int minor = 0;
        if (matcher.find()) {
            major = Integer.parseInt(matcher.group(1));
            minor = Integer.parseInt(matcher.group(2));
        }
        if (major > 2) {
            return true;
        }
        if (major == 2 && minor >= 1) {
            return true;
        }
        return false;
    }

}
