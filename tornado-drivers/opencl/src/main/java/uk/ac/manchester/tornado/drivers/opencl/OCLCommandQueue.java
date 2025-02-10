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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.opencl;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.drivers.opencl.enums.OCLCommandQueueInfo.CL_QUEUE_CONTEXT;
import static uk.ac.manchester.tornado.drivers.opencl.enums.OCLCommandQueueInfo.CL_QUEUE_DEVICE;

import java.nio.ByteBuffer;

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.drivers.common.CommandQueue;
import uk.ac.manchester.tornado.drivers.opencl.exceptions.OCLException;
import uk.ac.manchester.tornado.drivers.opencl.natives.NativeCommandQueue;
import uk.ac.manchester.tornado.runtime.EmptyEvent;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class OCLCommandQueue extends CommandQueue {

    protected static final Event EMPTY_EVENT = new EmptyEvent();
    private TornadoLogger logger = new TornadoLogger(this.getClass());

    private final long commandQueuePtr;

    /**
     * Small buffer for querying properties regarding the command queue.
     * This is useful for debugging.
     */
    private final ByteBuffer buffer;
    private final long properties;
    private final int openclVersion;

    public OCLCommandQueue(long commandQueuePtr, long properties, int version) {
        this.commandQueuePtr = commandQueuePtr;
        this.properties = properties;
        this.buffer = ByteBuffer.allocate(128);
        this.buffer.order(OpenCL.BYTE_ORDER);
        this.openclVersion = version;
    }

    public long getCommandQueuePtr() {
        return commandQueuePtr;
    }

    static native void clReleaseCommandQueue(long queueId) throws OCLException;

    static native void clGetCommandQueueInfo(long queueId, int info, byte[] buffer) throws OCLException;

    /**
     * Dispatch an OpenCL kernel via a JNI call.
     *
     * @param queueId
     *     OpenCL command queue object
     * @param kernelId
     *     OpenCL kernel ID object
     * @param dim
     *     Dimensions of the Kernel (1D, 2D or 3D)
     * @param global_work_offset
     *     Offset within global access
     * @param global_work_size
     *     Total number of threads to launch
     * @param local_work_size
     *     Local work group size
     * @param events
     *     List of events
     * @return Returns an event's ID
     * @throws OCLException
     *     OpenCL Exception
     */
    static native long clEnqueueNDRangeKernel(long queueId, long kernelId, int dim, long[] global_work_offset, long[] global_work_size, long[] local_work_size, long[] events) throws OCLException;

    static native long writeArrayToDevice(long queueId, byte[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    static native long writeArrayToDevice(long queueId, char[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    static native long writeArrayToDevice(long queueId, short[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    static native long writeArrayToDevice(long queueId, int[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    static native long writeArrayToDevice(long queueId, long[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    static native long writeArrayToDevice(long queueId, float[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    static native long writeArrayToDevice(long queueId, double[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    native static long writeArrayToDevice(long queueId, long hostPointer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    static native long readArrayFromDevice(long queueId, byte[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    static native long readArrayFromDevice(long queueId, char[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    static native long readArrayFromDevice(long queueId, short[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    static native long readArrayFromDevice(long queueId, int[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    static native long readArrayFromDevice(long queueId, long[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    static native long readArrayFromDevice(long queueId, float[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    static native long readArrayFromDevice(long queueId, double[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    static native long readArrayFromDeviceOffHeap(long queueId, long hostPointer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

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
            clFlush(commandQueuePtr);
        } catch (OCLException e) {
            e.printStackTrace();
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long getContextId() {
        long result;
        buffer.clear();
        try {
            clGetCommandQueueInfo(commandQueuePtr, CL_QUEUE_CONTEXT.getValue(), buffer.array());
            result = buffer.getLong();
        } catch (OCLException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
        return result;
    }

    public long getDeviceId() {
        long result;
        buffer.clear();
        try {
            clGetCommandQueueInfo(commandQueuePtr, CL_QUEUE_DEVICE.getValue(), buffer.array());
            result = buffer.getLong();
        } catch (OCLException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
        return result;
    }

    public long getProperties() {
        return properties;
    }

    /**
     * Enqueues a barrier into the command queue of the specified device
     */
    public long enqueueBarrier() {
        return enqueueBarrier(null);
    }

    public long enqueueMarker() {
        return enqueueMarker(null);
    }

    public void cleanup() {
        try {
            clReleaseCommandQueue(commandQueuePtr);
        } catch (OCLException e) {
            e.printStackTrace();
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    @Override
    public String toString() {
        return String.format("Queue: context=0x%x, device=0x%x", getContextId(), getDeviceId());
    }

    public long enqueueNDRangeKernel(OCLKernel kernel, int dim, long[] globalWorkOffset, long[] globalWorkSize, long[] localWorkSize, long[] waitEvents) {
        try {
            return clEnqueueNDRangeKernel(commandQueuePtr, kernel.getOclKernelID(), dim, (openclVersion > 100) ? globalWorkOffset : null, globalWorkSize, localWorkSize, waitEvents);
        } catch (OCLException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, byte[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return writeArrayToDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (OCLException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, char[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return writeArrayToDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (OCLException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, int[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return writeArrayToDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (OCLException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, short[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return writeArrayToDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (OCLException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, long[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return writeArrayToDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (OCLException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, float[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return writeArrayToDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (OCLException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, double[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return writeArrayToDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (OCLException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, long hostPointer, long hostOffset, long[] waitEvents) {
        guarantee(hostPointer != 0, "null segment");
        try {
            return writeArrayToDevice(commandQueuePtr, hostPointer, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (OCLException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, byte[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return readArrayFromDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (OCLException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, char[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return readArrayFromDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (OCLException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, int[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return readArrayFromDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (OCLException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, short[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "array is null");
        try {
            return readArrayFromDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (OCLException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, long[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "array is null");
        try {
            return readArrayFromDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (OCLException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, float[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "array is null");
        try {
            return readArrayFromDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (OCLException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, double[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "array is null");
        try {
            return readArrayFromDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (OCLException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, long hostPointer, long hostOffset, long[] waitEvents) {
        guarantee(hostPointer != 0, "segment is null");
        try {
            return readArrayFromDeviceOffHeap(commandQueuePtr, hostPointer, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (OCLException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public void finish() {
        try {
            clFinish(commandQueuePtr);
        } catch (OCLException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public void flush() {
        try {
            clFlush(commandQueuePtr);
        } catch (OCLException e) {
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueBarrier(long[] waitEvents) {
        return (openclVersion < 120) ? enqueueBarrier_OCLv1_1(waitEvents) : enqueueBarrier_OCLv1_2(waitEvents);
    }

    private int enqueueBarrier_OCLv1_1(long[] events) {
        try {
            if (events != null) {
                clEnqueueWaitForEvents(commandQueuePtr, events);
            }
        } catch (OCLException e) {
            logger.fatal(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
        return 0;
    }

    private long enqueueBarrier_OCLv1_2(long[] waitEvents) {
        try {
            return clEnqueueBarrierWithWaitList(commandQueuePtr, waitEvents);
        } catch (OCLException e) {
            logger.fatal(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueMarker(long[] waitEvents) {
        return (openclVersion < 120) ? enqueueMarker11(waitEvents) : enqueueMarker12(waitEvents);
    }

    private int enqueueMarker11(long[] events) {
        return enqueueBarrier_OCLv1_1(events);
    }

    private long enqueueMarker12(long[] waitEvents) {
        try {
            return clEnqueueMarkerWithWaitList(commandQueuePtr, waitEvents);
        } catch (OCLException e) {
            logger.fatal(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public int getOpenclVersion() {
        return openclVersion;
    }

    public long mapOnDeviceMemoryRegion(long commandQueuePtr, long destDevicePtr, long srcDevicePtr, long offset, int sizeOfType, long sizeSource, long sizeDest) {
        long ptr;
        if (offset == 0) {
            ptr = NativeCommandQueue.mapOnDeviceMemoryRegion(destDevicePtr, srcDevicePtr);
        } else {
            // FIXME: PoC to check custom ranges from the source array
            final long headerSize = TornadoNativeArray.ARRAY_HEADER / JavaKind.Int.getByteCount(); // Header always contains integer values
            ptr = NativeCommandQueue.mapOnDeviceMemoryNDRegion(commandQueuePtr, destDevicePtr, srcDevicePtr, offset, sizeOfType, headerSize, sizeSource, sizeDest);
        }
        return ptr;
    }
}
