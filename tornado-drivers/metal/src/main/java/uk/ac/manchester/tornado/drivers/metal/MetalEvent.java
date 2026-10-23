/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.metal;

import static uk.ac.manchester.tornado.drivers.metal.enums.MetalCommandExecutionStatus.METAL_COMPLETE;
import static uk.ac.manchester.tornado.drivers.metal.enums.MetalCommandExecutionStatus.createMetalCommandExecutionStatus;
import static uk.ac.manchester.tornado.drivers.metal.enums.MetalEventInfo.METAL_EVENT_COMMAND_EXECUTION_STATUS;
import static uk.ac.manchester.tornado.drivers.metal.enums.MetalProfilingInfo.METAL_PROFILING_COMMAND_END;
import static uk.ac.manchester.tornado.drivers.metal.enums.MetalProfilingInfo.METAL_PROFILING_COMMAND_QUEUED;
import static uk.ac.manchester.tornado.drivers.metal.enums.MetalProfilingInfo.METAL_PROFILING_COMMAND_START;
import static uk.ac.manchester.tornado.drivers.metal.enums.MetalProfilingInfo.METAL_PROFILING_COMMAND_SUBMIT;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.ENABLE_METAL_PROFILING;

import java.nio.ByteBuffer;

import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.enums.TornadoExecutionStatus;
import uk.ac.manchester.tornado.drivers.metal.enums.MetalCommandExecutionStatus;
import uk.ac.manchester.tornado.drivers.metal.enums.MetalProfilingInfo;
import uk.ac.manchester.tornado.drivers.metal.exceptions.MetalException;
import uk.ac.manchester.tornado.drivers.metal.ffm.MetalObjects;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class MetalEvent implements Event {

    private final long[] internalBuffer = new long[2];

    private MetalCommandQueue queue;
    private int localId;
    private long metalEventID;
    private final ByteBuffer buffer = ByteBuffer.allocate(8);
    private String name;
    private int status;
    private TornadoLogger logger;

    MetalEvent() {
        buffer.order(Metal.BYTE_ORDER);
        this.logger = new TornadoLogger(this.getClass());
    }

    public MetalEvent(String eventNameDescription, final MetalCommandQueue queue, final int event, final long metalEventID) {
        this();
        this.queue = queue;
        this.localId = event;
        this.metalEventID = metalEventID;
        this.name = String.format("%s: 0x", eventNameDescription);
        this.status = -1;
    }

    void setEventId(int localId, long eventId) {
        this.localId = localId;
        this.metalEventID = eventId;
    }

    static void metalGetEventInfo(long eventId, int param, byte[] buffer) throws MetalException {
        MetalObjects.eventInfo(eventId, param, buffer);
    }

    static void metalGetEventProfilingInfo(long eventId, long param, byte[] buffer) throws MetalException {
        MetalObjects.eventProfilingInfo(eventId, param, buffer);
    }

    static void metalWaitForEvents(long[] events) throws MetalException {
        MetalObjects.waitForEvents(events);
    }

    static void metalReleaseEvent(long eventId) throws MetalException {
        MetalObjects.releaseEvent(eventId);
    }

    private long readEventTime(MetalProfilingInfo eventType) {
        if (!ENABLE_METAL_PROFILING) {
            return -1;
        }
        long time = 0;
        buffer.clear();
        try {
            metalGetEventProfilingInfo(metalEventID, eventType.getValue(), buffer.array());
            time = buffer.getLong();
        } catch (MetalException e) {
            logger.error(e.getMessage());
        }
        return time;
    }

    @Override
    public void waitForEvents(long executionPlanId) {
        try {
            metalWaitForEvents(new long[] { metalEventID });
        } catch (MetalException e) {
            e.printStackTrace();
        }
    }

    long getCLQueuedTime() {
        return readEventTime(METAL_PROFILING_COMMAND_QUEUED);
    }

    long getCLSubmitTime() {
        return readEventTime(METAL_PROFILING_COMMAND_SUBMIT);
    }

    long getCLStartTime() {
        return readEventTime(METAL_PROFILING_COMMAND_START);
    }

    long getCLEndTime() {
        return readEventTime(METAL_PROFILING_COMMAND_END);
    }

    private MetalCommandExecutionStatus getCLStatus() {
        if (status == 0) {
            return METAL_COMPLETE;
        }

        buffer.clear();

        try {
            metalGetEventInfo(metalEventID, METAL_EVENT_COMMAND_EXECUTION_STATUS.getValue(), buffer.array());
            status = buffer.getInt();
        } catch (MetalException e) {
            logger.error(e.getMessage());
        }

        return createMetalCommandExecutionStatus(status);
    }

    @Override
    public void waitOn() {
        switch (getCLStatus()) {
            case METAL_COMPLETE:
                break;
            case METAL_SUBMITTED:
                queue.flush();
            case METAL_QUEUED:
            case METAL_RUNNING:
                waitOnPassive();
                break;
            case METAL_ERROR:
            case METAL_UNKNOWN:
                logger.fatal("error on event: %s", name);
        }
    }

    private void waitOnPassive() {
        try {
            internalBuffer[0] = 1;
            internalBuffer[1] = metalEventID;
            metalWaitForEvents(internalBuffer);
        } catch (MetalException e) {
            logger.error(e.getMessage());
        }
    }

    @Override
    public String toString() {
        return String.format("[MetalEVENT] event: name=%s, status=%s", name, getStatus());
    }

    public long getMetalEventID() {
        return metalEventID;
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
        return (getCLEndTime() - getCLStartTime());
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
            metalReleaseEvent(metalEventID);
        } catch (MetalException e) {
            logger.error(e.getMessage());
        }
    }
}
