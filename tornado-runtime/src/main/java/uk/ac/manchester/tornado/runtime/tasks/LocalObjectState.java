/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, 2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.runtime.tasks;

import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.runtime.common.XPUDeviceBufferState;

/**
 * Data structure to keep the state for each parameter used by the TornadoVM runtime.
 * The Local Object states identifies if a parameter is used for stream-in, stream-out,
 * or it needs to be forced to stream-in (for example due to a device migration, or a
 * task-graph rewrite for reductions).
 */
public class LocalObjectState {

    /**
     * Identifies a variable (or parameter) is used for
     * stream-in (host -> device).
     */
    private boolean streamIn;

    /**
     * Identifies a variable (or parameter) must be copy-in again
     * from the host to the device.
     */
    private boolean forceStreamIn;

    /**
     * Identifies a variable (or parameter) is used for
     * stream-out (device -> host).
     */
    private boolean streamOut;

    /**
     * For each variable, we need to keep track of all devices in which there is a shadow
     * copy. This is achieved by using the {@link DataObjectState} object.
     */
    private DataObjectState globalObjectState;

    private Object object;

    public LocalObjectState(Object object) {
        this.object = object;
        globalObjectState = new DataObjectState();
        streamIn = false;
        streamOut = false;
    }

    public Object getObject() {
        return object;
    }

    public boolean isStreamIn() {
        return streamIn;
    }

    public void setStreamIn(boolean streamIn) {
        this.streamIn = streamIn;
    }

    public void setForceStreamIn(boolean streamIn) {
        this.forceStreamIn = streamIn;
    }

    public boolean isForcedStreamIn() {
        return this.forceStreamIn;
    }

    public boolean isStreamOut() {
        return streamOut;
    }

    public void setStreamOut(boolean streamOut) {
        this.streamOut = streamOut;
    }

    public DataObjectState getGlobalState() {
        return globalObjectState;
    }

    public Event sync(long executionPlanId, Object object, TornadoDevice device) {
        XPUDeviceBufferState objectState = globalObjectState.getDeviceState(device);
        if (objectState.isLockedBuffer()) {
            int eventId = device.streamOutBlocking(executionPlanId, object, 0, objectState, null);
            return device.resolveEvent(executionPlanId, eventId);
        }
        return null;
    }

    public LocalObjectState clone() {
        LocalObjectState newLocalObjectState = new LocalObjectState(this.object);
        newLocalObjectState.streamIn = this.streamIn;
        newLocalObjectState.streamOut = this.streamOut;
        newLocalObjectState.forceStreamIn = this.forceStreamIn;
        newLocalObjectState.globalObjectState = globalObjectState.clone();
        return newLocalObjectState;
    }

    @Override
    public String toString() {
        return STR."\{streamIn ? "SIN" : "--"}\{streamOut ? "SOUT" : "--"} \{globalObjectState} ";
    }
}
