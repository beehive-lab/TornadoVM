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

import static uk.ac.manchester.tornado.common.RuntimeUtilities.isPrimitiveArray;
import static uk.ac.manchester.tornado.common.Tornado.FORCE_ALL_TO_GPU;
import static uk.ac.manchester.tornado.common.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.common.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompiler.compileSketchForDevice;
import static uk.ac.manchester.tornado.runtime.TornadoRuntime.getTornadoRuntime;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.Event;
import uk.ac.manchester.tornado.api.enums.TornadoSchedulingStrategy;
import uk.ac.manchester.tornado.api.meta.TaskMetaData;
import uk.ac.manchester.tornado.common.CallStack;
import uk.ac.manchester.tornado.common.DeviceObjectState;
import uk.ac.manchester.tornado.common.ObjectBuffer;
import uk.ac.manchester.tornado.common.SchedulableTask;
import uk.ac.manchester.tornado.common.TornadoDevice;
import uk.ac.manchester.tornado.common.TornadoInstalledCode;
import uk.ac.manchester.tornado.common.TornadoMemoryProvider;
import uk.ac.manchester.tornado.common.enums.Access;
import uk.ac.manchester.tornado.common.exceptions.TornadoOutOfMemoryException;
import uk.ac.manchester.tornado.drivers.opencl.OCLCodeCache;
import uk.ac.manchester.tornado.drivers.opencl.OCLDevice;
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContext;
import uk.ac.manchester.tornado.drivers.opencl.OCLDriver;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLProviders;
import uk.ac.manchester.tornado.drivers.opencl.graal.backend.OCLBackend;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompilationResult;
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
import uk.ac.manchester.tornado.runtime.api.CompilableTask;
import uk.ac.manchester.tornado.runtime.api.PrebuiltTask;
import uk.ac.manchester.tornado.runtime.sketcher.Sketch;
import uk.ac.manchester.tornado.runtime.sketcher.TornadoSketcher;

public class OCLTornadoDevice implements TornadoDevice {

    private final OCLDevice device;
    private final int deviceIndex;
    private final int platformIndex;
    private static OCLDriver driver = null;
    private String platformName;

