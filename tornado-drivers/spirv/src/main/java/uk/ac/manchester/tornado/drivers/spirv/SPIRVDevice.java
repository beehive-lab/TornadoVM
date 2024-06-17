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

import java.nio.ByteOrder;

import uk.ac.manchester.tornado.api.TornadoTargetDevice;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;

public abstract class SPIRVDevice implements TornadoTargetDevice {

    private final int platformIndex;
    private final int deviceIndex;
    private SPIRVDeviceContext deviceContext;

    public SPIRVDevice(int platformIndex, int deviceIndex) {
        this.platformIndex = platformIndex;
        this.deviceIndex = deviceIndex;
    }

    public void setDeviceContext(SPIRVDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
    }

    public SPIRVDeviceContext getDeviceContext() {
        return this.deviceContext;
    }

    public abstract boolean isDeviceDoubleFPSupported();

    public abstract String getDeviceExtensions();

    public abstract ByteOrder getByteOrder();

    public int getDeviceIndex() {
        return deviceIndex;
    }

    public int getPlatformIndex() {
        return platformIndex;
    }

    public abstract String getName();

    public abstract Object getDeviceRuntime();

    public abstract long getDeviceGlobalMemorySize();

    public abstract long getDeviceLocalMemorySize();

    public abstract long[] getDeviceMaxWorkgroupDimensions();

    public abstract String getDeviceOpenCLCVersion();

    public abstract long getMaxAllocMemory();

    public abstract TornadoDeviceType getTornadoDeviceType();

    public abstract String getPlatformName();

    public abstract boolean isSPIRVSupported();

    public abstract SPIRVRuntimeType getSPIRVRuntime();
}
