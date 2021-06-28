/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.ptx;

import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.runtime.EmptyEvent;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

import java.util.ArrayList;
import java.util.Arrays;

import static uk.ac.manchester.tornado.drivers.ptx.PTXEvent.DEFAULT_TAG;
import static uk.ac.manchester.tornado.drivers.ptx.PTXEvent.DESC_PARALLEL_KERNEL;
import static uk.ac.manchester.tornado.drivers.ptx.PTXEvent.DESC_READ_BYTE;
import static uk.ac.manchester.tornado.drivers.ptx.PTXEvent.DESC_READ_DOUBLE;
import static uk.ac.manchester.tornado.drivers.ptx.PTXEvent.DESC_READ_FLOAT;
import static uk.ac.manchester.tornado.drivers.ptx.PTXEvent.DESC_READ_INT;
import static uk.ac.manchester.tornado.drivers.ptx.PTXEvent.DESC_READ_LONG;
import static uk.ac.manchester.tornado.drivers.ptx.PTXEvent.DESC_READ_SHORT;
import static uk.ac.manchester.tornado.drivers.ptx.PTXEvent.DESC_SYNC_BARRIER;
import static uk.ac.manchester.tornado.drivers.ptx.PTXEvent.DESC_WRITE_BYTE;
import static uk.ac.manchester.tornado.drivers.ptx.PTXEvent.DESC_WRITE_DOUBLE;
import static uk.ac.manchester.tornado.drivers.ptx.PTXEvent.DESC_WRITE_FLOAT;
import static uk.ac.manchester.tornado.drivers.ptx.PTXEvent.DESC_WRITE_INT;
import static uk.ac.manchester.tornado.drivers.ptx.PTXEvent.DESC_WRITE_LONG;
import static uk.ac.manchester.tornado.drivers.ptx.PTXEvent.DESC_WRITE_SHORT;

public class PTXStream extends TornadoLogger {

    protected static final Event EMPTY_EVENT = new EmptyEvent();

    private static final int DYNAMIC_SHARED_MEMORY_BYTES = 0;

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
    //@formatter:on

    private native static byte[][] cuLaunchKernel(byte[] module, String name, int gridDimX, int gridDimY, int gridDimZ, int blockDimX, int blockDimY, int blockDimZ, long sharedMemBytes, byte[] stream,
            byte[] args);

    /**
     * This JNI call will create a CUDA Stream through an API call to
     * cuStreamCreateWithPriority. The priority passed to the
     * cuStreamCreateWithPriority method will always be the greatest value returned
     * by cuCtxGetStreamPriorityRange.
     */
    private native static byte[] cuCreateStream();

    private native static long cuDestroyStream(byte[] streamWrapper);

    private native static long cuStreamSynchronize(byte[] streamWrapper);

    private native static byte[][] cuEventCreateAndRecord(boolean isProfilingEnabled, byte[] streamWrapper);

    private int registerEvent(int descriptorId, long tag) {
        return eventsWrapper.registerEvent(cuEventCreateAndRecord(TornadoOptions.isProfilerEnabled(), streamWrapper), descriptorId, tag);
    }

    private int registerEvent(byte[][] eventWrapper, int descriptorId, long tag) {
        return eventsWrapper.registerEvent(eventWrapper, descriptorId, tag);
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
        if (localEventIds == null) {
            return;
        }

        ArrayList<PTXEvent> events = new ArrayList<>();
        for (int localEventId : localEventIds) {
            PTXEvent cuEvent = this.eventsWrapper.getEvent(localEventId);
            if (cuEvent != null) {
                events.add(cuEvent);
            }
        }
        PTXEvent.waitForEventArray((PTXEvent[]) events.toArray());
    }