    private static OCLDriver findDriver() {
        if (driver == null) {
            driver = getTornadoRuntime().getDriver(OCLDriver.class);
            guarantee(driver != null, "unable to find OpenCL driver");
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

            if (FORCE_ALL_TO_GPU) {
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
        shouldNotReachHere();
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

    @Override
    public TornadoInstalledCode installCode(SchedulableTask task) {
        final OCLDeviceContext deviceContext = getDeviceContext();
        OCLCodeCache tmp = new OCLCodeCache(deviceContext);

        if ((tmp.getBinStatus() == false) && (tmp.getFPGABinDir() == null)) {
            if (task instanceof CompilableTask) {
                final CompilableTask executable = (CompilableTask) task;
                // final long t0 = System.nanoTime();
                final ResolvedJavaMethod resolvedMethod = getTornadoRuntime().resolveMethod(executable.getMethod());

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
                    final OCLCompilationResult result = compileSketchForDevice(sketch, executable, (OCLProviders) getBackend().getProviders(), getBackend());

                    if (deviceContext.isCached(task.getId(), resolvedMethod.getName())) {

                        System.out.println("Kernel is cached " + task.getId() + "  -  " + resolvedMethod.getName());
                        return deviceContext.getCode(task.getId(), resolvedMethod.getName());
                    } else {
                        System.out.println("Kernel is NOT cached: " + task.getId() + "  -  " + resolvedMethod.getName());
                    }
                    return deviceContext.installCode(result);
                } catch (Exception e) {
                    driver.fatal("unable to compile %s for device %s", task.getId(), getDeviceName());
                    driver.fatal("exception occured when compiling %s", ((CompilableTask) task).getMethod().getName());
                    driver.fatal("exception: %s", e.toString());
                    e.printStackTrace();
                }
                return null;
            } else if (task instanceof PrebuiltTask) {
                final PrebuiltTask executable = (PrebuiltTask) task;
                if (deviceContext.isCached(task.getId(), executable.getEntryPoint())) {
                    return deviceContext.getCode(task.getId(), executable.getEntryPoint());
                }

                final Path path = Paths.get(executable.getFilename());
                guarantee(path.toFile().exists(), "file does not exist: %s", executable.getFilename());
                try {
                    final byte[] source = Files.readAllBytes(path);
                    return deviceContext.installCode(executable.meta(), task.getId(), executable.getEntryPoint(), source);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            final OCLCodeCache check = new OCLCodeCache(deviceContext);
            final Path lookupPath = Paths.get(check.getFPGABinDir());

            String[] tempEntryToSplit = task.getName().split("- ");
            String entry = tempEntryToSplit[1];

            return check.installEntryPointForBinaryForFPGAs(lookupPath, entry);
        }

        shouldNotReachHere("task of unknown type: " + task.getClass().getSimpleName());
        return null;
    }

    private ObjectBuffer createDeviceBuffer(Class<?> type, Object arg, OCLDeviceContext device) throws TornadoOutOfMemoryException {
        ObjectBuffer result = null;
        if (type.isArray()) {

            if (!type.getComponentType().isArray()) {
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
                    unimplemented("array of type %s", type.getName());
                }
            } else {
                final Class<?> componentType = type.getComponentType();
                if (isPrimitiveArray(componentType)) {

                    if (componentType == int[].class) {
                        result = new OCLMultiDimArrayWrapper<>(device, (OCLDeviceContext context) -> new OCLIntArrayWrapper(context));
                    } else if (componentType == short[].class) {
                        result = new OCLMultiDimArrayWrapper<>(device, (OCLDeviceContext context) -> new OCLShortArrayWrapper(context));
                    } else if (componentType == byte[].class) {
                        result = new OCLMultiDimArrayWrapper<>(device, (OCLDeviceContext context) -> new OCLByteArrayWrapper(context));
                    } else if (componentType == float[].class) {
                        result = new OCLMultiDimArrayWrapper<>(device, (OCLDeviceContext context) -> new OCLFloatArrayWrapper(context));
                    } else if (componentType == double[].class) {
                        result = new OCLMultiDimArrayWrapper<>(device, (OCLDeviceContext context) -> new OCLDoubleArrayWrapper(context));
                    } else if (componentType == long[].class) {
                        result = new OCLMultiDimArrayWrapper<>(device, (OCLDeviceContext context) -> new OCLLongArrayWrapper(context));
                    } else {
                        unimplemented("array of type %s", type.getName());
                    }
                } else {
                    unimplemented("multi-dimensional array of type %s", type.getName());
                }
            }

        } else if (!type.isPrimitive() && !type.isArray()) {
            result = new OCLObjectWrapper(device, arg);
        }

        guarantee(result != null, "Unable to create buffer for object: " + type);
        return result;
    }

    @Override
    public int ensureAllocated(Object object, DeviceObjectState state) {

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
    public int ensurePresent(Object object, DeviceObjectState state) {
        ensurePresent(object, state, null);
        return -1;
    }

    @Override
    public int ensurePresent(Object object, DeviceObjectState state, int[] events) {
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
    public int streamIn(Object object, DeviceObjectState state) {
        streamIn(object, state, null);
        return -1;
    }

    @Override
    public int streamIn(Object object, DeviceObjectState state, int[] events) {
        if (!state.isValid()) {
            ensureAllocated(object, state);
        }
        state.setContents(true);
        return state.getBuffer().enqueueWrite(object, events, events == null);

    }

    @Override
    public int streamOut(Object object, DeviceObjectState state) {
        streamOut(object, state, null);
        return -1;
    }

    @Override
    public int streamOut(Object object, DeviceObjectState state, int[] list) {
        guarantee(state.isValid(), "invalid variable");
        return state.getBuffer().enqueueRead(object, list, list == null);
    }

    @Override
    public void streamOutBlocking(Object object, DeviceObjectState state) {
        streamOutBlocking(object, state, null);
    }

    @Override
    public void streamOutBlocking(Object object, DeviceObjectState state, int[] events) {
        guarantee(state.isValid(), "invalid variable");

        state.getBuffer().read(object, events, events == null);
    }

    public void sync(Object... objects) {
        for (Object obj : objects) {
            sync(obj);
        }
    }

    public void sync(Object object) {
        final DeviceObjectState state = getTornadoRuntime().resolveObject(object).getDeviceState(this);
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

}
