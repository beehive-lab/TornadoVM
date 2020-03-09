package uk.ac.manchester.tornado.drivers.cuda;

import uk.ac.manchester.tornado.drivers.cuda.CUDAEvent.EventDescription;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static uk.ac.manchester.tornado.runtime.common.Tornado.DEBUG;

public class CUDAStream extends TornadoLogger {
    private final byte[] streamWrapper;
    private Map<Integer, CUDAEvent> recordedEvents;
    private int eventCount;

    public CUDAStream() {
        streamWrapper = cuCreateStream();
        recordedEvents = new HashMap<>();
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

    private int recordEvent(EventDescription description) {
        CUDAEvent event = new CUDAEvent(cuEventCreateAndRecord(TornadoOptions.isProfilerEnabled(), streamWrapper), description);
        recordedEvents.put(eventCount, event);
        eventCount++;
        return eventCount - 1;
    }


    public void reset() {
        recordedEvents.forEach((key, event) -> event.destroy());
        recordedEvents.clear();
        eventCount = 0;
    }

    public void sync() {
        cuStreamSynchronize(streamWrapper);
    }

    public void cleanup() {
        cuDestroyStream(streamWrapper);
    }

    public CUDAEvent resolveEvent(int event) {
        return recordedEvents.get(event);
    }

    private void waitForEvents(int[] eventIds) {
        if (eventIds == null) return;
        CUDAEvent[] events = new CUDAEvent[eventIds.length];
        for (int i = 0; i < eventIds.length; i++) {
            events[i] = recordedEvents.get(eventIds[i]);
        }
        CUDAEvent.waitForEventArray(events);
    }

    public int enqueueKernelLaunch(CUDAModule module, byte[] kernelParams, int[] gridDim, int[] blockDim) {
        if (DEBUG) {
            System.out.println("Executing: " + module.kernelFunctionName);
            System.out.println("   Blocks: " + Arrays.toString(blockDim));
            System.out.println("    Grids: " + Arrays.toString(gridDim));
        }

        cuLaunchKernel(module.moduleWrapper, module.kernelFunctionName,
                       gridDim[0], gridDim[1], gridDim[2],
                       blockDim[0], blockDim[1], blockDim[2],
                       0, streamWrapper,
                       kernelParams
        );
        return recordEvent(EventDescription.KERNEL);
    }

    public int enqueueBarrier() {
        return recordEvent(EventDescription.BARRIER);
    }

    public int enqueueBarrier(int[] events) {
        waitForEvents(events);
        return enqueueBarrier();
    }

    public int enqueueRead(long bufferId, long offset, long length, byte[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        writeArrayDtoH(bufferId, offset, length, array, hostOffset);
        return recordEvent(EventDescription.MEMCPY_D_TO_H_BYTE);
    }

    public int enqueueRead(long bufferId, long offset, long length, short[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        writeArrayDtoH(bufferId, offset, length, array, hostOffset);
        return recordEvent(EventDescription.MEMCPY_D_TO_H_SHORT);
    }

    public int enqueueRead(long bufferId, long offset, long length, char[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        writeArrayDtoH(bufferId, offset, length, array, hostOffset);
        return recordEvent(EventDescription.MEMCPY_D_TO_H_CHAR);
    }

    public int enqueueRead(long bufferId, long offset, long length, int[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        writeArrayDtoH(bufferId, offset, length, array, hostOffset);
        return recordEvent(EventDescription.MEMCPY_D_TO_H_INT);
    }

    public int enqueueRead(long bufferId, long offset, long length, long[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        writeArrayDtoH(bufferId, offset, length, array, hostOffset);
        return recordEvent(EventDescription.MEMCPY_D_TO_H_LONG);
    }

    public int enqueueRead(long bufferId, long offset, long length, float[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        writeArrayDtoH(bufferId, offset, length, array, hostOffset);
        return recordEvent(EventDescription.MEMCPY_D_TO_H_FLOAT);
    }

    public int enqueueRead(long bufferId, long offset, long length, double[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        writeArrayDtoH(bufferId, offset, length, array, hostOffset);
        return recordEvent(EventDescription.MEMCPY_D_TO_H_DOUBLE);
    }


    public int enqueueAsyncRead(long bufferId, long offset, long length, byte[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        writeArrayDtoHAsync(bufferId, offset, length, array, hostOffset, streamWrapper);
        return recordEvent(EventDescription.MEMCPY_D_TO_H_BYTE);
    }

    public int enqueueAsyncRead(long bufferId, long offset, long length, short[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        writeArrayDtoHAsync(bufferId, offset, length, array, hostOffset, streamWrapper);
        return recordEvent(EventDescription.MEMCPY_D_TO_H_SHORT);
    }

    public int enqueueAsyncRead(long bufferId, long offset, long length, char[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        writeArrayDtoHAsync(bufferId, offset, length, array, hostOffset, streamWrapper);
        return recordEvent(EventDescription.MEMCPY_D_TO_H_CHAR);
    }

    public int enqueueAsyncRead(long bufferId, long offset, long length, int[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        writeArrayDtoHAsync(bufferId, offset, length, array, hostOffset, streamWrapper);
        return recordEvent(EventDescription.MEMCPY_D_TO_H_INT);
    }

    public int enqueueAsyncRead(long bufferId, long offset, long length, long[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        writeArrayDtoHAsync(bufferId, offset, length, array, hostOffset, streamWrapper);
        return recordEvent(EventDescription.MEMCPY_D_TO_H_LONG);
    }

    public int enqueueAsyncRead(long bufferId, long offset, long length, float[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        writeArrayDtoHAsync(bufferId, offset, length, array, hostOffset, streamWrapper);
        return recordEvent(EventDescription.MEMCPY_D_TO_H_FLOAT);
    }

    public int enqueueAsyncRead(long bufferId, long offset, long length, double[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        writeArrayDtoHAsync(bufferId, offset, length, array, hostOffset, streamWrapper);
        return recordEvent(EventDescription.MEMCPY_D_TO_H_DOUBLE);
    }


    public void enqueueWrite(long bufferId, long offset, long length, byte[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        writeArrayHtoD(bufferId, offset, length, array, hostOffset);
    }

    public void enqueueWrite(long bufferId, long offset, long length, short[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        writeArrayHtoD(bufferId, offset, length, array, hostOffset);
    }

    public void enqueueWrite(long bufferId, long offset, long length, char[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        writeArrayHtoD(bufferId, offset, length, array, hostOffset);
    }

    public void enqueueWrite(long bufferId, long offset, long length, int[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        writeArrayHtoD(bufferId, offset, length, array, hostOffset);
    }

    public void enqueueWrite(long bufferId, long offset, long length, long[] array, int hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        writeArrayHtoD(bufferId, offset, length, array, hostOffset);
    }

    public void enqueueWrite(long bufferId, long offset, long length, float[] array, int hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        writeArrayHtoD(bufferId, offset, length, array, hostOffset);
    }

    public void enqueueWrite(long bufferId, long offset, long length, double[] array, int hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        writeArrayHtoD(bufferId, offset, length, array, hostOffset);
    }


    public int enqueueAsyncWrite(long bufferId, long offset, long length, byte[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        writeArrayHtoDAsync(bufferId, offset, length, array, hostOffset, streamWrapper);
        return recordEvent(EventDescription.MEMCPY_H_TO_D_BYTE);
    }

    public int enqueueAsyncWrite(long bufferId, long offset, long length, char[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        writeArrayHtoDAsync(bufferId, offset, length, array, hostOffset, streamWrapper);
        return recordEvent(EventDescription.MEMCPY_H_TO_D_CHAR);
    }

    public int enqueueAsyncWrite(long bufferId, long offset, long length, short[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        writeArrayHtoDAsync(bufferId, offset, length, array, hostOffset, streamWrapper);
        return recordEvent(EventDescription.MEMCPY_H_TO_D_SHORT);
    }

    public int enqueueAsyncWrite(long bufferId, long offset, long length, int[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        writeArrayHtoDAsync(bufferId, offset, length, array, hostOffset, streamWrapper);
        return recordEvent(EventDescription.MEMCPY_H_TO_D_INT);
    }

    public int enqueueAsyncWrite(long bufferId, long offset, long length, long[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        writeArrayHtoDAsync(bufferId, offset, length, array, hostOffset, streamWrapper);
        return recordEvent(EventDescription.MEMCPY_H_TO_D_LONG);
    }

    public int enqueueAsyncWrite(long bufferId, long offset, long length, float[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        writeArrayHtoDAsync(bufferId, offset, length, array, hostOffset, streamWrapper);
        return recordEvent(EventDescription.MEMCPY_H_TO_D_FLOAT);
    }

    public int enqueueAsyncWrite(long bufferId, long offset, long length, double[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        writeArrayHtoDAsync(bufferId, offset, length, array, hostOffset, streamWrapper);
        return recordEvent(EventDescription.MEMCPY_H_TO_D_DOUBLE);
    }
}
