/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornadovm
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
package uk.ac.manchester.tornado.runtime.tasks;

import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getTornadoRuntime;

import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.runtime.EmptyEvent;
import uk.ac.manchester.tornado.runtime.common.DeviceObjectState;
import uk.ac.manchester.tornado.runtime.common.TornadoAcceleratorDevice;

public class LocalObjectState {

    private boolean streamIn;
    private boolean streamOut;

    private GlobalObjectState global;
    private DeviceObjectState device;

    public LocalObjectState(Object object) {
        global = getTornadoRuntime().resolveObject(object);
        device = null;
        streamIn = false;
        streamOut = false;
    }

    public boolean isStreamIn() {
        return streamIn;
    }

    void setStreamIn(boolean streamIn) {
        this.streamIn = streamIn;
    }

    public boolean isStreamOut() {
        return streamOut;
    }

    public void setStreamOut(boolean streamOut) {
        this.streamOut = streamOut;
    }

    public boolean isModified() {
        return global.getDeviceState(getOwner()).isModified();
    }

    public void setModified(boolean modified) {
        global.getDeviceState(getOwner()).setModified(modified);
    }

    public boolean isShared() {
        return global.isShared();
    }

    public boolean isValid() {
        return (device != null) && device.isValid();
    }

    public void setValid(boolean valid) {
        device.setValid(valid);
    }

    public boolean isExclusive() {
        return global.isExclusive();
    }

    public TornadoAcceleratorDevice getOwner() {
        return global.getOwner();
    }

    public GlobalObjectState getGlobalState() {
        return global;
    }

    public void setOwner(TornadoAcceleratorDevice owner) {
        global.setOwner(owner);
    }

    public Event sync(Object object) {
        if (isModified()) {
            TornadoAcceleratorDevice owner = getOwner();
            int eventId = owner.streamOutBlocking(object, 0, global.getDeviceState(owner), null);
            setModified(false);
            return owner.resolveEvent(eventId);
        }
        return new EmptyEvent();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(streamIn ? "SI" : "--");
        sb.append(streamOut ? "SO" : "--");
        sb.append(" ");

        if (device != null) {
            sb.append(device.toString()).append(" ");
        }
        sb.append(global.toString()).append(" ");
        return sb.toString();
    }
}
