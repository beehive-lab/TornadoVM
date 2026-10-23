/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, 2024, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.runtime.tasks;

import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.memory.ObjectState;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.common.XPUDeviceBufferState;

public class DataObjectState implements ObjectState {

    private ConcurrentHashMap<TornadoXPUDevice, XPUDeviceBufferState> deviceStates;

    /** Last returned pair, as one holder so readers always observe a consistent (device, state). */
    private volatile CachedDeviceState cachedDeviceState;

    private record CachedDeviceState(TornadoXPUDevice device, XPUDeviceBufferState state) {
    }

    public DataObjectState() {
        deviceStates = new ConcurrentHashMap<>();
    }

    @Override
    public XPUDeviceBufferState getDeviceBufferState(TornadoDevice device) {
        if (!(device instanceof TornadoXPUDevice)) {
            throw new TornadoRuntimeException("[ERROR] Device not compatible: " + device.getClass());
        }
        // Objects are almost always used on the same device twice in a row.
        final CachedDeviceState cached = cachedDeviceState;
        if (cached != null && cached.device() == device) {
            return cached.state();
        }
        final XPUDeviceBufferState deviceState = deviceStates.computeIfAbsent((TornadoXPUDevice) device, key -> new XPUDeviceBufferState());
        cachedDeviceState = new CachedDeviceState((TornadoXPUDevice) device, deviceState);
        return deviceState;
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
        cachedDeviceState = null;
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
