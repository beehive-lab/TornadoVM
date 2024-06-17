/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021-2022, 2024, APT Group, Department of Computer Science,
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

import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.drivers.opencl.OCLTargetDevice;
import uk.ac.manchester.tornado.drivers.opencl.OpenCL;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLDeviceType;

public class SPIRVOCLDevice extends SPIRVDevice {

    // Holds a reference to the OpenCL device implementation from the OpenCL
    // backend. It reuses the JNI low level code from the OpenCL Backend.
    private final OCLTargetDevice oclDevice;

    public SPIRVOCLDevice(int platformIndex, int deviceIndex, OCLTargetDevice device) {
        super(platformIndex, deviceIndex);
        this.oclDevice = device;
    }

    public int deviceVersion() {
        return oclDevice.deviceVersion();
    }

    public long getId() {
        return oclDevice.getDevicePointer();
    }

    @Override
    public boolean isDeviceDoubleFPSupported() {
        return oclDevice.isDeviceDoubleFPSupported();
    }

    @Override
    public String getDeviceExtensions() {
        return oclDevice.getDeviceExtensions();
    }

    @Override
    public ByteOrder getByteOrder() {
        return oclDevice.getByteOrder();
    }

    @Override
    public String getName() {
        return "SPIRV OCL - " + oclDevice.getDeviceName();
    }

    @Override
    public OCLTargetDevice getDeviceRuntime() {
        return oclDevice;
    }

    @Override
    public String getDeviceName() {
        return oclDevice.getDeviceName();
    }

    @Override
    public long getDeviceGlobalMemorySize() {
        return oclDevice.getDeviceGlobalMemorySize();
    }

    @Override
    public long getDeviceLocalMemorySize() {
        return oclDevice.getDeviceLocalMemorySize();
    }

    @Override
    public int getDeviceMaxComputeUnits() {
        return oclDevice.getDeviceMaxComputeUnits();
    }

    @Override
    public long[] getDeviceMaxWorkItemSizes() {
        return oclDevice.getDeviceMaxWorkItemSizes();
    }

    @Override
    public long[] getDeviceMaxWorkGroupSize() {
        return oclDevice.getDeviceMaxWorkGroupSize();
    }

    @Override
    public int getMaxThreadsPerBlock() {
        return oclDevice.getMaxThreadsPerBlock();
    }

    @Override
    public int getDeviceMaxClockFrequency() {
        return oclDevice.getDeviceMaxClockFrequency();
    }

    @Override
    public long getDeviceMaxConstantBufferSize() {
        return oclDevice.getDeviceMaxConstantBufferSize();
    }

    @Override
    public long getDeviceMaxAllocationSize() {
        return oclDevice.getDeviceMaxAllocationSize();
    }

    @Override
    public String getDeviceInfo() {
        throw new UnsupportedOperationException("");
    }

    @Override
    public long[] getDeviceMaxWorkgroupDimensions() {
        return oclDevice.getDeviceMaxWorkItemSizes();
    }

    @Override
    public String getDeviceOpenCLCVersion() {
        return oclDevice.getDeviceOpenCLCVersion();
    }

    @Override
    public long getMaxAllocMemory() {
        return oclDevice.getDeviceMaxAllocationSize();
    }

    @Override
    public TornadoDeviceType getTornadoDeviceType() {
        OCLDeviceType type = oclDevice.getDeviceType();
        return switch (type) {
            case CL_DEVICE_TYPE_CPU -> TornadoDeviceType.CPU;
            case CL_DEVICE_TYPE_GPU -> TornadoDeviceType.GPU;
            case CL_DEVICE_TYPE_ACCELERATOR -> TornadoDeviceType.FPGA;
            case CL_DEVICE_TYPE_ALL -> TornadoDeviceType.DEFAULT;
            default -> null;
        };
    }

    @Override
    public String getPlatformName() {
        return OpenCL.getPlatform(getPlatformIndex()).getName();
    }

    @Override
    public boolean isSPIRVSupported() {
        return oclDevice.isSPIRVSupported();
    }

    @Override
    public SPIRVRuntimeType getSPIRVRuntime() {
        return SPIRVRuntimeType.OPENCL;
    }

}
