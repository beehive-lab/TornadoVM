/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
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
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.runtime;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.exceptions.TornadoOutOfMemoryException;
import uk.ac.manchester.tornado.api.mm.ObjectBuffer;
import uk.ac.manchester.tornado.api.mm.TaskMetaDataInterface;
import uk.ac.manchester.tornado.api.mm.TornadoDeviceObjectState;
import uk.ac.manchester.tornado.api.mm.TornadoMemoryProvider;
import uk.ac.manchester.tornado.drivers.opencl.OCLCodeCache;
import uk.ac.manchester.tornado.drivers.opencl.OCLDevice;
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContext;
import uk.ac.manchester.tornado.drivers.opencl.OCLDriver;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLDeviceType;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLProviders;
import uk.ac.manchester.tornado.drivers.opencl.graal.backend.OCLBackend;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompilationResult;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompiler;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLByteArrayWrapper;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLByteBuffer;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLCharArrayWrapper;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLDoubleArrayWrapper;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLFloatArrayWrapper;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLIntArrayWrapper;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLLongArrayWrapper;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLMemoryManager;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLMultiDimArrayWrapper;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLObjectWrapper;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLShortArrayWrapper;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.CallStack;
import uk.ac.manchester.tornado.runtime.common.DeviceObjectState;
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

public class OCLTornadoDevice implements TornadoAcceleratorDevice {

    private final OCLDevice device;
    private final int deviceIndex;
    private final int platformIndex;
    private static OCLDriver driver = null;
    private String platformName;

    private static OCLDriver findDriver() {
        if (driver == null) {
            driver = TornadoCoreRuntime.getTornadoRuntime().getDriver(OCLDriver.class);
            TornadoInternalError.guarantee(driver != null, "unable to find OpenCL driver");
        }
        return driver;
    }

    public OCLTornadoDevice(final int platformIndex, final int deviceIndex) {
        this.platformIndex = platformIndex;
        this.deviceIndex = deviceIndex;

        platformName = findDriver().getPlatformContext(platformIndex).getPlatform().getName();
        device = findDriver().getPlatformContext(platformIndex).devices().get(deviceIndex);

    }

    @Override
    public void dumpEvents() {
        getDeviceContext().dumpEvents();
    }

    @Override
    public String getDescription() {
        final String availability = (device.isAvailable()) ? "available" : "not available";
        return String.format("%s %s (%s)", device.getName(), device.getDeviceType(), availability);
    }

    @Override
    public String getPlatformName() {
        return platformName;
    }

