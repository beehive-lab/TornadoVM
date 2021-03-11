package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.drivers.opencl.OCLExecutionEnvironment;
import uk.ac.manchester.tornado.drivers.spirv.mm.SPIRVMemoryManager;
import uk.ac.manchester.tornado.drivers.spirv.runtime.SPIRVTornadoDevice;
import uk.ac.manchester.tornado.runtime.common.Initialisable;

/**
 * Class to map an SPIRV device (Device represented either in Level Zero or an
 * OpenCL device) with an SPIRV Context.
 */
public class SPIRVDeviceContext implements Initialisable, TornadoDeviceContext {

    private SPIRVDevice device;
    private SPIRVCommandQueue queue;
    private SPIRVContext spirvContext;
    private OCLExecutionEnvironment oclContext;
    private SPIRVTornadoDevice tornadoDevice;
    private SPIRVMemoryManager memoryManager;

    private void init(SPIRVDevice device, SPIRVCommandQueue queue) {
        this.device = device;
        this.queue = queue;
        this.tornadoDevice = new SPIRVTornadoDevice(device);
        this.memoryManager = new SPIRVMemoryManager(this);
    }

    public SPIRVDeviceContext(SPIRVDevice device, SPIRVCommandQueue queue, SPIRVContext context) {
        init(device, queue);
        this.spirvContext = context;
    }

    public SPIRVDeviceContext(SPIRVDevice device, SPIRVCommandQueue queue, OCLExecutionEnvironment context) {
        init(device, queue);
        this.oclContext = context;
    }

    public SPIRVContext getSpirvContext() {
        return this.spirvContext;
    }

    public SPIRVDevice getDevice() {
        return device;
    }

    @Override
    public boolean isInitialised() {
        return false;
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
        return false;
    }

    @Override
    public void setResetToFalse() {

    }

    @Override
    public boolean isPlatformFPGA() {
        return false;
    }

    @Override
    public boolean useRelativeAddresses() {
        return false;
    }

    @Override
    public boolean isCached(String methodName, SchedulableTask task) {
        return false;
    }

    @Override
    public int getDeviceIndex() {
        return 0;
    }

    @Override
    public int getDevicePlatform() {
        return 0;
    }

    @Override
    public String getDeviceName() {
        return null;
    }

    @Override
    public int getDriverIndex() {
        return 0;
    }

    public SPIRVTornadoDevice asMapping() {
        return tornadoDevice;
    }

    public void reset() {
        throw new RuntimeException("Unimplemented");
    }

    public int readBuffer(long bufferId, long offset, long bytes, byte[] value, long hostOffset, int[] waitEvents) {
        return 0;
    }

    public int readBuffer(long bufferId, long offset, long bytes, int[] value, long hostOffset, int[] waitEvents) {
        return 0;
    }

    public int readBuffer(long bufferId, long offset, long bytes, float[] value, long hostOffset, int[] waitEvents) {
        return 0;
    }

    public int readBuffer(long bufferId, long offset, long bytes, double[] value, long hostOffset, int[] waitEvents) {
        return 0;
    }

    public int readBuffer(long bufferId, long offset, long bytes, long[] value, long hostOffset, int[] waitEvents) {
        return 0;
    }

    public int readBuffer(long bufferId, long offset, long bytes, short[] value, long hostOffset, int[] waitEvents) {
        return 0;
    }

    public int readBuffer(long bufferId, long offset, long bytes, char[] value, long hostOffset, int[] waitEvents) {
        return 0;
    }

    public void writeBuffer(long bufferId, long offset, long bytes, byte[] value, long hostOffset, int[] waitEvents) {

    }

    public void writeBuffer(long bufferId, long offset, long bytes, int[] value, long hostOffset, int[] waitEvents) {

    }

    public void writeBuffer(long bufferId, long offset, long bytes, float[] value, long hostOffset, int[] waitEvents) {

    }

    public void writeBuffer(long bufferId, long offset, long bytes, double[] value, long hostOffset, int[] waitEvents) {

    }

    public void writeBuffer(long bufferId, long offset, long bytes, long[] value, long hostOffset, int[] waitEvents) {

    }

    public void writeBuffer(long bufferId, long offset, long bytes, short[] value, long hostOffset, int[] waitEvents) {

    }

    public void writeBuffer(long bufferId, long offset, long bytes, char[] value, long hostOffset, int[] waitEvents) {

    }

    public int enqueueReadBuffer(long bufferId, long offset, long bytes, byte[] value, long hostOffset, int[] waitEvents) {
        return 0;
    }

    public int enqueueReadBuffer(long bufferId, long offset, long bytes, int[] value, long hostOffset, int[] waitEvents) {
        return 0;
    }

    public int enqueueReadBuffer(long bufferId, long offset, long bytes, float[] value, long hostOffset, int[] waitEvents) {
        return 0;
    }

    public int enqueueReadBuffer(long bufferId, long offset, long bytes, double[] value, long hostOffset, int[] waitEvents) {
        return 0;
    }

    public int enqueueReadBuffer(long bufferId, long offset, long bytes, long[] value, long hostOffset, int[] waitEvents) {
        return 0;
    }

    public int enqueueReadBuffer(long bufferId, long offset, long bytes, short[] value, long hostOffset, int[] waitEvents) {
        return 0;
    }

    public int enqueueReadBuffer(long bufferId, long offset, long bytes, char[] value, long hostOffset, int[] waitEvents) {
        return 0;
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes, byte[] value, long hostOffset, int[] waitEvents) {
        int result = spirvContext.enqueueWriteBuffer(device.getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents);
        return result;
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes, int[] value, long hostOffset, int[] waitEvents) {
        int result = spirvContext.enqueueWriteBuffer(device.getDeviceIndex(), bufferId, offset, bytes, value, hostOffset, waitEvents);
        return result;
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes, float[] value, long hostOffset, int[] waitEvents) {
        return 0;
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes, double[] value, long hostOffset, int[] waitEvents) {
        return 0;
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes, long[] value, long hostOffset, int[] waitEvents) {
        return 0;
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes, short[] value, long hostOffset, int[] waitEvents) {
        return 0;
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes, char[] value, long hostOffset, int[] waitEvents) {
        return 0;
    }

}
