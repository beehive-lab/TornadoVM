package uk.ac.manchester.tornado.drivers.cuda.runtime;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.TornadoTargetDevice;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.exceptions.TornadoMemoryException;
import uk.ac.manchester.tornado.api.exceptions.TornadoOutOfMemoryException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.mm.ObjectBuffer;
import uk.ac.manchester.tornado.api.mm.TornadoDeviceObjectState;
import uk.ac.manchester.tornado.api.mm.TornadoMemoryProvider;
import uk.ac.manchester.tornado.drivers.cuda.CUDA;
import uk.ac.manchester.tornado.drivers.cuda.CUDADevice;
import uk.ac.manchester.tornado.drivers.cuda.CUDADeviceContext;
import uk.ac.manchester.tornado.drivers.cuda.CUDADriver;
import uk.ac.manchester.tornado.drivers.cuda.graal.PTXProviders;
import uk.ac.manchester.tornado.drivers.cuda.graal.backend.PTXBackend;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.PTXCompilationResult;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.PTXCompiler;
import uk.ac.manchester.tornado.drivers.cuda.mm.*;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.*;
import uk.ac.manchester.tornado.runtime.sketcher.Sketch;
import uk.ac.manchester.tornado.runtime.sketcher.TornadoSketcher;
import uk.ac.manchester.tornado.runtime.tasks.CompilableTask;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.List;

public class CUDATornadoDevice implements TornadoAcceleratorDevice {

    private static final boolean BENCHMARKING_MODE = Boolean.parseBoolean(System.getProperties().getProperty("tornado.benchmarking", "False"));

    private final CUDADevice device;
    private static CUDADriver driver = null;

    public static CUDADriver findDriver() {
        if (driver == null) {
            driver = TornadoCoreRuntime.getTornadoRuntime().getDriver(CUDADriver.class);
            TornadoInternalError.guarantee(driver != null, "unable to find CUDA driver");
        }
        return driver;
    }


    public CUDATornadoDevice(final int deviceIndex) {
        device = CUDA.getPlatform().getDevice(deviceIndex);
    }

    @Override
    public TornadoSchedulingStrategy getPreferredSchedule() {
        return TornadoSchedulingStrategy.PER_ITERATION;
    }

    @Override
    public CallStack createStack(int numArgs) {
        return getDeviceContext().getMemoryManager().createCallStack(numArgs);
    }

    @Override
    public TornadoInstalledCode installCode(SchedulableTask task) {
        if (!(task instanceof CompilableTask)) TornadoInternalError.unimplemented("Non compilable tasks are not yet implemented in the CUDA driver");

        final CUDADeviceContext deviceContext = getDeviceContext();

        final CompilableTask executable = (CompilableTask) task;
        final ResolvedJavaMethod resolvedMethod = TornadoCoreRuntime.getTornadoRuntime().resolveMethod(executable.getMethod());
        final Sketch sketch = TornadoSketcher.lookup(resolvedMethod);

        // copy meta data into task
        final TaskMetaData sketchMeta = sketch.getMeta();
        final TaskMetaData taskMeta = executable.meta();
        final Access[] sketchAccess = sketchMeta.getArgumentsAccess();
        final Access[] taskAccess = taskMeta.getArgumentsAccess();
        System.arraycopy(sketchAccess, 0, taskAccess, 0, sketchAccess.length);

        PTXCompilationResult result;
        if (deviceContext.shouldCompile(PTXCompiler.buildFunctionName(resolvedMethod, executable))) {
            PTXProviders providers = (PTXProviders) getBackend().getProviders();
            result = PTXCompiler.compileSketchForDevice(sketch, executable, providers, getBackend());
        }
        else {
            result = new PTXCompilationResult(PTXCompiler.buildFunctionName(resolvedMethod, executable), taskMeta);
        }

        return deviceContext.installCode(result, resolvedMethod.getName());
    }

    @Override
    public boolean isFullJITMode(SchedulableTask task) {
        return false;
    }

    @Override
    public TornadoInstalledCode getCodeFromCache(SchedulableTask task) {
        return null;
    }

