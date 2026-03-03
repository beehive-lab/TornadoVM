/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020-2021, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.ptx;

import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.EVENT_WINDOW;

import java.util.ArrayList;
import java.util.Arrays;

import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.drivers.common.utils.EventDescriptor;
import uk.ac.manchester.tornado.drivers.ptx.nstream.NativePTXStream;
import uk.ac.manchester.tornado.runtime.EmptyEvent;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

public class PTXStream {

    protected static final Event EMPTY_EVENT = new EmptyEvent();

    private static final int DYNAMIC_SHARED_MEMORY_BYTES = 0;

    private final byte[] streamPool;
    private final PTXEventPool ptxEventPool;
    private final PTXStreamType streamType;
    private boolean isDestroy;
    private boolean capturing = false;

    /** Pinned host staging buffer for async Java-array H2D transfers. 0 = unallocated. */
    private long stagingBufferPtr = 0L;
    /** Current capacity of the staging buffer in bytes. */
    private long stagingBufferSize = 0L;

    public PTXStream() {
        this(PTXStreamType.DEFAULT);
    }

    public PTXStream(PTXStreamType type) {
        streamPool = cuCreateStream();
        this.ptxEventPool = new PTXEventPool(EVENT_WINDOW);
        this.streamType = type;
    }

    //@formatter:off
    private static native byte[][] writeArrayDtoH(long address, long length, byte[] array, long hostOffset, byte[] streamWrapper);
    private static native byte[][] writeArrayDtoH(long address, long length, long hostPointer, long hostOffset, byte[] streamWrapper);

    private static native byte[][] writeArrayDtoH(long address, long length, short[] array, long hostOffset, byte[] streamWrapper);

    private static native byte[][] writeArrayDtoH(long address, long length, char[] array, long hostOffset, byte[] streamWrapper);

    private static native byte[][] writeArrayDtoH(long address, long length, int[] array, long hostOffset, byte[] streamWrapper);

    private static native byte[][] writeArrayDtoH(long address, long length, long[] array, long hostOffset, byte[] streamWrapper);

    private static native byte[][] writeArrayDtoH(long address, long length, float[] array, long hostOffset, byte[] streamWrapper);

    private static native byte[][] writeArrayDtoH(long address, long length, double[] array, long hostOffset, byte[] streamWrapper);

    private static native byte[][] writeArrayDtoHAsync(long address, long length, byte[] array, long hostOffset, byte[] streamWrapper);
    private static native byte[][] writeArrayDtoHAsync(long address, long length, long hostPointer, long hostOffset, byte[] streamWrapper);

    private static native byte[][] writeArrayDtoHAsync(long address, long length, short[] array, long hostOffset, byte[] streamWrapper);

    private static native byte[][] writeArrayDtoHAsync(long address, long length, char[] array, long hostOffset, byte[] streamWrapper);

    private static native byte[][] writeArrayDtoHAsync(long address, long length, int[] array, long hostOffset, byte[] streamWrapper);

    private static native byte[][] writeArrayDtoHAsync(long address, long length, long[] array, long hostOffset, byte[] streamWrapper);

    private static native byte[][] writeArrayDtoHAsync(long address, long length, float[] array, long hostOffset, byte[] streamWrapper);

    private static native byte[][] writeArrayDtoHAsync(long address, long length, double[] array, long hostOffset, byte[] streamWrapper);

    private static native byte[][] writeArrayHtoD(long address, long length, byte[] array, long hostOffset, byte[] streamWrapper);
    private static native byte[][] writeArrayHtoD(long address, long length, long hostPointer, long hostOffset, byte[] streamWrapper);

    private static native byte[][] writeArrayHtoD(long address, long length, short[] array, long hostOffset, byte[] streamWrapper);

    private static native byte[][] writeArrayHtoD(long address, long length, char[] array, long hostOffset, byte[] streamWrapper);

    private static native byte[][] writeArrayHtoD(long address, long length, int[] array, long hostOffset, byte[] streamWrapper);

    private static native byte[][] writeArrayHtoD(long address, long length, long[] array, long hostOffset, byte[] streamWrapper);

