package tornado.drivers.opencl.runtime;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import tornado.api.Event;
import tornado.api.enums.TornadoSchedulingStrategy;
import tornado.common.*;
import tornado.common.exceptions.TornadoInternalError;
import tornado.common.exceptions.TornadoOutOfMemoryException;
import tornado.drivers.opencl.OCLDevice;
import tornado.drivers.opencl.OCLDeviceContext;
import tornado.drivers.opencl.OCLDriver;
import tornado.drivers.opencl.graal.OCLProviders;
import tornado.drivers.opencl.graal.OCLInstalledCode;
import tornado.drivers.opencl.graal.backend.OCLBackend;
import tornado.drivers.opencl.graal.compiler.OCLCompiler;
import tornado.drivers.opencl.mm.*;
import tornado.runtime.api.CompilableTask;
import tornado.runtime.api.PrebuiltTask;

import static tornado.common.Tornado.FORCE_ALL_TO_GPU;
import static tornado.common.exceptions.TornadoInternalError.guarantee;
import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;
import static tornado.runtime.TornadoRuntime.getTornadoRuntime;
import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;
import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;
import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;

public class OCLDeviceMapping implements DeviceMapping {

    private final OCLDevice device;
    private final int deviceIndex;
    private final int platformIndex;

    private static OCLDriver findDriver() {
        OCLDriver driver = getTornadoRuntime().getDriver(OCLDriver.class);
        guarantee(driver != null, "unable to find OpenCL driver");
        return driver;
//        for (int i = 0; i < getTornadoRuntime().getNumDrivers(); i++) {
//            if (getTornadoRuntime().getDriver(i) instanceof OCLDriver) {
//                return (OCLDriver) getTornadoRuntime().getDriver(i);
//            }
//        }
//        shouldNotReachHere("unable to find OpenCL driver");
//        return null;
    }

    public OCLDeviceMapping(final int platformIndex, final int deviceIndex) {
        this.platformIndex = platformIndex;
        this.deviceIndex = deviceIndex;

        final OCLDriver driver = findDriver();
        device = driver.getPlatformContext(platformIndex).devices()
                .get(deviceIndex);

    }

    public OCLDevice getDevice() {
        return device;
    }

    public int getDeviceIndex() {
        return deviceIndex;
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
        if (task instanceof CompilableTask) {
            final CompilableTask executable = (CompilableTask) task;
//			final long t0 = System.nanoTime();
            final ResolvedJavaMethod resolvedMethod = getTornadoRuntime()
                    .resolveMethod(executable.getMethod());
//			final long t1 = System.nanoTime();
            final OCLInstalledCode code = OCLCompiler.compileCodeForDevice(
                    resolvedMethod, task.getArguments(), task.meta(),
                    (OCLProviders) getBackend().getProviders(), getBackend());
//			final long t2 = System.nanoTime();
//			System.out.printf("resolve : %.9f s\n",(t1-t0)*1e-9);
//			System.out.printf("compiler: %.9f s\n",(t2-t1)*1e-9);
            if (OCLBackend.SHOW_OPENCL) {
                String filename = getFile(executable.getMethodName());
                // Tornado.info("Generated code for device %s - %s\n",
                // deviceContext.getDevice().getName(), filename);
                try {
                    PrintWriter fileOut = new PrintWriter(filename);
                    String source = new String(code.getCode(), "ASCII");
                    fileOut.println(source.trim());
                    fileOut.close();
                } catch (UnsupportedEncodingException | FileNotFoundException e) {
                    e.printStackTrace();
                    // Tornado.warn("Unable to write source to file: %s",
                    // e.getMessage());
                }
            }
            return code;
        } else if (task instanceof PrebuiltTask) {
            final PrebuiltTask executable = (PrebuiltTask) task;
            final Path path = Paths.get(executable.getFilename());
            guarantee(path.toFile().exists(), "file does not exist: %s", executable.getFilename());
            try {
                final byte[] source = Files.readAllBytes(path);
                return getBackend().getCodeCache().addMethod(null, executable.getEntryPoint(),
                        source);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        shouldNotReachHere("task of unknown type: " + task.getClass().getSimpleName());
        return null;
    }

    private static String getFile(String name) {
        return String.format("%s/%s.cl", OCLBackend.OPENCL_PATH.trim(), name.trim());
    }

    private ObjectBuffer createDeviceBuffer(Class<?> type, Object arg,
            OCLDeviceContext device) throws TornadoOutOfMemoryException {
//		System.out.printf("creating bufffer: type=%s, arg=%s, device=%s\n",type.getSimpleName(),arg,device);
        ObjectBuffer result = null;
        if (type.isArray()) {

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
            }

        } else if (!type.isPrimitive() && !type.isArray()) {
//			System.out.println("creating object wrapper...good");
            result = new OCLObjectWrapper(device, arg);
        }

        TornadoInternalError.guarantee(result != null,
                "Unable to create buffer for object: " + type);
        return result;
    }

    @Override
    public int ensureAllocated(Object object, DeviceObjectState state) {
        if (!state.hasBuffer()) {
            try {
                final ObjectBuffer buffer = createDeviceBuffer(
                        object.getClass(), object, getDeviceContext());
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
        if (!state.isValid()) {
            ensureAllocated(object, state);
        }

        if (!state.hasContents()) {
            state.setContents(true);
            return state.getBuffer().enqueueWrite(object, null);
        }
        return -1;
    }

    @Override
    public int streamIn(Object object, DeviceObjectState state) {
        if (!state.isValid()) {
            ensureAllocated(object, state);
        }

        state.setContents(true);
        return state.getBuffer().enqueueWrite(object, null);

    }

    @Override
    public int streamOut(Object object, DeviceObjectState state,
            int[] list) {
        guarantee(state.isValid(), "invalid variable");

        return state.getBuffer().enqueueRead(object, list);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof OCLDeviceMapping) {
            final OCLDeviceMapping other = (OCLDeviceMapping) obj;
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
