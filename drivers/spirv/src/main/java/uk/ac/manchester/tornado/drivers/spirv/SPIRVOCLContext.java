package uk.ac.manchester.tornado.drivers.spirv;

import java.util.ArrayList;
import java.util.List;

import uk.ac.manchester.tornado.drivers.opencl.OCLExecutionEnvironment;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class SPIRVOCLContext extends SPIRVContext {

    private OCLExecutionEnvironment context;
    private List<SPIRVOCLDeviceContext> spirvoclDeviceContext;

    public SPIRVOCLContext(SPIRVPlatform platform, List<SPIRVDevice> devices, OCLExecutionEnvironment context) {
        super(platform, devices);
        this.context = context;

        // Create a command queue per device;
        for (int deviceIndex = 0; deviceIndex < devices.size(); deviceIndex++) {
            context.createCommandQueue(deviceIndex);
        }

        spirvoclDeviceContext = new ArrayList<>();
        for (SPIRVDevice device : devices) {
            // We do not need command queue from this class, it was already created in the
            // constructor
            SPIRVOCLDeviceContext deviceContext = new SPIRVOCLDeviceContext(device, null, context);
            device.setDeviContext(deviceContext);
            spirvoclDeviceContext.add(deviceContext);
        }
    }

    @Override
    public SPIRVDeviceContext getDeviceContext(int deviceIndex) {
        return spirvoclDeviceContext.get(deviceIndex);
    }

    @Override
    public SPIRVCommandQueue createCommandQueue(int deviceIndex) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public SPIRVCommandQueue getCommandQueueForDevice(int deviceIndex) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public long allocateMemory(int deviceIndex, long numBytes) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public int readBuffer(int deviceIndex, long bufferId, long offset, long bytes, int[] value, long hostOffset, int[] waitEvents) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public int readBuffer(int deviceIndex, long bufferId, long offset, long bytes, float[] value, long hostOffset, int[] waitEvents) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public int enqueueWriteBuffer(int deviceIndex, long bufferId, long offset, long bytes, byte[] value, long hostOffset, int[] waitEvents) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public int enqueueWriteBuffer(int deviceIndex, long bufferId, long offset, long bytes, int[] value, long hostOffset, int[] waitEvents) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public int enqueueWriteBuffer(int deviceIndex, long bufferId, long offset, long bytes, float[] value, long hostOffset, int[] waitEvents) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public void enqueueBarrier(int deviceIndex) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public void flush(int deviceIndex) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public long executeAndReadLookupBufferAddressKernel(TaskMetaData meta) {
        throw new RuntimeException("Unimplemented");
    }

}
