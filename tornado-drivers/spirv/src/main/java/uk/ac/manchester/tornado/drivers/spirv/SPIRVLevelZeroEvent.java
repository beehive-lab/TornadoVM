/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.tornado.api.enums.TornadoExecutionStatus;
import uk.ac.manchester.tornado.drivers.common.utils.EventDescriptor;
import uk.ac.manchester.tornado.drivers.spirv.timestamps.LevelZeroTransferTimeStamp;
import uk.ac.manchester.tornado.drivers.spirv.timestamps.TimeStamp;

public class SPIRVLevelZeroEvent extends SPIRVEvent {

    private int eventId;
    private EventDescriptor descriptorId;
    private LevelZeroTransferTimeStamp start;
    private LevelZeroTransferTimeStamp stop;
    private long startTime;
    private long endTime;

    public SPIRVLevelZeroEvent(EventDescriptor descriptorId, int eventId, TimeStamp start, TimeStamp end) {
        this.descriptorId = descriptorId;
        this.eventId = eventId;
        this.start = (LevelZeroTransferTimeStamp) start;
        this.stop = (LevelZeroTransferTimeStamp) end;
    }

    @Override
    public void waitForEvents(long executionPlanId) {
        start.readTimeStamp();
        stop.readTimeStamp();
        start.flush(executionPlanId);
        startTime = start.getTimeStamp();
        endTime = stop.getTimeStamp();
    }

    @Override
    public String getName() {
        return descriptorId.getNameDescription();
    }

    @Override
    public long getQueuedTime() {
        return 0;
    }

    @Override
    public long getSubmitTime() {
        return 0;
    }

    @Override
    public long getStartTime() {
        return startTime;
    }

    @Override
    public long getEndTime() {
        return endTime;
    }

    @Override
    public long getElapsedTime() {
        long value = (endTime - startTime) * start.getTimeResolution();
        return value;
    }

    @Override
    public long getDriverDispatchTime() {
        return 0;
    }

    @Override
    public double getElapsedTimeInSeconds() {
        return 0;
    }

    @Override
    public TornadoExecutionStatus getStatus() {
        return null;
    }

    @Override
    public double getTotalTimeInSeconds() {
        return 0;
    }

    @Override
    public void waitOn() {

    }

    @Override
    public void destroy() {
    }

}
