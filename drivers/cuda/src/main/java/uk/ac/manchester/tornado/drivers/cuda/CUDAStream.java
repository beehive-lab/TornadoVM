package uk.ac.manchester.tornado.drivers.cuda;

import uk.ac.manchester.tornado.runtime.common.CallStack;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

import java.nio.ByteBuffer;

public class CUDAStream extends TornadoLogger {

    private CUDAContext context;

    public CUDAStream(CUDAContext context) {

        this.context = context;
    }

    private native static int writeArrayDtoH(long bufferId, long offset, long length, byte[] array, long hostOffset, int[] waitEvents);
    private native static int writeArrayDtoH(long bufferId, long offset, long length, int[] array, long hostOffset, int[] waitEvents);

    private native static int writeArrayDtoHAsync(long bufferId, long offset, long length, byte[] array, long hostOffset, int[] waitEvents);
    private native static int writeArrayDtoHAsync(long bufferId, long offset, long length, int[] array, long hostOffset, int[] waitEvents);

    private native static void writeArrayHtoD(long bufferId, long offset, long length, int[] array, long hostOffset, int[] waitEvents);
    private native static void writeArrayHtoD(long bufferId, long offset, long length, byte[] array, long hostOffset, int[] waitEvents);

    private native static int writeArrayHtoDAsync(long bufferId, long offset, long length, int[] array, long hostOffset, int[] waitEvents);
    private native static int writeArrayHtoDAsync(long bufferId, long offset, long length, byte[] array, long hostOffset, int[] waitEvents);

    private native static int cuLaunchKernel(byte[] module, String name, int gridDimX, int gridDimY, int gridDimZ, int blockDimX, int blockDimY, int blockDimZ, long sharedMemBytes, byte[] stream, byte[] args);

    public int enqueueRead(long bufferId, long offset, long length, byte[] array, long hostOffset, int[] waitEvents) {
        return writeArrayDtoH(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public int enqueueRead(long bufferId, long offset, long length, int[] array, long hostOffset, int[] waitEvents) {
        writeArrayDtoH(bufferId, offset, length, array, hostOffset, waitEvents);
        return -1;
    }

    public int enqueueAsyncRead(long bufferId, long offset, long length, byte[] array, long hostOffset, int[] waitEvents) {
        return writeArrayDtoHAsync(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public int enqueueAsyncRead(long bufferId, long offset, long length, int[] array, long hostOffset, int[] waitEvents) {
        return writeArrayDtoHAsync(bufferId, offset, length, array, hostOffset, waitEvents);
    }


    public void enqueueWrite(long bufferId, long offset, long length, byte[] array, long hostOffset, int[] waitEvents) {
        writeArrayHtoD(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public void enqueueWrite(long bufferId, long offset, long length, int[] array, long hostOffset, int[] waitEvents) {
        writeArrayHtoD(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public int enqueueAsyncWrite(long bufferId, long offset, long length, byte[] array, long hostOffset, int[] waitEvents) {
        return writeArrayHtoDAsync(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public int enqueueAsyncWrite(long bufferId, long offset, long length, int[] array, long hostOffset, int[] waitEvents) {
        return writeArrayHtoDAsync(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public int enqueueKernelLaunch(CUDAModule module, String functionName, CallStack stack, TaskMetaData meta, long batchThreads) {
        return cuLaunchKernel(module.nativeModule, functionName, 1, 1, 1, 1, 1, 1, 0, null, getHardcodedParamsForSuperSketchyKernel());
    }

    private byte[] getHardcodedParamsForSuperSketchyKernel() {
        long baseAddress = context.getDeviceContext().getMemoryManager().toBuffer();
        long aAddr = baseAddress + 8320;
        long bAddr = baseAddress + 8448;
        long cAddr = baseAddress + 8576;
        long length = 8;

        ByteBuffer arguments = ByteBuffer.allocate(32);
        arguments.order(context.getDeviceContext().getByteOrder());
        arguments.putLong(length);
        arguments.putLong(aAddr);
        arguments.putLong(bAddr);
        arguments.putLong(cAddr);

        return arguments.array();
    }
}
