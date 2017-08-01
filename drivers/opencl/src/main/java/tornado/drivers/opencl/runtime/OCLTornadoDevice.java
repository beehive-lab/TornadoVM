/*
 * Copyright 2012 James Clarkson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tornado.drivers.opencl.runtime;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import tornado.api.Event;
import tornado.api.enums.TornadoSchedulingStrategy;
import tornado.api.meta.TaskMetaData;
import tornado.common.*;
import tornado.common.enums.Access;
import tornado.drivers.opencl.OCLDevice;
import tornado.drivers.opencl.OCLDeviceContext;
import tornado.drivers.opencl.OCLDriver;
import tornado.drivers.opencl.graal.OCLProviders;
import tornado.drivers.opencl.graal.backend.OCLBackend;
import tornado.drivers.opencl.graal.compiler.OCLCompilationResult;
import tornado.drivers.opencl.mm.OCLDataMover;
import tornado.drivers.opencl.mm.OCLMemoryManager;
import tornado.runtime.api.CompilableTask;
import tornado.runtime.api.PrebuiltTask;
import tornado.runtime.cache.LocalObjectCache;
import tornado.runtime.cache.TornadoByteBuffer;
import tornado.runtime.sketcher.Sketch;
import tornado.runtime.sketcher.TornadoSketcher;

import static tornado.common.Tornado.FORCE_ALL_TO_GPU;
import static tornado.common.exceptions.TornadoInternalError.guarantee;
import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;
import static tornado.drivers.opencl.graal.compiler.OCLCompiler.compileSketchForDevice;
import static tornado.runtime.TornadoRuntime.getTornadoRuntime;

public class OCLTornadoDevice implements TornadoDevice {

    private static final int LOCAL_CACHE_SIZE = 512;
    private final OCLDevice device;
    private final int deviceIndex;
    private final int platformIndex;
    private static OCLDriver driver = null;

    private OCLMemoryManager memoryManager;
    private OCLDataMover dataMover;
    private LocalObjectCache localCache;

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

        device = findDriver().getPlatformContext(platformIndex).devices()
                .get(deviceIndex);

    }

    @Override
    public void dumpEvents() {
        getDeviceContext().dumpEvents();
    }

    @Override
    public int flushCache() {
        return localCache.flush();
    }

    @Override
    public String getDescription() {
        final String availability = (device.isAvailable()) ? "available" : "not available";
        return String.format("%s %s (%s)", device.getName(), device.getDeviceType(), availability);
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
    public int read(BlockingMode blocking, SharingMode sharing, CacheMode caching, Object object, int[] waitList) {
        return localCache.read(blocking, sharing, caching, object, waitList);
    }

    @Override
    public long toAbsoluteDeviceAddress(Object object) {
        return memoryManager.toAbsoluteAddress(localCache.lookup(object));
    }

    @Override
    public long toRelativeDeviceAddress(Object object) {
        return memoryManager.toRelativeAddress(localCache.lookup(object));
    }

    @Override
    public int write(BlockingMode blocking, CacheMode caching, Object object, int[] waitList) {
        return localCache.write(blocking, caching, object, waitList);
    }

    @Override
    public void reset() {
        getBackend().reset();
    }

    @Override
    public String toString() {
        return String.format(device.getName());
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
    public void ensureLoaded() {
        final OCLBackend backend = getBackend();
        if (!backend.isInitialised()) {
            backend.init();
        }

        if (memoryManager == null) {
            OCLDeviceContext context = getDeviceContext();
            memoryManager = context.getMemoryManager();
            dataMover = new OCLDataMover(context);
            localCache = new LocalObjectCache(memoryManager, dataMover, LOCAL_CACHE_SIZE);
            dataMover.setLocalCache(localCache);
        }
    }

    @Override
    public DeviceFrame createStack(int numArgs) {
        return getDeviceContext().getMemoryManager().createCallStack(numArgs);
    }

    @Override
    public TornadoInstalledCode installCode(SchedulableTask task) {
        final OCLDeviceContext deviceContext = getDeviceContext();

        if (task instanceof CompilableTask) {
            final CompilableTask executable = (CompilableTask) task;
//			final long t0 = System.nanoTime();
            final ResolvedJavaMethod resolvedMethod = getTornadoRuntime()
                    .resolveMethod(executable.getMethod());

//			final long t1 = System.nanoTime();
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
                final OCLCompilationResult result = compileSketchForDevice(
                        sketch, executable,
                        (OCLProviders) getBackend().getProviders(), getBackend());

                if (deviceContext.isCached(task.getId(), resolvedMethod.getName())) {
                    return deviceContext.getCode(task.getId(), resolvedMethod.getName());
                }
                return deviceContext.installCode(result);
            } catch (Exception e) {
                driver.fatal("unable to compile %s for device %s", task.getId(), getDeviceName());
                driver.fatal("exception occured when compiling %s", ((CompilableTask) task).getMethod().getName());
                driver.fatal("exception: %s", e.toString());
                e.printStackTrace();
                System.exit(-1);
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
                return deviceContext.installCode(executable.meta(), task.getId(), executable.getEntryPoint(),
                        source);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        shouldNotReachHere("task of unknown type: " + task.getClass().getSimpleName());
        return null;
    }

    private String getFile(String name) {
        return String.format("%s/%s-%s.cl", OCLBackend.OPENCL_PATH.trim(), name.trim(), getDeviceName());
    }

//    private CachedObject createDeviceBuffer(Class<?> type, Object arg,
//            OCLDeviceContext device) throws TornadoOutOfMemoryException {
////		System.out.printf("creating buffer: type=%s, arg=%s, device=%s\n",type.getSimpleName(),arg,device);
//        CachedObject result = null;
//        if (type.isArray()) {
//
//            if (type == int[].class) {
//                result = new OCLIntArrayWrapper(device);
//            } else if (type == short[].class) {
//                result = new OCLShortArrayWrapper(device);
//            } else if (type == byte[].class) {
//                result = new OCLByteArrayWrapper(device);
//            } else if (type == float[].class) {
//                result = new OCLFloatArrayWrapper(device);
//            } else if (type == double[].class) {
//                result = new OCLDoubleArrayWrapper(device);
//            } else if (type == long[].class) {
//                result = new OCLLongArrayWrapper(device);
//            }
//
//        } else if (!type.isPrimitive() && !type.isArray()) {
////			System.out.println("creating object wrapper...good");
//            result = new OCLObjectWrapper(device, arg);
//        }
//
//        TornadoInternalError.guarantee(result != null,
//                "Unable to create buffer for object: " + type);
//        return result;
//    }
    @Override
    public void flush() {
        getDeviceContext().flush();
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

    public void dumpMemory(String file) {
        final OCLMemoryManager mm = getDeviceContext().getMemoryManager();
        final TornadoByteBuffer buffer = mm.getSubBuffer(0, (int) mm.getHeapSize());
        buffer.syncRead();

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
