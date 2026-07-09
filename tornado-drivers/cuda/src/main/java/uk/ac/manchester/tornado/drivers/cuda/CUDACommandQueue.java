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
package uk.ac.manchester.tornado.drivers.cuda;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.drivers.cuda.enums.CUDACommandQueueInfo.CL_QUEUE_CONTEXT;
import static uk.ac.manchester.tornado.drivers.cuda.enums.CUDACommandQueueInfo.CL_QUEUE_DEVICE;

import java.nio.ByteBuffer;

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.drivers.common.CommandQueue;
import uk.ac.manchester.tornado.drivers.cuda.exceptions.CUDAException;
import uk.ac.manchester.tornado.drivers.cuda.natives.NativeCommandQueue;
import uk.ac.manchester.tornado.runtime.EmptyEvent;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class CUDACommandQueue extends CommandQueue {

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

    public CUDACommandQueue(long commandQueuePtr, long properties, int version) {
        this.commandQueuePtr = commandQueuePtr;
        this.properties = properties;
        this.buffer = ByteBuffer.allocate(128);
        this.buffer.order(CUDADriver.BYTE_ORDER);
        this.openclVersion = version;
    }

    public long getCommandQueuePtr() {
        return commandQueuePtr;
    }

    /**
     * Labels this queue's CUDA stream with its role (DEFAULT / H2D / COMPUTE / D2H) so the Nsight
     * Systems timeline shows named stream rows instead of raw stream ids. Mirrors the PTX backend.
     */
    public void nameStream(String name) {
        nvtxNameStream(commandQueuePtr, name);
    }

    static native void clReleaseCommandQueue(long queueId) throws CUDAException;

    private static native void nvtxNameStream(long queueId, String name);

    /**
     * Pushes the profiler state to the native layer: when timing is off, per-operation
     * START timestamp events are skipped (halving events per op) and the remaining
     * dependency events are created with {@code CU_EVENT_DISABLE_TIMING} (cheaper).
     */
    static native void nativeEnableTiming(boolean enabled);

    static native void clGetCommandQueueInfo(long queueId, int info, byte[] buffer) throws CUDAException;

    /**
     * Dispatch an CUDADriver kernel via a JNI call.
     *
     * @param queueId
     *     CUDADriver command queue object
     * @param kernelId
     *     CUDADriver kernel ID object
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
     * @throws CUDAException
     *     CUDADriver Exception
     */
    static native long clEnqueueNDRangeKernel(long queueId, long kernelId, int dim, long[] global_work_offset, long[] global_work_size, long[] local_work_size, long[] events) throws CUDAException;

    static native long writeArrayToDevice(long queueId, byte[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws CUDAException;

    static native long writeArrayToDevice(long queueId, char[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws CUDAException;

    static native long writeArrayToDevice(long queueId, short[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws CUDAException;

    static native long writeArrayToDevice(long queueId, int[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws CUDAException;

    static native long writeArrayToDevice(long queueId, long[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws CUDAException;

    static native long writeArrayToDevice(long queueId, float[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws CUDAException;

    static native long writeArrayToDevice(long queueId, double[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws CUDAException;

    native static long writeArrayToDevice(long queueId, long hostPointer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws CUDAException;

    static native long readArrayFromDevice(long queueId, byte[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws CUDAException;

    static native long readArrayFromDevice(long queueId, char[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws CUDAException;

    static native long readArrayFromDevice(long queueId, short[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws CUDAException;

    static native long readArrayFromDevice(long queueId, int[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws CUDAException;

    static native long readArrayFromDevice(long queueId, long[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws CUDAException;

    static native long readArrayFromDevice(long queueId, float[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws CUDAException;

    static native long readArrayFromDevice(long queueId, double[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws CUDAException;

    static native long readArrayFromDeviceOffHeap(long queueId, long hostPointer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws CUDAException;

    static native void clEnqueueWaitForEvents(long queueId, long[] events) throws CUDAException;

    /*
     * for CUDADriver 1.2 implementations
     */
    static native long clEnqueueMarkerWithWaitList(long queueId, long[] events) throws CUDAException;

    static native long clEnqueueBarrierWithWaitList(long queueId, long[] events) throws CUDAException;

    static native void clFlush(long queueId) throws CUDAException;

    static native void clFinish(long queueId) throws CUDAException;

    /* ---- CUDA Graph (stream capture) native bindings ---- */

    private static native long cuStreamBeginCapture(long queueId, int mode);

    private static native long cuStreamEndCapture(long queueId);

    private static native boolean cuStreamIsCapturing(long queueId);

    private static native long cuGraphInstantiate(long graphHandle);

    private static native long cuGraphExecUpdate(long graphExecHandle, long graphHandle);

    private static native long cuGraphLaunch(long graphExecHandle, long queueId);

    private static native long cuGraphExecDestroy(long graphExecHandle);

    private static native long cuGraphDestroy(long graphHandle);

    /* ---- Native interop (external libraries, e.g. cuBLAS) ---- */

    private static native long getStreamPointer(long queueId);

    private static native long getContextPointer(long queueId);

    /**
     * Raw CUstream handle of this queue, for handing to external native
     * libraries (e.g., {@code cublasSetStream}) so their work is ordered with
     * TornadoVM kernels and transfers on the same stream.
     */
    public long getNativeStream() {
        return getStreamPointer(commandQueuePtr);
    }

    /**
     * Raw CUcontext handle of this queue.
     */
    public long getNativeContext() {
        return getContextPointer(commandQueuePtr);
    }

    /** CU_STREAM_CAPTURE_MODE_GLOBAL. */
    private static final int CU_STREAM_CAPTURE_MODE_GLOBAL = 0;

    /** True while this queue's stream is recording operations into a CUDA graph. */
    private boolean capturing = false;

    /**
     * Begins recording all subsequent operations submitted to this queue's
     * stream into a CUDA graph.
     */
    public void beginGraphCapture() {
        long result = cuStreamBeginCapture(commandQueuePtr, CU_STREAM_CAPTURE_MODE_GLOBAL);
        if (result != 0) {
            throw new TornadoBailoutRuntimeException("cuStreamBeginCapture failed. CUresult=" + result);
        }
        capturing = true;
    }

    /**
     * Ends capture and instantiates the recorded graph. The source CUgraph is
     * destroyed after instantiation; the returned handle is the CUgraphExec.
     *
     * @return opaque CUgraphExec handle
     */
    public long endGraphCaptureAndInstantiate() {
        capturing = false;
        long graphHandle = cuStreamEndCapture(commandQueuePtr);
        if (graphHandle == 0) {
            throw new TornadoBailoutRuntimeException("cuStreamEndCapture returned a null graph");
        }
        long graphExecHandle = cuGraphInstantiate(graphHandle);
        cuGraphDestroy(graphHandle);
        if (graphExecHandle == 0) {
            throw new TornadoBailoutRuntimeException("cuGraphInstantiate failed");
        }
        return graphExecHandle;
    }

    public boolean isCapturing() {
        return capturing;
    }

    /**
     * Launches a previously instantiated graph on this queue's stream and
     * blocks until completion so that captured device-to-host copies are
     * visible to the host once this call returns.
     */
    public void launchGraph(long graphExecHandle) {
        long result = cuGraphLaunch(graphExecHandle, commandQueuePtr);
        if (result != 0) {
            throw new TornadoBailoutRuntimeException("cuGraphLaunch failed. CUresult=" + result);
        }
        finish();
    }

    public void destroyGraph(long graphExecHandle) {
        cuGraphExecDestroy(graphExecHandle);
    }

    public void flushEvents() {
        try {
            clFlush(commandQueuePtr);
        } catch (CUDAException e) {
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
        } catch (CUDAException e) {
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
        } catch (CUDAException e) {
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
        } catch (CUDAException e) {
            e.printStackTrace();
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    @Override
    public String toString() {
        return String.format("Queue: context=0x%x, device=0x%x", getContextId(), getDeviceId());
    }

    public long enqueueNDRangeKernel(CUDAKernel kernel, int dim, long[] globalWorkOffset, long[] globalWorkSize, long[] localWorkSize, long[] waitEvents) {
        try {
            return clEnqueueNDRangeKernel(commandQueuePtr, kernel.getOclKernelID(), dim, (openclVersion > 100) ? globalWorkOffset : null, globalWorkSize, localWorkSize, waitEvents);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, byte[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return writeArrayToDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, char[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return writeArrayToDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, int[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return writeArrayToDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, short[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return writeArrayToDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, long[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return writeArrayToDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, float[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return writeArrayToDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, double[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return writeArrayToDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, long hostPointer, long hostOffset, long[] waitEvents) {
        guarantee(hostPointer != 0, "null segment");
        try {
            return writeArrayToDevice(commandQueuePtr, hostPointer, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, byte[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return readArrayFromDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, char[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return readArrayFromDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, int[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "null array");
        try {
            return readArrayFromDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, short[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "array is null");
        try {
            return readArrayFromDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, long[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "array is null");
        try {
            return readArrayFromDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, float[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "array is null");
        try {
            return readArrayFromDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, double[] array, long hostOffset, long[] waitEvents) {
        guarantee(array != null, "array is null");
        try {
            return readArrayFromDevice(commandQueuePtr, array, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, long hostPointer, long hostOffset, long[] waitEvents) {
        guarantee(hostPointer != 0, "segment is null");
        try {
            return readArrayFromDeviceOffHeap(commandQueuePtr, hostPointer, hostOffset, blocking, offset, bytes, devicePtr, waitEvents);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public void finish() {
        try {
            clFinish(commandQueuePtr);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public void flush() {
        try {
            clFlush(commandQueuePtr);
        } catch (CUDAException e) {
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueBarrier(long[] waitEvents) {
        return (openclVersion < 120) ? enqueueBarrier_CUDAv1_1(waitEvents) : enqueueBarrier_CUDAv1_2(waitEvents);
    }

    private int enqueueBarrier_CUDAv1_1(long[] events) {
        try {
            if (events != null) {
                clEnqueueWaitForEvents(commandQueuePtr, events);
            }
        } catch (CUDAException e) {
            logger.fatal(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
        return 0;
    }

    private long enqueueBarrier_CUDAv1_2(long[] waitEvents) {
        try {
            return clEnqueueBarrierWithWaitList(commandQueuePtr, waitEvents);
        } catch (CUDAException e) {
            logger.fatal(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public long enqueueMarker(long[] waitEvents) {
        return (openclVersion < 120) ? enqueueMarker11(waitEvents) : enqueueMarker12(waitEvents);
    }

    private int enqueueMarker11(long[] events) {
        return enqueueBarrier_CUDAv1_1(events);
    }

    private long enqueueMarker12(long[] waitEvents) {
        try {
            return clEnqueueMarkerWithWaitList(commandQueuePtr, waitEvents);
        } catch (CUDAException e) {
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
