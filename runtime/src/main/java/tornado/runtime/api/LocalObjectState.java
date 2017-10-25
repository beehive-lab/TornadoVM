/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2017 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
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
 *
 * Authors: James Clarkson
 *
 */
package tornado.runtime.api;

import tornado.api.Event;
import tornado.common.DeviceObjectState;
import tornado.common.TornadoDevice;
import tornado.runtime.EmptyEvent;

import static tornado.runtime.TornadoRuntime.getTornadoRuntime;

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

    public void setStreamIn(boolean streamIn) {
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
        return (device == null) ? false : device.isValid();
    }

    public void setValid(boolean valid) {
        device.setValid(valid);
    }

    public boolean isExclusive() {
        return global.isExclusive();
    }

    public TornadoDevice getOwner() {
        return global.getOwner();
    }

    public GlobalObjectState getGlobalState() {
        return global;
    }

    public void setOwner(TornadoDevice owner) {
        global.setOwner(owner);
    }

    public Event sync(Object object) {
        if (isModified()) {
            TornadoDevice owner = getOwner();
            int eventId = owner.streamOut(object, global.getDeviceState(owner), null);
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
