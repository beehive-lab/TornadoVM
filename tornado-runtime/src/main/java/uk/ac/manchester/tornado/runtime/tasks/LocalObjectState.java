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
package uk.ac.manchester.tornado.runtime.tasks;

import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getTornadoRuntime;

import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.runtime.common.DeviceObjectState;

public class LocalObjectState {

    private boolean streamIn;
    private boolean forceStreamIn;
    private boolean streamOut;

    private final GlobalObjectState global;

    public LocalObjectState(Object object) {
        global = getTornadoRuntime().resolveObject(object);
        streamIn = false;
        streamOut = false;
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

    public GlobalObjectState getGlobalState() {
        return global;
    }

    public Event sync(Object object, TornadoDevice device) {
        DeviceObjectState objectState = global.getDeviceState(device);
        if (objectState.isLockedBuffer()) {
            int eventId = device.streamOutBlocking(object, 0, objectState, null);
            return device.resolveEvent(eventId);
        }
        return null;
    }

    @Override
    public String toString() {
        return (streamIn ? "SI" : "--") + (streamOut ? "SO" : "--") + " " + global.toString() + " ";
    }
}
