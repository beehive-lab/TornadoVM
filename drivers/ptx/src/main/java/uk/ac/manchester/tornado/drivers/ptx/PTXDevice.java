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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.ptx;

import java.nio.ByteOrder;

import uk.ac.manchester.tornado.api.TornadoTargetDevice;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.drivers.ptx.enums.PTXDeviceAttribute;

public class PTXDevice implements TornadoTargetDevice {

    private final String name;
    private final long[] maxGridSizes;
    private final PTXContext context;
    private final PTXVersion ptxVersion;
    private final long[] maxWorkItemSizes;
    private final TargetArchitecture targetArchitecture;
    private final CUDAComputeCapability computeCapability;

    private final int deviceIndex;
    private final long cuDevice;
    private final int maxFrequency;
    private final int numComputeUnits;
    private final long localMemorySize;
    private final long totalDeviceMemory;
    private final long constantBufferSize;
    private final long maxAllocationSize;
    private int maxThreadsPerBlock;

    public PTXDevice(int deviceIndex) {
        this.deviceIndex = deviceIndex;
        cuDevice = cuDeviceGet(deviceIndex);
        name = cuDeviceGetName(cuDevice);
        constantBufferSize = cuDeviceGetAttribute(cuDevice, PTXDeviceAttribute.TOTAL_CONSTANT_MEMORY.value());
        totalDeviceMemory = cuDeviceTotalMem(cuDevice);
        localMemorySize = cuDeviceGetAttribute(cuDevice, PTXDeviceAttribute.MAX_SHARED_MEMORY_PER_BLOCK.value());
        numComputeUnits = cuDeviceGetAttribute(cuDevice, PTXDeviceAttribute.MULTIPROCESSOR_COUNT.value());
        maxFrequency = cuDeviceGetAttribute(cuDevice, PTXDeviceAttribute.CLOCK_RATE.value());
        maxWorkItemSizes = initMaxWorkItemSizes();
        maxGridSizes = initMaxGridSizes();
        maxThreadsPerBlock = cuDeviceGetAttribute(cuDevice, PTXDeviceAttribute.MAX_THREADS_PER_BLOCK.value());
        ptxVersion = CUDAVersion.getMaxPTXVersion(cuDriverGetVersion());
        computeCapability = initComputeCapability();
        targetArchitecture = ptxVersion.getArchitecture(computeCapability);

        // A PTXContext for the CUDevice must be created first before cuMemGetInfo
        // is invoked.
        context = new PTXContext(this);
        maxAllocationSize = cuMemGetInfo();
    }

    private static native long cuDeviceGet(int deviceId);

    private static native String cuDeviceGetName(long cuDevice);

    private static native int cuDeviceGetAttribute(long cuDevice, int attribute);

    private static native long cuDeviceTotalMem(long cuDevice);

    private static native long cuMemGetInfo();

    private static native int cuDriverGetVersion();

    @Override
    public String getDeviceName() {
        return name;
    }

    private CUDAComputeCapability initComputeCapability() {
        int major = cuDeviceGetAttribute(cuDevice, PTXDeviceAttribute.COMPUTE_CAPABILITY_MAJOR.value());
        int minor = cuDeviceGetAttribute(cuDevice, PTXDeviceAttribute.COMPUTE_CAPABILITY_MINOR.value());
        return new CUDAComputeCapability(major, minor);
    }

    public CUDAComputeCapability getComputeCapability() {
        return computeCapability;
    }

    @Override
    public long getDeviceGlobalMemorySize() {
        return totalDeviceMemory;
    }

    @Override
    public long getDeviceLocalMemorySize() {
        return localMemorySize;
    }

    @Override
    public int getDeviceMaxComputeUnits() {
        return numComputeUnits;
    }

    private long[] initMaxWorkItemSizes() {
        long[] maxWorkItemSizes = new long[3];
        maxWorkItemSizes[0] = cuDeviceGetAttribute(cuDevice, PTXDeviceAttribute.MAX_BLOCK_DIM_X.value());
        maxWorkItemSizes[1] = cuDeviceGetAttribute(cuDevice, PTXDeviceAttribute.MAX_BLOCK_DIM_Y.value());
        maxWorkItemSizes[2] = cuDeviceGetAttribute(cuDevice, PTXDeviceAttribute.MAX_BLOCK_DIM_Z.value());
        return maxWorkItemSizes;
    }

    @Override
    public long[] getDeviceMaxWorkItemSizes() {
        return maxWorkItemSizes;
    }

    private long[] initMaxGridSizes() {
        long[] maxGridSizes = new long[3];
        maxGridSizes[0] = cuDeviceGetAttribute(cuDevice, PTXDeviceAttribute.MAX_GRID_DIM_X.value());
        maxGridSizes[1] = cuDeviceGetAttribute(cuDevice, PTXDeviceAttribute.MAX_GRID_DIM_Y.value());
        maxGridSizes[2] = cuDeviceGetAttribute(cuDevice, PTXDeviceAttribute.MAX_GRID_DIM_Z.value());
        return maxGridSizes;
    }

    @Override
    public long[] getDeviceMaxWorkGroupSize() {
        return maxGridSizes;
    }

    @Override
    public int getMaxThreadsPerBlock() {
        return maxThreadsPerBlock;
    }

    @Override
    public int getDeviceMaxClockFrequency() {
        return maxFrequency;
    }

    @Override
    public long getDeviceMaxConstantBufferSize() {
        return constantBufferSize;
    }

    @Override
    public long getDeviceMaxAllocationSize() {
        return maxAllocationSize;
    }

    @Override
    public String getDeviceInfo() {
        return getDeviceName();
    }

    public ByteOrder getByteOrder() {
        return ByteOrder.LITTLE_ENDIAN;
    }

    public int getDeviceIndex() {
        return deviceIndex;
    }

    public PTXContext getPTXContext() {
        return context;
    }

    public TornadoDeviceType getDeviceType() {
        return TornadoDeviceType.GPU;
    }

    public TargetArchitecture getTargetArchitecture() {
        return targetArchitecture;
    }

    public String getTargetPTXVersion() {
        return ptxVersion.toString();
    }

    public long getCuDevice() {
        return cuDevice;
    }
}
