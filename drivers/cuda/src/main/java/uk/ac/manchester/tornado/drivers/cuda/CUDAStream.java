package uk.ac.manchester.tornado.drivers.cuda;

import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class CUDAStream extends TornadoLogger {
    public CUDAStream(int index) {

    }

    private native static int writeArrayHtoDAsync(long bufferId, long offset, long length, byte[] array, long hostOffset, int[] waitEvents);
    private native static int writeArrayHtoDAsync(long bufferId, long offset, long length, int[] array, long hostOffset, int[] waitEvents);

    public int enqueueRead(long bufferId, long offset, long length, byte[] array, long hostOffset, int[] waitEvents) {
        return 0;
    }

    public int enqueueRead(long bufferId, long offset, long length, int[] array, long hostOffset, int[] waitEvents) {
        return 0;
    }

    public int enqueueAsyncRead(long bufferId, long offset, long length, byte[] array, long hostOffset, int[] waitEvents) {
        return 0;
    }

    public int enqueueAsyncRead(long bufferId, long offset, long length, int[] array, long hostOffset, int[] waitEvents) {
        return 0;
    }

    public void enqueueWrite(long bufferId, long offset, long length, byte[] array, long hostOffset, int[] waitEvents) {

    }

    public void enqueueWrite(long bufferId, long offset, long length, int[] array, long hostOffset, int[] waitEvents) {

    }

    public int enqueueAsyncWrite(long bufferId, long offset, long length, byte[] array, long hostOffset, int[] waitEvents) {
        return writeArrayHtoDAsync(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public int enqueueAsyncWrite(long bufferId, long offset, long length, int[] array, long hostOffset, int[] waitEvents) {
        return writeArrayHtoDAsync(bufferId, offset, length, array, hostOffset, waitEvents);
    }
}
