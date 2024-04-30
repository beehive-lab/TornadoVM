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

import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.memory.ObjectState;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.common.XPUDeviceBufferState;

public class DataObjectState implements ObjectState {

    private ConcurrentHashMap<TornadoXPUDevice, XPUDeviceBufferState> deviceStates;

    public DataObjectState() {
        deviceStates = new ConcurrentHashMap<>();
    }

    @Override
    public XPUDeviceBufferState getDeviceBufferState(TornadoDevice device) {
        if (!(device instanceof TornadoXPUDevice)) {
            throw new TornadoRuntimeException("[ERROR] Device not compatible: " + device.getClass());
        }
        if (!deviceStates.containsKey(device)) {
            deviceStates.put((TornadoXPUDevice) device, new XPUDeviceBufferState());
        }
        return deviceStates.get(device);
    }

    @Override
    public DataObjectState clone() {
        DataObjectState dataObjectState = new DataObjectState();
        dataObjectState.deviceStates = new ConcurrentHashMap<>();
        deviceStates.keySet().forEach(device -> {
            XPUDeviceBufferState xpuDeviceBufferState = deviceStates.get(device).createSnapshot();
            dataObjectState.deviceStates.put(device, xpuDeviceBufferState);
        });
        return dataObjectState;
    }

    @Override
    public void clear() {
        deviceStates.clear();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        for (TornadoXPUDevice device : deviceStates.keySet()) {
            sb.append(device.toString()).append(" ");
        }
        sb.append("]");

        return sb.toString();
    }
}
