/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020-2023, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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

import static uk.ac.manchester.tornado.drivers.cuda.CUDADriver.CL_TRUE;
import static uk.ac.manchester.tornado.runtime.common.RuntimeUtilities.humanReadableByteCount;
import static uk.ac.manchester.tornado.runtime.common.RuntimeUtilities.humanReadableFreq;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import uk.ac.manchester.tornado.drivers.cuda.enums.CUDADeviceInfo;
import uk.ac.manchester.tornado.drivers.cuda.enums.CUDADeviceType;
import uk.ac.manchester.tornado.drivers.cuda.enums.CUDALocalMemType;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;

public class CUDADevice implements CUDATargetDevice {

    private static final int INIT_VALUE = -1;
    private static final int MAX_BUFFER_SIZE = 8192;

    private final long devicePtr;
    private final int index;

    private final ByteBuffer buffer;
    private String name;
    private int deviceEndianLittle;
    private String openCLVersion;
    private int maxComputeUnits;
    private long maxAllocationSize;
    private long globalMemorySize;
    private long localMemorySize;
    private int maxWorkItemDimensions;
    private long[] maxWorkItemSizes;
    private int maxWorkGroupSize;
    private long maxConstantBufferSize;
    private long doubleFPConfig;
    private long singleFPConfig;
    private int deviceMemoryBaseAlignment;
    private String version;
    private CUDADeviceType deviceType;

    private String deviceVendorName;
    private String driverVersion;
    private String deviceVersion;
    private String deviceExtensions;
    private int deviceMaxClockFrequency;
    private int deviceAddressBits;
    private CUDALocalMemType localMemoryType;
    private int deviceVendorID;
    private CUDADeviceContextInterface deviceContext;
    private int asyncEngineCount = INIT_VALUE;
    private int concurrentKernels = INIT_VALUE;

    public CUDADevice(int index, long devicePointer) {
        this.index = index;
        this.devicePtr = devicePointer;
        this.buffer = ByteBuffer.allocate(MAX_BUFFER_SIZE);
        this.buffer.order(CUDADriver.BYTE_ORDER);
        initialValues();
        obtainDeviceProperties();
    }

    private void initialValues() {
        this.openCLVersion = null;
        this.deviceEndianLittle = INIT_VALUE;
        this.maxComputeUnits = INIT_VALUE;
        this.maxAllocationSize = INIT_VALUE;
        this.globalMemorySize = INIT_VALUE;
        this.localMemorySize = INIT_VALUE;
        this.maxWorkItemDimensions = INIT_VALUE;
        this.maxWorkGroupSize = INIT_VALUE;
        this.maxConstantBufferSize = INIT_VALUE;
        this.doubleFPConfig = INIT_VALUE;
        this.singleFPConfig = INIT_VALUE;
        this.deviceMemoryBaseAlignment = INIT_VALUE;
        this.maxWorkItemSizes = null;
        this.name = null;
        this.version = null;
        this.deviceType = CUDADeviceType.Unknown;
        this.deviceVendorName = null;
        this.driverVersion = null;
        this.deviceVersion = null;
        this.deviceExtensions = null;
        this.deviceMaxClockFrequency = INIT_VALUE;
        this.deviceAddressBits = INIT_VALUE;
        this.localMemoryType = null;
        this.deviceVendorID = INIT_VALUE;
    }

    private void obtainDeviceProperties() {
        getDeviceOpenCLCVersion();
        getDeviceEndianLittle();
        getDeviceMaxComputeUnits();
        getDeviceMaxAllocationSize();
        getDeviceGlobalMemorySize();
        getDeviceLocalMemorySize();
        getDeviceMaxWorkItemDimensions();
        getDeviceMaxWorkGroupSize_0();
        getDeviceMaxConstantBufferSize();
        getDoubleFPConfig();
        getDeviceSingleFPConfig();
        getDeviceMemoryBaseAlignment();
        getDeviceMaxWorkItemSizes();
        getDeviceMaxWorkGroupSize();
        getDeviceName();
        getDeviceType();
        getDeviceVendor();
        getDriverVersion();
        getDeviceVersion();
        getDeviceExtensions();
        getDeviceMaxClockFrequency();
        getDeviceAddressBits();
        getDeviceLocalMemoryType();
        getDeviceVendorId();
    }

    static native void clGetDeviceInfo(long id, int info, byte[] buffer);

    public long getDevicePointer() {
        return devicePtr;
    }

    public int getIndex() {
        return index;
    }

