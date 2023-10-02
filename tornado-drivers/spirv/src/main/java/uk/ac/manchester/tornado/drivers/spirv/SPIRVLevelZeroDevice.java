/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021-2022, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
 */
package uk.ac.manchester.tornado.drivers.spirv;

import java.nio.ByteOrder;

import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDevice;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDriver;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeAPIVersion;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDeviceComputeProperties;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDeviceModuleFlags;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDeviceModuleProperties;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDeviceProperties;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDeviceType;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDriverHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeMemoryProperties;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeResult;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

public class SPIRVLevelZeroDevice extends SPIRVDevice {

    private final LevelZeroDevice device;
    private final long totalMemorySize;
    ZeAPIVersion apiVersion;
    private String deviceName;
    private ZeMemoryProperties[] memoryProperties;
    private ZeDeviceProperties deviceProperties;
    private ZeDeviceComputeProperties computeProperties;
    private boolean queriedSupportFP64;
    private ZeDeviceModuleProperties moduleProperties;

    public SPIRVLevelZeroDevice(int platformIndex, int deviceIndex, LevelZeroDevice device) {
        super(platformIndex, deviceIndex);
        this.device = device;
        this.totalMemorySize = getTotalGlobalMemorySize();
        initDeviceProperties();
        initDeviceComputeProperties();
        initDriverVersion();
    }

    private static void errorLog(String method, int result) {
        if (result != ZeResult.ZE_RESULT_SUCCESS) {
            System.out.println("Error " + method);
        }
    }

    private void initDeviceProperties() {
        deviceProperties = new ZeDeviceProperties();
        int result = device.zeDeviceGetProperties(device.getDeviceHandlerPtr(), deviceProperties);
        errorLog("zeDeviceGetProperties", result);
        deviceName = deviceProperties.getName();
    }

    private void initDeviceComputeProperties() {
        computeProperties = new ZeDeviceComputeProperties();
        int result = device.zeDeviceGetComputeProperties(device.getDeviceHandlerPtr(), computeProperties);
        errorLog("zeDeviceGetComputeProperties", result);
    }

    private long getTotalGlobalMemorySize() {
        // A) Count memories
        int[] memoryCount = new int[1];
        int result = device.zeDeviceGetMemoryProperties(device.getDeviceHandlerPtr(), memoryCount, null);
        errorLog("zeDeviceGetMemoryProperties", result);

        // B) Access the properties of each of the memories
        memoryProperties = new ZeMemoryProperties[memoryCount[0]];
        result = device.zeDeviceGetMemoryProperties(device.getDeviceHandlerPtr(), memoryCount, memoryProperties);
        errorLog("zeDeviceGetMemoryProperties", result);

        long memorySize = 0;
        for (ZeMemoryProperties m : memoryProperties) {
            memorySize += m.getTotalSize();
        }
        return memorySize;
    }

    private void initDriverVersion() {
        apiVersion = new ZeAPIVersion();
        LevelZeroDriver driver = device.getDriver();
        ZeDriverHandle driverHandler = device.getDriverHandler();
        int result = driver.zeDriverGetApiVersion(driverHandler, 0, apiVersion);
        errorLog("zeDriverGetApiVersion", result);
    }

    @Override
    public ByteOrder getByteOrder() {
        return ByteOrder.LITTLE_ENDIAN;
    }

    @Override
    public String getName() {
        return "SPIRV LevelZero - " + deviceName;
    }

    @Override
    public boolean isDeviceDoubleFPSupported() {
        if (!queriedSupportFP64) {
            moduleProperties = new ZeDeviceModuleProperties();
            int result = device.zeDeviceGetModuleProperties(device.getDeviceHandlerPtr(), moduleProperties);
            errorLog("zeDeviceGetModuleProperties", result);
            queriedSupportFP64 = true;
        }
        int flags = moduleProperties.getFlags();
        return (ZeDeviceModuleFlags.ZE_DEVICE_MODULE_FLAG_FP64 & flags) == ZeDeviceModuleFlags.ZE_DEVICE_MODULE_FLAG_FP64;
    }

    @Override
    public String getDeviceExtensions() {
        return device.getDeviceExtensions();
    }

    @Override
    public LevelZeroDevice getDevice() {
        return device;
    }

    @Override
    public String getDeviceName() {
        return deviceProperties.getName();
    }

    @Override
    public long getDeviceGlobalMemorySize() {
        return totalMemorySize;
    }

    @Override
    public long getDeviceLocalMemorySize() {
        return computeProperties.getMaxSharedLocalMemory();
    }

    // FIXME - Not sure this is the max of compute UNITS
    @Override
    public int getDeviceMaxComputeUnits() {
        return deviceProperties.getNumEUsPerSubslice() * deviceProperties.getNumSubslicesPerSlice();
    }

    /**
     * Return max thread for each dimension
     *
     * @return
     */
    @Override
    public long[] getDeviceMaxWorkItemSizes() {
        return getDeviceMaxWorkgroupDimensions();
    }

    /**
     * Return the maximum number of threads for all groups.
     *
     * @return long[]
     */
    @Override
    public long[] getDeviceMaxWorkGroupSize() {
        return new long[] { computeProperties.getMaxTotalGroupSize() };
    }

    @Override
    public int getMaxThreadsPerBlock() {
        return (int) getDeviceMaxWorkGroupSize()[0];
    }

    @Override
    public int getDeviceMaxClockFrequency() {
        return deviceProperties.getCoreClockRate();
    }

    // FIXME
    @Override
    public long getDeviceMaxConstantBufferSize() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public long getDeviceMaxAllocationSize() {
        if (TornadoOptions.LEVEL_ZERO_EXTENDED_MEMORY_MODE) {
            return totalMemorySize;
        }
        return deviceProperties.getMaxMemAllocSize();
    }

    @Override
    public String getDeviceInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(deviceProperties.toString() + "\n");
        sb.append(computeProperties.toString() + "\n");
        sb.append(memoryProperties.toString() + "\n");
        return sb.toString();
    }

    @Override
    public long[] getDeviceMaxWorkgroupDimensions() {
        long[] maxWorkGroup = new long[3];
        maxWorkGroup[0] = computeProperties.getMaxGroupSizeX();
        maxWorkGroup[1] = computeProperties.getMaxGroupSizeY();
        maxWorkGroup[2] = computeProperties.getMaxGroupSizeZ();
        return maxWorkGroup;
    }

    @Override
    public String getDeviceOpenCLCVersion() {
        return " (LEVEL ZERO) " + apiVersion.getAPIVersion();
    }

    @Override
    public long getMaxAllocMemory() {
        return deviceProperties.getMaxMemAllocSize();
    }

    @Override
    public TornadoDeviceType getTornadoDeviceType() {
        ZeDeviceType type = deviceProperties.getType();
        if (type == ZeDeviceType.ZE_DEVICE_TYPE_GPU) {
            return TornadoDeviceType.GPU;
        } else if (type == ZeDeviceType.ZE_DEVICE_TYPE_FPGA) {
            return TornadoDeviceType.FPGA;
        } else if (type == ZeDeviceType.ZE_DEVICE_TYPE_CPU) {
            return TornadoDeviceType.CPU;
        }
        return null;
    }

    @Override
    public String getPlatformName() {
        return "LevelZero";
    }

}
