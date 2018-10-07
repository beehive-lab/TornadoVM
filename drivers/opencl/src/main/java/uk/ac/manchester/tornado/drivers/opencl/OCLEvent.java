/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
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

import static uk.ac.manchester.tornado.drivers.opencl.OCLCommandQueue.EVENT_DESCRIPTIONS;
import static uk.ac.manchester.tornado.drivers.opencl.enums.OCLCommandExecutionStatus.CL_COMPLETE;
import static uk.ac.manchester.tornado.drivers.opencl.enums.OCLCommandExecutionStatus.createOCLCommandExecutionStatus;
import static uk.ac.manchester.tornado.drivers.opencl.enums.OCLEventInfo.CL_EVENT_COMMAND_EXECUTION_STATUS;
import static uk.ac.manchester.tornado.drivers.opencl.enums.OCLProfilingInfo.CL_PROFILING_COMMAND_END;
import static uk.ac.manchester.tornado.drivers.opencl.enums.OCLProfilingInfo.CL_PROFILING_COMMAND_QUEUED;
import static uk.ac.manchester.tornado.drivers.opencl.enums.OCLProfilingInfo.CL_PROFILING_COMMAND_START;
import static uk.ac.manchester.tornado.drivers.opencl.enums.OCLProfilingInfo.CL_PROFILING_COMMAND_SUBMIT;
import static uk.ac.manchester.tornado.runtime.common.Tornado.ENABLE_PROFILING;

import java.nio.ByteBuffer;

import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.enums.TornadoExecutionStatus;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLCommandExecutionStatus;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLProfilingInfo;
import uk.ac.manchester.tornado.drivers.opencl.exceptions.OCLException;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

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

    native static void clGetEventProfilingInfo(long eventId, long param, byte[] buffer) throws OCLException;

    native static void clWaitForEvents(long[] events) throws OCLException;

    native static void clReleaseEvent(long eventId) throws OCLException;

    private long readEventTime(OCLProfilingInfo eventType) {

        if (!ENABLE_PROFILING || getCLStatus() != CL_COMPLETE) {
            return -1;
        }

        long time = 0;
        buffer.clear();

        try {
            clWaitForEvents(new long[] { id });
            queue.finish();
            clGetEventProfilingInfo(id, eventType.getValue(), buffer.array());
            time = buffer.getLong();
        } catch (OCLException e) {
            error(e.getMessage());
        }

        return time;
    }

    public long getCLQueuedTime() {
        return readEventTime(CL_PROFILING_COMMAND_QUEUED);
    }

    public long getCLSubmitTime() {
        return readEventTime(CL_PROFILING_COMMAND_SUBMIT);
    }

    public long getCLStartTime() {
        return readEventTime(CL_PROFILING_COMMAND_START);
    }

    public long getCLEndTime() {
        return readEventTime(CL_PROFILING_COMMAND_END);
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

        return createOCLCommandExecutionStatus(status);
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
    public TornadoExecutionStatus getStatus() {
        return getCLStatus().toTornadoExecutionStatus();
    }

    @Override
    public long getExecutionTime() {
        return (getCLEndTime() - getCLStartTime());
    }

    @Override
    public double getExecutionTimeInSeconds() {
        return RuntimeUtilities.elapsedTimeInSeconds(getCLStartTime(), getCLEndTime());
    }

    @Override
    public double getExecutionTimeInMilliSeconds() {
        return RuntimeUtilities.elapsedTimeInMilliSeconds(getCLStartTime(), getCLEndTime());
    }

    @Override
    public long getTotalTime() {
        return getExecutionTime();
    }

    @Override
    public double getTotalTimeInSeconds() {
        return getExecutionTimeInSeconds();
    }

    @Override
    public double getTotalTimeInMilliSeconds() {
        return getExecutionTimeInMilliSeconds();
    }

    @Override
    public long getTotalQueuedTime() {
        return (getCLStartTime() - getCLQueuedTime());
    }

    @Override
    public double getTotalQueuedTimeInMilliSeconds() {
        return RuntimeUtilities.elapsedTimeInMilliSeconds(getCLQueuedTime(), getCLStartTime());
    }

    @Override
    public long getQueuedTime() {
        return getCLQueuedTime();
    }

    @Override
    public double getQueuedTimeInMilliSeconds() {
        return RuntimeUtilities.elapsedTimeInMilliSeconds(getCLQueuedTime());
    }

    @Override
    public long getSubmitTime() {
        return getCLSubmitTime();
    }

    @Override
    public double getSubmitTimeInMilliSeconds() {
        return RuntimeUtilities.elapsedTimeInMilliSeconds(getCLSubmitTime());
    }

    @Override
    public long getStartTime() {
        return getCLStartTime();
    }

    @Override
    public double getStartTimeInMilliSeconds() {
        return RuntimeUtilities.elapsedTimeInMilliSeconds(getCLStartTime());
    }

    @Override
    public long getEndTime() {
        return getCLEndTime();
    }

    @Override
    public double getEndTimeInMilliSeconds() {
        return RuntimeUtilities.elapsedTimeInMilliSeconds(getCLEndTime());
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