    public int enqueueKernelLaunch(PTXModule module, TaskMetaData taskMeta, byte[] kernelParams, int[] gridDim, int[] blockDim) {
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
                streamWrapper, kernelParams), DESC_PARALLEL_KERNEL, module.kernelFunctionName.hashCode());
    }

    public int enqueueBarrier() {
        cuStreamSynchronize(streamWrapper);
        return registerEvent(DESC_SYNC_BARRIER, DEFAULT_TAG);
    }

    public int enqueueBarrier(int[] events) {
        waitForEvents(events);
        return registerEvent(DESC_SYNC_BARRIER, DEFAULT_TAG);
    }

    public int enqueueRead(long address, long length, byte[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayDtoH(address, length, array, hostOffset, streamWrapper), DESC_READ_BYTE, address);
    }

    public int enqueueRead(long address, long length, short[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayDtoH(address, length, array, hostOffset, streamWrapper), DESC_READ_SHORT, address);
    }

    public int enqueueRead(long address, long length, char[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayDtoH(address, length, array, hostOffset, streamWrapper), DESC_READ_BYTE, address);
    }

    public int enqueueRead(long address, long length, int[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayDtoH(address, length, array, hostOffset, streamWrapper), DESC_READ_INT, address);
    }

    public int enqueueRead(long address, long length, long[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayDtoH(address, length, array, hostOffset, streamWrapper), DESC_READ_LONG, address);
    }

    public int enqueueRead(long address, long length, float[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayDtoH(address, length, array, hostOffset, streamWrapper), DESC_READ_FLOAT, address);
    }

    public int enqueueRead(long address, long length, double[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayDtoH(address, length, array, hostOffset, streamWrapper), DESC_READ_DOUBLE, address);
    }

    public int enqueueAsyncRead(long address, long length, byte[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayDtoHAsync(address, length, array, hostOffset, streamWrapper), DESC_READ_BYTE, address);
    }

    public int enqueueAsyncRead(long address, long length, short[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayDtoHAsync(address, length, array, hostOffset, streamWrapper), DESC_READ_SHORT, address);
    }

    public int enqueueAsyncRead(long address, long length, char[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayDtoHAsync(address, length, array, hostOffset, streamWrapper), DESC_READ_BYTE, address);
    }

    public int enqueueAsyncRead(long address, long length, int[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayDtoHAsync(address, length, array, hostOffset, streamWrapper), DESC_READ_INT, address);
    }

    public int enqueueAsyncRead(long address, long length, long[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayDtoHAsync(address, length, array, hostOffset, streamWrapper), DESC_READ_LONG, address);
    }

    public int enqueueAsyncRead(long address, long length, float[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayDtoHAsync(address, length, array, hostOffset, streamWrapper), DESC_READ_FLOAT, address);
    }

    public int enqueueAsyncRead(long address, long length, double[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayDtoHAsync(address, length, array, hostOffset, streamWrapper), DESC_READ_DOUBLE, address);
    }

    public void enqueueWrite(long address, long length, byte[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        registerEvent(writeArrayHtoD(address, length, array, hostOffset, streamWrapper), DESC_WRITE_BYTE, address);
    }

    public void enqueueWrite(long address, long length, short[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        registerEvent(writeArrayHtoD(address, length, array, hostOffset, streamWrapper), DESC_WRITE_SHORT, address);
    }

    public void enqueueWrite(long address, long length, char[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        registerEvent(writeArrayHtoD(address, length, array, hostOffset, streamWrapper), DESC_WRITE_BYTE, address);
    }

    public void enqueueWrite(long address, long length, int[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        registerEvent(writeArrayHtoD(address, length, array, hostOffset, streamWrapper), DESC_WRITE_INT, address);
    }

    public void enqueueWrite(long address, long length, long[] array, int hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        registerEvent(writeArrayHtoD(address, length, array, hostOffset, streamWrapper), DESC_WRITE_LONG, address);
    }

    public void enqueueWrite(long address, long length, float[] array, int hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        registerEvent(writeArrayHtoD(address, length, array, hostOffset, streamWrapper), DESC_WRITE_FLOAT, address);
    }

    public void enqueueWrite(long address, long length, double[] array, int hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        registerEvent(writeArrayHtoD(address, length, array, hostOffset, streamWrapper), DESC_WRITE_DOUBLE, address);
    }

    public int enqueueAsyncWrite(long address, long length, byte[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayHtoDAsync(address, length, array, hostOffset, streamWrapper), DESC_WRITE_BYTE, address);
    }

    public int enqueueAsyncWrite(long address, long length, char[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayHtoDAsync(address, length, array, hostOffset, streamWrapper), DESC_WRITE_BYTE, address);
    }

    public int enqueueAsyncWrite(long address, long length, short[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayHtoDAsync(address, length, array, hostOffset, streamWrapper), DESC_WRITE_SHORT, address);
    }

    public int enqueueAsyncWrite(long address, long length, int[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayHtoDAsync(address, length, array, hostOffset, streamWrapper), DESC_WRITE_INT, address);

    }

    public int enqueueAsyncWrite(long address, long length, long[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayHtoDAsync(address, length, array, hostOffset, streamWrapper), DESC_WRITE_LONG, address);
    }

    public int enqueueAsyncWrite(long address, long length, float[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayHtoDAsync(address, length, array, hostOffset, streamWrapper), DESC_WRITE_FLOAT, address);
    }

    public int enqueueAsyncWrite(long address, long length, double[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return registerEvent(writeArrayHtoDAsync(address, length, array, hostOffset, streamWrapper), DESC_WRITE_DOUBLE, address);
    }

    public PTXEventsWrapper getEventsWrapper() {
        return eventsWrapper;
    }
}
