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
package uk.ac.manchester.tornado.drivers.spirv;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.drivers.opencl.OCLCommandQueue;
import uk.ac.manchester.tornado.drivers.opencl.OCLTargetDevice;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroCommandList;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroCommandQueue;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroContext;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDevice;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandListDescriptor;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandListFlag;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandListHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandQueueDescriptor;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandQueueGroupProperties;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandQueueGroupPropertyFlags;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandQueueHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandQueueMode;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandQueuePriority;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.utils.LevelZeroUtils;

public class SPIRVLevelZeroCommandQueueTable {

    private final Map<SPIRVDevice, ThreadCommandQueueTable> deviceCommandMap;

    public SPIRVLevelZeroCommandQueueTable() {
        deviceCommandMap = new ConcurrentHashMap<>();
    }

    public SPIRVLevelZeroCommandQueue get(SPIRVDevice device, LevelZeroContext levelZeroContext) {
        if (!deviceCommandMap.containsKey(device)) {
            ThreadCommandQueueTable table = new ThreadCommandQueueTable();
            table.get(Thread.currentThread().threadId(), device, levelZeroContext);
            deviceCommandMap.put(device, table);
        }
        return deviceCommandMap.get(device).get(Thread.currentThread().threadId(), device, levelZeroContext);
    }

    public void cleanup(SPIRVDevice device, LevelZeroContext levelZeroContext) {
        if (deviceCommandMap.containsKey(device)) {
            deviceCommandMap.get(device).cleanup(Thread.currentThread().threadId(), levelZeroContext);
        }
        if (deviceCommandMap.get(device).size() == 0) {
            deviceCommandMap.remove(device);
        }
    }

    public int size() {
        return deviceCommandMap.size();
    }

    private static class ThreadCommandQueueTable {

        private final Map<Long, SPIRVLevelZeroCommandQueue> commandQueueMap;

        ThreadCommandQueueTable() {
            commandQueueMap = new ConcurrentHashMap<>();
        }

        public SPIRVLevelZeroCommandQueue get(long threadId, SPIRVDevice device, LevelZeroContext levelZeroContext) {
            if (!commandQueueMap.containsKey(threadId)) {
                // Create Command Queue and Command List
                LevelZeroCommandQueue commandQueue = createCommandQueue(levelZeroContext, device);
                LevelZeroCommandList commandList = createCommandList(levelZeroContext, device);
                SPIRVLevelZeroCommandQueue spirvLevelZeroCommandQueue = new SPIRVLevelZeroCommandQueue(commandQueue, commandList, (LevelZeroDevice) device.getDeviceRuntime());
                commandQueueMap.put(threadId, spirvLevelZeroCommandQueue);
            }
            return commandQueueMap.get(threadId);
        }

        private LevelZeroCommandQueue createCommandQueue(LevelZeroContext context, SPIRVDevice spirvDevice) {
            LevelZeroDevice device = (LevelZeroDevice) spirvDevice.getDeviceRuntime();
            // Create Command Queue
            ZeCommandQueueDescriptor cmdDescriptor = new ZeCommandQueueDescriptor();
            cmdDescriptor.setFlags(0);
            cmdDescriptor.setMode(ZeCommandQueueMode.ZE_COMMAND_QUEUE_MODE_DEFAULT);
            cmdDescriptor.setPriority(ZeCommandQueuePriority.ZE_COMMAND_QUEUE_PRIORITY_NORMAL);
            cmdDescriptor.setOrdinal(getCommandQueueOrdinal(device));
            cmdDescriptor.setIndex(0);

            ZeCommandQueueHandle zeCommandQueueHandle = new ZeCommandQueueHandle();
            int result = context.zeCommandQueueCreate(context.getContextHandle().getContextPtr()[0], device.getDeviceHandlerPtr(), cmdDescriptor, zeCommandQueueHandle);
            LevelZeroUtils.errorLog("zeCommandQueueCreate", result);
            return new LevelZeroCommandQueue(context, zeCommandQueueHandle);
        }

        private LevelZeroCommandList createCommandList(LevelZeroContext context, SPIRVDevice spirvDevice) {
            LevelZeroDevice device = (LevelZeroDevice) spirvDevice.getDeviceRuntime();
            ZeCommandListDescriptor cmdListDescriptor = new ZeCommandListDescriptor();
            cmdListDescriptor.setFlags(ZeCommandListFlag.ZE_COMMAND_LIST_FLAG_RELAXED_ORDERING);
            cmdListDescriptor.setCommandQueueGroupOrdinal(getCommandQueueOrdinal(device));
            ZeCommandListHandle commandListHandler = new ZeCommandListHandle();
            int result = context.zeCommandListCreate(context.getContextHandle().getContextPtr()[0], device.getDeviceHandlerPtr(), cmdListDescriptor, commandListHandler);
            LevelZeroUtils.errorLog("zeCommandListCreate", result);
            return new LevelZeroCommandList(context, commandListHandler);
        }

        private int getCommandQueueOrdinal(LevelZeroDevice device) {
            int[] numQueueGroups = new int[1];
            int result = device.zeDeviceGetCommandQueueGroupProperties(device.getDeviceHandlerPtr(), numQueueGroups, null);
            LevelZeroUtils.errorLog("zeDeviceGetCommandQueueGroupProperties", result);

            if (numQueueGroups[0] == 0) {
                throw new RuntimeException("Number of Queue Groups is 0 for device: " + device.getDeviceProperties().getName());
            }
            int ordinal = numQueueGroups[0];

            if (device.getCommandQueueGroupProperties() == null) {
                ZeCommandQueueGroupProperties[] commandQueueGroupProperties = new ZeCommandQueueGroupProperties[numQueueGroups[0]];
                result = device.zeDeviceGetCommandQueueGroupProperties(device.getDeviceHandlerPtr(), numQueueGroups, commandQueueGroupProperties);
                LevelZeroUtils.errorLog("zeDeviceGetCommandQueueGroupProperties", result);
            }

            for (int i = 0; i < numQueueGroups[0]; i++) {
                if ((device.getCommandQueueGroupProperties(i).getFlags() & ZeCommandQueueGroupPropertyFlags.ZE_COMMAND_QUEUE_GROUP_PROPERTY_FLAG_COMPUTE) //
                        == ZeCommandQueueGroupPropertyFlags.ZE_COMMAND_QUEUE_GROUP_PROPERTY_FLAG_COMPUTE) {
                    ordinal = i;
                    break;
                }
            }
            return ordinal;
        }

        public void cleanup(long threadId, LevelZeroContext levelZeroContext) {
            if (commandQueueMap.containsKey(threadId)) {
                SPIRVLevelZeroCommandQueue queue = commandQueueMap.remove(threadId);
                levelZeroContext.zeCommandQueueDestroy(queue.getCommandQueue().getCommandQueueHandle());
                levelZeroContext.zeCommandListDestroy(queue.getCommandList().getCommandListHandler());
            }
        }

        public int size() {
            return commandQueueMap.size();
        }
    }

}