    /**
     * It allocates an object in the pre-defined heap of the target device. It also
     * ensure that there is enough space for the input object.
     *
     * @param object    to be allocated
     * @param batchSize size of the object to be allocated. If this value is <= 0, then it
     *                  allocates the sizeof(object).
     * @param state     state of the object in the target device
     *                  {@link TornadoDeviceObjectState}
     * @return an event ID
     */
    @Override
    public int ensureAllocated(Object object, long batchSize, TornadoDeviceObjectState state) {
        if (!state.hasBuffer()) {
            try {
                ObjectBuffer buffer = createDeviceBuffer(object.getClass(), object, batchSize);
                buffer.allocate(object, batchSize);
                state.setBuffer(buffer);

                final Class<?> type = object.getClass();
                if (!type.isArray()) {
                    if (batchSize > 0) {
                        throw new TornadoRuntimeException("[ERROR] Batch computation with non-arrays not supported yet.");
                    }
                    buffer.write(object);
                }
            } catch (TornadoOutOfMemoryException | TornadoMemoryException e) {
                e.printStackTrace();
            }
        }
        state.setValid(true);
        return -1;
    }

    private ObjectBuffer createDeviceBuffer(Class<?> type, Object arg, long batchSize) {
        ObjectBuffer result = null;
        if (type.isArray()) {

            if (!type.getComponentType().isArray()) {
                result = createArrayWrapper(type, getDeviceContext(), batchSize);
            } else {
                final Class<?> componentType = type.getComponentType();
                if (RuntimeUtilities.isPrimitiveArray(componentType)) {
                    //result = createMultiArrayWrapper(componentType, type, device, batchSize);
                } else {
                    TornadoInternalError.unimplemented("multi-dimensional array of type %s", type.getName());
                }
            }

        } else if (!type.isPrimitive() && !type.isArray()) {
            result = new CUDAObjectWrapper(getDeviceContext(), arg, batchSize);
        }

        TornadoInternalError.guarantee(result != null, "Unable to create buffer for object: " + type);
        return result;
    }

    private ObjectBuffer createArrayWrapper(Class<?> type, CUDADeviceContext deviceContext, long batchSize) {
        ObjectBuffer result = null;
        if (type == int[].class) {
            result = new CUDAIntArrayWrapper(deviceContext);
        } else if (type == short[].class) {
            result = new CUDAShortArrayWrapper(deviceContext);
        } else if (type == byte[].class) {
            result = new CUDAByteArrayWrapper(deviceContext);
        } else if (type == float[].class) {
            result = new CUDAFloatArrayWrapper(deviceContext);
        } else if (type == double[].class) {
            result = new CUDADoubleArrayWrapper(deviceContext);
        } else if (type == long[].class) {
            result = new CUDALongArrayWrapper(deviceContext);
        } else if (type == char[].class) {
            result = new CUDACharArrayWrapper(deviceContext);
        } else {
            TornadoInternalError.unimplemented("array of type %s", type.getName());
        }
        return result;
    }

    /**
     * It allocates and copy in the content of the object to the target device.
     *
     * @param object      to be allocated
     * @param objectState state of the object in the target device
     *                    {@link TornadoDeviceObjectState}
     * @param events      list of pending events (dependencies)
     * @param batchSize   size of the object to be allocated. If this value is <= 0, then it
     *                    allocates the sizeof(object).
     * @param hostOffset  offset in bytes for the copy within the host input array (or
     *                    object)
     * @return an event ID
     */
    @Override
    public List<Integer> ensurePresent(Object object, TornadoDeviceObjectState objectState, int[] events, long batchSize, long hostOffset) {
        if (!objectState.isValid()) ensureAllocated(object, batchSize, objectState);

        if (BENCHMARKING_MODE || !objectState.hasContents()) {
            objectState.setContents(true);
            return objectState.getBuffer().enqueueWrite(object, batchSize, hostOffset, events, events == null);
        }
        return null;
    }

