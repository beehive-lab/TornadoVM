/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2021, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.cuda;

import static uk.ac.manchester.tornado.drivers.cuda.enums.CUDACommandExecutionStatus.CL_COMPLETE;
import static uk.ac.manchester.tornado.drivers.cuda.enums.CUDACommandExecutionStatus.createCUDACommandExecutionStatus;
import static uk.ac.manchester.tornado.drivers.cuda.enums.CUDAEventInfo.CL_EVENT_COMMAND_EXECUTION_STATUS;
import static uk.ac.manchester.tornado.drivers.cuda.enums.CUDAProfilingInfo.CL_PROFILING_COMMAND_END;
import static uk.ac.manchester.tornado.drivers.cuda.enums.CUDAProfilingInfo.CL_PROFILING_COMMAND_QUEUED;
import static uk.ac.manchester.tornado.drivers.cuda.enums.CUDAProfilingInfo.CL_PROFILING_COMMAND_START;
import static uk.ac.manchester.tornado.drivers.cuda.enums.CUDAProfilingInfo.CL_PROFILING_COMMAND_SUBMIT;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.ENABLE_OPENCL_PROFILING;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.Arrays;

import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.enums.TornadoExecutionStatus;
import uk.ac.manchester.tornado.drivers.cuda.enums.CUDACommandExecutionStatus;
import uk.ac.manchester.tornado.drivers.cuda.enums.CUDAProfilingInfo;
import uk.ac.manchester.tornado.drivers.cuda.exceptions.CUDAException;
import uk.ac.manchester.tornado.drivers.cuda.ffm.CUDADriverAPI;
import uk.ac.manchester.tornado.drivers.cuda.ffm.CUDAHandles;
import uk.ac.manchester.tornado.drivers.cuda.ffm.FFMSupport;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class CUDAEvent implements Event {

    private final long[] internalBuffer = new long[2];

    private CUDACommandQueue queue;
    private int localId;
    private long oclEventID;
    private final ByteBuffer buffer = ByteBuffer.allocate(8);
    private String name;
    private int status;
    private TornadoLogger logger;

    CUDAEvent() {
        buffer.order(CUDADriver.BYTE_ORDER);
        this.logger = new TornadoLogger(this.getClass());
    }

    public CUDAEvent(String eventNameDescription, final CUDACommandQueue queue, final int event, final long oclEventID) {
        this();
        this.queue = queue;
        this.localId = event;
        this.oclEventID = oclEventID;
        this.name = String.format("%s: 0x", eventNameDescription);
        this.status = -1;
    }

    void setEventId(int localId, long eventId) {
        this.localId = localId;
        this.oclEventID = eventId;
    }

    /** OpenCL {@code CL_EVENT_COMMAND_EXECUTION_STATUS}. */
    private static final int CL_EVENT_COMMAND_EXECUTION_STATUS = 0x11D3;
    /** OpenCL {@code CL_COMPLETE} / {@code CL_RUNNING} (see CUDACommandExecutionStatus). */
    private static final int CL_COMPLETE = 0;
    private static final int CL_RUNNING = 1;

    static void clGetEventInfo(long eventId, int param, byte[] buffer) throws CUDAException {
        Arrays.fill(buffer, (byte) 0);
        if (param != CL_EVENT_COMMAND_EXECUTION_STATUS || buffer.length < Integer.BYTES) {
            return;
        }
        CUDAHandles.Event event = CUDAHandles.resolve(eventId, CUDAHandles.Event.class);
        int status = CL_COMPLETE;
        if (event != null) {
            status = CUDADriverAPI.cuEventQuery(event.event()) == CUDADriverAPI.CUDA_SUCCESS ? CL_COMPLETE : CL_RUNNING;
        }
        ByteBuffer.wrap(buffer).order(CUDADriver.BYTE_ORDER).putInt(status);
    }

    /**
     * CUDA exposes no absolute event timestamps, unlike OpenCL's COMMAND_START/END: only
     * {@code cuEventElapsedTime} between two events. The absolute-timestamp queries are therefore
     * reported as zero, and elapsed time comes from {@link #cuEventElapsedTime} instead.
     */
    static void clGetEventProfilingInfo(long eventId, long param, byte[] buffer) throws CUDAException {
        Arrays.fill(buffer, (byte) 0);
    }

    /**
     * Waits for every event in the list.
     *
     * <p>
     * This is reached with two different array layouts: a plain list of handles from
     * {@code waitForEvents}, and a count-prefixed {@code {1, handle}} from {@code waitOnPassive}.
     * Rather than guess which one it has been given, it walks every element and waits on the ones
     * that resolve to a real event; a count word resolves to nothing and is skipped.
     */
    static void clWaitForEvents(long[] events) throws CUDAException {
        if (events == null) {
            return;
        }
        for (long handle : events) {
            CUDAHandles.Event event = CUDAHandles.resolve(handle, CUDAHandles.Event.class);
            if (event == null) {
                continue;
            }
            int result = CUDADriverAPI.cuEventSynchronize(event.event());
            if (result != CUDADriverAPI.CUDA_SUCCESS) {
                // A failed wait means the caller is about to read a buffer the device may still be
                // writing, so it has to see the failure rather than the buffer.
                throw new CUDAException(CUDADriverAPI.describe("cuEventSynchronize", result));
            }
        }
    }

    static void clReleaseEvent(long eventId) throws CUDAException {
        CUDAHandles.Event event = (CUDAHandles.Event) CUDAHandles.release(eventId);
        if (event == null) {
            return;
        }
        if (event.start() != 0) {
            CUDADriverAPI.cuEventDestroy(event.start());
        }
        if (event.event() != 0) {
            CUDADriverAPI.cuEventDestroy(event.event());
        }
    }

    /**
     * The device time, in nanoseconds, of the operation bracketed by the event's start and end
     * CUevents ({@code cuEventElapsedTime} reports milliseconds as a float). Zero when the event has
     * no start timestamp -- a marker, say -- or when the query fails.
     */
    static long cuEventElapsedTime(long eventId) {
        CUDAHandles.Event event = CUDAHandles.resolve(eventId, CUDAHandles.Event.class);
        if (event == null || event.start() == 0 || event.event() == 0) {
            return 0;
        }
        // Both events must have completed, or cuEventElapsedTime returns CUDA_ERROR_NOT_READY.
        if (CUDADriverAPI.cuEventSynchronize(event.event()) != CUDADriverAPI.CUDA_SUCCESS) {
            return 0;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment milliseconds = arena.allocate(Float.BYTES, Float.BYTES);
            if (CUDADriverAPI.cuEventElapsedTime(milliseconds, event.start(), event.event()) != CUDADriverAPI.CUDA_SUCCESS) {
                return 0;
            }
            return (long) (milliseconds.get(FFMSupport.C_FLOAT, 0) * 1.0e6f);
        }
    }

    private long readEventTime(CUDAProfilingInfo eventType) {
        if (!ENABLE_OPENCL_PROFILING) {
            return -1;
        }
        long time = 0;
        buffer.clear();
        try {
            clGetEventProfilingInfo(oclEventID, eventType.getValue(), buffer.array());
            time = buffer.getLong();
        } catch (CUDAException e) {
            logger.error(e.getMessage());
        }
        return time;
    }

    @Override
    public void waitForEvents(long executionPlanId) {
        try {
            clWaitForEvents(new long[] { oclEventID });
        } catch (CUDAException e) {
            e.printStackTrace();
        }
    }

    long getCLQueuedTime() {
        return readEventTime(CL_PROFILING_COMMAND_QUEUED);
    }

    long getCLSubmitTime() {
        return readEventTime(CL_PROFILING_COMMAND_SUBMIT);
    }

    long getCLStartTime() {
        return readEventTime(CL_PROFILING_COMMAND_START);
    }

    long getCLEndTime() {
        return readEventTime(CL_PROFILING_COMMAND_END);
    }

    private CUDACommandExecutionStatus getCLStatus() {
        if (status == 0) {
            return CL_COMPLETE;
        }

        buffer.clear();

        try {
            clGetEventInfo(oclEventID, CL_EVENT_COMMAND_EXECUTION_STATUS.getValue(), buffer.array());
            status = buffer.getInt();
        } catch (CUDAException e) {
            logger.error(e.getMessage());
        }

        return createCUDACommandExecutionStatus(status);
    }

    @Override
    public void waitOn() {
        switch (getCLStatus()) {
            case CL_COMPLETE:
                break;
            case CL_SUBMITTED:
                queue.flush();
            case CL_QUEUED:
            case CL_RUNNING:
                waitOnPassive();
                break;
            case CL_ERROR:
            case CL_UNKNOWN:
                logger.fatal("error on event: %s", name);
        }
    }

    private void waitOnPassive() {
        try {
            internalBuffer[0] = 1;
            internalBuffer[1] = oclEventID;
            clWaitForEvents(internalBuffer);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
        }
    }

    @Override
    public String toString() {
        return String.format("[CUDAEVENT] event: name=%s, status=%s", name, getStatus());
    }

    public long getOclEventID() {
        return oclEventID;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public TornadoExecutionStatus getStatus() {
        return getCLStatus().toTornadoExecutionStatus();
    }

    @Override
    public long getElapsedTime() {
        if (!ENABLE_OPENCL_PROFILING) {
            return 0;
        }
        // CUDA has no absolute event timestamps; use cuEventElapsedTime between the
        // operation's start and end events (returns nanoseconds).
        return cuEventElapsedTime(oclEventID);
    }

    @Override
    public long getDriverDispatchTime() {
        return (getCLStartTime() - getCLQueuedTime());
    }

    @Override
    public double getElapsedTimeInSeconds() {
        return RuntimeUtilities.elapsedTimeInSeconds(getCLStartTime(), getCLEndTime());
    }

    @Override
    public double getTotalTimeInSeconds() {
        return getElapsedTimeInSeconds();
    }

    @Override
    public long getQueuedTime() {
        return getCLQueuedTime();
    }

    @Override
    public long getSubmitTime() {
        return getCLSubmitTime();
    }

    @Override
    public long getStartTime() {
        return getCLStartTime();
    }

    @Override
    public long getEndTime() {
        return getCLEndTime();
    }

    void release() {
        try {
            clReleaseEvent(oclEventID);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
        }
    }
}