    public CUDADeviceType getDeviceType() {
        if (deviceType != CUDADeviceType.Unknown) {
            return deviceType;
        }
        queryOpenCLAPI(CUDADeviceInfo.CL_DEVICE_TYPE.getValue());
        long type = buffer.getLong();
        deviceType = CUDADeviceType.toDeviceType(type);
        return deviceType;
    }

    public int getDeviceVendorId() {
        if (deviceVendorID == INIT_VALUE) {
            queryOpenCLAPI(CUDADeviceInfo.CL_DEVICE_VENDOR_ID.getValue());
            deviceVendorID = buffer.getInt();
        }
        return deviceVendorID;
    }

    public int getDeviceMemoryBaseAlignment() {
        if (deviceMemoryBaseAlignment != INIT_VALUE) {
            return deviceMemoryBaseAlignment;
        }
        queryOpenCLAPI(CUDADeviceInfo.CL_DEVICE_MEM_BASE_ADDR_ALIGN.getValue());
        deviceMemoryBaseAlignment = buffer.getInt();
        return deviceMemoryBaseAlignment;
    }

    public boolean isDeviceAvailable() {
        queryOpenCLAPI(CUDADeviceInfo.CL_DEVICE_AVAILABLE.getValue());
        return (buffer.get() == 1);
    }

    @Override
    public String getDeviceName() {
        if (name == null) {
            queryOpenCLAPI(CUDADeviceInfo.CL_DEVICE_NAME.getValue());
            try {
                name = new String(buffer.array(), "ASCII").trim();
            } catch (UnsupportedEncodingException e) {
                name = "unknown";
            }
        }

        return name;
    }

    public String getDeviceVendor() {
        if (deviceVendorName != null) {
            return deviceVendorName;
        }
        queryOpenCLAPI(CUDADeviceInfo.CL_DEVICE_VENDOR.getValue());
        try {
            deviceVendorName = new String(buffer.array(), "ASCII").trim();
        } catch (UnsupportedEncodingException e) {
            deviceVendorName = "unknown";
        }
        return deviceVendorName;
    }

    @Override
    public String getDriverVersion() {
        if (driverVersion != null) {
            return driverVersion;
        }
        queryOpenCLAPI(CUDADeviceInfo.CL_DRIVER_VERSION.getValue());
        try {
            driverVersion = new String(buffer.array(), "ASCII").trim();
        } catch (UnsupportedEncodingException e) {
            driverVersion = "unknown";
        }
        return driverVersion;
    }

    public String getDeviceVersion() {
        if (deviceVersion == null) {
            queryOpenCLAPI(CUDADeviceInfo.CL_DEVICE_VERSION.getValue());
            try {
                deviceVersion = new String(buffer.array(), "ASCII").trim();
            } catch (UnsupportedEncodingException e) {
                deviceVersion = "unknown";
            }
        }
        return deviceVersion;
    }

    public String getDeviceOpenCLCVersion() {
        if (openCLVersion == null) {
            queryOpenCLAPI(CUDADeviceInfo.CL_DEVICE_OPENCL_C_VERSION.getValue());
            try {
                openCLVersion = new String(buffer.array(), "ASCII").trim();
            } catch (UnsupportedEncodingException e) {
                openCLVersion = "unknown";
            }
        }
        return openCLVersion;
    }

    public String getDeviceExtensions() {
        if (deviceExtensions != null) {
            return deviceExtensions;
        }
        queryOpenCLAPI(CUDADeviceInfo.CL_DEVICE_EXTENSIONS.getValue());
        try {
            deviceExtensions = new String(buffer.array(), "ASCII").trim();
        } catch (UnsupportedEncodingException e) {
            deviceExtensions = "unknown";
        }
        return deviceExtensions;
    }

    public int getComputeCapabilityMajor() { return getComputeCapability()[0]; }
    public int getComputeCapabilityMinor() { return getComputeCapability()[1]; }