    /**
     * It always copies in the input data (object) from the host to the target
     * device.
     *
     * @param object      to be copied
     * @param batchSize   size of the object to be allocated. If this value is <= 0, then it
     *                    allocates the sizeof(object).
     * @param hostOffset  offset in bytes for the copy within the host input array (or
     *                    object)
     * @param objectState state of the object in the target device
     *                    {@link TornadoDeviceObjectState}
     * @param events      list of previous events
     * @return and event ID
     */
    @Override
    public List<Integer> streamIn(Object object, long batchSize, long hostOffset, TornadoDeviceObjectState objectState, int[] events) {
        if (batchSize > 0 || !objectState.isValid()) {
            ensureAllocated(object, batchSize, objectState);
        }
        objectState.setContents(true);
        return objectState.getBuffer().enqueueWrite(object, batchSize, hostOffset, events, events == null);
    }

    /**
     * It copies a device buffer from the target device to the host. Copies are
     * non-blocking
     *
     * @param object      to be copied.
     * @param hostOffset  offset in bytes for the copy within the host input array (or
     *                    object)
     * @param objectState state of the object in the target device
     *                    {@link TornadoDeviceObjectState}
     * @param events      of pending events
     * @return and event ID
     */
    @Override
    public int streamOut(Object object, long hostOffset, TornadoDeviceObjectState objectState, int[] events) {
        TornadoInternalError.guarantee(objectState.isValid(), "invalid variable");
        int event = objectState.getBuffer().enqueueRead(object, hostOffset, events, events == null);
        if (events != null) {
            return event;
        }
        return -1;
    }

    /**
     * It copies a device buffer from the target device to the host. Copies are
     * blocking between the device and the host.
     *
     * @param object      to be copied.
     * @param hostOffset  offset in bytes for the copy within the host input array (or
     *                    object)
     * @param objectState state of the object in the target device
     *                    {@link TornadoDeviceObjectState}
     * @param events      of pending events
     * @return and event ID
     */
    @Override
    public int streamOutBlocking(Object object, long hostOffset, TornadoDeviceObjectState objectState, int[] events) {
        TornadoInternalError.guarantee(objectState.isValid(), "invalid variable");
        return objectState.getBuffer().read(object, hostOffset, events, events == null);
    }

    /**
     * It resolves an pending event.
     *
     * @param event ID
     * @return an object of type {@link Event}
     */
    @Override
    public Event resolveEvent(int event) {
        return getDeviceContext().resolveEvent(event);
    }

    @Override
    public void ensureLoaded() {
        getDeviceContext().flushEvents();
    }

    @Override
    public void markEvent() {
        getDeviceContext().markEvent();
    }

    @Override
    public void flushEvents() {
        getDeviceContext().flushEvents();
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
    public void sync() {
        getDeviceContext().sync();
    }

    @Override
    public void flush() {
        getDeviceContext().flush();
    }

    @Override
    public void reset() {
        getDeviceContext().reset();
    }

    @Override
    public void dumpEvents() {
        getDeviceContext().dumpEvents();
    }

    @Override
    public void dumpMemory(String file) {
        final CUDAMemoryManager mm = getDeviceContext().getMemoryManager();
        final CUDAByteBuffer buffer = mm.getSubBuffer(0, (int) mm.getHeapSize());
        buffer.read();

        try (FileOutputStream fos = new FileOutputStream(file); FileChannel channel = fos.getChannel()) {
            channel.write(buffer.buffer());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getDeviceName() {
        return "cuda-" + device.getIndex();
    }

    @Override
    public String getDescription() {
        return String.format("%s %s", device.getDeviceName(), device.getDeviceType());
    }

    @Override
    public String getPlatformName() {
        return CUDA.getPlatform().getName();
    }

    @Override
    public CUDADeviceContext getDeviceContext() {
        return getBackend().getDeviceContext();
    }

    public PTXBackend getBackend() {
        return findDriver().getBackend(device.getIndex());
    }

    @Override
    public TornadoTargetDevice getDevice() {
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
    public boolean isDistibutedMemory() {
        return false;
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
    public int getDriverIndex() {
        return TornadoCoreRuntime.getTornadoRuntime().getDriverIndex(CUDADriver.class);
    }

    @Override
    public String toString() {

        return device.getDeviceName();
    }
}