    private static native byte[][] writeArrayHtoD(long address, long length, float[] array, long hostOffset, byte[] streamWrapper);

    private static native byte[][] writeArrayHtoD(long address, long length, double[] array, long hostOffset, byte[] streamWrapper);

    private static native byte[][] writeArrayHtoDAsync(long address, long length, byte[] array, long hostOffset, long stagingPtr, byte[] streamWrapper);
    private static native byte[][] writeArrayHtoDAsync(long address, long length, long hostPointer, long hostOffset, byte[] streamWrapper);

    private static native byte[][] writeArrayHtoDAsync(long address, long length, short[] array, long hostOffset, long stagingPtr, byte[] streamWrapper);

    private static native byte[][] writeArrayHtoDAsync(long address, long length, char[] array, long hostOffset, long stagingPtr, byte[] streamWrapper);

    private static native byte[][] writeArrayHtoDAsync(long address, long length, int[] array, long hostOffset, long stagingPtr, byte[] streamWrapper);

    private static native byte[][] writeArrayHtoDAsync(long address, long length, long[] array, long hostOffset, long stagingPtr, byte[] streamWrapper);

    private static native byte[][] writeArrayHtoDAsync(long address, long length, float[] array, long hostOffset, long stagingPtr, byte[] streamWrapper);

    private static native byte[][] writeArrayHtoDAsync(long address, long length, double[] array, long hostOffset, long stagingPtr, byte[] streamWrapper);

    private static native long cuMemAllocHost(long numBytes);

    private static native void cuMemFreeHost(long hostPtr);

    //@formatter:on

    private static native byte[][] cuLaunchKernel(byte[] module, String name, int gridDimX, int gridDimY, int gridDimZ, int blockDimX, int blockDimY, int blockDimZ, long sharedMemBytes, byte[] stream,
            byte[] args);

    private static native long cuStreamBeginCapture(byte[] streamWrapper, int mode);
    private static native byte[] cuStreamEndCapture(byte[] streamWrapper);
    private static native byte[] cuGraphInstantiate(byte[] graphWrapper);
    private static native long cuGraphLaunch(byte[] graphExecWrapper, byte[] streamWrapper);
    private static native long cuGraphExecDestroy(byte[] graphExecWrapper);
    private static native long cuGraphDestroy(byte[] graphWrapper);

    /**
     * This JNI call will create a CUDA Stream through an API call to
     * cuStreamCreateWithPriority. The priority passed to the
     * cuStreamCreateWithPriority method will always be the greatest value returned
     * by cuCtxGetStreamPriorityRange.
     */
    private static native byte[] cuCreateStream();

    private static native long cuDestroyStream(byte[] streamWrapper);

    private static native long cuStreamSynchronize(byte[] streamWrapper);

    protected static native byte[][] cuEventCreateAndRecord(boolean isProfilingEnabled, byte[] streamWrapper);

    private int registerEvent(EventDescriptor descriptorId) {
        return ptxEventPool.registerEvent(
                cuEventCreateAndRecord(TornadoOptions.isProfilerEnabled(), streamPool),
                descriptorId,
                streamType);
    }

    private int registerEvent(byte[][] eventWrapper, EventDescriptor descriptorId) {
        return ptxEventPool.registerEvent(eventWrapper, descriptorId, streamType);
    }

    /**
     * Ensures the per-stream pinned staging buffer can hold at least {@code required} bytes.
     * Grows lazily, doubling capacity on each expansion to amortise re-allocation cost.
     *
     * @param required minimum bytes needed for the next staged H2D transfer
     */
    private void ensureStagingCapacity(long required) {
        if (stagingBufferPtr == 0L || stagingBufferSize < required) {
            if (stagingBufferPtr != 0L) {
                cuMemFreeHost(stagingBufferPtr);
            }
            long newSize = Math.max(required, stagingBufferSize * 2);
            stagingBufferPtr = cuMemAllocHost(newSize);
            stagingBufferSize = newSize;
        }
    }

    public void reset() {
        ptxEventPool.reset();
    }

    public void sync() {
        cuStreamSynchronize(streamPool);
    }