    public int[] getComputeCapability() {
        String v = getDeviceVersion();
        if (v == null || !v.startsWith("CUDA ")) return new int[]{0, 0};
        try {
            String[] parts = v.substring(5).trim().split("\\.");
            return new int[]{ Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) };
        } catch (Exception e) {
            return new int[]{0, 0};
        }
    }

    @Override
    public int getDeviceMaxComputeUnits() {
        if (maxComputeUnits != -1) {
            return maxComputeUnits;
        }
        queryOpenCLAPI(CUDADeviceInfo.CL_DEVICE_MAX_COMPUTE_UNITS.getValue());
        maxComputeUnits = buffer.getInt();
        return maxComputeUnits;
    }

    @Override
    public int getDeviceMaxClockFrequency() {
        if (deviceMaxClockFrequency != INIT_VALUE) {
            return deviceMaxClockFrequency;
        }
        queryOpenCLAPI(CUDADeviceInfo.CL_DEVICE_MAX_CLOCK_FREQUENCY.getValue());
        deviceMaxClockFrequency = buffer.getInt();
        return deviceMaxClockFrequency;
    }

    @Override
    public long getDeviceMaxAllocationSize() {
        if (maxAllocationSize != -1) {
            return maxAllocationSize;
        }
        queryOpenCLAPI(CUDADeviceInfo.CL_DEVICE_MAX_MEM_ALLOC_SIZE.getValue());
        maxAllocationSize = buffer.getLong();
        return maxAllocationSize;
    }

    @Override
    public long getDeviceGlobalMemorySize() {
        if (globalMemorySize != -1) {
            return globalMemorySize;
        }
        queryOpenCLAPI(CUDADeviceInfo.CL_DEVICE_GLOBAL_MEM_SIZE.getValue());
        globalMemorySize = buffer.getLong();
        return globalMemorySize;
    }

    @Override
    public long getDeviceLocalMemorySize() {
        if (localMemorySize != -1) {
            return localMemorySize;
        }
        queryOpenCLAPI(CUDADeviceInfo.CL_DEVICE_LOCAL_MEM_SIZE.getValue());
        localMemorySize = buffer.getLong();
        return localMemorySize;
    }

    public int getDeviceMaxWorkItemDimensions() {
        if (maxWorkItemDimensions != -1) {
            return maxWorkItemDimensions;
        }
        queryOpenCLAPI(CUDADeviceInfo.CL_DEVICE_MAX_WORK_ITEM_DIMENSIONS.getValue());
        maxWorkItemDimensions = buffer.getInt();
        return maxWorkItemDimensions;
    }

    @Override
    public long[] getDeviceMaxWorkItemSizes() {

        if (maxWorkItemSizes != null) {
            return maxWorkItemSizes;
        }

        final int elements = getDeviceMaxWorkItemDimensions();

        queryOpenCLAPI(CUDADeviceInfo.CL_DEVICE_MAX_WORK_ITEM_SIZES.getValue());
        buffer.rewind();
        maxWorkItemSizes = new long[elements];
        for (int i = 0; i < elements; i++) {
            maxWorkItemSizes[i] = buffer.getLong();
        }
        return maxWorkItemSizes;
    }

    private int getDeviceMaxWorkGroupSize_0() {
        queryOpenCLAPI(CUDADeviceInfo.CL_DEVICE_MAX_WORK_GROUP_SIZE.getValue());
        maxWorkGroupSize = buffer.getInt();
        return maxWorkGroupSize;
    }

    @Override
    public long[] getDeviceMaxWorkGroupSize() {
        if (maxWorkGroupSize != -1) {
            return new long[] { maxWorkGroupSize };
        }
        maxWorkGroupSize = getDeviceMaxWorkGroupSize_0();
        return new long[] { maxWorkGroupSize };
    }

    @Override
    public int getMaxThreadsPerBlock() {
        return maxWorkGroupSize;
    }

    @Override
    public long getDeviceMaxConstantBufferSize() {
        if (maxConstantBufferSize != -1) {
            return maxConstantBufferSize;
        }
        queryOpenCLAPI(CUDADeviceInfo.CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE.getValue());
        maxConstantBufferSize = buffer.getLong();
        return maxConstantBufferSize;
    }

    private long getDoubleFPConfig() {
        queryOpenCLAPI(CUDADeviceInfo.CL_DEVICE_DOUBLE_FP_CONFIG.getValue());
        return buffer.getLong();
    }

    public boolean isDeviceDoubleFPSupported() {
        if (doubleFPConfig != -1) {
            return doubleFPConfig != 0;
        }
        doubleFPConfig = getDoubleFPConfig();
        return doubleFPConfig != 0;
    }

    public long getDeviceSingleFPConfig() {
        if (singleFPConfig != -1) {
            return singleFPConfig;
        }
        queryOpenCLAPI(CUDADeviceInfo.CL_DEVICE_SINGLE_FP_CONFIG.getValue());
        singleFPConfig = buffer.getLong();
        return singleFPConfig;
    }

    public int getDeviceAddressBits() {
        if (deviceAddressBits != INIT_VALUE) {
            return deviceAddressBits;
        }
        queryOpenCLAPI(CUDADeviceInfo.CL_DEVICE_ADDRESS_BITS.getValue());
        deviceAddressBits = buffer.getInt();
        return deviceAddressBits;
    }

    public boolean hasDeviceUnifiedMemory() {
        queryOpenCLAPI(CUDADeviceInfo.CL_DEVICE_HOST_UNIFIED_MEMORY.getValue());
        return buffer.getInt() == CUDADriver.CL_TRUE;
    }

    public CUDALocalMemType getDeviceLocalMemoryType() {
        if (localMemoryType != null) {
            return localMemoryType;
        }
        queryOpenCLAPI(CUDADeviceInfo.CL_DEVICE_LOCAL_MEM_TYPE.getValue());
        localMemoryType = CUDALocalMemType.toLocalMemType(buffer.getInt());
        return localMemoryType;
    }

    private int getDeviceEndianLittle() {
        queryOpenCLAPI(CUDADeviceInfo.CL_DEVICE_ENDIAN_LITTLE.getValue());
        deviceEndianLittle = buffer.getInt();
        return deviceEndianLittle;
    }

    @Override
    public boolean isLittleEndian() {
        if (deviceEndianLittle != -1) {
            return deviceEndianLittle == CL_TRUE;
        }
        getDeviceEndianLittle();
        return (deviceEndianLittle == CL_TRUE);
    }

    @Override
    public CUDADeviceContextInterface getDeviceContext() {
        return this.deviceContext;
    }

    @Override
    public void setDeviceContext(CUDADeviceContextInterface deviceContext) {
        this.deviceContext = deviceContext;
    }

    @Override
    public int deviceVersion() {
        return Integer.parseInt(getVersion().split(" ")[1].replace(".", "")) * 10;
    }

    @Override
    public int getAsyncEngineCount() {
        if (asyncEngineCount == INIT_VALUE) {
            queryOpenCLAPI(CUDADeviceInfo.TORNADO_DEVICE_ASYNC_ENGINE_COUNT.getValue());
            asyncEngineCount = buffer.getInt();
        }
        return asyncEngineCount;
    }

    @Override
    public boolean supportsConcurrentKernels() {
        if (concurrentKernels == INIT_VALUE) {
            queryOpenCLAPI(CUDADeviceInfo.TORNADO_DEVICE_CONCURRENT_KERNELS.getValue());
            concurrentKernels = buffer.getInt();
        }
        return concurrentKernels != 0;
    }

    public int getWordSize() {
        return getDeviceAddressBits() >> 3;
    }

    public ByteOrder getByteOrder() {
        return isLittleEndian() ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
    }

    public String getVersion() {
        if (version != null) {
            return version;
        }
        queryOpenCLAPI(CUDADeviceInfo.CL_DEVICE_VERSION.getValue());
        try {
            version = new String(buffer.array(), "ASCII").trim();
        } catch (UnsupportedEncodingException e) {
            version = "CUDADriver 0.0";
        }
        return version;
    }

    private void queryOpenCLAPI(int value) {
        Arrays.fill(buffer.array(), (byte) 0);
        buffer.clear();
        clGetDeviceInfo(devicePtr, value, buffer.array());
    }

    @Override
    public String toString() {
        return String.format("id=0x%x, deviceName=%s, type=%s, available=%s", devicePtr, getDeviceName(), getDeviceType().toString(), isDeviceAvailable());
    }

    @Override
    public String getDeviceInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("id=0x%x, deviceName=%s, type=%s, available=%s\n", devicePtr, getDeviceName(), getDeviceType().toString(), isDeviceAvailable()));
        sb.append(String.format("Freq=%s, max compute units=%d\n", humanReadableFreq(getDeviceMaxClockFrequency()), getDeviceMaxComputeUnits()));
        sb.append(String.format("Global mem. size=%s, local mem. size=%s\n", RuntimeUtilities.humanReadableByteCount(getDeviceGlobalMemorySize(), false), humanReadableByteCount(
                getDeviceLocalMemorySize(), false)));
        sb.append(String.format("Extensions:\n"));
        for (String extension : getDeviceExtensions().split(" ")) {
            sb.append("\t" + extension + "\n");
        }
        sb.append(String.format("Unified memory   : %s\n", hasDeviceUnifiedMemory()));
        sb.append(String.format("Device vendor    : %s\n", getDeviceVendor()));
        sb.append(String.format("Device version   : %s\n", getDeviceVersion()));
        sb.append(String.format("Driver version   : %s\n", getDriverVersion()));
        sb.append(String.format("CUDADriver C version : %s\n", getDeviceOpenCLCVersion()));
        sb.append(String.format("Endianness       : %s\n", isLittleEndian() ? "little" : "big"));
        sb.append(String.format("Address size     : %d\n", getDeviceAddressBits()));
        sb.append(String.format("Single fp config : %b\n", getDeviceSingleFPConfig()));
        sb.append(String.format("Double fp config : %b\n", isDeviceDoubleFPSupported()));
        return sb.toString();
    }
}
