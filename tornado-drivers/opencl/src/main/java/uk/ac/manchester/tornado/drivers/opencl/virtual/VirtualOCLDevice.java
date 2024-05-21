/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020-2022 APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.drivers.opencl.virtual;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

import java.nio.ByteOrder;

import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContextInterface;
import uk.ac.manchester.tornado.drivers.opencl.OCLTargetDevice;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLDeviceType;

public class VirtualOCLDevice implements OCLTargetDevice {

    private final int index;
    private final String name;
    private final boolean deviceEndianLittle;
    private final int maxComputeUnits;
    private final long maxAllocationSize;
    private final long globalMemorySize;
    private final long localMemorySize;
    private final int maxWorkItemDimensions;
    private final long[] maxWorkItemSizes;
    private final long[] maxWorkGroupSize;
    private final long maxConstantBufferSize;
    private final boolean doubleFPConfig;
    private final long singleFPConfig;
    private final OCLDeviceType deviceType;
    private final int deviceMaxClockFrequency;
    private final int deviceAddressBits;
    private final String deviceExtensions;
    private final int availableProcessors;

    private static final int INIT_VALUE = -1;
    private OCLDeviceContextInterface deviceContex;

    public VirtualOCLDevice(VirtualDeviceDescriptor info) {
        this.name = info.getDeviceName();
        this.index = 0;
        this.deviceEndianLittle = true;
        this.maxComputeUnits = INIT_VALUE;
        this.maxAllocationSize = INIT_VALUE;
        this.globalMemorySize = INIT_VALUE;
        this.localMemorySize = INIT_VALUE;
        this.maxWorkItemDimensions = INIT_VALUE;
        this.maxWorkGroupSize = null;
        this.maxConstantBufferSize = INIT_VALUE;
        this.deviceMaxClockFrequency = INIT_VALUE;
        this.deviceAddressBits = info.getDeviceAddressBits();
        this.doubleFPConfig = info.getDoubleFPSupport();
        this.singleFPConfig = INIT_VALUE;
        this.maxWorkItemSizes = info.getMaxWorkItemSizes();
        this.deviceType = info.deviceType();
        this.deviceExtensions = info.getDeviceExtensions();
        this.availableProcessors = info.getAvailableProcessors();
    }

    public long getId() {
        return -1;
    }

    public int getIndex() {
        return index;
    }

    public OCLDeviceType getDeviceType() {
        return deviceType;
    }

    public boolean isDeviceAvailable() {
        return true;
    }

    @Override
    public String getDeviceName() {
        return name;
    }

    public String getDeviceVendor() {
        return "DummyVendor";
    }

    @Override
    public String getDriverVersion() {
        return "DummyDriverVersion";
    }

    public String getDeviceVersion() {
        return "DummyDeviceVersion";
    }

    public String getDeviceOpenCLCVersion() {
        return "DummyOpenCLCVersion";
    }

    public String getDeviceExtensions() {
        return deviceExtensions;
    }

    @Override
    public int getDeviceMaxComputeUnits() {
        return maxComputeUnits;
    }

    @Override
    public int getDeviceMaxClockFrequency() {
        return deviceMaxClockFrequency;
    }

    @Override
    public long getDeviceMaxAllocationSize() {
        return maxAllocationSize;
    }

    @Override
    public long getDeviceGlobalMemorySize() {
        return globalMemorySize;
    }

    @Override
    public long getDeviceLocalMemorySize() {
        return localMemorySize;
    }

    public int getDeviceMaxWorkItemDimensions() {
        return maxWorkItemDimensions;
    }

    @Override
    public long[] getDeviceMaxWorkItemSizes() {
        return maxWorkItemSizes;
    }

    public long[] getDeviceMaxWorkGroupSize() {
        return maxWorkGroupSize;
    }

    @Override
    public int getMaxThreadsPerBlock() {
        return (int) maxWorkGroupSize[0];
    }

    @Override
    public long getDeviceMaxConstantBufferSize() {
        return maxConstantBufferSize;
    }

    public boolean isDeviceDoubleFPSupported() {
        return doubleFPConfig;
    }

    public long getDeviceSingleFPConfig() {
        return singleFPConfig;
    }

    public int getDeviceAddressBits() {
        return deviceAddressBits;
    }

    public boolean hasDeviceUnifiedMemory() {
        return false;
    }

    @Override
    public boolean isLittleEndian() {
        return deviceEndianLittle;
    }

    @Override
    public OCLDeviceContextInterface getDeviceContext() {
        return this.deviceContex;
    }

    @Override
    public void setDeviceContext(OCLDeviceContextInterface deviceContext) {
        this.deviceContex = deviceContext;
    }

    @Override
    public int deviceVersion() {
        return 0;
    }

    public int getWordSize() {
        return getDeviceAddressBits() >> 3;
    }

    public ByteOrder getByteOrder() {
        return isLittleEndian() ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("id=0x%x, deviceName=%s, type=%s, available=%s", -1, getDeviceName(), getDeviceType().toString(), isDeviceAvailable()));
        return sb.toString();
    }

    @Override
    public String getDeviceInfo() {
        return "DummyDeviceInfo";
    }

    public String getVersion() {
        unimplemented();
        return null;
    }

    public int getAvailableProcessors() {
        return availableProcessors;
    }
}
