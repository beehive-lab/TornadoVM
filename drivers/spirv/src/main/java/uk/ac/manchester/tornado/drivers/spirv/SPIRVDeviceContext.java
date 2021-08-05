package uk.ac.manchester.tornado.drivers.spirv;

import java.lang.reflect.Array;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVInstalledCode;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResult;
import uk.ac.manchester.tornado.drivers.spirv.mm.SPIRVMemoryManager;
import uk.ac.manchester.tornado.drivers.spirv.runtime.SPIRVTornadoDevice;
import uk.ac.manchester.tornado.runtime.common.Initialisable;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.common.TornadoInstalledCode;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

/**
 * Class to map a SPIR-V device (Device represented either in LevelZero or an
 * OpenCL device) with an SPIR-V Context.
 */
public abstract class SPIRVDeviceContext implements Initialisable, TornadoDeviceContext {

    protected SPIRVDevice device;
    protected SPIRVCommandQueue queue;
    protected SPIRVContext spirvContext;
    protected SPIRVTornadoDevice tornadoDevice;
    protected SPIRVMemoryManager memoryManager;
    protected SPIRVCodeCache codeCache;
    protected boolean wasReset;
    protected SPIRVEventPool spirvEventPool;

    private void init(SPIRVDevice device, SPIRVCommandQueue queue) {
        this.device = device;
        this.queue = queue;
        this.tornadoDevice = new SPIRVTornadoDevice(device);
        this.memoryManager = new SPIRVMemoryManager(this);
        if (this instanceof SPIRVLevelZeroDeviceContext) {
            this.codeCache = new SPIRVLevelZeroCodeCache(this);
        } else {
            this.codeCache = new SPIRVOCLCodeCache(this);
        }
        this.wasReset = false;
        this.spirvEventPool = new SPIRVEventPool(Tornado.EVENT_WINDOW);

    }

    public SPIRVDeviceContext(SPIRVDevice device, SPIRVCommandQueue queue, SPIRVContext context) {
        init(device, queue);
        this.spirvContext = context;
    }

    public SPIRVContext getSpirvContext() {
        return this.spirvContext;
    }

    public SPIRVDevice getDevice() {
        return device;
    }

    @Override
    public boolean isInitialised() {
        return memoryManager.isInitialised();
    }

    @Override
    public SPIRVMemoryManager getMemoryManager() {
        return this.memoryManager;
    }

    @Override
    public boolean needsBump() {
        return false;
    }

    @Override
    public boolean wasReset() {
        return wasReset;
    }

    @Override
    public void setResetToFalse() {
        wasReset = false;
    }

    @Override
    public boolean isPlatformFPGA() {
        return false;
    }

    @Override
    public boolean useRelativeAddresses() {
        return false;
    }

    private String buildKernelName(String methodName, SchedulableTask task) {
        StringBuilder sb = new StringBuilder(methodName);

        for (Object arg : task.getArguments()) {
            // Object is either array or primitive
            sb.append('_');
            Class<?> argClass = arg.getClass();
            if (RuntimeUtilities.isBoxedPrimitiveClass(argClass)) {
                // Only need to append value.
                // If negative value, remove the minus sign in front
                sb.append(arg.toString().replace('.', '_').replaceAll("-", ""));
            } else if (argClass.isArray() && RuntimeUtilities.isPrimitiveArray(argClass)) {
                // Need to append type and length
                sb.append(argClass.getComponentType().getName());
                sb.append(Array.getLength(arg));
            } else {
                sb.append(argClass.getName().replace('.', '_'));

                // Since with objects there is no way to know what will be a
                // constant differentiate using the hashcode of the object
                sb.append('_');
                sb.append(arg.hashCode());
            }
        }

        return sb.toString();
    }

    @Override
    public int getDeviceIndex() {
        return device.getDeviceIndex();
    }

    @Override
    public int getDevicePlatform() {
        return device.getPlatformIndex();
    }

    @Override
    public String getDeviceName() {
        return device.getDeviceName();
    }

    @Override
    public int getDriverIndex() {
        return TornadoRuntime.getTornadoRuntime().getDriverIndex(SPIRVDriver.class);
    }

    public SPIRVTornadoDevice asMapping() {
        return tornadoDevice;
    }

    public void reset() {
        memoryManager.reset();
        codeCache.reset();
        wasReset = true;
    }

    public int readBuffer(long bufferId, long offset, long bytes, byte[] value, long hostOffset, int[] waitEvents) {
        return spirvContext.readBuffer(getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents);
    }

    public int readBuffer(long bufferId, long offset, long bytes, int[] value, long hostOffset, int[] waitEvents) {
        return spirvContext.readBuffer(getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents);
    }

    public int readBuffer(long bufferId, long offset, long bytes, float[] value, long hostOffset, int[] waitEvents) {
        return spirvContext.readBuffer(getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents);
    }

    public int readBuffer(long bufferId, long offset, long bytes, double[] value, long hostOffset, int[] waitEvents) {
        return spirvContext.readBuffer(getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents);
    }

    public int readBuffer(long bufferId, long offset, long bytes, long[] value, long hostOffset, int[] waitEvents) {
        return spirvContext.readBuffer(getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents);
    }

