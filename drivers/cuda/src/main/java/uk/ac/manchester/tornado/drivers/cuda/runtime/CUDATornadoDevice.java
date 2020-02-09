package uk.ac.manchester.tornado.drivers.cuda.runtime;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.api.TornadoTargetDevice;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.mm.TornadoDeviceObjectState;
import uk.ac.manchester.tornado.api.mm.TornadoMemoryProvider;
import uk.ac.manchester.tornado.drivers.cuda.CUDADeviceContext;
import uk.ac.manchester.tornado.drivers.cuda.CUDADriver;
import uk.ac.manchester.tornado.drivers.cuda.graal.PTXInstalledCode;
import uk.ac.manchester.tornado.drivers.cuda.graal.PTXProviders;
import uk.ac.manchester.tornado.drivers.cuda.graal.backend.PTXBackend;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.PTXCompilationResult;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.PTXCompiler;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.CallStack;
import uk.ac.manchester.tornado.runtime.common.TornadoAcceleratorDevice;
import uk.ac.manchester.tornado.runtime.common.TornadoInstalledCode;
import uk.ac.manchester.tornado.runtime.common.TornadoSchedulingStrategy;
import uk.ac.manchester.tornado.runtime.sketcher.Sketch;
import uk.ac.manchester.tornado.runtime.sketcher.TornadoSketcher;
import uk.ac.manchester.tornado.runtime.tasks.CompilableTask;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

import java.util.List;

public class CUDATornadoDevice implements TornadoAcceleratorDevice {

    private final int deviceIndex;
    private final int platformIndex;
    private static CUDADriver driver = null;

    public static CUDADriver findDriver() {
        if (driver == null) {
            driver = TornadoCoreRuntime.getTornadoRuntime().getDriver(CUDADriver.class);
            TornadoInternalError.guarantee(driver != null, "unable to find CUDA driver");
        }
        return driver;
    }


    public CUDATornadoDevice(final int platformIndex, final int deviceIndex) {
        this.platformIndex = platformIndex;
        this.deviceIndex = deviceIndex;
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

        PTXProviders providers = (PTXProviders) getBackend().getProviders();
        final PTXCompilationResult result = PTXCompiler.compileSketchForDevice(sketch, executable, providers, getBackend());

        return deviceContext.installCode(result);
    }

    @Override
    public boolean isFullJITMode(SchedulableTask task) {
        return false;
    }

    @Override public TornadoInstalledCode getCodeFromCache(SchedulableTask task) {
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
        state.setValid(true);
        return 0;
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
        objectState.setValid(true);
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
        objectState.setValid(true);
        return null;
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
        objectState.setValid(true);
        return 0;
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
        objectState.setValid(true);
        return 0;
    }

    /**
     * It resolves an pending event.
     *
     * @param event ID
     * @return an object of type {@link Event}
     */
    @Override
    public Event resolveEvent(int event) {
        return null;
    }

    @Override
    public void ensureLoaded() {

    }

    @Override
    public void markEvent() {

    }

    @Override
    public void flushEvents() {

    }

    @Override
    public int enqueueBarrier() {
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

    }

    @Override
    public void reset() {

    }

    @Override
    public void dumpEvents() {

    }

    @Override
    public void dumpMemory(String file) {

    }

    @Override
    public String getDeviceName() {
        return "CUDA tornado device";
    }

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public String getPlatformName() {
        return "CUDA Platform";
    }

    @Override
    public CUDADeviceContext getDeviceContext() {
        return getBackend().getDeviceContext();
    }

    public PTXBackend getBackend() {return findDriver().getBackend(platformIndex, deviceIndex);}

    @Override
    public TornadoTargetDevice getDevice() {
        return null;
    }

    @Override
    public TornadoMemoryProvider getMemoryProvider() {
        return null;
    }

    @Override
    public TornadoDeviceType getDeviceType() {
        return null;
    }

    @Override
    public long getMaxAllocMemory() {
        return 0;
    }

    @Override
    public long getMaxGlobalMemory() {
        return 0;
    }

    @Override
    public long getDeviceLocalMemorySize() {
        return 0;
    }

    @Override
    public long[] getDeviceMaxWorkgroupDimensions() {
        return new long[0];
    }

    @Override
    public boolean isDistibutedMemory() {
        return false;
    }

    @Override
    public String getDeviceOpenCLCVersion() {
        return null;
    }

    @Override
    public Object getDeviceInfo() {
        return null;
    }
}