    public void cuDestroyStream() {
        if (stagingBufferPtr != 0L) {
            cuMemFreeHost(stagingBufferPtr);
            stagingBufferPtr = 0L;
        }
        cuDestroyStream(streamPool);
        isDestroy = true;
    }

    public Event resolveEvent(int event) {
        if (event == -1) {
            return EMPTY_EVENT;
        }
        return ptxEventPool.getEvent(event);
    }

    private void waitForEvents(int[] localEventIds) {
        if (localEventIds == null) {
            // we don't use dependencies (VM_USE_DEPS=false) -> DEFAULT stream -> no need for wait
            return;
        }

        ArrayList<PTXEvent> events = new ArrayList<>();
        for (int localEventId : localEventIds) {
            if (localEventId == -1) {
                // we use dependencies (VM_USE_DEPS=true) so we need to filter out independent events (with id=-1)
                continue;
            }
            PTXEvent cuEvent = this.ptxEventPool.getEvent(localEventId);
            if (cuEvent != null) {
                events.add(cuEvent);
            }
        }
        PTXEvent.waitForEventArray(events.toArray(new PTXEvent[0]));
    }

    public int enqueueKernelLaunch(long executionPlanId, PTXModule module, TaskDataContext taskMeta, byte[] kernelParams, int[] gridDim, int[] blockDim) {
        assert Arrays.stream(gridDim).filter(i -> i <= 0).count() == 0;
        assert Arrays.stream(blockDim).filter(i -> i <= 0).count() == 0;

        if (taskMeta.isThreadInfoEnabled()) {
            long[] blockDims = Arrays.stream(blockDim).mapToLong(i -> i).toArray();
            long[] gridDims = Arrays.stream(gridDim).mapToLong(i -> i).toArray();
            taskMeta.setPtxBlockDim(blockDims);
            taskMeta.setPtxGridDim(gridDims);
            taskMeta.printThreadDims();
        }

        return registerEvent(cuLaunchKernel(module.moduleWrapper, module.kernelFunctionName, gridDim[0], gridDim[1], gridDim[2], blockDim[0], blockDim[1], blockDim[2], DYNAMIC_SHARED_MEMORY_BYTES,
                streamPool, kernelParams), EventDescriptor.DESC_PARALLEL_KERNEL);
    }

    public int enqueueBarrier(long executionPlanId) {
        cuStreamSynchronize(streamPool);
        return registerEvent(EventDescriptor.DESC_SYNC_BARRIER);
    }

    public int enqueueBarrier(long executionPlanId, int[] events) {
        waitForEvents(events);
        return registerEvent(EventDescriptor.DESC_SYNC_BARRIER);
    }

    public int enqueueRead(long executionPlanId, long address, long length, byte[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayDtoH(address, length, array, hostOffset, streamPool), EventDescriptor.DESC_READ_BYTE);
    }

