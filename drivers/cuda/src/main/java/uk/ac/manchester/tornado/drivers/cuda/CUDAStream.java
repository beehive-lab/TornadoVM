package uk.ac.manchester.tornado.drivers.cuda;

import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

import java.util.HashMap;
import java.util.Map;

public class CUDAStream extends TornadoLogger {
    private final byte[] streamWrapper;
    private Map<Integer, CUDAEvent> events;
    private int eventCount;

    public CUDAStream() {
        streamWrapper = cuCreateStream();
        events = new HashMap<>();
        eventCount = 0;
    }

    private native static int writeArrayDtoH(long bufferId, long offset, long length, byte[] array, long hostOffset);
    private native static int writeArrayDtoH(long bufferId, long offset, long length, short[] array, long hostOffset);
    private native static int writeArrayDtoH(long bufferId, long offset, long length, char[] array, long hostOffset);
    private native static int writeArrayDtoH(long bufferId, long offset, long length, int[] array, long hostOffset);
    private native static int writeArrayDtoH(long bufferId, long offset, long length, long[] array, long hostOffset);
    private native static int writeArrayDtoH(long bufferId, long offset, long length, float[] array, long hostOffset);
    private native static int writeArrayDtoH(long bufferId, long offset, long length, double[] array, long hostOffset);

    private native static int writeArrayDtoHAsync(long bufferId, long offset, long length, byte[] array, long hostOffset, byte[] streamWrapper);
    private native static int writeArrayDtoHAsync(long bufferId, long offset, long length, short[] array, long hostOffset, byte[] streamWrapper);
    private native static int writeArrayDtoHAsync(long bufferId, long offset, long length, char[] array, long hostOffset, byte[] streamWrapper);
    private native static int writeArrayDtoHAsync(long bufferId, long offset, long length, int[] array, long hostOffset, byte[] streamWrapper);
    private native static int writeArrayDtoHAsync(long bufferId, long offset, long length, long[] array, long hostOffset, byte[] streamWrapper);
    private native static int writeArrayDtoHAsync(long bufferId, long offset, long length, float[] array, long hostOffset, byte[] streamWrapper);
    private native static int writeArrayDtoHAsync(long bufferId, long offset, long length, double[] array, long hostOffset, byte[] streamWrapper);

    private native static void writeArrayHtoD(long bufferId, long offset, long length, byte[] array, long hostOffset);
    private native static void writeArrayHtoD(long bufferId, long offset, long length, short[] array, long hostOffset);
    private native static void writeArrayHtoD(long bufferId, long offset, long length, char[] array, long hostOffset);
    private native static void writeArrayHtoD(long bufferId, long offset, long length, int[] array, long hostOffset);
    private native static void writeArrayHtoD(long bufferId, long offset, long length, long[] array, long hostOffset);
    private native static void writeArrayHtoD(long bufferId, long offset, long length, float[] array, long hostOffset);
    private native static void writeArrayHtoD(long bufferId, long offset, long length, double[] array, long hostOffset);

    private native static int writeArrayHtoDAsync(long bufferId, long offset, long length, byte[] array, long hostOffset, byte[] streamWrapper);
    private native static int writeArrayHtoDAsync(long bufferId, long offset, long length, short[] array, long hostOffset, byte[] streamWrapper);
    private native static int writeArrayHtoDAsync(long bufferId, long offset, long length, char[] array, long hostOffset, byte[] streamWrapper);
    private native static int writeArrayHtoDAsync(long bufferId, long offset, long length, int[] array, long hostOffset, byte[] streamWrapper);
    private native static int writeArrayHtoDAsync(long bufferId, long offset, long length, long[] array, long hostOffset, byte[] streamWrapper);
    private native static int writeArrayHtoDAsync(long bufferId, long offset, long length, float[] array, long hostOffset, byte[] streamWrapper);
    private native static int writeArrayHtoDAsync(long bufferId, long offset, long length, double[] array, long hostOffset, byte[] streamWrapper);

