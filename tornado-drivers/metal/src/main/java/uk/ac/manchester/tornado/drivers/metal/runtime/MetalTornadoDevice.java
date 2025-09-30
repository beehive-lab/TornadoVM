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
package uk.ac.manchester.tornado.drivers.metal.runtime;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
import uk.ac.manchester.tornado.api.memory.TornadoMemoryProvider;
import uk.ac.manchester.tornado.api.memory.XPUBuffer;
import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;
import uk.ac.manchester.tornado.api.runtime.TaskContextInterface;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.CharArray;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;
import uk.ac.manchester.tornado.drivers.common.TornadoBufferProvider;
import uk.ac.manchester.tornado.drivers.metal.MetalBackendImpl;
import uk.ac.manchester.tornado.drivers.metal.MetalCodeCache;
import uk.ac.manchester.tornado.drivers.metal.MetalDeviceContext;
import uk.ac.manchester.tornado.drivers.metal.MetalDeviceContextInterface;
import uk.ac.manchester.tornado.drivers.metal.MetalTargetDevice;
import uk.ac.manchester.tornado.drivers.metal.enums.MetalDeviceType;
import uk.ac.manchester.tornado.drivers.metal.graal.MetalInstalledCode;
import uk.ac.manchester.tornado.drivers.metal.graal.MetalProviders;
import uk.ac.manchester.tornado.drivers.metal.graal.backend.MetalBackend;
import uk.ac.manchester.tornado.drivers.metal.graal.compiler.MetalCompilationResult;
import uk.ac.manchester.tornado.drivers.metal.graal.compiler.MetalCompiler;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalKind;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.TornadoAtomicIntegerNode;
import uk.ac.manchester.tornado.drivers.metal.mm.MetalAtomicsBuffer;
import uk.ac.manchester.tornado.drivers.metal.mm.MetalByteArrayWrapper;
import uk.ac.manchester.tornado.drivers.metal.mm.MetalCharArrayWrapper;
import uk.ac.manchester.tornado.drivers.metal.mm.MetalDoubleArrayWrapper;
import uk.ac.manchester.tornado.drivers.metal.mm.MetalFieldBuffer;
import uk.ac.manchester.tornado.drivers.metal.mm.MetalFloatArrayWrapper;
import uk.ac.manchester.tornado.drivers.metal.mm.MetalIntArrayWrapper;
import uk.ac.manchester.tornado.drivers.metal.mm.MetalLongArrayWrapper;
import uk.ac.manchester.tornado.drivers.metal.mm.MetalMemorySegmentWrapper;
import uk.ac.manchester.tornado.drivers.metal.mm.MetalMultiDimArrayWrapper;
import uk.ac.manchester.tornado.drivers.metal.mm.MetalShortArrayWrapper;
import uk.ac.manchester.tornado.drivers.metal.mm.MetalVectorWrapper;
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

public class MetalTornadoDevice implements TornadoXPUDevice {

    private static MetalBackendImpl driver = null;
    private static final Pattern NAME_PATTERN = Pattern.compile("^Metal (\\d)\\.(\\d).*");
    private final MetalTargetDevice device;
    private final int deviceIndex;
    private final int platformIndex;
    private final String platformName;
    private XPUBuffer atomicsBuffer;
    private ConcurrentHashMap<Object, Integer> mappingAtomics;
    private TornadoLogger logger = new TornadoLogger(this.getClass());

