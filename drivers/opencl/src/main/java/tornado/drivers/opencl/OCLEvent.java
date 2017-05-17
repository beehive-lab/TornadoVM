/*
 * Copyright 2012 James Clarkson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tornado.drivers.opencl;

import java.nio.ByteBuffer;
import tornado.api.Event;
import tornado.api.enums.TornadoExecutionStatus;
import tornado.common.RuntimeUtilities;
import tornado.common.TornadoLogger;
import tornado.drivers.opencl.enums.OCLCommandExecutionStatus;
import tornado.drivers.opencl.exceptions.OCLException;

import static tornado.common.Tornado.ENABLE_PROFILING;
import static tornado.drivers.opencl.OCLCommandQueue.EVENT_DESCRIPTIONS;
import static tornado.drivers.opencl.enums.OCLCommandExecutionStatus.*;
import static tornado.drivers.opencl.enums.OCLEventInfo.CL_EVENT_COMMAND_EXECUTION_STATUS;
import static tornado.drivers.opencl.enums.OCLProfilingInfo.*;

public class OCLEvent extends TornadoLogger implements Event {

    private static final long[] internalBuffer = new long[2];

    private final OCLCommandQueue queue;
    private int localId;
    private long id;
    private static final ByteBuffer buffer = ByteBuffer.allocate(8);
    private final String name;
    private int status;

    static {
        buffer.order(OpenCL.BYTE_ORDER);
    }

    public OCLEvent(final OCLCommandQueue queue, final int event, final long eventId) {
        this.queue = queue;
        this.localId = event;
        this.id = eventId;
        this.name = String.format("%s: 0x%x", EVENT_DESCRIPTIONS[queue.descriptors[localId]], queue.tags[localId]);
        this.status = -1;
    }

    @Override
    public void retain() {
        queue.retainEvent(localId);
    }

    protected void setEventId(int localId, long eventId) {
        this.localId = localId;
        this.id = eventId;
    }

    native static void clGetEventInfo(long eventId, int param, byte[] buffer) throws OCLException;

    native static void clGetEventProfilingInfo(long eventId, int param,
            byte[] buffer) throws OCLException;

    native static void clWaitForEvents(long[] events) throws OCLException;

    native static void clReleaseEvent(long eventId) throws OCLException;

    private long readEventTime(int param) {

        if (!ENABLE_PROFILING || getCLStatus() != CL_COMPLETE) {
            return -1;
        }

        long time = 0;
        buffer.clear();

        try {
            clGetEventProfilingInfo(id, param, buffer.array());
            time = buffer.getLong();
        } catch (OCLException e) {
            error(e.getMessage());
        }

        return time;
    }

    public long getCLQueuedTime() {
        return readEventTime(CL_PROFILING_COMMAND_QUEUED
                .getValue());
    }

    public long getCLSubmitTime() {
        return readEventTime(CL_PROFILING_COMMAND_SUBMIT
                .getValue());
    }

    public long getCLStartTime() {
        return readEventTime(CL_PROFILING_COMMAND_START
                .getValue());
    }

    public long getCLEndTime() {
        return readEventTime(CL_PROFILING_COMMAND_END
                .getValue());
    }

    @Override
    public double getExecutionTime() {
        return RuntimeUtilities.elapsedTimeInSeconds(getCLStartTime(),
                getCLEndTime());
    }

    @Override
    public double getTotalTime() {
        return RuntimeUtilities.elapsedTimeInSeconds(
                getCLSubmitTime(), getCLEndTime());
    }

    protected OCLCommandExecutionStatus getCLStatus() {
        if (status == 0) {
            return CL_COMPLETE;
        }

        buffer.clear();

        try {
            clGetEventInfo(id, CL_EVENT_COMMAND_EXECUTION_STATUS.getValue(), buffer.array());
            status = buffer.getInt();
        } catch (OCLException e) {
            error(e.getMessage());
        }

        return toEnum(status);
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
                fatal("error on event: %s", name);
        }
    }

    private void waitOnPassive() {
        try {
            internalBuffer[0] = 1;
            internalBuffer[1] = id;
            clWaitForEvents(internalBuffer);
        } catch (OCLException e) {
            error(e.getMessage());
        }
    }

    @Override
    public String toString() {
        return String.format("event: name=%s, status=%s", name, getStatus());
    }

    public long getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public double getQueuedTime() {
        return RuntimeUtilities.elapsedTimeInSeconds(getCLSubmitTime(),
                getCLStartTime());
    }

    @Override
    public TornadoExecutionStatus getStatus() {
        return getCLStatus().toTornadoExecutionStatus();
    }

    @Override
    public long getSubmitTime() {
        return getCLQueuedTime();
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
        queue.releaseEvent(localId);
        try {
            clReleaseEvent(id);
        } catch (OCLException e) {
            error(e.getMessage());
        }
    }
}
