/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.cuda;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.cuda.exceptions.CUDAException;

public class CUDACommandQueueTable {

    private final Map<CUDATargetDevice, ThreadCommandQueueTable> deviceCommandMap;

    public CUDACommandQueueTable() {
        deviceCommandMap = new ConcurrentHashMap<>();
    }

    public CUDACommandQueue get(CUDATargetDevice device, CUDAContext context) {
        if (!deviceCommandMap.containsKey(device)) {
            ThreadCommandQueueTable table = new ThreadCommandQueueTable();
            table.get(Thread.currentThread().threadId(), device, context);
            deviceCommandMap.put(device, table);
        }
        return deviceCommandMap.get(device).get(Thread.currentThread().threadId(), device, context);
    }

    public void cleanup(CUDATargetDevice device) {
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
        private final Map<Long, CUDACommandQueue> commandQueueMap;

        ThreadCommandQueueTable() {
            commandQueueMap = new ConcurrentHashMap<>();
        }

        public CUDACommandQueue get(long threadId, CUDATargetDevice device, CUDAContext context) {
            if (!commandQueueMap.containsKey(threadId)) {
                final int deviceVersion = device.deviceVersion();
                long commandProperties = context.getProperties();
                long commandQueuePtr;
                try {
                    commandQueuePtr = context.clCreateCommandQueue(context.getContextId(device.getIndex()), device.getDevicePointer(), commandProperties);
                } catch (CUDAException e) {
                    throw new TornadoRuntimeException(e);
                }
                CUDACommandQueue commandQueue = new CUDACommandQueue(commandQueuePtr, commandProperties, deviceVersion);
                commandQueueMap.put(threadId, commandQueue);
            }
            return commandQueueMap.get(threadId);
        }

        public void cleanup(long threadId) {
            if (commandQueueMap.containsKey(threadId)) {
                CUDACommandQueue queue = commandQueueMap.remove(threadId);
                queue.cleanup();
            }
        }

        public int size() {
            return commandQueueMap.size();
        }
    }
}
