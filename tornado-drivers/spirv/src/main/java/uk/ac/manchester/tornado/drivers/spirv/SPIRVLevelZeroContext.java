/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.opencl.OCLContextInterface;
import uk.ac.manchester.tornado.drivers.opencl.OCLEventPool;
import uk.ac.manchester.tornado.drivers.opencl.natives.NativeCommandQueue;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroByteBuffer;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroCommandList;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroCommandQueue;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroContext;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDevice;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDeviceMemAllocDescriptor;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDeviceMemAllocFlags;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeHostMemAllocDescriptor;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeHostMemAllocFlags;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeRelaxedAllocationLimitsExpDescriptor;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeRelaxedAllocationLimitsFlags;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.utils.LevelZeroUtils;
import uk.ac.manchester.tornado.drivers.spirv.timestamps.LevelZeroTransferTimeStamp;
import uk.ac.manchester.tornado.drivers.spirv.timestamps.TimeStamp;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

public class SPIRVLevelZeroContext extends SPIRVContext {

    private static final String BUFFER_NOT_FOUND_ERROR_MESSAGE = "Should always have a buffer created at this point.";

    private final LevelZeroContext levelZeroContext;
    private final List<SPIRVDeviceContext> spirvDeviceContext;

    // Maps buffer ID -> LevelZeroByteBuffer
    private final Map<Long, LevelZeroByteBuffer> deviceBufferMap;

    private final Map<Long, SPIRVLevelZeroCommandQueueTable> commmandQueueTable;

    public SPIRVLevelZeroContext(SPIRVPlatform platform, List<SPIRVDevice> devices, LevelZeroContext levelZeroContext) {
        super(platform, devices);
        this.levelZeroContext = levelZeroContext;

        spirvDeviceContext = new ArrayList<>();
        deviceBufferMap = new ConcurrentHashMap<>();
        commmandQueueTable = new ConcurrentHashMap<>();

        // Create LevelZeroDeviceContext per level-zero device
        for (SPIRVDevice device : devices) {
            SPIRVDeviceContext deviceContext = new SPIRVLevelZeroDeviceContext(device, this);
            device.setDeviceContext(deviceContext);
            spirvDeviceContext.add(deviceContext);
        }
    }

    public LevelZeroContext getLevelZeroContext() {
        return levelZeroContext;
    }

    @Override
    public OCLContextInterface getOpenCLLayer() {
        throw new TornadoRuntimeException("Unimplemented");
    }

    @Override
    public OCLEventPool getOCLEventPool(long executionPlanId) {
        throw new TornadoRuntimeException("Unimplemented");
    }

    @Override
    public SPIRVDeviceContext getDeviceContext(int deviceIndex) {
        return spirvDeviceContext.get(deviceIndex);
    }

    @Override
    public SPIRVLevelZeroCommandQueue getCommandQueueForDevice(long executionPlanId, int deviceIndex) {
        if (!commmandQueueTable.containsKey(executionPlanId)) {
            SPIRVDevice device = devices.get(deviceIndex);
            SPIRVLevelZeroCommandQueueTable spirvCommandQueueTable = new SPIRVLevelZeroCommandQueueTable();
            spirvCommandQueueTable.get(device, levelZeroContext);
            commmandQueueTable.put(executionPlanId, spirvCommandQueueTable);
        }
        return commmandQueueTable.get(executionPlanId).get(devices.get(deviceIndex), levelZeroContext);
    }

    private ZeDeviceMemAllocDescriptor createDeviceDescription() {
        ZeDeviceMemAllocDescriptor deviceMemAllocDesc = new ZeDeviceMemAllocDescriptor();
        deviceMemAllocDesc.setFlags(ZeDeviceMemAllocFlags.ZE_DEVICE_MEM_ALLOC_FLAG_BIAS_CACHED);
        deviceMemAllocDesc.setOrdinal(0);
        return deviceMemAllocDesc;
    }

