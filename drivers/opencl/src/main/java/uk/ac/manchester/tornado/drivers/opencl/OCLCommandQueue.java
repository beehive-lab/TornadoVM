/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2019, APT Group, School of Computer Science,
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
import static uk.ac.manchester.tornado.runtime.common.Tornado.ENABLE_OOO_EXECUTION;
import static uk.ac.manchester.tornado.runtime.common.Tornado.EVENT_WINDOW;
import static uk.ac.manchester.tornado.runtime.common.Tornado.MARKER_USE_BARRIER;
import static uk.ac.manchester.tornado.runtime.common.Tornado.MAX_WAIT_EVENTS;
import static uk.ac.manchester.tornado.runtime.common.Tornado.getProperty;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.drivers.opencl.exceptions.OCLException;
import uk.ac.manchester.tornado.runtime.EmptyEvent;
import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class OCLCommandQueue extends TornadoLogger {

    protected static final Event EMPTY_EVENT = new EmptyEvent();
    private static final int NUM_EVENTS_BUFFERS = 1;

    // @formatter:off
    protected static final String[] EVENT_DESCRIPTIONS = { 
            "kernel - serial", 
            "kernel - parallel", 
            "writeToDevice - byte[]", 
            "writeToDevice - short[]", 
            "writeToDevice - int[]",
            "writeToDevice - long[]", 
            "writeToDevice - float[]", 
            "writeToDevice - double[]", 
            "readFromDevice - byte[]", 
            "readFromDevice - short[]", 
            "readFromDevice - int[]", 
            "readFromDevice - long[]",
            "readFromDevice - float[]", 
            "readFromDevice - double[]", 
            "sync - marker", 
            "sync - barrier" 
            };
    // @formatter:on

    protected static final int DESC_SERIAL_KERNEL = 0;
    protected static final int DESC_PARALLEL_KERNEL = 1;
    protected static final int DESC_WRITE_BYTE = 2;
    protected static final int DESC_WRITE_SHORT = 3;
    protected static final int DESC_WRITE_INT = 4;
    protected static final int DESC_WRITE_LONG = 5;
    protected static final int DESC_WRITE_FLOAT = 6;
    protected static final int DESC_WRITE_DOUBLE = 7;
    protected static final int DESC_READ_BYTE = 8;
    protected static final int DESC_READ_SHORT = 9;
    protected static final int DESC_READ_INT = 10;
    protected static final int DESC_READ_LONG = 11;
    protected static final int DESC_READ_FLOAT = 12;
    protected static final int DESC_READ_DOUBLE = 13;
    protected static final int DESC_SYNC_MARKER = 14;
    protected static final int DESC_SYNC_BARRIER = 15;

    private final long[] waitEventsBuffer;

    private final long id;
    private final ByteBuffer buffer;
    private final long properties;
    protected long[] events;
    protected final long[][] eventsBuffers;
    protected final int[] descriptors;
    protected final long[] tags;
    protected final BitSet retain;
    private int eventIndex;
    private final int openclVersion;

    private int eventsBufferIndex;
    private int eventMark;

    private final OCLEvent internalEvent;

    private static final boolean CIRCULAR_EVENTS = Boolean.parseBoolean(getProperty("tornado.opencl.circularevents", "True"));

    public OCLCommandQueue(long id, long properties, int version) {
        this.id = id;
        this.properties = properties;
        this.buffer = ByteBuffer.allocate(128);
        this.buffer.order(OpenCL.BYTE_ORDER);
        this.eventsBuffers = new long[NUM_EVENTS_BUFFERS][EVENT_WINDOW];
        this.retain = new BitSet(EVENT_WINDOW);
        this.retain.clear();
        this.eventsBufferIndex = 0;
        this.events = eventsBuffers[eventsBufferIndex];
        this.descriptors = new int[EVENT_WINDOW];
        this.tags = new long[EVENT_WINDOW];
        this.eventIndex = 0;
        this.eventMark = -1;
        this.openclVersion = version;
        this.waitEventsBuffer = new long[MAX_WAIT_EVENTS];
        this.internalEvent = new OCLEvent(this, 0, 0);
    }

    native static void clReleaseCommandQueue(long queueId) throws OCLException;

    native static void clGetCommandQueueInfo(long queueId, int info, byte[] buffer) throws OCLException;

    native static void clSetCommandQueueProperty(long queueId, long property, boolean value) throws OCLException;

    /**
     * Enqueues a kernel for execution on the specified command queue
     *
     * @param queueId
     * @param kernelId
     * @param dim
     * @param global_work_offset
     * @param global_work_size
     * @param local_work_size
     * @param events
     *
     * @return eventId of this command
     *
     * @throws OCLException
     */
    native static long clEnqueueNDRangeKernel(long queueId, long kernelId, int dim, long[] global_work_offset, long[] global_work_size, long[] local_work_size, long[] events) throws OCLException;

    native static long clEnqueueTask(long queueID, long kernelId, long[] events) throws OCLException;

    native static long clEnqueueReadBuffer(long queueId, long buffer, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    native static long clEnqueueWriteBuffer(long queueId, long buffer, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    native static long writeArrayToDevice(long queueId, byte[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    native static long writeArrayToDevice(long queueId, char[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    native static long writeArrayToDevice(long queueId, short[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    native static long writeArrayToDevice(long queueId, int[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    native static long writeArrayToDevice(long queueId, long[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    native static long writeArrayToDevice(long queueId, float[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    native static long writeArrayToDevice(long queueId, double[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    native static long readArrayFromDevice(long queueId, byte[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    native static long readArrayFromDevice(long queueId, char[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    native static long readArrayFromDevice(long queueId, short[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    native static long readArrayFromDevice(long queueId, int[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    native static long readArrayFromDevice(long queueId, long[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    native static long readArrayFromDevice(long queueId, float[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    native static long readArrayFromDevice(long queueId, double[] buffer, long hostOffset, boolean blocking, long offset, long bytes, long ptr, long[] events) throws OCLException;

    /*
     * for OpenCL 1.1 compatibility
     */
    native static long clEnqueueMarker(long queueId) throws OCLException;

    native static void clEnqueueBarrier(long queueId) throws OCLException;

    native static void clEnqueueWaitForEvents(long queueId, long[] events) throws OCLException;

    /*
     * for OpenCL 1.2 implementations
     */
    native static long clEnqueueMarkerWithWaitList(long queueId, long[] events) throws OCLException;

    native static long clEnqueueBarrierWithWaitList(long queueId, long[] events) throws OCLException;

    native static void clFlush(long queueId) throws OCLException;

    native static void clFinish(long queueId) throws OCLException;

    public Event resolveEvent(int event) {
        if (event == -1) {
            return EMPTY_EVENT;
        }
        return new OCLEvent(this, event, events[event]);
    }

    public void markEvent() {
        eventMark = eventIndex;
    }

    public void retainEvent(int localId) {
        retain.set(localId);
    }

    public void releaseEvent(int localId) {
        retain.clear(localId);
    }

    @Deprecated
    public void flushEvents() {
    }

    private void findNextEventSlot() {
        eventIndex = retain.nextClearBit(eventIndex + 1);

        if (CIRCULAR_EVENTS) {
            if (eventIndex >= events.length) {
                eventIndex = 0;
            }
        }

        guarantee(eventIndex != -1, "event window is full (retained=%d, capacity=%d)", retain.cardinality(), EVENT_WINDOW);
    }

    private int registerEvent(long eventId, int descriptorId, long tag) {
        if (retain.get(eventIndex)) {
            findNextEventSlot();
        }
        final int currentEvent = eventIndex;
        guarantee(!retain.get(currentEvent), "overwriting retained event");

        /*
         * OpenCL can generate an out of resources error which produces an invalid event
         * (-1) we need to avoid releasing any invalid events and trigger a fatal
         * exception when we miss an event
         */
        if (eventId == -1) {
            fatal("invalid event: event=0x%x, description=%s, tag=0x%x\n", eventId, EVENT_DESCRIPTIONS[descriptorId], tag);
            fatal("terminating application as system integrity has been compromised.");
            System.exit(-1);
        }

        if (events[currentEvent] > 0 && !retain.get(currentEvent)) {
            internalEvent.setEventId(currentEvent, events[currentEvent]);
            internalEvent.release();
        }
        events[currentEvent] = eventId;
        descriptors[currentEvent] = descriptorId;
        tags[currentEvent] = tag;

        findNextEventSlot();
        return currentEvent;
    }

    private boolean serialiseEvents(int[] dependencies) {
        if (dependencies == null || dependencies.length == 0 || !ENABLE_OOO_EXECUTION) {
            return false;
        }

        Arrays.fill(waitEventsBuffer, 0);

        int index = 0;
        for (int i = 0; i < dependencies.length; i++) {
            final int value = dependencies[i];
            if (value != -1) {
                index++;
                waitEventsBuffer[index] = events[value];
                debug("[%d] 0x%x - %s 0x%x\n", index, events[value], EVENT_DESCRIPTIONS[descriptors[value]], tags[value]);

            }
        }
        waitEventsBuffer[0] = index;

        return (index > 0);
    }

    public long getContextId() {
        long result = -1;
        buffer.clear();
        try {
            clGetCommandQueueInfo(id, CL_QUEUE_CONTEXT.getValue(), buffer.array());
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
            clGetCommandQueueInfo(id, CL_QUEUE_DEVICE.getValue(), buffer.array());
            result = buffer.getLong();
        } catch (OCLException e) {
            error(e.getMessage());
        }

        return result;
    }

    public void setProperties(long properties, boolean value) {
        try {
            clSetCommandQueueProperty(id, properties, value);
        } catch (OCLException e) {
            error(e.getMessage());
        }
    }

    /**
     * Enqueues a barrier into the command queue of the specified device
     *
     */
    public int enqueueBarrier() {
        return enqueueBarrier(null);
    }

    public int enqueueMarker() {
        return enqueueMarker(null);
    }

    public long getProperties() {
        return properties;
    }

    public void cleanup() {
        try {
            clReleaseCommandQueue(id);
        } catch (OCLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return String.format("Queue: context=0x%x, device=0x%x", getContextId(), getDeviceId());
    }

    public long getId() {
        return id;
    }

    public int enqueueTask(OCLKernel kernel, int[] waitEvents) {
        int event = -1;

        try {
            event = registerEvent(clEnqueueTask(id, kernel.getId(), serialiseEvents(waitEvents) ? waitEventsBuffer : null), DESC_SERIAL_KERNEL, kernel.getId());
        } catch (OCLException e) {
            error(e.getMessage());
        }

        if (Tornado.FORCE_BLOCKING_API_CALLS) {
            enqueueBarrier();
        }

        return event;
    }

    public int enqueueNDRangeKernel(OCLKernel kernel, int dim, long[] globalWorkOffset, long[] globalWorkSize, long[] localWorkSize, int[] waitEvents) {
        int event = -1;

        try {
            event = registerEvent(clEnqueueNDRangeKernel(id, kernel.getId(), dim, (openclVersion > 100) ? globalWorkOffset : null, globalWorkSize, localWorkSize,
                    serialiseEvents(waitEvents) ? waitEventsBuffer : null), DESC_PARALLEL_KERNEL, kernel.getId());
        } catch (OCLException e) {
            error(e.getMessage());
        }

        if (Tornado.FORCE_BLOCKING_API_CALLS) {
            enqueueBarrier();
        }

        return event;
    }

    public int enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, byte[] array, long hostOffset, int[] waitEvents) {
        guarantee(array != null, "null array");
        int event = -1;
        try {
            long eventID = writeArrayToDevice(id, array, hostOffset, blocking, offset, bytes, devicePtr, serialiseEvents(waitEvents) ? waitEventsBuffer : null);
            event = registerEvent(eventID, DESC_WRITE_BYTE, offset);
        } catch (OCLException e) {
            error(e.getMessage());
        }

        return event;
    }

    public int enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, char[] array, long hostOffset, int[] waitEvents) {
        guarantee(array != null, "null array");
        int event = -1;

        try {
            long eventID = writeArrayToDevice(id, array, hostOffset, blocking, offset, bytes, devicePtr, serialiseEvents(waitEvents) ? waitEventsBuffer : null);
            event = registerEvent(eventID, DESC_WRITE_BYTE, offset);
        } catch (OCLException e) {
            error(e.getMessage());
        }

        return event;
    }

    public int enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, int[] array, long hostOffset, int[] waitEvents) {
        guarantee(array != null, "null array");
        int event = -1;
        try {
            long eventID = writeArrayToDevice(id, array, hostOffset, blocking, offset, bytes, devicePtr, serialiseEvents(waitEvents) ? waitEventsBuffer : null);
            event = registerEvent(eventID, DESC_WRITE_INT, offset);

        } catch (OCLException e) {
            error(e.getMessage());
        }

        return event;
    }

    public int enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, short[] array, long hostOffset, int[] waitEvents) {
        guarantee(array != null, "null array");
        int event = -1;
        try {
            long eventID = writeArrayToDevice(id, array, hostOffset, blocking, offset, bytes, devicePtr, serialiseEvents(waitEvents) ? waitEventsBuffer : null);
            event = registerEvent(eventID, DESC_WRITE_SHORT, offset);
        } catch (OCLException e) {
            error(e.getMessage());
        }

        return event;
    }

    public int enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, long[] array, long hostOffset, int[] waitEvents) {
        guarantee(array != null, "null array");
        int event = -1;
        try {
            long eventID = writeArrayToDevice(id, array, hostOffset, blocking, offset, bytes, devicePtr, serialiseEvents(waitEvents) ? waitEventsBuffer : null);
            event = registerEvent(eventID, DESC_WRITE_LONG, offset);
        } catch (OCLException e) {
            error(e.getMessage());
        }

        return event;
    }

    public int enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, float[] array, long hostOffset, int[] waitEvents) {
        guarantee(array != null, "null array");
        int event = -1;
        try {
            long eventID = writeArrayToDevice(id, array, hostOffset, blocking, offset, bytes, devicePtr, serialiseEvents(waitEvents) ? waitEventsBuffer : null);
            event = registerEvent(eventID, DESC_WRITE_FLOAT, offset);
        } catch (OCLException e) {
            error(e.getMessage());
        }
        return event;
    }

    public int enqueueWrite(long devicePtr, boolean blocking, long offset, long bytes, double[] array, long hostOffset, int[] waitEvents) {
        guarantee(array != null, "null array");
        int event = -1;
        try {
            long eventID = writeArrayToDevice(id, array, hostOffset, blocking, offset, bytes, devicePtr, serialiseEvents(waitEvents) ? waitEventsBuffer : null);
            event = registerEvent(eventID, DESC_WRITE_DOUBLE, offset);
        } catch (OCLException e) {
            error(e.getMessage());
        }
        return event;
    }

    public int enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, byte[] array, long hostOffset, int[] waitEvents) {
        guarantee(array != null, "null array");
        int event = -1;
        try {
            long eventID = readArrayFromDevice(id, array, hostOffset, blocking, offset, bytes, devicePtr, serialiseEvents(waitEvents) ? waitEventsBuffer : null);
            event = registerEvent(eventID, DESC_READ_BYTE, offset);
        } catch (OCLException e) {
            error(e.getMessage());
        }

        return event;
    }

    public int enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, char[] array, long hostOffset, int[] waitEvents) {
        guarantee(array != null, "null array");
        int event = -1;

        try {
            long eventID = readArrayFromDevice(id, array, hostOffset, blocking, offset, bytes, devicePtr, serialiseEvents(waitEvents) ? waitEventsBuffer : null);
            event = registerEvent(eventID, DESC_READ_BYTE, offset);
        } catch (OCLException e) {
            error(e.getMessage());
        }

        return event;
    }

    public int enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, int[] array, long hostOffset, int[] waitEvents) {
        guarantee(array != null, "null array");
        int event = -1;
        try {
            long eventID = readArrayFromDevice(id, array, hostOffset, blocking, offset, bytes, devicePtr, serialiseEvents(waitEvents) ? waitEventsBuffer : null);
            event = registerEvent(eventID, DESC_READ_INT, offset);

        } catch (OCLException e) {
            error(e.getMessage());
        }

        return event;
    }

    public int enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, short[] array, long hostOffset, int[] waitEvents) {
        guarantee(array != null, "array is null");
        int event = -1;

        try {
            long eventID = readArrayFromDevice(id, array, hostOffset, blocking, offset, bytes, devicePtr, serialiseEvents(waitEvents) ? waitEventsBuffer : null);
            event = registerEvent(eventID, DESC_READ_SHORT, offset);
        } catch (OCLException e) {
            error(e.getMessage());
        }

        return event;
    }

    public int enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, long[] array, long hostOffset, int[] waitEvents) {
        guarantee(array != null, "array is null");
        int event = -1;

        try {
            long eventID = readArrayFromDevice(id, array, hostOffset, blocking, offset, bytes, devicePtr, serialiseEvents(waitEvents) ? waitEventsBuffer : null);
            event = registerEvent(eventID, DESC_READ_LONG, offset);
        } catch (OCLException e) {
            error(e.getMessage());
        }

        return event;
    }

    public int enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, float[] array, long hostOffset, int[] waitEvents) {
        guarantee(array != null, "array is null");
        int event = -1;
        try {
            long eventID = readArrayFromDevice(id, array, hostOffset, blocking, offset, bytes, devicePtr, serialiseEvents(waitEvents) ? waitEventsBuffer : null);
            event = registerEvent(eventID, DESC_READ_FLOAT, offset);
        } catch (OCLException e) {
            error(e.getMessage());
        }

        return event;
    }

    public int enqueueRead(long devicePtr, boolean blocking, long offset, long bytes, double[] array, long hostOffset, int[] waitEvents) {
        guarantee(array != null, "array is null");
        int event = -1;
        try {
            long eventID = readArrayFromDevice(id, array, hostOffset, blocking, offset, bytes, devicePtr, serialiseEvents(waitEvents) ? waitEventsBuffer : null);
            event = registerEvent(eventID, DESC_READ_DOUBLE, offset);
        } catch (OCLException e) {
            error(e.getMessage());
        }
        return event;
    }

    public void finish() {
        try {
            clFinish(id);
        } catch (OCLException e) {
            error(e.getMessage());
        }
    }

    public void flush() {
        try {
            clFlush(id);
        } catch (OCLException e) {
            error(e.getMessage());
        }
    }

    public void printEvents() {
    }

    public int enqueueBarrier(int[] events) {
        final long[] waitEvents = serialiseEvents(events) ? waitEventsBuffer : null;
        return (openclVersion < 120) ? enqueueBarrier_OCLv1_1(waitEvents) : enqueueBarrier_OCLv1_2(waitEvents);
    }

    private int enqueueBarrier_OCLv1_1(long[] events) {
        try {
            clEnqueueWaitForEvents(id, events);
        } catch (OCLException e) {
            fatal(e.getMessage());
        }
        return -1;
    }

    private int enqueueBarrier_OCLv1_2(long[] waitEvents) {
        int event = -1;
        try {
            event = registerEvent(clEnqueueBarrierWithWaitList(id, waitEvents), DESC_SYNC_BARRIER, 0x12);
        } catch (OCLException e) {
            fatal(e.getMessage());
        }
        return event;
    }

    public int enqueueMarker(int[] events) {
        final long[] waitEvents = serialiseEvents(events) ? waitEventsBuffer : null;
        if (MARKER_USE_BARRIER) {
            return enqueueBarrier(events);
        }
        return (openclVersion < 120) ? enqueueMarker11(waitEvents) : enqueueMarker12(waitEvents);
    }

    private int enqueueMarker11(long[] events) {
        return enqueueBarrier_OCLv1_1(events);
    }

    private int enqueueMarker12(long[] waitEvents) {
        int event = -1;
        try {
            event = registerEvent(clEnqueueMarkerWithWaitList(id, waitEvents), DESC_SYNC_MARKER, 0x12);
        } catch (OCLException e) {
            fatal(e.getMessage());
        }
        return event;
    }

    public List<OCLEvent> getEvents() {
        List<OCLEvent> result = new ArrayList<>();
        for (int i = 0; i < eventIndex; i++) {
            final long eventId = events[i];
            if (eventId <= 0) {
                continue;
            }
            result.add(new OCLEvent(this, i, eventId));
        }
        return result;
    }

    public void reset() {
        Arrays.fill(events, 0);
        eventIndex = 0;
    }
}
