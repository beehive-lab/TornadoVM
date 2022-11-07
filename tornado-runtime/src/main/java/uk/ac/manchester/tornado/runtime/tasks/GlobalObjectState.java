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

import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.mm.TornadoGlobalObjectState;
import uk.ac.manchester.tornado.runtime.common.DeviceObjectState;
import uk.ac.manchester.tornado.runtime.common.TornadoAcceleratorDevice;

public class GlobalObjectState implements TornadoGlobalObjectState {

    private final ConcurrentHashMap<TornadoAcceleratorDevice, DeviceObjectState> deviceStates;

    public GlobalObjectState() {
        deviceStates = new ConcurrentHashMap<>();
    }

    public DeviceObjectState getDeviceState(TornadoDevice device) {
        if (!(device instanceof TornadoAcceleratorDevice)) {
            throw new TornadoRuntimeException("Device not compatible");
        }
        deviceStates.computeIfAbsent((TornadoAcceleratorDevice) device, k -> new DeviceObjectState());
        return deviceStates.get(device);
    }

    public void clear() {
        deviceStates.clear();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        for (TornadoAcceleratorDevice device : deviceStates.keySet()) {
            sb.append(device.toString()).append(" ");
        }
        sb.append("]");

        return sb.toString();
    }
}
