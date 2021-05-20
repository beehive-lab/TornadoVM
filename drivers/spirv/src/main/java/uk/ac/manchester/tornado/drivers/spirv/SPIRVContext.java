package uk.ac.manchester.tornado.drivers.spirv;

import java.util.List;

import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public abstract class SPIRVContext {

    SPIRVPlatform platform;
    List<SPIRVDevice> devices;

    public SPIRVContext(SPIRVPlatform platform, List<SPIRVDevice> devices) {
        this.platform = platform;
        this.devices = devices;

    }

    public abstract SPIRVDeviceContext getDeviceContext(int deviceIndex);

    public abstract SPIRVCommandQueue createCommandQueue(int deviceIndex);

    public abstract SPIRVCommandQueue getCommandQueueForDevice(int deviceIndex);

    public abstract long allocateMemory(int deviceIndex, long numBytes);

    public abstract int readBuffer(int deviceIndex, long bufferId, long offset, long bytes, byte[] value, long hostOffset, int[] waitEvents);

    public abstract int readBuffer(int deviceIndex, long bufferId, long offset, long bytes, char[] value, long hostOffset, int[] waitEvents);

    public abstract int readBuffer(int deviceIndex, long bufferId, long offset, long bytes, short[] value, long hostOffset, int[] waitEvents);

    public abstract int readBuffer(int deviceIndex, long bufferId, long offset, long bytes, int[] value, long hostOffset, int[] waitEvents);

    public abstract int readBuffer(int deviceIndex, long bufferId, long offset, long bytes, float[] value, long hostOffset, int[] waitEvents);

    public abstract int readBuffer(int deviceIndex, long bufferId, long offset, long bytes, double[] value, long hostOffset, int[] waitEvents);

    public abstract int readBuffer(int deviceIndex, long bufferId, long offset, long bytes, long[] value, long hostOffset, int[] waitEvents);

    public abstract int enqueueWriteBuffer(int deviceIndex, long bufferId, long offset, long bytes, byte[] value, long hostOffset, int[] waitEvents);

    public abstract int enqueueWriteBuffer(int deviceIndex, long bufferId, long offset, long bytes, char[] value, long hostOffset, int[] waitEvents);

    public abstract int enqueueWriteBuffer(int deviceIndex, long bufferId, long offset, long bytes, short[] value, long hostOffset, int[] waitEvents);

    public abstract int enqueueWriteBuffer(int deviceIndex, long bufferId, long offset, long bytes, int[] value, long hostOffset, int[] waitEvents);

    public abstract int enqueueWriteBuffer(int deviceIndex, long bufferId, long offset, long bytes, float[] value, long hostOffset, int[] waitEvents);

    public abstract int enqueueWriteBuffer(int deviceIndex, long bufferId, long offset, long bytes, double[] value, long hostOffset, int[] waitEvents);

    public abstract int enqueueWriteBuffer(int deviceIndex, long bufferId, long offset, long bytes, long[] value, long hostOffset, int[] waitEvents);

    public abstract void enqueueBarrier(int deviceIndex);

    public abstract void flush(int deviceIndex);

    public abstract long executeAndReadLookupBufferAddressKernel(TaskMetaData meta);

}
