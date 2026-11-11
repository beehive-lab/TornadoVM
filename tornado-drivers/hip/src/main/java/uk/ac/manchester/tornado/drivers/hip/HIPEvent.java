/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2021, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.drivers.hip;

import static uk.ac.manchester.tornado.drivers.hip.enums.HIPCommandExecutionStatus.CL_COMPLETE;
import static uk.ac.manchester.tornado.drivers.hip.enums.HIPCommandExecutionStatus.createHIPCommandExecutionStatus;
import static uk.ac.manchester.tornado.drivers.hip.enums.HIPEventInfo.CL_EVENT_COMMAND_EXECUTION_STATUS;
import static uk.ac.manchester.tornado.drivers.hip.enums.HIPProfilingInfo.CL_PROFILING_COMMAND_END;
import static uk.ac.manchester.tornado.drivers.hip.enums.HIPProfilingInfo.CL_PROFILING_COMMAND_QUEUED;
import static uk.ac.manchester.tornado.drivers.hip.enums.HIPProfilingInfo.CL_PROFILING_COMMAND_START;
import static uk.ac.manchester.tornado.drivers.hip.enums.HIPProfilingInfo.CL_PROFILING_COMMAND_SUBMIT;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.ENABLE_OPENCL_PROFILING;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.Arrays;

