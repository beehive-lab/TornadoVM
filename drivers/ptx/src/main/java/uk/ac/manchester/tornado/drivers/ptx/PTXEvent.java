/*
* This file is part of Tornado: A heterogeneous programming framework:
* https://github.com/beehive-lab/tornadovm
*
* Copyright (c) 2020, APT Group, Department of Computer Science,
* School of Engineering, The University of Manchester. All rights reserved.
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
*/
package uk.ac.manchester.tornado.drivers.ptx;

import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.enums.TornadoExecutionStatus;
import uk.ac.manchester.tornado.drivers.ptx.enums.PTXEventStatus;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class PTXEvent extends TornadoLogger implements Event {

    protected static final long DEFAULT_TAG = 0x12;

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
            "sync - barrier",
            "none"
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
    protected static final int EVENT_NONE = 16;

    /**
     * Wrapper containing two serialized CUevent structs. Between the two events, on
     * the same CUDA stream has been registered another API call described by the
     * value of {@link PTXEvent#description}. We measure the time difference between
     * the two events to get the duration of the API call.
     *
     * <p>
     * The first position (eventWrapper[0]) contains the beforeEvent The second
     * position eventWrapper[1] contains the afterEvent.
     */
    private final byte[][] eventWrapper;

    private boolean isCompleted;
    private final String description;
    private final long tag;
    private final String name;

    public PTXEvent(byte[][] bytes, int descriptorId, long tag) {
        eventWrapper = bytes;
        this.description = EVENT_DESCRIPTIONS[descriptorId];
        this.tag = tag;
        this.name = String.format("%s: 0x%x", description, tag);
        isCompleted = false;
    }

    private native static long cuEventDestroy(byte[] eventWrapper);

    private native static void tornadoCUDAEventsSynchronize(byte[][] wrappers);

    private native static long cuEventQuery(byte[] eventWrapper);

    /**
     * Returns the time in nanoseconds between two events. We convert from
     * milliseconds to nanoseconds because the tornado profiler uses this
     * measurement unit.
     */
    private native static long cuEventElapsedTime(byte[][] wrappers);

    public static void waitForEventArray(PTXEvent[] events) {
        byte[][] wrappers = new byte[events.length][];
        for (int i = 0; i < events.length; i++) {
            wrappers[i] = events[i].eventWrapper[1];
        }

        tornadoCUDAEventsSynchronize(wrappers);
    }

    @Override
    public void waitForEvents() {
        waitForEventArray(new PTXEvent[] { this });
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * The CUDA API does not provide any call to get such information. Therefore,
     * this method always returns -1.
     */
    @Override
    public long getQueuedTime() {
        return -1;
    }

    /**
     * The CUDA API does not provide any call to get such information. Therefore,
     * this method always returns -1.
     */
    @Override
    public long getSubmitTime() {
        return -1;
    }

    /**
     * The CUDA API does not provide any call to get such information. Therefore,
     * this method always returns -1.
     */
    @Override
    public long getStartTime() {
        return -1;
    }

    /**
     * The CUDA API does not provide any call to get such information. Therefore,
     * this method always returns -1.
     */
    @Override
    public long getEndTime() {
        return -1;
    }

    @Override
    public long getElapsedTime() {
        return cuEventElapsedTime(eventWrapper);
    }

    @Override
    public long getDriverDispatchTime() {
        return 0;
    }

    @Override
    public double getElapsedTimeInSeconds() {
        return RuntimeUtilities.elapsedTimeInSeconds(cuEventElapsedTime(eventWrapper));
    }

    @Override
    public TornadoExecutionStatus getStatus() {
        if (!isCompleted) {
            PTXEventStatus status = PTXEventStatus.getStatus(cuEventQuery(eventWrapper[1]));

            isCompleted = (status == PTXEventStatus.CUDA_SUCCESS);
            return status.toTornadoExecutionStatus();
        }
        return TornadoExecutionStatus.COMPLETE;
    }

    @Override
    public double getTotalTimeInSeconds() {
        return getElapsedTimeInSeconds();
    }

    @Override
    public void waitOn() {
        waitForEvents();
    }

    public void destroy() {
        cuEventDestroy(eventWrapper[0]);
        cuEventDestroy(eventWrapper[1]);
    }
}