    private ZeHostMemAllocDescriptor createHostMemDescription() {
        ZeHostMemAllocDescriptor hostMemAllocDesc = new ZeHostMemAllocDescriptor();
        hostMemAllocDesc.setFlags(ZeHostMemAllocFlags.ZE_HOST_MEM_ALLOC_FLAG_BIAS_CACHED);
        return hostMemAllocDesc;
    }

    private void registerTimeStamp(LevelZeroCommandList commandList, TimeStamp startT, TimeStamp stopT) {
        LevelZeroTransferTimeStamp start = (LevelZeroTransferTimeStamp) startT;
        LevelZeroTransferTimeStamp stop = (LevelZeroTransferTimeStamp) stopT;
        setupTimeStamps(commandList, start, stop);
        start.appendTimeStamp();
    }

    @Override
    public long allocateMemory(int deviceIndex, long numBytes, Access access) {
        LevelZeroByteBuffer deviceBuffer = new LevelZeroByteBuffer();
        LevelZeroDevice l0Device = (LevelZeroDevice) devices.get(deviceIndex).getDeviceRuntime();
        ZeDeviceMemAllocDescriptor deviceMemAllocDesc = createDeviceDescription();

        ZeRelaxedAllocationLimitsExpDescriptor relaxedAllocationLimitsExpDescriptor = null;
        if (TornadoOptions.LEVEL_ZERO_EXTENDED_MEMORY_MODE) {
            relaxedAllocationLimitsExpDescriptor = new ZeRelaxedAllocationLimitsExpDescriptor();
            relaxedAllocationLimitsExpDescriptor.setFlags(ZeRelaxedAllocationLimitsFlags.ZE_RELAXED_ALLOCATION_LIMITS_EXP_FLAG_MAX_SIZE);
            relaxedAllocationLimitsExpDescriptor.materialize();

            deviceMemAllocDesc.setNext(relaxedAllocationLimitsExpDescriptor);
        }

        if (TornadoOptions.LEVEL_ZERO_SHARED_MEMORY) {
            // Buffer Allocation in Shared Memory
            ZeHostMemAllocDescriptor hostMemAllocDesc = createHostMemDescription();
            if (TornadoOptions.LEVEL_ZERO_EXTENDED_MEMORY_MODE) {
                hostMemAllocDesc.setNext(relaxedAllocationLimitsExpDescriptor);
            }
            int result = levelZeroContext.zeMemAllocShared( //
                    levelZeroContext.getDefaultContextPtr(), //
                    deviceMemAllocDesc, //
                    hostMemAllocDesc, //
                    numBytes, //
                    TornadoOptions.LEVEL_ZERO_BUFFER_ALIGNMENT, //
                    l0Device.getDeviceHandlerPtr(), //
                    deviceBuffer);
            LevelZeroUtils.errorLog("zeMemAllocShared", result);
        } else {
            // Buffer Allocation in Device Memory
            int result = levelZeroContext.zeMemAllocDevice(//
                    levelZeroContext.getDefaultContextPtr(), //
                    deviceMemAllocDesc, //
                    numBytes, //
                    TornadoOptions.LEVEL_ZERO_BUFFER_ALIGNMENT, //
                    l0Device.getDeviceHandlerPtr(), //
                    deviceBuffer);
            LevelZeroUtils.errorLog("zeMemAllocDevice", result);
        }
        deviceBufferMap.put(deviceBuffer.getPtrBuffer(), deviceBuffer);
        return deviceBuffer.getPtrBuffer();
    }

    @Override
    public void freeMemory(long buffer, int deviceIndex) {
        LevelZeroByteBuffer deviceBuffer = deviceBufferMap.remove(buffer);
        if (deviceBuffer == null) {
            TornadoInternalError.shouldNotReachHere(BUFFER_NOT_FOUND_ERROR_MESSAGE);
        }

        int result = levelZeroContext.zeMemFree(levelZeroContext.getDefaultContextPtr(), deviceBuffer);
        LevelZeroUtils.errorLog("zeMemFree", result);
    }