    public int readBuffer(long bufferId, long offset, long bytes, short[] value, long hostOffset, int[] waitEvents) {
        return spirvContext.readBuffer(getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents);
    }

    public int readBuffer(long bufferId, long offset, long bytes, char[] value, long hostOffset, int[] waitEvents) {
        return spirvContext.readBuffer(getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents);
    }

    public void writeBuffer(long bufferId, long offset, long bytes, byte[] value, long hostOffset, int[] waitEvents) {
        throw new RuntimeException("Unimplemented");
    }

    public void writeBuffer(long bufferId, long offset, long bytes, int[] value, long hostOffset, int[] waitEvents) {
        throw new RuntimeException("Unimplemented");
    }

    public void writeBuffer(long bufferId, long offset, long bytes, float[] value, long hostOffset, int[] waitEvents) {
        throw new RuntimeException("Unimplemented");
    }

    public void writeBuffer(long bufferId, long offset, long bytes, double[] value, long hostOffset, int[] waitEvents) {
        throw new RuntimeException("Unimplemented");
    }

    public void writeBuffer(long bufferId, long offset, long bytes, long[] value, long hostOffset, int[] waitEvents) {
        throw new RuntimeException("Unimplemented");
    }

    public void writeBuffer(long bufferId, long offset, long bytes, short[] value, long hostOffset, int[] waitEvents) {
        throw new RuntimeException("Unimplemented");
    }

    public void writeBuffer(long bufferId, long offset, long bytes, char[] value, long hostOffset, int[] waitEvents) {
        throw new RuntimeException("Unimplemented");
    }

    public int enqueueReadBuffer(long bufferId, long offset, long bytes, byte[] value, long hostOffset, int[] waitEvents) {
        throw new RuntimeException("Unimplemented");
    }

    public int enqueueReadBuffer(long bufferId, long offset, long bytes, int[] value, long hostOffset, int[] waitEvents) {
        throw new RuntimeException("Unimplemented");
    }

    public int enqueueReadBuffer(long bufferId, long offset, long bytes, float[] value, long hostOffset, int[] waitEvents) {
        throw new RuntimeException("Unimplemented");
    }

    public int enqueueReadBuffer(long bufferId, long offset, long bytes, double[] value, long hostOffset, int[] waitEvents) {
        throw new RuntimeException("Unimplemented");
    }

    public int enqueueReadBuffer(long bufferId, long offset, long bytes, long[] value, long hostOffset, int[] waitEvents) {
        throw new RuntimeException("Unimplemented");
    }

    public int enqueueReadBuffer(long bufferId, long offset, long bytes, short[] value, long hostOffset, int[] waitEvents) {
        throw new RuntimeException("Unimplemented");
    }

    public int enqueueReadBuffer(long bufferId, long offset, long bytes, char[] value, long hostOffset, int[] waitEvents) {
        throw new RuntimeException("Unimplemented");
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes, byte[] value, long hostOffset, int[] waitEvents) {
        return spirvContext.enqueueWriteBuffer(device.getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes, int[] value, long hostOffset, int[] waitEvents) {
        return spirvContext.enqueueWriteBuffer(device.getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes, float[] value, long hostOffset, int[] waitEvents) {
        return spirvContext.enqueueWriteBuffer(device.getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes, double[] value, long hostOffset, int[] waitEvents) {
        return spirvContext.enqueueWriteBuffer(device.getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes, long[] value, long hostOffset, int[] waitEvents) {
        return spirvContext.enqueueWriteBuffer(device.getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes, short[] value, long hostOffset, int[] waitEvents) {
        return spirvContext.enqueueWriteBuffer(device.getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes, char[] value, long hostOffset, int[] waitEvents) {
        return spirvContext.enqueueWriteBuffer(device.getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents);
    }

    public void enqueueBarrier(int deviceIndex) {
        spirvContext.enqueueBarrier(deviceIndex);
    }

    public void flush(int deviceIndex) {
        spirvContext.flush(deviceIndex);
    }

    public TornadoInstalledCode installBinary(SPIRVCompilationResult result) {
        return installBinary(result.getMeta(), result.getId(), result.getName(), result.getTargetCode());
    }

    public SPIRVInstalledCode installBinary(TaskMetaData meta, String id, String entryPoint, byte[] code) {
        return codeCache.installSPIRVBinary(meta, id, entryPoint, code);
    }

    public SPIRVInstalledCode installBinary(TaskMetaData meta, String id, String entryPoint, String pathToFile) {
        return codeCache.installSPIRVBinary(meta, id, entryPoint, pathToFile);
    }

    public boolean isCached(String id, String entryPoint) {
        return codeCache.isCached(id + "-" + entryPoint);
    }

    @Override
    public boolean isCached(String methodName, SchedulableTask task) {
        return codeCache.isCached(task.getId() + "-" + methodName);
    }

    public SPIRVInstalledCode getInstalledCode(String id, String entryPoint) {
        return codeCache.getInstalledCode(id, entryPoint);
    }

    public int enqueueMarker(int[] internalEvents) {
        spirvContext.enqueueBarrier(getDeviceIndex());
        return 0;
    }

    @Override
    public boolean isPlatformXilinxFPGA() {
        return false;
    }
}