import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.enums.TornadoExecutionStatus;
import uk.ac.manchester.tornado.drivers.hip.enums.HIPCommandExecutionStatus;
import uk.ac.manchester.tornado.drivers.hip.enums.HIPProfilingInfo;
import uk.ac.manchester.tornado.drivers.hip.exceptions.HIPException;
import uk.ac.manchester.tornado.drivers.hip.ffm.HIPDriverAPI;
import uk.ac.manchester.tornado.drivers.hip.ffm.HIPHandles;
import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class HIPEvent implements Event {

    private final long[] internalBuffer = new long[2];

    private HIPCommandQueue queue;
    private int localId;
    private long oclEventID;
    private final ByteBuffer buffer = ByteBuffer.allocate(8);
    private String name;
    private int status;
    private TornadoLogger logger;

    HIPEvent() {
        buffer.order(HIPDriver.BYTE_ORDER);
        this.logger = new TornadoLogger(this.getClass());
    }

    public HIPEvent(String eventNameDescription, final HIPCommandQueue queue, final int event, final long oclEventID) {
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

    static void clGetEventInfo(long eventId, int param, byte[] buffer) throws HIPException {
        Arrays.fill(buffer, (byte) 0);
        if (param != CL_EVENT_COMMAND_EXECUTION_STATUS.getValue() || buffer.length < Integer.BYTES) {
            return;
        }
        HIPHandles.Event event = HIPHandles.resolve(eventId, HIPHandles.Event.class);
        HIPCommandExecutionStatus executionStatus = CL_COMPLETE;
        if (event != null) {
            executionStatus = HIPDriverAPI.hipEventQuery(event.event()) == HIPDriverAPI.HIP_SUCCESS ? CL_COMPLETE : HIPCommandExecutionStatus.CL_RUNNING;
        }
        ByteBuffer.wrap(buffer).order(HIPDriver.BYTE_ORDER).putInt(executionStatus.getValue());
    }

    /**
     * HIP exposes no absolute event timestamps, unlike OpenCL's COMMAND_START/END: only
     * {@code hipEventElapsedTime} between two events. The absolute-timestamp queries are therefore
     * reported as zero, and elapsed time comes from {@link #hipEventElapsedTime} instead.
     */
    static void clGetEventProfilingInfo(long eventId, long param, byte[] buffer) throws HIPException {
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
    static void clWaitForEvents(long[] events) throws HIPException {
        if (events == null) {
            return;
        }
        for (long handle : events) {
            HIPHandles.Event event = HIPHandles.resolve(handle, HIPHandles.Event.class);
            if (event == null) {
                continue;
            }
            int result = HIPDriverAPI.hipEventSynchronize(event.event());
            if (result != HIPDriverAPI.HIP_SUCCESS) {
                // A failed wait means the caller is about to read a buffer the device may still be
                // writing, so it has to see the failure rather than the buffer.
                throw new HIPException(HIPDriverAPI.describe("hipEventSynchronize", result));
            }
        }
    }

    static void clReleaseEvent(long eventId) throws HIPException {
        HIPHandles.Event event = (HIPHandles.Event) HIPHandles.release(eventId);
        if (event == null) {
            return;
        }
        if (event.start() != 0) {
            HIPDriverAPI.hipEventDestroy(event.start());
        }
        if (event.event() != 0) {
            HIPDriverAPI.hipEventDestroy(event.event());
        }
    }

    /**
     * The device time, in nanoseconds, of the operation bracketed by the event's start and end
     * hipEvent_ts ({@code hipEventElapsedTime} reports milliseconds as a float). Zero when the event has
     * no start timestamp -- a marker, say -- or when the query fails.
     */
    static long hipEventElapsedTime(long eventId) {
        HIPHandles.Event event = HIPHandles.resolve(eventId, HIPHandles.Event.class);
        if (event == null || event.start() == 0 || event.event() == 0) {
            return 0;
        }
        // Both events must have completed, or hipEventElapsedTime returns HIP_ERROR_NOT_READY.
        if (HIPDriverAPI.hipEventSynchronize(event.event()) != HIPDriverAPI.HIP_SUCCESS) {
            return 0;
        }
        MemorySegment milliseconds = FFMSupport.scratchFloat();
        if (HIPDriverAPI.hipEventElapsedTime(milliseconds, event.start(), event.event()) != HIPDriverAPI.HIP_SUCCESS) {
            return 0;
        }
        return (long) (milliseconds.get(FFMSupport.C_FLOAT, 0) * 1.0e6f);
    }

    private long readEventTime(HIPProfilingInfo eventType) {
        if (!ENABLE_OPENCL_PROFILING) {
            return -1;
        }
        long time = 0;
        buffer.clear();
        try {
            clGetEventProfilingInfo(oclEventID, eventType.getValue(), buffer.array());
            time = buffer.getLong();
        } catch (HIPException e) {
            logger.error(e.getMessage());
        }
        return time;
    }

    @Override
    public void waitForEvents(long executionPlanId) {
        try {
            clWaitForEvents(new long[] { oclEventID });
        } catch (HIPException e) {
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

    private HIPCommandExecutionStatus getCLStatus() {
        if (status == 0) {
            return CL_COMPLETE;
        }

        buffer.clear();

        try {
            clGetEventInfo(oclEventID, CL_EVENT_COMMAND_EXECUTION_STATUS.getValue(), buffer.array());
            status = buffer.getInt();
        } catch (HIPException e) {
            logger.error(e.getMessage());
        }

        return createHIPCommandExecutionStatus(status);
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
        } catch (HIPException e) {
            logger.error(e.getMessage());
        }
    }

    @Override
    public String toString() {
        return String.format("[HIPEVENT] event: name=%s, status=%s", name, getStatus());
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
        // HIP has no absolute event timestamps; use hipEventElapsedTime between the
        // operation's start and end events (returns nanoseconds).
        return hipEventElapsedTime(oclEventID);
    }

    /**
     * HIP events carry no queued/start timestamps, so the dispatch time is the host time the driver
     * call that issued the operation took, measured when the operation was enqueued.
     */
    @Override
    public long getDriverDispatchTime() {
        if (!ENABLE_OPENCL_PROFILING) {
            return 0;
        }
        HIPHandles.Event event = HIPHandles.resolve(oclEventID, HIPHandles.Event.class);
        return event == null ? 0 : event.dispatchTime();
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
        } catch (HIPException e) {
            logger.error(e.getMessage());
        }
    }
}
