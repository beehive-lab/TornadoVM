package uk.ac.manchester.tornado.drivers.ptx;

import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.drivers.ptx.PTXEvent.EventDescription;
import uk.ac.manchester.tornado.runtime.EmptyEvent;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

import java.util.ArrayList;
import java.util.Arrays;

import static uk.ac.manchester.tornado.runtime.common.Tornado.DEBUG;

public class PTXStream extends TornadoLogger {

    protected static final Event EMPTY_EVENT = new EmptyEvent();

    private final byte[] streamWrapper;
    private final PTXEventsWrapper eventsWrapper;

    public PTXStream() {
        streamWrapper = cuCreateStream();

        this.eventsWrapper = new PTXEventsWrapper();
    }

    //@formatter:off
    private native static byte[][] writeArrayDtoH(long address, long length, byte[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayDtoH(long address, long length, short[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayDtoH(long address, long length, char[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayDtoH(long address, long length, int[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayDtoH(long address, long length, long[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayDtoH(long address, long length, float[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayDtoH(long address, long length, double[] array, long hostOffset, byte[] streamWrapper);

    private native static byte[][] writeArrayDtoHAsync(long address, long length, byte[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayDtoHAsync(long address, long length, short[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayDtoHAsync(long address, long length, char[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayDtoHAsync(long address, long length, int[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayDtoHAsync(long address, long length, long[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayDtoHAsync(long address, long length, float[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayDtoHAsync(long address, long length, double[] array, long hostOffset, byte[] streamWrapper);

    private native static byte[][] writeArrayHtoD(long address, long length, byte[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayHtoD(long address, long length, short[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayHtoD(long address, long length, char[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayHtoD(long address, long length, int[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayHtoD(long address, long length, long[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayHtoD(long address, long length, float[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayHtoD(long address, long length, double[] array, long hostOffset, byte[] streamWrapper);

    private native static byte[][] writeArrayHtoDAsync(long address, long length, byte[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayHtoDAsync(long address, long length, short[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayHtoDAsync(long address, long length, char[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayHtoDAsync(long address, long length, int[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayHtoDAsync(long address, long length, long[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayHtoDAsync(long address, long length, float[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayHtoDAsync(long address, long length, double[] array, long hostOffset, byte[] streamWrapper);

    private native static byte[][] cuLaunchKernel(byte[] module, String name, int gridDimX, int gridDimY, int gridDimZ, int blockDimX, int blockDimY, int blockDimZ, long sharedMemBytes, byte[] stream, byte[] args);

    private native static byte[] cuCreateStream();
    private native static long cuDestroyStream(byte[] streamWrapper);
    private native static long cuStreamSynchronize(byte[] streamWrapper);

    private native static byte[][] cuEventCreateAndRecord(boolean isProfilingEnabled, byte[] streamWrapper);
    //@formatter:on

    private int recordEvent(EventDescription description) {
        return eventsWrapper.registerEvent(cuEventCreateAndRecord(TornadoOptions.isProfilerEnabled(), streamWrapper), description);
    }

    private int record(byte[][] eventWrapper, EventDescription description) {
        return eventsWrapper.registerEvent(eventWrapper, description);
    }

    public void reset() {
        eventsWrapper.reset();
    }

    public void sync() {
        cuStreamSynchronize(streamWrapper);
    }

    public void cleanup() {
        cuDestroyStream(streamWrapper);
    }

    public Event resolveEvent(int event) {
        if (event == -1) {
            return EMPTY_EVENT;
        }
        return eventsWrapper.getEvent(event);
    }

    private void waitForEvents(int[] localEventIds) {
        if (localEventIds == null)
            return;
        ArrayList<PTXEvent> events = new ArrayList<>();
        for (int localEventId : localEventIds) {
            PTXEvent cuEvent = this.eventsWrapper.getEvent(localEventId);
            if (cuEvent != null) {
                events.add(cuEvent);
            }
        }
        PTXEvent.waitForEventArray((PTXEvent[]) events.toArray());
    }

    public int enqueueKernelLaunch(PTXModule module, byte[] kernelParams, int[] gridDim, int[] blockDim) {
        if (DEBUG) {
            System.out.println("Executing: " + module.kernelFunctionName);
            System.out.println("   Blocks: " + Arrays.toString(blockDim));
            System.out.println("    Grids: " + Arrays.toString(gridDim));
        }

        return record(cuLaunchKernel(module.moduleWrapper, module.kernelFunctionName, gridDim[0], gridDim[1], gridDim[2], blockDim[0], blockDim[1], blockDim[2], 0, streamWrapper, kernelParams),
                EventDescription.KERNEL);
    }

    public int enqueueBarrier() {
        return recordEvent(EventDescription.BARRIER);
    }

    public int enqueueBarrier(int[] events) {
        waitForEvents(events);
        return enqueueBarrier();
    }

    public int enqueueRead(long address, long length, byte[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(writeArrayDtoH(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_D_TO_H_BYTE);
    }

    public int enqueueRead(long address, long length, short[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(writeArrayDtoH(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_D_TO_H_SHORT);
    }

    public int enqueueRead(long address, long length, char[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(writeArrayDtoH(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_D_TO_H_CHAR);
    }

    public int enqueueRead(long address, long length, int[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(writeArrayDtoH(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_D_TO_H_INT);
    }

    public int enqueueRead(long address, long length, long[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(writeArrayDtoH(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_D_TO_H_LONG);
    }

    public int enqueueRead(long address, long length, float[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(writeArrayDtoH(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_D_TO_H_FLOAT);
    }

    public int enqueueRead(long address, long length, double[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(writeArrayDtoH(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_D_TO_H_DOUBLE);
    }

    public int enqueueAsyncRead(long address, long length, byte[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(writeArrayDtoHAsync(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_D_TO_H_BYTE);
    }

    public int enqueueAsyncRead(long address, long length, short[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(writeArrayDtoHAsync(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_D_TO_H_SHORT);
    }

    public int enqueueAsyncRead(long address, long length, char[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(writeArrayDtoHAsync(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_D_TO_H_CHAR);
    }

    public int enqueueAsyncRead(long address, long length, int[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(writeArrayDtoHAsync(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_D_TO_H_INT);
    }

    public int enqueueAsyncRead(long address, long length, long[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(writeArrayDtoHAsync(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_D_TO_H_LONG);
    }

    public int enqueueAsyncRead(long address, long length, float[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(writeArrayDtoHAsync(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_D_TO_H_FLOAT);
    }

    public int enqueueAsyncRead(long address, long length, double[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(writeArrayDtoHAsync(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_D_TO_H_DOUBLE);
    }

    public void enqueueWrite(long address, long length, byte[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        record(writeArrayHtoD(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_H_TO_D_BYTE);
    }

    public void enqueueWrite(long address, long length, short[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        record(writeArrayHtoD(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_H_TO_D_SHORT);
    }

    public void enqueueWrite(long address, long length, char[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        record(writeArrayHtoD(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_H_TO_D_CHAR);
    }

    public void enqueueWrite(long address, long length, int[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        record(writeArrayHtoD(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_H_TO_D_INT);
    }

    public void enqueueWrite(long address, long length, long[] array, int hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        record(writeArrayHtoD(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_H_TO_D_LONG);
    }

    public void enqueueWrite(long address, long length, float[] array, int hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        record(writeArrayHtoD(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_H_TO_D_FLOAT);
    }

    public void enqueueWrite(long address, long length, double[] array, int hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        record(writeArrayHtoD(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_H_TO_D_DOUBLE);
    }

    public int enqueueAsyncWrite(long address, long length, byte[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(writeArrayHtoDAsync(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_H_TO_D_BYTE);
    }

    public int enqueueAsyncWrite(long address, long length, char[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(writeArrayHtoDAsync(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_H_TO_D_CHAR);
    }

    public int enqueueAsyncWrite(long address, long length, short[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(writeArrayHtoDAsync(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_H_TO_D_SHORT);
    }

    public int enqueueAsyncWrite(long address, long length, int[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(writeArrayHtoDAsync(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_H_TO_D_INT);

    }

    public int enqueueAsyncWrite(long address, long length, long[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(writeArrayHtoDAsync(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_H_TO_D_LONG);
    }

    public int enqueueAsyncWrite(long address, long length, float[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(writeArrayHtoDAsync(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_H_TO_D_FLOAT);
    }

    public int enqueueAsyncWrite(long address, long length, double[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(writeArrayHtoDAsync(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_H_TO_D_DOUBLE);
    }
}