    private native static int cuLaunchKernel(byte[] module, String name, int gridDimX, int gridDimY, int gridDimZ, int blockDimX, int blockDimY, int blockDimZ, long sharedMemBytes, byte[] stream, byte[] args);

    private native static byte[] cuCreateStream();
    private native static void cuDestroyStream(byte[] streamWrapper);
    private native static void cuStreamSynchronize(byte[] streamWrapper);

    private native static byte[] cuEventCreateAndRecord(boolean isProfilingEnabled, byte[] streamWrapper);

    private int recordEvent() {
        CUDAEvent event = new CUDAEvent(cuEventCreateAndRecord(TornadoOptions.isProfilerEnabled(), streamWrapper));
        events.put(eventCount, event);
        eventCount++;
        return eventCount - 1;
    }


    public void reset() {
        events.forEach((key, event) -> {
            event.destroy();
        });
    }

    public void sync() {
        cuStreamSynchronize(streamWrapper);
    }

    public void cleanup() {
        cuDestroyStream(streamWrapper);
    }

    public CUDAEvent resolveEvent(int event) {
        return events.get(event);
    }

    public int enqueueKernelLaunch(CUDAModule module, byte[] kernelParams, int[] gridDim, int[] blockDim) {
        cuLaunchKernel(module.nativeModule, module.kernelFunctionName,
                              gridDim[0], gridDim[1], gridDim[2],
                              blockDim[0], blockDim[1], blockDim[2],
                              0, streamWrapper,
                              kernelParams
        );
        return recordEvent();
    }

    public int enqueueRead(long bufferId, long offset, long length, byte[] array, long hostOffset, int[] waitEvents) {
        writeArrayDtoH(bufferId, offset, length, array, hostOffset);
        return recordEvent();
    }

    public int enqueueRead(long bufferId, long offset, long length, short[] array, long hostOffset, int[] waitEvents) {
        writeArrayDtoH(bufferId, offset, length, array, hostOffset);
        return recordEvent();
    }

    public int enqueueRead(long bufferId, long offset, long length, char[] array, long hostOffset, int[] waitEvents) {
        writeArrayDtoH(bufferId, offset, length, array, hostOffset);
        return recordEvent();
    }

    public int enqueueRead(long bufferId, long offset, long length, int[] array, long hostOffset, int[] waitEvents) {
        writeArrayDtoH(bufferId, offset, length, array, hostOffset);
        return recordEvent();
    }

    public int enqueueRead(long bufferId, long offset, long length, long[] array, long hostOffset, int[] waitEvents) {
        writeArrayDtoH(bufferId, offset, length, array, hostOffset);
        return recordEvent();
    }

    public int enqueueRead(long bufferId, long offset, long length, float[] array, long hostOffset, int[] waitEvents) {
        writeArrayDtoH(bufferId, offset, length, array, hostOffset);
        return recordEvent();
    }

    public int enqueueRead(long bufferId, long offset, long length, double[] array, long hostOffset, int[] waitEvents) {
        writeArrayDtoH(bufferId, offset, length, array, hostOffset);
        return recordEvent();
    }


    public int enqueueAsyncRead(long bufferId, long offset, long length, byte[] array, long hostOffset, int[] waitEvents) {
        writeArrayDtoHAsync(bufferId, offset, length, array, hostOffset, streamWrapper);
        return recordEvent();
    }

    public int enqueueAsyncRead(long bufferId, long offset, long length, short[] array, long hostOffset, int[] waitEvents) {
        writeArrayDtoHAsync(bufferId, offset, length, array, hostOffset, streamWrapper);
        return recordEvent();
    }

    public int enqueueAsyncRead(long bufferId, long offset, long length, char[] array, long hostOffset, int[] waitEvents) {
        writeArrayDtoHAsync(bufferId, offset, length, array, hostOffset, streamWrapper);
        return recordEvent();
    }

    public int enqueueAsyncRead(long bufferId, long offset, long length, int[] array, long hostOffset, int[] waitEvents) {
        writeArrayDtoHAsync(bufferId, offset, length, array, hostOffset, streamWrapper);
        return recordEvent();
    }

