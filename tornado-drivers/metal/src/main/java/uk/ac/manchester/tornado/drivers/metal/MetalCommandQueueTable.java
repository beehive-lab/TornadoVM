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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.metal.exceptions.MetalException;

public class MetalCommandQueueTable {

    private final Map<MetalTargetDevice, ThreadCommandQueueTable> deviceCommandMap;

    public MetalCommandQueueTable() {
        deviceCommandMap = new ConcurrentHashMap<>();
    }

    public MetalCommandQueue get(MetalTargetDevice device, MetalContext context) {
        if (!deviceCommandMap.containsKey(device)) {
            ThreadCommandQueueTable table = new ThreadCommandQueueTable();
            table.get(Thread.currentThread().threadId(), device, context);
            deviceCommandMap.put(device, table);
        }
        return deviceCommandMap.get(device).get(Thread.currentThread().threadId(), device, context);
    }

    public void cleanup(MetalTargetDevice device) {
        if (deviceCommandMap.containsKey(device)) {
            deviceCommandMap.get(device).cleanup(Thread.currentThread().threadId());
        }
        if (deviceCommandMap.get(device).size() == 0) {
            deviceCommandMap.remove(device);
        }
    }

    public int size() {
        return deviceCommandMap.size();
    }

    private static class ThreadCommandQueueTable {
        private final Map<Long, MetalCommandQueue> commandQueueMap;

        ThreadCommandQueueTable() {
            commandQueueMap = new ConcurrentHashMap<>();
        }

        public MetalCommandQueue get(long threadId, MetalTargetDevice device, MetalContext context) {
            if (!commandQueueMap.containsKey(threadId)) {
                final int deviceVersion = device.deviceVersion();
                long commandProperties = context.getProperties();
                long commandQueuePtr;
                try {
                    // TODO: set max in flight here if needed
                    commandQueuePtr = context.metalCreateCommandQueue(device.getDevicePointer(), 0 /* maxInFlight */);
                } catch (MetalException e) {
                    throw new TornadoRuntimeException(e);
                }
                MetalCommandQueue commandQueue = new MetalCommandQueue(commandQueuePtr, commandProperties, deviceVersion);
                commandQueueMap.put(threadId, commandQueue);
            }
            return commandQueueMap.get(threadId);
        }

        public void cleanup(long threadId) {
            if (commandQueueMap.containsKey(threadId)) {
                MetalCommandQueue queue = commandQueueMap.remove(threadId);
                queue.cleanup();
            }
        }

        public int size() {
            return commandQueueMap.size();
        }
    }
}
