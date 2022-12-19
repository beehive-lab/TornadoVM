/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.drivers.opencl;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.drivers.opencl.enums.OCLCommandQueueInfo.CL_QUEUE_CONTEXT;
import static uk.ac.manchester.tornado.drivers.opencl.enums.OCLCommandQueueInfo.CL_QUEUE_DEVICE;
import static uk.ac.manchester.tornado.runtime.common.Tornado.MARKER_USE_BARRIER;

import java.nio.ByteBuffer;

import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.drivers.opencl.exceptions.OCLException;
import uk.ac.manchester.tornado.runtime.EmptyEvent;
import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class OCLCommandQueue extends TornadoLogger {

    protected static final Event EMPTY_EVENT = new EmptyEvent();

    private final long commandQueue;
    private final ByteBuffer buffer;
    private final long properties;
    private final int openclVersion;

    public OCLCommandQueue(long id, long properties, int version) {
        this.commandQueue = id;
        this.properties = properties;
        this.buffer = ByteBuffer.allocate(128);
        this.buffer.order(OpenCL.BYTE_ORDER);
        this.openclVersion = version;
    }

    static native void clReleaseCommandQueue(long queueId) throws OCLException;

    static native void clGetCommandQueueInfo(long queueId, int info, byte[] buffer) throws OCLException;

    /**
     * Dispatch an OpenCL kernel via a JNI call.
     *
     * @param queueId
     *            OpenCL command queue object
     * @param kernelId
     *            OpenCL kernel ID object
     * @param dim
     *            Dimensions of the Kernel (1D, 2D or 3D)
     * @param global_work_offset
     *            Offset within global access
     * @param global_work_size
     *            Total number of threads to launch
     * @param local_work_size
     *            Local work group size
     * @param events
     *            List of events
     * @return Returns an event's ID
     * @throws OCLException
     *             OpenCL Exception
     */
    static native long clEnqueueNDRangeKernel(long queueId, long kernelId, int dim, long[] global_work_offset, long[] global_work_size, long[] local_work_size, long[] events) throws OCLException;

    static native long writeArrayToDevice(long queueId, byte[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    static native long writeArrayToDevice(long queueId, char[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    static native long writeArrayToDevice(long queueId, short[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    static native long writeArrayToDevice(long queueId, int[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    static native long writeArrayToDevice(long queueId, long[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    static native long writeArrayToDevice(long queueId, float[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    static native long writeArrayToDevice(long queueId, double[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    static native long readArrayFromDevice(long queueId, byte[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    static native long readArrayFromDevice(long queueId, char[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    static native long readArrayFromDevice(long queueId, short[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    static native long readArrayFromDevice(long queueId, int[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    static native long readArrayFromDevice(long queueId, long[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    static native long readArrayFromDevice(long queueId, float[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    static native long readArrayFromDevice(long queueId, double[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    static native void clEnqueueWaitForEvents(long queueId, long[] events) throws OCLException;

    /*
     * for OpenCL 1.2 implementations
     */
    static native long clEnqueueMarkerWithWaitList(long queueId, long[] events) throws OCLException;

    static native long clEnqueueBarrierWithWaitList(long queueId, long[] events) throws OCLException;

    static native void clFlush(long queueId) throws OCLException;

    static native void clFinish(long queueId) throws OCLException;

    public void flushEvents() {
        try {
            clFlush(commandQueue);
        } catch (OCLException e) {
            e.printStackTrace();
        }
    }

    public long getContextId() {
        long result = -1;
        buffer.clear();
        try {
            clGetCommandQueueInfo(commandQueue, CL_QUEUE_CONTEXT.getValue(), buffer.array());
            result = buffer.getLong();
        } catch (OCLException e) {
            error(e.getMessage());
        }

        return result;
    }

    public long getDeviceId() {
        long result = -1;
        buffer.clear();
        try {
            clGetCommandQueueInfo(commandQueue, CL_QUEUE_DEVICE.getValue(), buffer.array());
            result = buffer.getLong();
        } catch (OCLException e) {
            error(e.getMessage());
        }

        return result;
    }

    public long getProperties() {
        return properties;
    }

    /**
     * Enqueues a barrier into the command queue of the specified device
     *
     */
    public long enqueueBarrier() {
        return enqueueBarrier(null);
    }

    public long enqueueMarker() {
        return enqueueMarker(null);
    }

    public void cleanup() {
        try {
            clReleaseCommandQueue(commandQueue);
        } catch (OCLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return String.format("Queue: context=0x%x, device=0x%x", getContextId(), getDeviceId());
    }

    public long enqueueNDRangeKernel(OCLKernel kernel, int dim, long[] globalWorkOffset, long[] globalWorkSize, long[] localWorkSize, long[] waitEvents) {
        try {
            return clEnqueueNDRangeKernel(commandQueue, kernel.getOclKernelID(), dim, (openclVersion > 100) ? globalWorkOffset : null, globalWorkSize, localWorkSize, waitEvents);
        } catch (OCLException e) {
            error(e.getMessage());
        }

        if (Tornado.FORCE_BLOCKING_API_CALLS) {
            enqueueBarrier();
        }
        return -1;
    }

    public long enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, byte[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return writeArrayToDevice(commandQueue, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (OCLException e) {
            error(e.getMessage());
        }
        return -1;
    }

    public long enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, char[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return writeArrayToDevice(commandQueue, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (OCLException e) {
            error(e.getMessage());
        }
        return -1;
    }

    public long enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, int[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return writeArrayToDevice(commandQueue, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (OCLException e) {
            error(e.getMessage());
        }
        return -1;
    }

    public long enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, short[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return writeArrayToDevice(commandQueue, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (OCLException e) {
            error(e.getMessage());
        }
        return -1;
    }

    public long enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, long[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return writeArrayToDevice(commandQueue, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (OCLException e) {
            error(e.getMessage());
        }
        return -1;
    }

    public long enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, float[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return writeArrayToDevice(commandQueue, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (OCLException e) {
            error(e.getMessage());
        }
        return -1;
    }

    public long enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, double[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return writeArrayToDevice(commandQueue, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (OCLException e) {
            error(e.getMessage());
        }
        return -1;
    }

    public long enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, byte[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return readArrayFromDevice(commandQueue, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (OCLException e) {
            error(e.getMessage());
        }
        return -1;
    }

    public long enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, char[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return readArrayFromDevice(commandQueue, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (OCLException e) {
            error(e.getMessage());
        }
        return -1;
    }

    public long enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, int[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return readArrayFromDevice(commandQueue, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (OCLException e) {
            error(e.getMessage());
        }
        return -1;
    }

    public long enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, short[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "array is null");
        try {
            return readArrayFromDevice(commandQueue, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (OCLException e) {
            error(e.getMessage());
        }
        return -1;
    }

    public long enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, long[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "array is null");
        try {
            return readArrayFromDevice(commandQueue, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (OCLException e) {
            error(e.getMessage());
        }
        return -1;
    }

    public long enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, float[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "array is null");
        try {
            return readArrayFromDevice(commandQueue, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (OCLException e) {
            error(e.getMessage());
        }
        return -1;
    }

    public long enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, double[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "array is null");
        try {
            return readArrayFromDevice(commandQueue, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (OCLException e) {
            error(e.getMessage());
        }
        return -1;
    }

    public void finish() {
        try {
            clFinish(commandQueue);
        } catch (OCLException e) {
            error(e.getMessage());
        }
    }

    public void flush() {
        try {
            clFlush(commandQueue);
        } catch (OCLException e) {
            error(e.getMessage());
        }
    }

    public long enqueueBarrier(long[] waitEvents) {
        return (openclVersion < 120) ? enqueueBarrier_OCLv1_1(waitEvents) : enqueueBarrier_OCLv1_2(waitEvents);
    }

    private int enqueueBarrier_OCLv1_1(long[] events) {
        try {
            if (events != null) {
                clEnqueueWaitForEvents(commandQueue, events);
            }
        } catch (OCLException e) {
            fatal(e.getMessage());
        }
        return -1;
    }

    private long enqueueBarrier_OCLv1_2(long[] waitEvents) {
        try {
            return clEnqueueBarrierWithWaitList(commandQueue, waitEvents);
        } catch (OCLException e) {
            fatal(e.getMessage());
        }
        return -1;
    }

    public long enqueueMarker(long[] waitEvents) {
        if (MARKER_USE_BARRIER) {
            return enqueueBarrier(waitEvents);
        }
        return (openclVersion < 120) ? enqueueMarker11(waitEvents) : enqueueMarker12(waitEvents);
    }

    private int enqueueMarker11(long[] events) {
        return enqueueBarrier_OCLv1_1(events);
    }

    private long enqueueMarker12(long[] waitEvents) {
        try {
            return clEnqueueMarkerWithWaitList(commandQueue, waitEvents);
        } catch (OCLException e) {
            fatal(e.getMessage());
        }
        return -1;
    }

    public int getOpenclVersion() {
        return openclVersion;
    }
}