    @Override
    public int readBuffer(long executionPlanId, int deviceIndex, long bufferId, long srcOffset, long bytes, byte[] value, long dstOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        SPIRVLevelZeroCommandQueue spirvCommandQueue = getCommandQueueForDevice(executionPlanId, deviceIndex);
        LevelZeroCommandList commandList = spirvCommandQueue.getCommandList();
        LevelZeroByteBuffer deviceBuffer = deviceBufferMap.get(bufferId);
        if (deviceBuffer == null) {
            TornadoInternalError.shouldNotReachHere(BUFFER_NOT_FOUND_ERROR_MESSAGE);
        }

        if (profilerTransfer != null) {
            registerTimeStamp(commandList, profilerTransfer.getStart(), profilerTransfer.getStop());
        }
        int result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), value, deviceBuffer, bytes, dstOffset, srcOffset, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendMemoryCopyWithOffset", result);
        enqueueBarrier(executionPlanId, deviceIndex);
        if (profilerTransfer != null) {
            appendTimeStamp(profilerTransfer.getStop());
        }
        return 0;
    }

    @Override
    public int readBuffer(long executionPlanId, int deviceIndex, long bufferId, long srcOffset, long bytes, char[] value, long dstOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        SPIRVLevelZeroCommandQueue spirvCommandQueue = getCommandQueueForDevice(executionPlanId, deviceIndex);
        LevelZeroCommandList commandList = spirvCommandQueue.getCommandList();
        LevelZeroByteBuffer deviceBuffer = deviceBufferMap.get(bufferId);
        if (deviceBuffer == null) {
            TornadoInternalError.shouldNotReachHere(BUFFER_NOT_FOUND_ERROR_MESSAGE);
        }

        if (profilerTransfer != null) {
            registerTimeStamp(commandList, profilerTransfer.getStart(), profilerTransfer.getStop());
        }
        int result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), value, deviceBuffer, bytes, dstOffset, srcOffset, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendMemoryCopyWithOffset", result);
        enqueueBarrier(executionPlanId, deviceIndex);
        if (profilerTransfer != null) {
            appendTimeStamp(profilerTransfer.getStop());
        }
        return 0;
    }

    @Override
    public int readBuffer(long executionPlanId, int deviceIndex, long bufferId, long srcOffset, long bytes, short[] value, long dstOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        SPIRVLevelZeroCommandQueue spirvCommandQueue = getCommandQueueForDevice(executionPlanId, deviceIndex);
        LevelZeroCommandList commandList = spirvCommandQueue.getCommandList();
        LevelZeroByteBuffer deviceBuffer = deviceBufferMap.get(bufferId);
        if (deviceBuffer == null) {
            TornadoInternalError.shouldNotReachHere(BUFFER_NOT_FOUND_ERROR_MESSAGE);
        }

        if (profilerTransfer != null) {
            registerTimeStamp(commandList, profilerTransfer.getStart(), profilerTransfer.getStop());
        }
        int result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), value, deviceBuffer, bytes, dstOffset, srcOffset, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendMemoryCopyWithOffset", result);
        enqueueBarrier(executionPlanId, deviceIndex);
        if (profilerTransfer != null) {
            appendTimeStamp(profilerTransfer.getStop());
        }
        return 0;
    }

    @Override
    public int readBuffer(long executionPlanId, int deviceIndex, long bufferId, long srcOffset, long bytes, int[] array, long dstOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        SPIRVLevelZeroCommandQueue spirvCommandQueue = getCommandQueueForDevice(executionPlanId, deviceIndex);
        LevelZeroCommandList commandList = spirvCommandQueue.getCommandList();
        LevelZeroByteBuffer deviceBuffer = deviceBufferMap.get(bufferId);
        if (deviceBuffer == null) {
            TornadoInternalError.shouldNotReachHere(BUFFER_NOT_FOUND_ERROR_MESSAGE);
        }

        if (profilerTransfer != null) {
            registerTimeStamp(commandList, profilerTransfer.getStart(), profilerTransfer.getStop());
        }
        int result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), array, deviceBuffer, bytes, dstOffset, srcOffset, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendMemoryCopyWithOffset", result);
        enqueueBarrier(executionPlanId, deviceIndex);

        if (profilerTransfer != null) {
            appendTimeStamp(profilerTransfer.getStop());
        }
        return 0;
    }

    @Override
    public int readBuffer(long executionPlanId, int deviceIndex, long bufferId, long srcOffset, long bytes, float[] value, long dstOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        SPIRVLevelZeroCommandQueue spirvCommandQueue = getCommandQueueForDevice(executionPlanId, deviceIndex);
        LevelZeroCommandList commandList = spirvCommandQueue.getCommandList();
        LevelZeroByteBuffer deviceBuffer = deviceBufferMap.get(bufferId);
        if (deviceBuffer == null) {
            TornadoInternalError.shouldNotReachHere(BUFFER_NOT_FOUND_ERROR_MESSAGE);
        }

        if (profilerTransfer != null) {
            registerTimeStamp(commandList, profilerTransfer.getStart(), profilerTransfer.getStop());
        }
        int result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), value, deviceBuffer, bytes, dstOffset, srcOffset, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendMemoryCopyWithOffset", result);
        enqueueBarrier(executionPlanId, deviceIndex);

        if (profilerTransfer != null) {
            appendTimeStamp(profilerTransfer.getStop());
        }
        return 0;
    }

    @Override
    public int readBuffer(long executionPlanId, int deviceIndex, long bufferId, long srcOffset, long bytes, double[] value, long dstOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        SPIRVLevelZeroCommandQueue spirvCommandQueue = getCommandQueueForDevice(executionPlanId, deviceIndex);
        LevelZeroCommandList commandList = spirvCommandQueue.getCommandList();
        LevelZeroByteBuffer deviceBuffer = deviceBufferMap.get(bufferId);
        if (deviceBuffer == null) {
            TornadoInternalError.shouldNotReachHere(BUFFER_NOT_FOUND_ERROR_MESSAGE);
        }

        if (profilerTransfer != null) {
            registerTimeStamp(commandList, profilerTransfer.getStart(), profilerTransfer.getStop());
        }
        int result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), value, deviceBuffer, bytes, dstOffset, srcOffset, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendMemoryCopyWithOffset", result);
        enqueueBarrier(executionPlanId, deviceIndex);

        if (profilerTransfer != null) {
            appendTimeStamp(profilerTransfer.getStop());
        }
        return 0;
    }

    @Override
    public int readBuffer(long executionPlanId, int deviceIndex, long bufferId, long srcOffset, long bytes, long[] value, long dstOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        SPIRVLevelZeroCommandQueue spirvCommandQueue = getCommandQueueForDevice(executionPlanId, deviceIndex);
        LevelZeroCommandList commandList = spirvCommandQueue.getCommandList();
        LevelZeroByteBuffer deviceBuffer = deviceBufferMap.get(bufferId);
        if (deviceBuffer == null) {
            TornadoInternalError.shouldNotReachHere(BUFFER_NOT_FOUND_ERROR_MESSAGE);
        }

        if (profilerTransfer != null) {
            registerTimeStamp(commandList, profilerTransfer.getStart(), profilerTransfer.getStop());
        }
        int result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), value, deviceBuffer, bytes, dstOffset, srcOffset, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendMemoryCopyWithOffset", result);
        enqueueBarrier(executionPlanId, deviceIndex);
        if (profilerTransfer != null) {
            appendTimeStamp(profilerTransfer.getStop());
        }

        return 0;
    }

    // FIXME: <TODO> Events are still pending
    @Override
    public int enqueueWriteBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, byte[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        SPIRVLevelZeroCommandQueue spirvCommandQueue = getCommandQueueForDevice(executionPlanId, deviceIndex);
        LevelZeroCommandList commandList = spirvCommandQueue.getCommandList();
        LevelZeroByteBuffer deviceBuffer = deviceBufferMap.get(bufferId);
        if (deviceBuffer == null) {
            TornadoInternalError.shouldNotReachHere(BUFFER_NOT_FOUND_ERROR_MESSAGE);
        }

        if (profilerTransfer != null) {
            registerTimeStamp(commandList, profilerTransfer.getStart(), profilerTransfer.getStop());
        }
        int result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), deviceBuffer, value, bytes, offset, hostOffset, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendMemoryCopyWithOffset", result);
        enqueueBarrier(executionPlanId, deviceIndex);

        if (profilerTransfer != null) {
            appendTimeStamp(profilerTransfer.getStop());
        }
        return 0;
    }

    @Override
    public int enqueueWriteBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, char[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        SPIRVLevelZeroCommandQueue spirvCommandQueue = getCommandQueueForDevice(executionPlanId, deviceIndex);
        LevelZeroCommandList commandList = spirvCommandQueue.getCommandList();
        LevelZeroByteBuffer deviceBuffer = deviceBufferMap.get(bufferId);
        if (deviceBuffer == null) {
            TornadoInternalError.shouldNotReachHere(BUFFER_NOT_FOUND_ERROR_MESSAGE);
        }

        if (profilerTransfer != null) {
            registerTimeStamp(commandList, profilerTransfer.getStart(), profilerTransfer.getStop());
        }

        int result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), deviceBuffer, value, bytes, offset, hostOffset, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendMemoryCopyWithOffset", result);
        enqueueBarrier(executionPlanId, deviceIndex);

        if (profilerTransfer != null) {
            appendTimeStamp(profilerTransfer.getStop());
        }
        return 0;
    }

    @Override
    public int enqueueWriteBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, short[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        SPIRVLevelZeroCommandQueue spirvCommandQueue = getCommandQueueForDevice(executionPlanId, deviceIndex);
        LevelZeroCommandList commandList = spirvCommandQueue.getCommandList();
        LevelZeroByteBuffer deviceBuffer = deviceBufferMap.get(bufferId);
        if (deviceBuffer == null) {
            TornadoInternalError.shouldNotReachHere(BUFFER_NOT_FOUND_ERROR_MESSAGE);
        }

        if (profilerTransfer != null) {
            registerTimeStamp(commandList, profilerTransfer.getStart(), profilerTransfer.getStop());
        }
        int result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), deviceBuffer, value, bytes, offset, hostOffset, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendMemoryCopyWithOffset", result);
        enqueueBarrier(executionPlanId, deviceIndex);

        if (profilerTransfer != null) {
            appendTimeStamp(profilerTransfer.getStop());
        }
        return 0;
    }

    private void setupTimeStamps(LevelZeroCommandList commandList, LevelZeroTransferTimeStamp start, LevelZeroTransferTimeStamp stop) {
        start.setCommandList(commandList);
        start.createBufferTimeStamp();
        stop.setCommandList(commandList);
        stop.createBufferTimeStamp();
    }

    private void appendTimeStamp(TimeStamp timeStamp) {
        LevelZeroTransferTimeStamp timeStamp1 = (LevelZeroTransferTimeStamp) timeStamp;
        timeStamp1.appendTimeStamp();
    }

    @Override
    public int enqueueWriteBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, int[] array, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        SPIRVLevelZeroCommandQueue spirvCommandQueue = getCommandQueueForDevice(executionPlanId, deviceIndex);
        LevelZeroCommandList commandList = spirvCommandQueue.getCommandList();
        LevelZeroByteBuffer deviceBuffer = deviceBufferMap.get(bufferId);
        if (deviceBuffer == null) {
            TornadoInternalError.shouldNotReachHere(BUFFER_NOT_FOUND_ERROR_MESSAGE);
        }

        if (profilerTransfer != null) {
            registerTimeStamp(commandList, profilerTransfer.getStart(), profilerTransfer.getStop());
        }
        int result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), deviceBuffer, array, bytes, offset, hostOffset, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendMemoryCopyWithOffset", result);
        enqueueBarrier(executionPlanId, deviceIndex);

        if (profilerTransfer != null) {
            appendTimeStamp(profilerTransfer.getStop());
        }

        return 0;
    }

    @Override
    public int enqueueWriteBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, float[] array, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        SPIRVLevelZeroCommandQueue spirvCommandQueue = getCommandQueueForDevice(executionPlanId, deviceIndex);
        LevelZeroCommandList commandList = spirvCommandQueue.getCommandList();
        LevelZeroByteBuffer deviceBuffer = deviceBufferMap.get(bufferId);
        if (deviceBuffer == null) {
            TornadoInternalError.shouldNotReachHere(BUFFER_NOT_FOUND_ERROR_MESSAGE);
        }

        if (profilerTransfer != null) {
            registerTimeStamp(commandList, profilerTransfer.getStart(), profilerTransfer.getStop());
        }
        int result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), deviceBuffer, array, bytes, offset, hostOffset, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendMemoryCopyWithOffset", result);
        enqueueBarrier(executionPlanId, deviceIndex);

        if (profilerTransfer != null) {
            appendTimeStamp(profilerTransfer.getStop());
        }

        return 0;
    }

    @Override
    public int enqueueWriteBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, double[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        SPIRVLevelZeroCommandQueue spirvCommandQueue = getCommandQueueForDevice(executionPlanId, deviceIndex);
        LevelZeroCommandList commandList = spirvCommandQueue.getCommandList();
        LevelZeroByteBuffer deviceBuffer = deviceBufferMap.get(bufferId);
        if (deviceBuffer == null) {
            TornadoInternalError.shouldNotReachHere(BUFFER_NOT_FOUND_ERROR_MESSAGE);
        }

        if (profilerTransfer != null) {
            registerTimeStamp(commandList, profilerTransfer.getStart(), profilerTransfer.getStop());
        }
        int result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), deviceBuffer, value, bytes, offset, hostOffset, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendMemoryCopyWithOffset", result);
        enqueueBarrier(executionPlanId, deviceIndex);

        if (profilerTransfer != null) {
            appendTimeStamp(profilerTransfer.getStop());
        }
        return 0;
    }

    @Override
    public int enqueueWriteBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, long[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        SPIRVLevelZeroCommandQueue spirvCommandQueue = getCommandQueueForDevice(executionPlanId, deviceIndex);
        LevelZeroCommandList commandList = spirvCommandQueue.getCommandList();
        LevelZeroByteBuffer deviceBuffer = deviceBufferMap.get(bufferId);
        if (deviceBuffer == null) {
            TornadoInternalError.shouldNotReachHere(BUFFER_NOT_FOUND_ERROR_MESSAGE);
        }

        if (profilerTransfer != null) {
            registerTimeStamp(commandList, profilerTransfer.getStart(), profilerTransfer.getStop());
        }

        int result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), deviceBuffer, value, bytes, offset, hostOffset, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendMemoryCopyWithOffset", result);
        enqueueBarrier(executionPlanId, deviceIndex);
        if (profilerTransfer != null) {
            appendTimeStamp(profilerTransfer.getStop());
        }
        return 0;
    }

    @Override
    public int enqueueWriteBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, long value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        SPIRVLevelZeroCommandQueue spirvCommandQueue = getCommandQueueForDevice(executionPlanId, deviceIndex);
        LevelZeroCommandList commandList = spirvCommandQueue.getCommandList();
        LevelZeroByteBuffer deviceBuffer = deviceBufferMap.get(bufferId);
        if (deviceBuffer == null) {
            TornadoInternalError.shouldNotReachHere(BUFFER_NOT_FOUND_ERROR_MESSAGE);
        }

        if (profilerTransfer != null) {
            registerTimeStamp(commandList, profilerTransfer.getStart(), profilerTransfer.getStop());
        }

        int result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), deviceBuffer, value, bytes, offset, hostOffset, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendMemoryCopyWithOffset", result);
        enqueueBarrier(executionPlanId, deviceIndex);

        if (profilerTransfer != null) {
            appendTimeStamp(profilerTransfer.getStop());
        }
        return 0;
    }

    @Override
    public void enqueueBarrier(long executionPlanId, int deviceIndex) {
        SPIRVLevelZeroCommandQueue spirvCommandQueue = getCommandQueueForDevice(executionPlanId, deviceIndex);
        LevelZeroCommandList commandList = spirvCommandQueue.getCommandList();
        int result = commandList.zeCommandListAppendBarrier(commandList.getCommandListHandlerPtr(), null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendBarrier", result);
    }

    @Override
    public void flush(long executionPlanId, int deviceIndex) {
        SPIRVLevelZeroCommandQueue spirvCommandQueue = getCommandQueueForDevice(executionPlanId, deviceIndex);
        LevelZeroCommandList commandList = spirvCommandQueue.getCommandList();
        LevelZeroCommandQueue commandQueue = spirvCommandQueue.getCommandQueue();

        // Close the command list
        int result = commandList.zeCommandListClose(commandList.getCommandListHandlerPtr());
        LevelZeroUtils.errorLog("zeCommandListClose", result);

        // Execute all commands within the command list
        result = commandQueue.zeCommandQueueExecuteCommandLists(commandQueue.getCommandQueueHandlerPtr(), 1, commandList.getCommandListHandler(), null);
        LevelZeroUtils.errorLog("zeCommandQueueExecuteCommandLists", result);

        // Synchronize
        result = commandQueue.zeCommandQueueSynchronize(commandQueue.getCommandQueueHandlerPtr(), Long.MAX_VALUE);
        LevelZeroUtils.errorLog("zeCommandQueueSynchronize", result);

        // Reset for the rest of executions
        result = commandList.zeCommandListReset(commandList.getCommandListHandlerPtr());
        LevelZeroUtils.errorLog("zeCommandListReset", result);
    }

    @Override
    public void readBuffer(long executionPlanId, int deviceIndex, long bufferId, long srcOffset, long bytes, long offHeapSegmentAddress, long dstOffset, int[] waitEvents,
            ProfilerTransfer profilerTransfer) {
        SPIRVLevelZeroCommandQueue spirvCommandQueue = getCommandQueueForDevice(executionPlanId, deviceIndex);
        LevelZeroCommandList commandList = spirvCommandQueue.getCommandList();
        LevelZeroByteBuffer deviceBuffer = deviceBufferMap.get(bufferId);
        if (deviceBuffer == null) {
            TornadoInternalError.shouldNotReachHere(BUFFER_NOT_FOUND_ERROR_MESSAGE);
        }

        if (profilerTransfer != null) {
            registerTimeStamp(commandList, profilerTransfer.getStart(), profilerTransfer.getStop());
        }
        int result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), offHeapSegmentAddress, deviceBuffer, bytes, dstOffset, srcOffset, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendMemoryCopyWithOffset", result);
        enqueueBarrier(executionPlanId, deviceIndex);

        if (profilerTransfer != null) {
            appendTimeStamp(profilerTransfer.getStop());
        }
    }

    @Override
    public void reset(long executionPlanId, int deviceIndex) {
        SPIRVLevelZeroCommandQueueTable table = commmandQueueTable.get(executionPlanId);
        if (table != null) {
            SPIRVDevice device = devices.get(deviceIndex);
            table.cleanup(device, levelZeroContext);
            if (table.size() == 0) {
                commmandQueueTable.remove(executionPlanId);
            }
        }
    }

    @Override
    public long mapOnDeviceMemoryRegion(long executionPlanId, int deviceIndex, long destBuffer, long srcBuffer, long offset, int sizeOfType, long sizeSource, long sizeDest) {
        LevelZeroByteBuffer deviceBuffer = deviceBufferMap.get(srcBuffer);
        if (offset == 0) {
            return NativeCommandQueue.mapOnDeviceMemoryRegion(destBuffer, deviceBuffer.getPtrBuffer());
        } else {
            throw new TornadoRuntimeException("mapOnDeviceMemoryRegion with offset not supported");
        }
    }
}