    @Override
    public OCLDevice getDevice() {
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
    public OCLDeviceContext getDeviceContext() {
        return getBackend().getDeviceContext();
    }

    public OCLBackend getBackend() {
        return findDriver().getBackend(platformIndex, deviceIndex);
    }

    @Override
    public void reset() {
        getBackend().reset();
    }

    @Override
    public String toString() {
        return String.format(getPlatformName() + " -- " + device.getName());
    }

    @Override
    public TornadoSchedulingStrategy getPreferedSchedule() {
        if (null != device.getDeviceType()) {

            if (Tornado.FORCE_ALL_TO_GPU) {
                return TornadoSchedulingStrategy.PER_ITERATION;
            }

            switch (device.getDeviceType()) {
                case CL_DEVICE_TYPE_GPU:
                    return TornadoSchedulingStrategy.PER_ITERATION;
                case CL_DEVICE_TYPE_CPU:
                    return TornadoSchedulingStrategy.PER_BLOCK;
                default:
                    return TornadoSchedulingStrategy.PER_ITERATION;
            }
        }
        TornadoInternalError.shouldNotReachHere();
        return TornadoSchedulingStrategy.PER_ITERATION;
    }

    @Override
    public boolean isDistibutedMemory() {
        return true;
    }

    @Override
    public void ensureLoaded() {
        final OCLBackend backend = getBackend();
        if (!backend.isInitialised()) {
            backend.init();
        }
    }

    @Override
    public CallStack createStack(int numArgs) {
        return getDeviceContext().getMemoryManager().createCallStack(numArgs);
    }

    private boolean isOpenCLPreLoadBinary(OCLDeviceContext deviceContext, String deviceInfo) {
        OCLCodeCache installedCode = new OCLCodeCache(deviceContext);
        if ((installedCode.isLoadBinaryOptionEnabled() == false) || (installedCode.getOpenCLBinary(deviceInfo) == null)) {
            return false;
        }
        return true;
    }

    private TornadoInstalledCode compileTask(SchedulableTask task) {
        final OCLDeviceContext deviceContext = getDeviceContext();

        final CompilableTask executable = (CompilableTask) task;
        // final long t0 = System.nanoTime();
        final ResolvedJavaMethod resolvedMethod = TornadoCoreRuntime.getTornadoRuntime().resolveMethod(executable.getMethod());

        // final long t1 = System.nanoTime();
        final Sketch sketch = TornadoSketcher.lookup(resolvedMethod);

        // copy meta data into task
        final TaskMetaData sketchMeta = sketch.getMeta();
        final TaskMetaData taskMeta = executable.meta();
        final Access[] sketchAccess = sketchMeta.getArgumentsAccess();
        final Access[] taskAccess = taskMeta.getArgumentsAccess();
        for (int i = 0; i < sketchAccess.length; i++) {
            taskAccess[i] = sketchAccess[i];
        }

        try {
            OCLProviders providers = (OCLProviders) getBackend().getProviders();
            final OCLCompilationResult result = OCLCompiler.compileSketchForDevice(sketch, executable, providers, getBackend());
            if (deviceContext.isCached(task.getId(), resolvedMethod.getName())) {
                return deviceContext.getCode(task.getId(), resolvedMethod.getName());
            }
            return deviceContext.installCode(result);
        } catch (Exception e) {
            driver.fatal("unable to compile %s for device %s", task.getId(), getDeviceName());
            driver.fatal("exception occured when compiling %s", ((CompilableTask) task).getMethod().getName());
            driver.fatal("exception: %s", e.toString());
            e.printStackTrace();
        }
        return null;
    }

    private TornadoInstalledCode compilePreBuiltTask(SchedulableTask task) {
        final OCLDeviceContext deviceContext = getDeviceContext();
        final PrebuiltTask executable = (PrebuiltTask) task;
        if (deviceContext.isCached(task.getId(), executable.getEntryPoint())) {
            return deviceContext.getCode(task.getId(), executable.getEntryPoint());
        }

        final Path path = Paths.get(executable.getFilename());
        TornadoInternalError.guarantee(path.toFile().exists(), "file does not exist: %s", executable.getFilename());
        try {
            final byte[] source = Files.readAllBytes(path);
            return deviceContext.installCode(executable.meta(), task.getId(), executable.getEntryPoint(), source);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private TornadoInstalledCode compileJavaToAccelertor(SchedulableTask task) {
        if (task instanceof CompilableTask) {
            return compileTask(task);
        } else if (task instanceof PrebuiltTask) {
            return compilePreBuiltTask(task);
        }
        TornadoInternalError.shouldNotReachHere("task of unknown type: " + task.getClass().getSimpleName());
        return null;
    }

    private TornadoInstalledCode loadPreCompiledBinaryFromCache(SchedulableTask task) {
        final OCLDeviceContext deviceContext = getDeviceContext();
        final OCLCodeCache check = new OCLCodeCache(deviceContext);
        final String deviceFullName = getFullTaskIdDevice(task);
        final Path lookupPath = Paths.get(check.getOpenCLBinary(deviceFullName));
        String[] tempEntryToSplit = task.getName().split("- ");
        String entry = tempEntryToSplit[1];
        return check.installEntryPointForBinaryForFPGAs(lookupPath, entry);
    }

    private String getFullTaskIdDevice(SchedulableTask task) {
        TaskMetaDataInterface meta = task.meta();
        if (meta instanceof TaskMetaData) {
            TaskMetaData stask = (TaskMetaData) task.meta();
            return task.getId() + ".device=" + stask.getDriverIndex() + ":" + stask.getDeviceIndex();
        } else {
            throw new RuntimeException("[ERROR] TaskMedata Expected");
        }
    }

    @Override
    public TornadoInstalledCode installCode(SchedulableTask task) {
        final OCLDeviceContext deviceContext = getDeviceContext();
        final String deviceFullName = getFullTaskIdDevice(task);
        if (!isOpenCLPreLoadBinary(deviceContext, deviceFullName)) {
            return compileJavaToAccelertor(task);
        } else {
            return loadPreCompiledBinaryFromCache(task);
        }
    }

    private ObjectBuffer createArrayWrapper(Class<?> type, OCLDeviceContext device) {
        ObjectBuffer result = null;
        if (type == int[].class) {
            result = new OCLIntArrayWrapper(device);
        } else if (type == short[].class) {
            result = new OCLShortArrayWrapper(device);
        } else if (type == byte[].class) {
            result = new OCLByteArrayWrapper(device);
        } else if (type == float[].class) {
            result = new OCLFloatArrayWrapper(device);
        } else if (type == double[].class) {
            result = new OCLDoubleArrayWrapper(device);
        } else if (type == long[].class) {
            result = new OCLLongArrayWrapper(device);
        } else if (type == char[].class) {
            result = new OCLCharArrayWrapper(device);
        } else {
            TornadoInternalError.unimplemented("array of type %s", type.getName());
        }
        return result;
    }

    private ObjectBuffer createMultiArrayWrapper(Class<?> componentType, Class<?> type, OCLDeviceContext device) {
        ObjectBuffer result = null;

        if (componentType == int[].class) {
            result = new OCLMultiDimArrayWrapper<>(device, (OCLDeviceContext context) -> new OCLIntArrayWrapper(context));
        } else if (componentType == short[].class) {
            result = new OCLMultiDimArrayWrapper<>(device, (OCLDeviceContext context) -> new OCLShortArrayWrapper(context));
        } else if (componentType == char[].class) {
            result = new OCLMultiDimArrayWrapper<>(device, (OCLDeviceContext context) -> new OCLCharArrayWrapper(context));
        } else if (componentType == byte[].class) {
            result = new OCLMultiDimArrayWrapper<>(device, (OCLDeviceContext context) -> new OCLByteArrayWrapper(context));
        } else if (componentType == float[].class) {
            result = new OCLMultiDimArrayWrapper<>(device, (OCLDeviceContext context) -> new OCLFloatArrayWrapper(context));
        } else if (componentType == double[].class) {
            result = new OCLMultiDimArrayWrapper<>(device, (OCLDeviceContext context) -> new OCLDoubleArrayWrapper(context));
        } else if (componentType == long[].class) {
            result = new OCLMultiDimArrayWrapper<>(device, (OCLDeviceContext context) -> new OCLLongArrayWrapper(context));
        } else {
            TornadoInternalError.unimplemented("array of type %s", type.getName());
        }
        return result;
    }

    private ObjectBuffer createDeviceBuffer(Class<?> type, Object arg, OCLDeviceContext device) throws TornadoOutOfMemoryException {
        ObjectBuffer result = null;
        if (type.isArray()) {

            if (!type.getComponentType().isArray()) {
                result = createArrayWrapper(type, device);
            } else {
                final Class<?> componentType = type.getComponentType();
                if (RuntimeUtilities.isPrimitiveArray(componentType)) {
                    result = createMultiArrayWrapper(componentType, type, device);
                } else {
                    TornadoInternalError.unimplemented("multi-dimensional array of type %s", type.getName());
                }
            }

        } else if (!type.isPrimitive() && !type.isArray()) {
            result = new OCLObjectWrapper(device, arg);
        }

        TornadoInternalError.guarantee(result != null, "Unable to create buffer for object: " + type);
        return result;
    }

    @Override
    public int ensureAllocated(Object object, TornadoDeviceObjectState state) {
        if (!state.hasBuffer()) {
            try {
                final ObjectBuffer buffer = createDeviceBuffer(object.getClass(), object, getDeviceContext());
                buffer.allocate(object);
                state.setBuffer(buffer);

                final Class<?> type = object.getClass();
                if (!type.isArray()) {
                    buffer.write(object);
                }

                state.setValid(true);
            } catch (TornadoOutOfMemoryException e) {
                e.printStackTrace();
            }
        }

        if (!state.isValid()) {
            try {
                state.getBuffer().allocate(object);
                final Class<?> type = object.getClass();
                if (!type.isArray()) {
                    state.getBuffer().write(object);
                }
                state.setValid(true);
            } catch (TornadoOutOfMemoryException e) {
                e.printStackTrace();
            }
        }
        return -1;
    }

    @Override
    public int ensurePresent(Object object, TornadoDeviceObjectState state) {
        ensurePresent(object, state, null);
        return -1;
    }

    @Override
    public int ensurePresent(Object object, TornadoDeviceObjectState state, int[] events) {
        if (!state.isValid()) {
            ensureAllocated(object, state);
        }

        if (!state.hasContents()) {
            state.setContents(true);
            return state.getBuffer().enqueueWrite(object, events, events == null);
        }
        return -1;
    }

    @Override
    public int streamIn(Object object, TornadoDeviceObjectState state) {
        streamIn(object, state, null);
        return -1;
    }

    @Override
    public int streamIn(Object object, TornadoDeviceObjectState state, int[] events) {
        if (!state.isValid()) {
            ensureAllocated(object, state);
        }
        state.setContents(true);
        return state.getBuffer().enqueueWrite(object, events, events == null);

    }

    @Override
    public int streamOut(Object object, TornadoDeviceObjectState state) {
        streamOut(object, state, null);
        return -1;
    }

    @Override
    public int streamOut(Object object, TornadoDeviceObjectState state, int[] list) {
        TornadoInternalError.guarantee(state.isValid(), "invalid variable");
        return state.getBuffer().enqueueRead(object, list, list == null);
    }

    @Override
    public void streamOutBlocking(Object object, TornadoDeviceObjectState state) {
        streamOutBlocking(object, state, null);
    }

    @Override
    public void streamOutBlocking(Object object, TornadoDeviceObjectState state, int[] events) {
        TornadoInternalError.guarantee(state.isValid(), "invalid variable");

        state.getBuffer().read(object, events, events == null);
    }

    public void sync(Object... objects) {
        for (Object obj : objects) {
            sync(obj);
        }
    }

    public void sync(Object object) {
        final DeviceObjectState state = TornadoCoreRuntime.getTornadoRuntime().resolveObject(object).getDeviceState(this);
        resolveEvent(streamOut(object, state)).waitOn();
    }

    @Override
    public void flush() {
        this.getDeviceContext().flush();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof OCLTornadoDevice) {
            final OCLTornadoDevice other = (OCLTornadoDevice) obj;
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
        getDeviceContext().sync();
    }

    @Override
    public int enqueueBarrier() {
        return getDeviceContext().enqueueBarrier();
    }

    @Override
    public int enqueueBarrier(int[] events) {
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
    public void dumpMemory(String file) {
        final OCLMemoryManager mm = getDeviceContext().getMemoryManager();
        final OCLByteBuffer buffer = mm.getSubBuffer(0, (int) mm.getHeapSize());
        buffer.read();

        try (FileOutputStream fos = new FileOutputStream(file); FileChannel channel = fos.getChannel()) {
            channel.write(buffer.buffer());
        } catch (IOException e) {
            e.printStackTrace();
        }

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
    public void markEvent() {
        getDeviceContext().markEvent();
    }

    @Override
    public String getDeviceName() {
        return String.format("opencl-%d-%d", platformIndex, deviceIndex);
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

}