    public int enqueueRead(long executionPlanId, long address, long length, long hostPointer, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayDtoH(address, length, hostPointer, hostOffset, streamPool), EventDescriptor.DESC_READ_BYTE);
    }

    public int enqueueRead(long executionPlanId, long address, long length, short[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayDtoH(address, length, array, hostOffset, streamPool), EventDescriptor.DESC_READ_SHORT);
    }

    public int enqueueRead(long executionPlanId, long address, long length, char[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayDtoH(address, length, array, hostOffset, streamPool), EventDescriptor.DESC_READ_BYTE);
    }

    public int enqueueRead(long executionPlanId, long address, long length, int[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayDtoH(address, length, array, hostOffset, streamPool), EventDescriptor.DESC_READ_INT);
    }

    public int enqueueRead(long executionPlanId, long address, long length, long[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayDtoH(address, length, array, hostOffset, streamPool), EventDescriptor.DESC_READ_LONG);
    }

    public int enqueueRead(long executionPlanId, long address, long length, float[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayDtoH(address, length, array, hostOffset, streamPool), EventDescriptor.DESC_READ_FLOAT);
    }

    public int enqueueRead(long executionPlanId, long address, long length, double[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayDtoH(address, length, array, hostOffset, streamPool), EventDescriptor.DESC_READ_DOUBLE);
    }

    public int enqueueAsyncRead(long executionPlanId, long address, long length, long hostPointer, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayDtoHAsync(address, length, hostPointer, hostOffset, streamPool), EventDescriptor.DESC_READ_BYTE);
    }

    public int enqueueAsyncRead(long executionPlanId, long address, long length, byte[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayDtoHAsync(address, length, array, hostOffset, streamPool), EventDescriptor.DESC_READ_BYTE);
    }

    public int enqueueAsyncRead(long executionPlanId, long address, long length, short[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayDtoHAsync(address, length, array, hostOffset, streamPool), EventDescriptor.DESC_READ_SHORT);
    }

    public int enqueueAsyncRead(long executionPlanId, long address, long length, char[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayDtoHAsync(address, length, array, hostOffset, streamPool), EventDescriptor.DESC_READ_BYTE);
    }

    public int enqueueAsyncRead(long executionPlanId, long address, long length, int[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayDtoHAsync(address, length, array, hostOffset, streamPool), EventDescriptor.DESC_READ_INT);
    }

    public int enqueueAsyncRead(long executionPlanId, long address, long length, long[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayDtoHAsync(address, length, array, hostOffset, streamPool), EventDescriptor.DESC_READ_LONG);
    }

    public int enqueueAsyncRead(long executionPlanId, long address, long length, float[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayDtoHAsync(address, length, array, hostOffset, streamPool), EventDescriptor.DESC_READ_FLOAT);
    }

    public int enqueueAsyncRead(long executionPlanId, long address, long length, double[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayDtoHAsync(address, length, array, hostOffset, streamPool), EventDescriptor.DESC_READ_DOUBLE);
    }

    public void enqueueWrite(long executionPlanId, long address, long length, long hostPointer, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        registerEvent(writeArrayHtoD(address, length, hostPointer, hostOffset, streamPool), EventDescriptor.DESC_WRITE_BYTE);
    }

    public void enqueueWrite(long executionPlanId, long address, long length, byte[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        registerEvent(writeArrayHtoD(address, length, array, hostOffset, streamPool), EventDescriptor.DESC_WRITE_BYTE);
    }

    public void enqueueWrite(long executionPlanId, long address, long length, short[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        registerEvent(writeArrayHtoD(address, length, array, hostOffset, streamPool), EventDescriptor.DESC_WRITE_SHORT);
    }

    public void enqueueWrite(long executionPlanId, long address, long length, char[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        registerEvent(writeArrayHtoD(address, length, array, hostOffset, streamPool), EventDescriptor.DESC_WRITE_BYTE);
    }

    public void enqueueWrite(long executionPlanId, long address, long length, int[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        registerEvent(writeArrayHtoD(address, length, array, hostOffset, streamPool), EventDescriptor.DESC_WRITE_INT);
    }

    public void enqueueWrite(long executionPlanId, long address, long length, long[] array, int hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        registerEvent(writeArrayHtoD(address, length, array, hostOffset, streamPool), EventDescriptor.DESC_WRITE_LONG);
    }

    public void enqueueWrite(long executionPlanId, long address, long length, float[] array, int hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        registerEvent(writeArrayHtoD(address, length, array, hostOffset, streamPool), EventDescriptor.DESC_WRITE_FLOAT);
    }

    public void enqueueWrite(long executionPlanId, long address, long length, double[] array, int hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        registerEvent(writeArrayHtoD(address, length, array, hostOffset, streamPool), EventDescriptor.DESC_WRITE_DOUBLE);
    }

    public int enqueueAsyncWrite(long executionPlanId, long address, long length, long hostPointer, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayHtoDAsync(address, length, hostPointer, hostOffset, streamPool), EventDescriptor.DESC_WRITE_BYTE);
    }

    public int enqueueAsyncWrite(long executionPlanId, long address, long length, byte[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        ensureStagingCapacity(length);
        return registerEvent(writeArrayHtoDAsync(address, length, array, hostOffset, stagingBufferPtr, streamPool), EventDescriptor.DESC_WRITE_BYTE);
    }

    public int enqueueAsyncWrite(long executionPlanId, long address, long length, char[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        ensureStagingCapacity(length);
        return registerEvent(writeArrayHtoDAsync(address, length, array, hostOffset, stagingBufferPtr, streamPool), EventDescriptor.DESC_WRITE_BYTE);
    }

    public int enqueueAsyncWrite(long executionPlanId, long address, long length, short[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        ensureStagingCapacity(length);
        return registerEvent(writeArrayHtoDAsync(address, length, array, hostOffset, stagingBufferPtr, streamPool), EventDescriptor.DESC_WRITE_SHORT);
    }

    public int enqueueAsyncWrite(long executionPlanId, long address, long length, int[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        ensureStagingCapacity(length);
        return registerEvent(writeArrayHtoDAsync(address, length, array, hostOffset, stagingBufferPtr, streamPool), EventDescriptor.DESC_WRITE_INT);
    }

    public int enqueueAsyncWrite(long executionPlanId, long address, long length, long[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        ensureStagingCapacity(length);
        return registerEvent(writeArrayHtoDAsync(address, length, array, hostOffset, stagingBufferPtr, streamPool), EventDescriptor.DESC_WRITE_LONG);
    }

    public int enqueueAsyncWrite(long executionPlanId, long address, long length, float[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        ensureStagingCapacity(length);
        return registerEvent(writeArrayHtoDAsync(address, length, array, hostOffset, stagingBufferPtr, streamPool), EventDescriptor.DESC_WRITE_FLOAT);
    }

    public int enqueueAsyncWrite(long executionPlanId, long address, long length, double[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        ensureStagingCapacity(length);
        return registerEvent(writeArrayHtoDAsync(address, length, array, hostOffset, stagingBufferPtr, streamPool), EventDescriptor.DESC_WRITE_DOUBLE);
    }

    public PTXEventPool getEventPool() {
        return this.ptxEventPool;
    }

    public boolean isDestroy() {
        return isDestroy;
    }

    public byte[] getStreamHandle() {
        return streamPool;
    }

    public PTXStreamType getStreamType() {
        return streamType;
    }

    public long mapOnDeviceMemoryRegion(long destDevicePtr, long srcDevicePtr, long offset, int sizeofType) {
        return NativePTXStream.mapOnDeviceMemoryRegion(destDevicePtr, srcDevicePtr, offset, sizeofType);
    }

    /**
     * Put this stream into capture mode. All subsequent CUDA operations
     * submitted to this stream will be recorded as graph nodes.
     */
    public void beginGraphCapture() {
        long result = cuStreamBeginCapture(streamPool, 0);
        if (result != 0) {
            throw new RuntimeException("Failed to begin capture. Error: " + result);
        }
        capturing = true;
    }

    public long endGraphCaptureAndInstantiate() {
        capturing = false;
        byte[] graphWrapper = cuStreamEndCapture(streamPool);
        byte[] graphExecWrapper = cuGraphInstantiate(graphWrapper);
        cuGraphDestroy(graphWrapper);
        ptxEventPool.reset();
        return nativeHandleToLong(graphExecWrapper);
    }

    public boolean isCapturing() {
        return capturing;
    }

    /**
     * Launch a previously instantiated graph on this stream.
     */
    public int launchGraph(long graphExecHandle) {
        byte[] graphExecWrapper = longToNativeHandle(graphExecHandle);
        cuGraphLaunch(graphExecWrapper, streamPool);
        return registerEvent(EventDescriptor.DESC_SYNC_BARRIER);
    }

    /**
     * Destroy an instantiated graph.
     */
    public static void destroyGraph(long graphExecHandle) {
        byte[] graphExecWrapper = longToNativeHandle(graphExecHandle);
        cuGraphExecDestroy(graphExecWrapper);
    }

    // ─── Handle conversion helpers ───
    private static long nativeHandleToLong(byte[] handle) {
        return java.nio.ByteBuffer.wrap(handle)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN).getLong();
    }

    private static byte[] longToNativeHandle(long value) {
        byte[] handle = new byte[8];
        java.nio.ByteBuffer.wrap(handle)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN).putLong(value);
        return handle;
    }
}