    public int enqueueAsyncRead(long bufferId, long offset, long length, long[] array, long hostOffset, int[] waitEvents) {
        writeArrayDtoHAsync(bufferId, offset, length, array, hostOffset, streamWrapper);
        return recordEvent();
    }

    public int enqueueAsyncRead(long bufferId, long offset, long length, float[] array, long hostOffset, int[] waitEvents) {
        writeArrayDtoHAsync(bufferId, offset, length, array, hostOffset, streamWrapper);
        return recordEvent();
    }

    public int enqueueAsyncRead(long bufferId, long offset, long length, double[] array, long hostOffset, int[] waitEvents) {
        writeArrayDtoHAsync(bufferId, offset, length, array, hostOffset, streamWrapper);
        return recordEvent();
    }


    public void enqueueWrite(long bufferId, long offset, long length, byte[] array, long hostOffset, int[] waitEvents) {
        writeArrayHtoD(bufferId, offset, length, array, hostOffset);
    }

    public void enqueueWrite(long bufferId, long offset, long length, short[] array, long hostOffset, int[] waitEvents) {
        writeArrayHtoD(bufferId, offset, length, array, hostOffset);
    }

    public void enqueueWrite(long bufferId, long offset, long length, char[] array, long hostOffset, int[] waitEvents) {
        writeArrayHtoD(bufferId, offset, length, array, hostOffset);
    }

    public void enqueueWrite(long bufferId, long offset, long length, int[] array, long hostOffset, int[] waitEvents) {
        writeArrayHtoD(bufferId, offset, length, array, hostOffset);
    }

    public void enqueueWrite(long bufferId, long offset, long length, long[] array, int hostOffset, int[] waitEvents) {
        writeArrayHtoD(bufferId, offset, length, array, hostOffset);
    }

    public void enqueueWrite(long bufferId, long offset, long length, float[] array, int hostOffset, int[] waitEvents) {
        writeArrayHtoD(bufferId, offset, length, array, hostOffset);
    }

    public void enqueueWrite(long bufferId, long offset, long length, double[] array, int hostOffset, int[] waitEvents) {
        writeArrayHtoD(bufferId, offset, length, array, hostOffset);
    }


    public int enqueueAsyncWrite(long bufferId, long offset, long length, byte[] array, long hostOffset, int[] waitEvents) {
        writeArrayHtoDAsync(bufferId, offset, length, array, hostOffset, streamWrapper);
        return recordEvent();
    }

    public int enqueueAsyncWrite(long bufferId, long offset, long length, char[] array, long hostOffset, int[] waitEvents) {
        writeArrayHtoDAsync(bufferId, offset, length, array, hostOffset, streamWrapper);
        return recordEvent();
    }

    public int enqueueAsyncWrite(long bufferId, long offset, long length, short[] array, long hostOffset, int[] waitEvents) {
        writeArrayHtoDAsync(bufferId, offset, length, array, hostOffset, streamWrapper);
        return recordEvent();
    }

    public int enqueueAsyncWrite(long bufferId, long offset, long length, int[] array, long hostOffset, int[] waitEvents) {
        writeArrayHtoDAsync(bufferId, offset, length, array, hostOffset, streamWrapper);
        return recordEvent();
    }

    public int enqueueAsyncWrite(long bufferId, long offset, long length, long[] array, long hostOffset, int[] waitEvents) {
        writeArrayHtoDAsync(bufferId, offset, length, array, hostOffset, streamWrapper);
        return recordEvent();
    }

    public int enqueueAsyncWrite(long bufferId, long offset, long length, float[] array, long hostOffset, int[] waitEvents) {
        writeArrayHtoDAsync(bufferId, offset, length, array, hostOffset, streamWrapper);
        return recordEvent();
    }

    public int enqueueAsyncWrite(long bufferId, long offset, long length, double[] array, long hostOffset, int[] waitEvents) {
        writeArrayHtoDAsync(bufferId, offset, length, array, hostOffset, streamWrapper);
        return recordEvent();
    }
}
