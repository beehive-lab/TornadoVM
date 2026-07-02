/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2024, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
                    commandQueuePtr = context.clCreateCommandQueue(context.getContextId(), device.getDevicePointer(), commandProperties);
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
