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

import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroContext;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDevice;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDriver;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeContextDescriptor;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDevicesHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDriverHandle;

public class SPIRVLevelZeroPlatform implements SPIRVPlatform {

    private final int indexDriver;
    private final LevelZeroDriver driver;
    private final ZeDriverHandle driversHandler;
    private int deviceCount = -1;
    private List<SPIRVDevice> spirvDevices;

    SPIRVContext spirvContext;
    LevelZeroContext levelZeroContext;

    public SPIRVLevelZeroPlatform(LevelZeroDriver driver, ZeDriverHandle driversHandler, int indexDriver) {
        this.driver = driver;
        this.driversHandler = driversHandler;
        this.indexDriver = indexDriver;
        initDevices();
    }

    private void initDevices() {
        // Get number of devices in a driver
        int[] deviceCountArray = new int[1];
        driver.zeDeviceGet(driversHandler, indexDriver, deviceCountArray, null);
        deviceCount = deviceCountArray[0];

        // Instantiate a device Handler
        ZeDevicesHandle deviceHandler = new ZeDevicesHandle(deviceCount);
        driver.zeDeviceGet(driversHandler, indexDriver, deviceCountArray, deviceHandler);

        List<LevelZeroDevice> devices = new ArrayList<>();
        spirvDevices = new ArrayList<>();

        for (int i = 0; i < deviceCount; i++) {
            LevelZeroDevice device = driver.getDevice(driversHandler, i);
            devices.add(device);
            SPIRVDevice spirvDevice = new SPIRVLevelZeroDevice(indexDriver, i, devices.get(i));
            spirvDevices.add(spirvDevice);
        }
    }

    @Override
    public int getNumDevices() {
        if (deviceCount == -1) {
            initDevices();
            return deviceCount;
        }
        return deviceCount;
    }

    @Override
    public SPIRVDevice getDevice(int deviceIndex) {
        return spirvDevices.get(deviceIndex);
    }

    @Override
    public SPIRVContext createContext() {
        ZeContextDescriptor contextDescriptor = new ZeContextDescriptor();
        levelZeroContext = new LevelZeroContext(driversHandler, contextDescriptor);
        levelZeroContext.zeContextCreate(driversHandler.getZe_driver_handle_t_ptr()[indexDriver]);
        spirvContext = new SPIRVLevelZeroContext(this, spirvDevices, levelZeroContext);
        return spirvContext;
    }

    @Override
    public SPIRVDevice[] getDevices() {
        return spirvDevices.toArray(new SPIRVDevice[0]);
    }

    @Override
    public SPIRVRuntimeType getRuntime() {
        return SPIRVRuntimeType.LEVEL_ZERO;
    }
}