    /**
     * Constructor used also in SLAMBench/KFusion
     *
     * @param platformIndex
     *     Metal Platform index
     * @param deviceIndex
     *     Metal Device Index
     */
    public MetalTornadoDevice(final int platformIndex, final int deviceIndex) {
        this.platformIndex = platformIndex;
        this.deviceIndex = deviceIndex;
        driver = TornadoCoreRuntime.getTornadoRuntime().getBackend(MetalBackendImpl.class);

        if (driver == null) {
            throw new RuntimeException("TornadoVM Metal Driver not found");
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
    public MetalTargetDevice getPhysicalDevice() {
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
    public String getDeviceOpenCLCVersion() {
        return "N/A";
    }

    @Override
    public MetalDeviceContextInterface getDeviceContext() {
        return getBackend().getDeviceContext();
    }

    public MetalBackend getBackend() {
        return driver.getBackend(platformIndex, deviceIndex);
    }

    private void disableProfilerOptions() {
        TornadoOptions.TORNADO_PROFILER_LOG = false;
        TornadoOptions.TORNADO_PROFILER = false;
    }

    @Override
    public void clean() {
        Set<Long> ids = new HashSet<>(device.getDeviceContext().getRegisteredPlanIds());
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
        final MetalBackend backend = getBackend();
        if (!backend.isInitialised()) {
            backend.init();
        }
    }

    @Override
    public KernelStackFrame createKernelStackFrame(long executionPlanId, int numArgs, Access access) {
        return getDeviceContext().getMemoryManager().createKernelStackFrame(executionPlanId, numArgs);
    }

    @Override
    public XPUBuffer createOrReuseAtomicsBuffer(int[] array, Access access) {
        if (atomicsBuffer == null) {
            atomicsBuffer = getDeviceContext().getMemoryManager().createAtomicsBuffer(array, access);
        }
        atomicsBuffer.setIntBuffer(array);
        return atomicsBuffer;
    }

    private boolean isMetalPreLoadBinary(long executionPlanId, MetalDeviceContextInterface deviceContext, String deviceInfo) {
        MetalCodeCache installedCode = deviceContext.getCodeCache(executionPlanId);
        return (installedCode.isLoadBinaryOptionEnabled() && (installedCode.getMetalBinary(deviceInfo) != null));
    }

    private TornadoInstalledCode compileTask(long executionPlanId, SchedulableTask task) {
        final MetalDeviceContextInterface deviceContext = getDeviceContext();
        final CompilableTask executable = (CompilableTask) task;
        final ResolvedJavaMethod resolvedMethod = TornadoCoreRuntime.getTornadoRuntime().resolveMethod(executable.getMethod());
        final Sketch sketch = TornadoSketcher.lookup(resolvedMethod, task.meta().getBackendIndex(), task.meta().getDeviceIndex());

        // Return the code from the cache
        if (!task.shouldCompile() && deviceContext.isCached(executionPlanId, task.getId(), resolvedMethod.getName())) {
            return deviceContext.getInstalledCode(executionPlanId, task.getId(), resolvedMethod.getName());
        }

        // copy meta data into task
        final TaskDataContext taskMeta = executable.meta();
        final Access[] sketchAccess = sketch.getArgumentsAccess();
        final Access[] taskAccess = taskMeta.getArgumentsAccess();
        System.arraycopy(sketchAccess, 0, taskAccess, 0, sketchAccess.length);

        try {
            MetalProviders providers = (MetalProviders) getBackend().getProviders();
            TornadoProfiler profiler = task.getProfiler();
            profiler.start(ProfilerType.TASK_COMPILE_GRAAL_TIME, taskMeta.getId());
            final MetalCompilationResult result = MetalCompiler.compileSketchForDevice(sketch, executable, providers, getBackend(), executable.getProfiler());

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
            MetalInstalledCode installedCode;
            if (MetalBackend.isDeviceAnFPGAAccelerator(deviceContext)) {
                // A) for FPGA
                installedCode = deviceContext.installCode(executionPlanId, result.getId(), result.getName(), result.getTargetCode(), task.meta().isPrintKernelEnabled());
            } else {
                // B) for CPU multi-core or GPU
                installedCode = deviceContext.installCode(executionPlanId, result);
            }
            profiler.stop(ProfilerType.TASK_COMPILE_DRIVER_TIME, taskMeta.getId());
            profiler.sum(ProfilerType.TOTAL_DRIVER_COMPILE_TIME, profiler.getTaskTimer(ProfilerType.TASK_COMPILE_DRIVER_TIME, taskMeta.getId()));

            return installedCode;
        } catch (Exception e) {
            logger.fatal("Unable to compile %s for device %s\n", task.getId(), getDeviceName());
            logger.fatal("Exception occurred when compiling %s\n", ((CompilableTask) task).getMethod().getName());
            if (TornadoOptions.RECOVER_BAILOUT) {
                throw new TornadoBailoutRuntimeException("[Error during the Task Compilation]: " + e.getMessage());
            } else {
                throw e;
            }
        }
    }

    private TornadoInstalledCode compilePreBuiltTask(long executionPlanId, SchedulableTask task) {
        final MetalDeviceContextInterface deviceContext = getDeviceContext();
        final PrebuiltTask executable = (PrebuiltTask) task;
        if (deviceContext.isCached(executionPlanId, task.getId(), executable.getEntryPoint())) {
            return deviceContext.getInstalledCode(executionPlanId, task.getId(), executable.getEntryPoint());
        }

        final Path path = Paths.get(executable.getFilename());
        TornadoInternalError.guarantee(path.toFile().exists(), "file does not exist: %s", executable.getFilename());
        try {
            final byte[] source = Files.readAllBytes(path);

            MetalInstalledCode installedCode;
            if (MetalBackend.isDeviceAnFPGAAccelerator(deviceContext)) {
                // A) for FPGA
                installedCode = deviceContext.installCode(executionPlanId, task.getId(), executable.getEntryPoint(), source, task.meta().isPrintKernelEnabled());
            } else {
                // B) for CPU multi-core or GPU
                installedCode = deviceContext.installCode(executionPlanId, executable.meta(), task.getId(), executable.getEntryPoint(), source);
            }
            return installedCode;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private TornadoInstalledCode compileJavaToAccelerator(long executionPlanId, SchedulableTask task) {
        if (task instanceof CompilableTask) {
            return compileTask(executionPlanId, task);
        } else if (task instanceof PrebuiltTask) {
            return compilePreBuiltTask(executionPlanId, task);
        }
        TornadoInternalError.shouldNotReachHere("task of unknown type: " + task.getClass().getSimpleName());
        return null;
    }

    private String getTaskEntryName(SchedulableTask task) {
        return task.getTaskName();
    }

    private TornadoInstalledCode loadPreCompiledBinaryForTask(long executionPlanId, SchedulableTask task) {
        final MetalDeviceContextInterface deviceContext = getDeviceContext();
        final MetalCodeCache codeCache = deviceContext.getCodeCache(executionPlanId);
        final String deviceFullName = getFullTaskIdDevice(task);
        final Path lookupPath = Paths.get(codeCache.getMetalBinary(deviceFullName));
        String entry = getTaskEntryName(task);

        if (deviceContext.getInstalledCode(executionPlanId, task.getId(), entry) != null) {
            return deviceContext.getInstalledCode(executionPlanId, task.getId(), entry);
        } else {
            return codeCache.installEntryPointForBinaryForFPGAs(task.getId(), lookupPath, entry);
        }
    }

    private String getFullTaskIdDevice(SchedulableTask task) {
        TaskContextInterface meta = task.meta();
        if (meta instanceof TaskDataContext) {
            TaskDataContext metaData = (TaskDataContext) task.meta();
            return task.getId() + ".device=" + metaData.getBackendIndex() + ":" + metaData.getDeviceIndex();
        } else {
            throw new RuntimeException("[ERROR] TaskMetadata expected");
        }
    }

    @Override
    public boolean isFullJITMode(long executionPlanId, SchedulableTask task) {
        final MetalDeviceContextInterface deviceContext = getDeviceContext();
        final String deviceFullName = getFullTaskIdDevice(task);
        return (!isMetalPreLoadBinary(executionPlanId, deviceContext, deviceFullName) && deviceContext.isPlatformFPGA());
    }

    @Override
    public TornadoInstalledCode getCodeFromCache(long executionPlanId, SchedulableTask task) {
        String entry = getTaskEntryName(task);
        return getDeviceContext().getInstalledCode(executionPlanId, task.getId(), entry);
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
        if (value instanceof AtomicInteger ai) {
            Object compiledResolvedJavaMethod = task.meta().getCompiledResolvedJavaMethod();
            if (TornadoAtomicIntegerNode.globalAtomicsParameters.containsKey(compiledResolvedJavaMethod)) {
                HashMap<Integer, Integer> values = TornadoAtomicIntegerNode.globalAtomicsParameters.get(compiledResolvedJavaMethod);
                int index = values.get(paramIndex);
                array[index] = ai.get();
            }
        }
        return array;
    }

    @Override
    public int[] updateAtomicRegionAndObjectState(SchedulableTask task, int[] array, int paramIndex, Object value, XPUDeviceBufferState atomicState) {
        int[] atomicsArray = checkAtomicsForTask(task, array, paramIndex, value);
        mappingAtomics.put(value, getAtomicsGlobalIndexForTask(task, paramIndex));
        XPUBuffer xpuBufferForAtomic = atomicState.getXPUBuffer();
        xpuBufferForAtomic.setIntBuffer(atomicsArray);
        this.atomicsBuffer = xpuBufferForAtomic;
        atomicState.setAtomicRegion(xpuBufferForAtomic);
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

    private boolean isJITTaskForFGPA(long executionPlanId, SchedulableTask task) {
        final MetalDeviceContextInterface deviceContext = getDeviceContext();
        final String deviceFullName = getFullTaskIdDevice(task);
        return !isMetalPreLoadBinary(executionPlanId, deviceContext, deviceFullName) && deviceContext.isPlatformFPGA();
    }

    private boolean isJITTaskForGPUsAndCPUs(long executionplanId, SchedulableTask task) {
        final MetalDeviceContextInterface deviceContext = getDeviceContext();
        final String deviceFullName = getFullTaskIdDevice(task);
        return !isMetalPreLoadBinary(executionplanId, deviceContext, deviceFullName) && !deviceContext.isPlatformFPGA();
    }

    private TornadoInstalledCode compileJavaForFPGAs(long executionPlanId, SchedulableTask task) {
        TornadoInstalledCode tornadoInstalledCode = compileJavaToAccelerator(executionPlanId, task);
        if (tornadoInstalledCode != null) {
            return loadPreCompiledBinaryForTask(executionPlanId, task);
        }
        return null;
    }

    @Override
    public TornadoInstalledCode installCode(long executionPlanId, SchedulableTask task) {
        if (isJITTaskForFGPA(executionPlanId, task)) {
            return compileJavaForFPGAs(executionPlanId, task);
        } else if (isJITTaskForGPUsAndCPUs(executionPlanId, task)) {
            return compileJavaToAccelerator(executionPlanId, task);
        }
        return loadPreCompiledBinaryForTask(executionPlanId, task);
    }

    private XPUBuffer createArrayWrapper(Class<?> type, MetalDeviceContext device, long batchSize, Access access) {
        XPUBuffer result = null;
        if (type == float[].class) {
            result = new MetalFloatArrayWrapper(device, batchSize, access);
        } else if (type == int[].class) {
            result = new MetalIntArrayWrapper(device, batchSize, access);
        } else if (type == double[].class) {
            result = new MetalDoubleArrayWrapper(device, batchSize, access);
        } else if (type == short[].class) {
            result = new MetalShortArrayWrapper(device, batchSize, access);
        } else if (type == byte[].class) {
            result = new MetalByteArrayWrapper(device, batchSize, access);
        } else if (type == long[].class) {
            result = new MetalLongArrayWrapper(device, batchSize, access);
        } else if (type == char[].class) {
            result = new MetalCharArrayWrapper(device, batchSize, access);
        } else {
            TornadoInternalError.unimplemented("array of type %s", type.getName());
        }
        return result;
    }

    private XPUBuffer createMultiArrayWrapper(Class<?> componentType, Class<?> type, MetalDeviceContext device, long batchSize, Access access) {
        XPUBuffer result = null;

        if (componentType == int[].class) {
            result = new MetalMultiDimArrayWrapper<>(device, (MetalDeviceContext context) -> new MetalIntArrayWrapper(context, batchSize, access), batchSize, access);
        } else if (componentType == short[].class) {
            result = new MetalMultiDimArrayWrapper<>(device, (MetalDeviceContext context) -> new MetalShortArrayWrapper(context, batchSize, access), batchSize, access);
        } else if (componentType == char[].class) {
            result = new MetalMultiDimArrayWrapper<>(device, (MetalDeviceContext context) -> new MetalCharArrayWrapper(context, batchSize, access), batchSize, access);
        } else if (componentType == byte[].class) {
            result = new MetalMultiDimArrayWrapper<>(device, (MetalDeviceContext context) -> new MetalByteArrayWrapper(context, batchSize, access), batchSize, access);
        } else if (componentType == float[].class) {
            result = new MetalMultiDimArrayWrapper<>(device, (MetalDeviceContext context) -> new MetalFloatArrayWrapper(context, batchSize, access), batchSize, access);
        } else if (componentType == double[].class) {
            result = new MetalMultiDimArrayWrapper<>(device, (MetalDeviceContext context) -> new MetalDoubleArrayWrapper(context, batchSize, access), batchSize, access);
        } else if (componentType == long[].class) {
            result = new MetalMultiDimArrayWrapper<>(device, (MetalDeviceContext context) -> new MetalLongArrayWrapper(context, batchSize, access), batchSize, access);
        } else {
            TornadoInternalError.unimplemented("array of type %s", type.getName());
        }
        return result;
    }

    private XPUBuffer createDeviceBuffer(Class<?> type, Object object, MetalDeviceContext deviceContext, long batchSize, Access access) {
        XPUBuffer result = null;
        if (type.isArray()) {
            if (!type.getComponentType().isArray()) {
                result = createArrayWrapper(type, deviceContext, batchSize, access);
            } else {
                final Class<?> componentType = type.getComponentType();
                if (RuntimeUtilities.isPrimitiveArray(componentType)) {
                    result = createMultiArrayWrapper(componentType, type, deviceContext, batchSize, access);
                } else {
                    TornadoInternalError.unimplemented("multi-dimensional array of type %s", type.getName());
                }
            }
        } else if (!type.isPrimitive()) {
            if (object instanceof AtomicInteger) {
                result = new MetalAtomicsBuffer(new int[] {}, deviceContext, access);
            } else if (object.getClass().getAnnotation(Vector.class) != null) {
                result = new MetalVectorWrapper(deviceContext, object, batchSize, access);
            } else if (object instanceof MemorySegment) {
                result = new MetalMemorySegmentWrapper(deviceContext, batchSize, access, 0);
            } else if (object instanceof IntArray) {
                result = new MetalMemorySegmentWrapper(deviceContext, batchSize, access, MetalKind.INT.getSizeInBytes());
            } else if (object instanceof FloatArray) {
                result = new MetalMemorySegmentWrapper(deviceContext, batchSize, access, MetalKind.FLOAT.getSizeInBytes());
            } else if (object instanceof DoubleArray) {
                result = new MetalMemorySegmentWrapper(deviceContext, batchSize, access, MetalKind.DOUBLE.getSizeInBytes());
            } else if (object instanceof LongArray) {
                result = new MetalMemorySegmentWrapper(deviceContext, batchSize, access, MetalKind.LONG.getSizeInBytes());
            } else if (object instanceof ShortArray) {
                result = new MetalMemorySegmentWrapper(deviceContext, batchSize, access, MetalKind.SHORT.getSizeInBytes());
            } else if (object instanceof ByteArray) {
                result = new MetalMemorySegmentWrapper(deviceContext, batchSize, access, MetalKind.CHAR.getSizeInBytes());
            } else if (object instanceof CharArray) {
                result = new MetalMemorySegmentWrapper(deviceContext, batchSize, access, MetalKind.CHAR.getSizeInBytes());
            } else if (object instanceof HalfFloatArray) {
                result = new MetalMemorySegmentWrapper(deviceContext, batchSize, access, MetalKind.HALF.getSizeInBytes());
            } else {
                result = new MetalFieldBuffer(deviceContext, object, access);
            }
        }

        TornadoInternalError.guarantee(result != null, "Unable to create buffer for object: " + type);
        return result;
    }

    private HashMap<Access, Integer> getNumOfDistinctAccess(Access[] accesses) {
        HashMap<Access, Integer> distinctAccesses = new HashMap<>();
        for (Access access : accesses) {
            if (distinctAccesses.containsKey(access)) {
                int numOfAccesses = distinctAccesses.get(access);
                distinctAccesses.replace(access, numOfAccesses, numOfAccesses + 1);
            } else {
                distinctAccesses.put(access, 1);
            }
        }
        return distinctAccesses;
    }

    @Override
    public synchronized long allocateObjects(Object[] objects, long batchSize, DeviceBufferState[] states, Access[] accesses) {
        TornadoBufferProvider bufferProvider = getDeviceContext().getBufferProvider();
        HashMap<Access, Integer> distinctAccesses = getNumOfDistinctAccess(accesses);
        for (Access access : distinctAccesses.keySet()) {
            int numOfObjectsForAccessType = distinctAccesses.get(access);
            if (!bufferProvider.isNumFreeBuffersAvailable(numOfObjectsForAccessType, access)) {
                bufferProvider.resetBuffers(access);
            }
        }

        long allocatedSpace = 0L;
        for (int i = 0; i < objects.length; i++) {
            if (!reuseBatchBuffer(batchSize, accesses[i], bufferProvider, distinctAccesses, states[i])) {
                logger.debug("Allocate object %s with access: %s", objects[i], accesses[i]);
                allocatedSpace += allocate(objects[i], batchSize, states[i], accesses[i]);
            }

        }
        return allocatedSpace;
    }

    private boolean reuseBatchBuffer(long batchSize, Access access, TornadoBufferProvider bufferProvider, HashMap<Access, Integer> distinctAccesses, DeviceBufferState state) {
        if (batchSize != 0) {
            int numberOfBuffersForAccessType = distinctAccesses.get(access);
            // if there is a buffer available in the used-list with the same access type, reuse it
            if (bufferProvider.reuseBufferForBatchProcessing(batchSize, access, numberOfBuffersForAccessType)) {
                state.markBufferAsReused();
                return true;
            }
        }
        return false;
    }

    private XPUBuffer newDeviceBufferAllocation(Object object, long batchSize, DeviceBufferState deviceObjectState, Access access) {
        final XPUBuffer buffer;
        TornadoInternalError.guarantee(deviceObjectState.isAtomicRegionPresent() || !deviceObjectState.hasObjectBuffer() || batchSize != 0, "A device memory leak might be occurring.");
        buffer = createDeviceBuffer(object.getClass(), object, (MetalDeviceContext) getDeviceContext(), batchSize, access);
        deviceObjectState.setXPUBuffer(buffer);
        buffer.allocate(object, batchSize, access);
        return buffer;
    }

    @Override
    public long allocate(Object object, long batchSize, DeviceBufferState state, Access access) {
        final XPUBuffer buffer;
        if (state.hasObjectBuffer() && state.isLockedBuffer()) {
            buffer = state.getXPUBuffer();
            if (batchSize != 0) {
                buffer.setSizeSubRegion(batchSize);
            }
        } else {
            buffer = newDeviceBufferAllocation(object, batchSize, state, access);
        }

        if (buffer.getClass() == MetalAtomicsBuffer.class) {
            state.setAtomicRegion();
        }
        return state.getXPUBuffer().size();
    }

    @Override
    public synchronized long deallocate(DeviceBufferState deviceBufferState) {
        long deallocatedSpace = 0L;
        if (deviceBufferState.isLockedBuffer()) {
            return deallocatedSpace;
        }
        deviceBufferState.getXPUBuffer().markAsFreeBuffer();
        if (TornadoOptions.isDeallocateBufferEnabled()) {
            deallocatedSpace = deviceBufferState.getXPUBuffer().deallocate();
        }
        deviceBufferState.setContents(false);
        deviceBufferState.setXPUBuffer(null);
        return deallocatedSpace;
    }

    @Override
    public List<Integer> ensurePresent(long executionPlanId, Object object, DeviceBufferState state, int[] events, long batchSize, long offset) {
        if (!state.hasContent()) {
            state.setContents(true);
            return state.getXPUBuffer().enqueueWrite(executionPlanId, object, batchSize, offset, events, events == null);
        }
        // return a NULL list
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
        if (obj instanceof MetalTornadoDevice other) {
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
        return String.format("metal-%d-%d", platformIndex, deviceIndex);
    }

    @Override
    public TornadoDeviceType getDeviceType() {
        MetalDeviceType deviceType = device.getDeviceType();
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
    public Object getDeviceInfo() {
        return device.getDeviceInfo();
    }

    @Override
    public int getBackendIndex() {
        return TornadoCoreRuntime.getTornadoRuntime().getBackendIndex(MetalBackendImpl.class);
    }

    @Override
    public XPUBuffer getAtomic() {
        return atomicsBuffer;
    }

    @Override
    public void setAtomicsMapping(ConcurrentHashMap<Object, Integer> mappingAtomics) {
        this.mappingAtomics = mappingAtomics;
    }

    @Override
    public void enableThreadSharing() {
        // Metal device context is shared by different threads, by default
    }

    @Override
    public void setAtomicRegion(XPUBuffer bufferAtomics) {
        atomicsBuffer = bufferAtomics;
    }

    @Override
    public TornadoVMBackendType getTornadoVMBackend() {
        return TornadoVMBackendType.METAL;
    }

    @Override
    public boolean isSPIRVSupported() {
        // An Metal device supports SPIR-V if the version is >= 2.1
        String version = device.getDeviceContext().getPlatformContext().getPlatform().getVersion();

        if (version.contains("CUDA")) {
            // Currently, the CUDA platform does not allow dispatching SPIR-V kernels
            return false;
        }

        Matcher matcher = NAME_PATTERN.matcher(version);
        int majorVersion = 0;
        int minorVersion = 0;
        if (matcher.find()) {
            majorVersion = Integer.parseInt(matcher.group(1));
            minorVersion = Integer.parseInt(matcher.group(2));
        }
        if (majorVersion > 2) {
            return true;
        }
        return majorVersion == 2 && minorVersion >= 1;
    }

    @Override
    public void mapDeviceRegion(long executionPlanId, Object destArray, Object srcArray, DeviceBufferState deviceStateSrc, DeviceBufferState deviceStateDest, long offset) {
        XPUBuffer devicePointer = deviceStateDest.getXPUBuffer();
        XPUBuffer srcPointer = deviceStateSrc.getXPUBuffer();
        devicePointer.mapOnDeviceMemoryRegion(executionPlanId, srcPointer, offset);
    }

}
