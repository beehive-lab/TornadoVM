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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.Arrays;
import jdk.vm.ci.meta.JavaKind;

import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.drivers.common.CommandQueue;
import uk.ac.manchester.tornado.drivers.common.ffm.FFMSupport;
import uk.ac.manchester.tornado.drivers.opencl.exceptions.OCLException;
import uk.ac.manchester.tornado.drivers.opencl.ffm.OpenCLAPI;
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

    /** Reusable per-thread native buffers for info queries and for Java-array transfer staging. */
    private static final FFMSupport.Staging INFO_STAGING = new FFMSupport.Staging();

    private static final FFMSupport.Staging TRANSFER_STAGING = new FFMSupport.Staging();

    static void clReleaseCommandQueue(long queueId) throws OCLException {
        OpenCLAPI.clReleaseCommandQueue(queueId);
    }

    static void clGetCommandQueueInfo(long queueId, int info, byte[] buffer) throws OCLException {
        Arrays.fill(buffer, (byte) 0);
        MemorySegment value = INFO_STAGING.forBytes(buffer.length);
        if (OpenCLAPI.clGetCommandQueueInfo(queueId, info, buffer.length, value, MemorySegment.NULL) != OpenCLAPI.CL_SUCCESS) {
            return;
        }
        MemorySegment.copy(value, FFMSupport.C_CHAR, 0, buffer, 0, buffer.length);
    }

    /**
     * Copies a wait list into native memory. The array is laid out as {@code [count, e0, e1, ...]},
     * so the count comes from the head and the handles from what follows it.
     *
     * @return the native {@code cl_event} vector, or {@code MemorySegment.NULL} when the list is
     *     empty -- which is what OpenCL requires when the count is zero.
     */
    private static MemorySegment waitList(Arena arena, long[] events, int[] countOut) {
        int count = 0;
        if (events != null && events.length > 0) {
            count = (int) Math.min(events[0], events.length - 1L);
        }
        countOut[0] = count;
        if (count <= 0) {
            return MemorySegment.NULL;
        }
        MemorySegment handles = FFMSupport.allocateArray(arena, FFMSupport.C_POINTER, count);
        for (int i = 0; i < count; i++) {
            handles.set(FFMSupport.C_POINTER, i * FFMSupport.C_POINTER.byteSize(), MemorySegment.ofAddress(events[i + 1]));
        }
        return handles;
    }

    /** Copies a work-size array into a native {@code size_t} vector, or NULL when absent. */
    private static MemorySegment workSize(Arena arena, long[] sizes, int dimensions) {
        if (sizes == null) {
            return MemorySegment.NULL;
        }
        int count = Math.min(sizes.length, dimensions);
        MemorySegment segment = FFMSupport.allocateArray(arena, FFMSupport.C_LONG, Math.max(count, 1));
        MemorySegment.copy(sizes, 0, segment, FFMSupport.C_LONG, 0, count);
        return segment;
    }

    /**
     * Dispatch an OpenCL kernel.
     *
     * @param queueId
     *     OpenCL command queue object
     * @param kernelId
     *     OpenCL kernel ID object
     * @param dim
     *     Dimensions of the Kernel (1D, 2D or 3D)
     * @param globalWorkOffset
     *     Offset within global access
     * @param globalWorkSize
     *     Total number of threads to launch
     * @param localWorkSize
     *     Local work group size
     * @param events
     *     wait list, laid out as {@code [count, e0, e1, ...]}
     * @return Returns an event's ID
     * @throws OCLException
     *     OpenCL Exception
     */
    static long clEnqueueNDRangeKernel(long queueId, long kernelId, int dim, long[] globalWorkOffset, long[] globalWorkSize, long[] localWorkSize, long[] events) throws OCLException {
        try (Arena arena = Arena.ofConfined()) {
            int[] count = new int[1];
            MemorySegment waitList = waitList(arena, events, count);
            MemorySegment event = FFMSupport.allocatePointer(arena);
            OpenCLAPI.clEnqueueNDRangeKernel(queueId, kernelId, dim, workSize(arena, globalWorkOffset, dim), workSize(arena, globalWorkSize, dim), workSize(arena, localWorkSize, dim), count[0],
                    waitList, event);
            return event.get(FFMSupport.C_POINTER, 0).address();
        }
    }

    /**
     * Host-to-device copy whose host side is a Java array.
     *
     * <p>
     * A downcall cannot address the Java heap, so the array's bytes go through a reused per-thread
     * native buffer rather than being pinned in place the way the JNI critical region did. The copy
     * is blocking regardless of the caller's flag, exactly as the JNI version was: the staging
     * buffer is reused as soon as this returns.
     */
    private static long writeArray(long queueId, Object array, ValueLayout layout, int elementOffset, int elementCount, long offset, long bytes, long ptr, long[] events) throws OCLException {
        try (Arena arena = Arena.ofConfined()) {
            int[] count = new int[1];
            MemorySegment waitList = waitList(arena, events, count);
            MemorySegment staging = TRANSFER_STAGING.forBytes(bytes);
            MemorySegment.copy(array, elementOffset, staging, layout, 0, elementCount);
            MemorySegment event = FFMSupport.allocatePointer(arena);
            OpenCLAPI.clEnqueueWriteBuffer(queueId, ptr, OpenCLAPI.CL_TRUE, offset, bytes, staging.address(), count[0], waitList, event);
            return event.get(FFMSupport.C_POINTER, 0).address();
        }
    }

    /** Device-to-host counterpart of {@link #writeArray}; blocking, so the copy back is complete. */
    private static long readArray(long queueId, Object array, ValueLayout layout, int elementOffset, int elementCount, long offset, long bytes, long ptr, long[] events) throws OCLException {
        try (Arena arena = Arena.ofConfined()) {
            int[] count = new int[1];
            MemorySegment waitList = waitList(arena, events, count);
            MemorySegment staging = TRANSFER_STAGING.forBytes(bytes);
            MemorySegment event = FFMSupport.allocatePointer(arena);
            OpenCLAPI.clEnqueueReadBuffer(queueId, ptr, OpenCLAPI.CL_TRUE, offset, bytes, staging.address(), count[0], waitList, event);
            MemorySegment.copy(staging, layout, 0, array, elementOffset, elementCount);
            return event.get(FFMSupport.C_POINTER, 0).address();
        }
    }

    static long writeArrayToDevice(long queueId, byte[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException {
        return writeArray(queueId, buffer, FFMSupport.C_CHAR, (int) hostOffset, (int) bytes, offset, bytes, ptr, events);
    }

    static long writeArrayToDevice(long queueId, char[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException {
        return writeArray(queueId, buffer, ValueLayout.JAVA_CHAR, (int) (hostOffset / Character.BYTES), (int) (bytes / Character.BYTES), offset, bytes, ptr, events);
    }

    static long writeArrayToDevice(long queueId, short[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException {
        return writeArray(queueId, buffer, ValueLayout.JAVA_SHORT, (int) (hostOffset / Short.BYTES), (int) (bytes / Short.BYTES), offset, bytes, ptr, events);
    }

    static long writeArrayToDevice(long queueId, int[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException {
        return writeArray(queueId, buffer, ValueLayout.JAVA_INT, (int) (hostOffset / Integer.BYTES), (int) (bytes / Integer.BYTES), offset, bytes, ptr, events);
    }

    static long writeArrayToDevice(long queueId, long[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException {
        return writeArray(queueId, buffer, FFMSupport.C_LONG, (int) (hostOffset / Long.BYTES), (int) (bytes / Long.BYTES), offset, bytes, ptr, events);
    }

    static long writeArrayToDevice(long queueId, float[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException {
        return writeArray(queueId, buffer, FFMSupport.C_FLOAT, (int) (hostOffset / Float.BYTES), (int) (bytes / Float.BYTES), offset, bytes, ptr, events);
    }

    static long writeArrayToDevice(long queueId, double[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException {
        return writeArray(queueId, buffer, ValueLayout.JAVA_DOUBLE, (int) (hostOffset / Double.BYTES), (int) (bytes / Double.BYTES), offset, bytes, ptr, events);
    }

    static long writeArrayToDevice(long queueId, long hostPointer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException {
        try (Arena arena = Arena.ofConfined()) {
            int[] count = new int[1];
            MemorySegment waitList = waitList(arena, events, count);
            MemorySegment event = FFMSupport.allocatePointer(arena);
            OpenCLAPI.clEnqueueWriteBuffer(queueId, ptr, blocking ? OpenCLAPI.CL_TRUE : OpenCLAPI.CL_FALSE, offset, bytes, hostPointer + hostOffset, count[0], waitList, event);
            return event.get(FFMSupport.C_POINTER, 0).address();
        }
    }

    static long readArrayFromDevice(long queueId, byte[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException {
        return readArray(queueId, buffer, FFMSupport.C_CHAR, (int) hostOffset, (int) bytes, offset, bytes, ptr, events);
    }

    static long readArrayFromDevice(long queueId, char[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException {
        return readArray(queueId, buffer, ValueLayout.JAVA_CHAR, (int) (hostOffset / Character.BYTES), (int) (bytes / Character.BYTES), offset, bytes, ptr, events);
    }

    static long readArrayFromDevice(long queueId, short[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException {
        return readArray(queueId, buffer, ValueLayout.JAVA_SHORT, (int) (hostOffset / Short.BYTES), (int) (bytes / Short.BYTES), offset, bytes, ptr, events);
    }

    static long readArrayFromDevice(long queueId, int[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException {
        return readArray(queueId, buffer, ValueLayout.JAVA_INT, (int) (hostOffset / Integer.BYTES), (int) (bytes / Integer.BYTES), offset, bytes, ptr, events);
    }

    static long readArrayFromDevice(long queueId, long[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException {
        return readArray(queueId, buffer, FFMSupport.C_LONG, (int) (hostOffset / Long.BYTES), (int) (bytes / Long.BYTES), offset, bytes, ptr, events);
    }

    static long readArrayFromDevice(long queueId, float[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException {
        return readArray(queueId, buffer, FFMSupport.C_FLOAT, (int) (hostOffset / Float.BYTES), (int) (bytes / Float.BYTES), offset, bytes, ptr, events);
    }

    static long readArrayFromDevice(long queueId, double[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException {
        return readArray(queueId, buffer, ValueLayout.JAVA_DOUBLE, (int) (hostOffset / Double.BYTES), (int) (bytes / Double.BYTES), offset, bytes, ptr, events);
    }

    static long readArrayFromDeviceOffHeap(long queueId, long hostPointer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException {
        try (Arena arena = Arena.ofConfined()) {
            int[] count = new int[1];
            MemorySegment waitList = waitList(arena, events, count);
            MemorySegment event = FFMSupport.allocatePointer(arena);
            OpenCLAPI.clEnqueueReadBuffer(queueId, ptr, blocking ? OpenCLAPI.CL_TRUE : OpenCLAPI.CL_FALSE, offset, bytes, hostPointer + hostOffset, count[0], waitList, event);
            return event.get(FFMSupport.C_POINTER, 0).address();
        }
    }

    /**
     * Makes later commands on this queue wait for the listed events.
     *
     * <p>
     * {@code clEnqueueWaitForEvents} is an OpenCL 1.1 entry point that 1.2 dropped from the headers.
     * Where an ICD no longer exports it, a barrier on the same wait list is enqueued instead: it
     * orders the queue behind those events the same way.
     */
    static void clEnqueueWaitForEvents(long queueId, long[] events) throws OCLException {
        try (Arena arena = Arena.ofConfined()) {
            int[] count = new int[1];
            MemorySegment waitList = waitList(arena, events, count);
            if (OpenCLAPI.hasEnqueueWaitForEvents()) {
                OpenCLAPI.clEnqueueWaitForEvents(queueId, count[0], waitList);
            } else {
                OpenCLAPI.clEnqueueBarrierWithWaitList(queueId, count[0], waitList, MemorySegment.NULL);
            }
        }
    }

    /*
     * for OpenCL 1.2 implementations
     */
    static long clEnqueueMarkerWithWaitList(long queueId, long[] events) throws OCLException {
        try (Arena arena = Arena.ofConfined()) {
            int[] count = new int[1];
            MemorySegment waitList = waitList(arena, events, count);
            MemorySegment event = FFMSupport.allocatePointer(arena);
            OpenCLAPI.clEnqueueMarkerWithWaitList(queueId, count[0], waitList, event);
            return event.get(FFMSupport.C_POINTER, 0).address();
        }
    }

    static long clEnqueueBarrierWithWaitList(long queueId, long[] events) throws OCLException {
        try (Arena arena = Arena.ofConfined()) {
            int[] count = new int[1];
            MemorySegment waitList = waitList(arena, events, count);
            MemorySegment event = FFMSupport.allocatePointer(arena);
            OpenCLAPI.clEnqueueBarrierWithWaitList(queueId, count[0], waitList, event);
            return event.get(FFMSupport.C_POINTER, 0).address();
        }
    }

    static void clFlush(long queueId) throws OCLException {
        OpenCLAPI.clFlush(queueId);
    }

    static void clFinish(long queueId) throws OCLException {
        OpenCLAPI.clFinish(queueId);
    }

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
