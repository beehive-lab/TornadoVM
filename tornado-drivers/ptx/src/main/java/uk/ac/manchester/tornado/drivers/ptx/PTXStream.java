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
    private boolean isDestroy;

    public PTXStream() {
        streamPool = cuCreateStream();
        this.ptxEventPool = new PTXEventPool(EVENT_WINDOW);
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

    private static native byte[][] writeArrayHtoDAsync(long address, long length, byte[] array, long hostOffset, byte[] streamWrapper);
    private static native byte[][] writeArrayHtoDAsync(long address, long length, long hostPointer, long hostOffset, byte[] streamWrapper);

    private static native byte[][] writeArrayHtoDAsync(long address, long length, short[] array, long hostOffset, byte[] streamWrapper);

    private static native byte[][] writeArrayHtoDAsync(long address, long length, char[] array, long hostOffset, byte[] streamWrapper);

    private static native byte[][] writeArrayHtoDAsync(long address, long length, int[] array, long hostOffset, byte[] streamWrapper);

    private static native byte[][] writeArrayHtoDAsync(long address, long length, long[] array, long hostOffset, byte[] streamWrapper);

    private static native byte[][] writeArrayHtoDAsync(long address, long length, float[] array, long hostOffset, byte[] streamWrapper);

    private static native byte[][] writeArrayHtoDAsync(long address, long length, double[] array, long hostOffset, byte[] streamWrapper);
    //@formatter:on

    private static native byte[][] cuLaunchKernel(byte[] module, String name, int gridDimX, int gridDimY, int gridDimZ, int blockDimX, int blockDimY, int blockDimZ, long sharedMemBytes, byte[] stream,
            byte[] args);

    /**
     * This JNI call will create a CUDA Stream through an API call to
     * cuStreamCreateWithPriority. The priority passed to the
     * cuStreamCreateWithPriority method will always be the greatest value returned
     * by cuCtxGetStreamPriorityRange.
     */
    private static native byte[] cuCreateStream();

    private static native long cuDestroyStream(byte[] streamWrapper);

    private static native long cuStreamSynchronize(byte[] streamWrapper);

    private static native byte[][] cuEventCreateAndRecord(boolean isProfilingEnabled, byte[] streamWrapper);

    private int registerEvent(EventDescriptor descriptorId) {
        return ptxEventPool.registerEvent(cuEventCreateAndRecord(TornadoOptions.isProfilerEnabled(), streamPool), descriptorId);
    }

    private int registerEvent(byte[][] eventWrapper, EventDescriptor descriptorId) {
        return ptxEventPool.registerEvent(eventWrapper, descriptorId);
    }

    public void reset() {
        ptxEventPool.reset();
    }

    public void sync() {
        cuStreamSynchronize(streamPool);
    }

    public void cuDestroyStream() {
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
            return;
        }

        ArrayList<PTXEvent> events = new ArrayList<>();
        for (int localEventId : localEventIds) {
            PTXEvent cuEvent = this.ptxEventPool.getEvent(localEventId);
            if (cuEvent != null) {
                events.add(cuEvent);
            }
        }
        PTXEvent.waitForEventArray((PTXEvent[]) events.toArray());
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
        return registerEvent(writeArrayHtoDAsync(address, length, array, hostOffset, streamPool), EventDescriptor.DESC_WRITE_BYTE);
    }

    public int enqueueAsyncWrite(long executionPlanId, long address, long length, char[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayHtoDAsync(address, length, array, hostOffset, streamPool), EventDescriptor.DESC_WRITE_BYTE);
    }

    public int enqueueAsyncWrite(long executionPlanId, long address, long length, short[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayHtoDAsync(address, length, array, hostOffset, streamPool), EventDescriptor.DESC_WRITE_SHORT);
    }

    public int enqueueAsyncWrite(long executionPlanId, long address, long length, int[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayHtoDAsync(address, length, array, hostOffset, streamPool), EventDescriptor.DESC_WRITE_INT);

    }

    public int enqueueAsyncWrite(long executionPlanId, long address, long length, long[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayHtoDAsync(address, length, array, hostOffset, streamPool), EventDescriptor.DESC_WRITE_LONG);
    }

    public int enqueueAsyncWrite(long executionPlanId, long address, long length, float[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayHtoDAsync(address, length, array, hostOffset, streamPool), EventDescriptor.DESC_WRITE_FLOAT);
    }

    public int enqueueAsyncWrite(long executionPlanId, long address, long length, double[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayHtoDAsync(address, length, array, hostOffset, streamPool), EventDescriptor.DESC_WRITE_DOUBLE);
    }

    public PTXEventPool getEventPool() {
        return this.ptxEventPool;
    }

    public boolean isDestroy() {
        return isDestroy;
    }

    public long mapOnDeviceMemoryRegion(long destDevicePtr, long srcDevicePtr, long offset) {
        final int sizeofType = 4;
        return NativePTXStream.mapOnDeviceMemoryRegion(destDevicePtr, srcDevicePtr, offset, sizeofType);
    }
}
