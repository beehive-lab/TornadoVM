package uk.ac.manchester.tornado.drivers.cuda;

import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class CUDAStream extends TornadoLogger {

    public CUDAStream() {

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
        System.out.println("Async write");
        return writeArrayHtoDAsync(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public int enqueueKernelLaunch(CUDAModule module, String functionName, byte[] kernelParams, int[] gridDim, int[] blockDim) {
        return cuLaunchKernel(module.nativeModule, functionName,
                              gridDim[0], gridDim[1], gridDim[2],
                              blockDim[0], blockDim[1], blockDim[2],
                              0, null,
                              kernelParams
        );
    }
}
