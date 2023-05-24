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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.opencl;

import static uk.ac.manchester.tornado.drivers.opencl.OpenCL.CL_TRUE;
import static uk.ac.manchester.tornado.runtime.common.RuntimeUtilities.humanReadableByteCount;
import static uk.ac.manchester.tornado.runtime.common.RuntimeUtilities.humanReadableFreq;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import uk.ac.manchester.tornado.drivers.opencl.enums.OCLDeviceInfo;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLDeviceType;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLLocalMemType;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;

public class OCLDevice implements OCLTargetDevice {

    private final long id;
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
    private int deviceMemoryBaseAligment;
    private String version;
    private OCLDeviceType deviceType;

    private static final int INIT_VALUE = -1;
    private static final int MAX_BUFFER_SIZE = 8192;
    private String deviceVendorName;
    private String driverVersion;
    private String deviceVersion;
    private String deviceExtensions;
    private int deviceMaxClockFrequency;
    private int deviceAddressBits;
    private OCLLocalMemType localMemoryType;
    private int deviceVendorID;
    private OCLDeviceContextInterface deviceContext;

    public OCLDevice(int index, long id) {
        this.index = index;
        this.id = id;
        this.buffer = ByteBuffer.allocate(MAX_BUFFER_SIZE);
        this.buffer.order(OpenCL.BYTE_ORDER);
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
        this.deviceMemoryBaseAligment = INIT_VALUE;
        this.maxWorkItemSizes = null;
        this.name = null;
        this.version = null;
        this.deviceType = OCLDeviceType.Unknown;
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
        getDeviceVersion();
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

    public long getId() {
        return id;
    }

    public int getIndex() {
        return index;
    }

    public OCLDeviceType getDeviceType() {
        if (deviceType != OCLDeviceType.Unknown) {
            return deviceType;
        }
        queryOpenCLAPI(OCLDeviceInfo.CL_DEVICE_TYPE.getValue());
        long type = buffer.getLong();
        deviceType = OCLDeviceType.toDeviceType(type);
        return deviceType;
    }

    public int getDeviceVendorId() {
        if (deviceVendorID == INIT_VALUE) {
            queryOpenCLAPI(OCLDeviceInfo.CL_DEVICE_VENDOR_ID.getValue());
            deviceVendorID = buffer.getInt();
        }
        return deviceVendorID;
    }

    public int getDeviceMemoryBaseAlignment() {
        if (deviceMemoryBaseAligment != INIT_VALUE) {
            return deviceMemoryBaseAligment;
        }
        queryOpenCLAPI(OCLDeviceInfo.CL_DEVICE_MEM_BASE_ADDR_ALIGN.getValue());
        deviceMemoryBaseAligment = buffer.getInt();
        return deviceMemoryBaseAligment;
    }

    public boolean isDeviceAvailable() {
        queryOpenCLAPI(OCLDeviceInfo.CL_DEVICE_AVAILABLE.getValue());
        return buffer.getInt() == 1;
    }

    @Override
    public String getDeviceName() {
        if (name == null) {
            queryOpenCLAPI(OCLDeviceInfo.CL_DEVICE_NAME.getValue());
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
        queryOpenCLAPI(OCLDeviceInfo.CL_DEVICE_VENDOR.getValue());
        try {
            deviceVendorName = new String(buffer.array(), "ASCII").trim();
        } catch (UnsupportedEncodingException e) {
            deviceVendorName = "unknown";
        }
        return deviceVendorName;
    }

    public String getDriverVersion() {
        if (driverVersion != null) {
            return driverVersion;
        }
        queryOpenCLAPI(OCLDeviceInfo.CL_DRIVER_VERSION.getValue());
        try {
            driverVersion = new String(buffer.array(), "ASCII").trim();
        } catch (UnsupportedEncodingException e) {
            driverVersion = "unknown";
        }
        return driverVersion;
    }

    public String getDeviceVersion() {
        if (deviceVersion == null) {
            queryOpenCLAPI(OCLDeviceInfo.CL_DEVICE_VERSION.getValue());
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
            queryOpenCLAPI(OCLDeviceInfo.CL_DEVICE_OPENCL_C_VERSION.getValue());
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
        queryOpenCLAPI(OCLDeviceInfo.CL_DEVICE_EXTENSIONS.getValue());
        try {
            deviceExtensions = new String(buffer.array(), "ASCII").trim();
        } catch (UnsupportedEncodingException e) {
            deviceExtensions = "unknown";
        }
        return deviceExtensions;
    }

    @Override
    public int getDeviceMaxComputeUnits() {
        if (maxComputeUnits != -1) {
            return maxComputeUnits;
        }
        queryOpenCLAPI(OCLDeviceInfo.CL_DEVICE_MAX_COMPUTE_UNITS.getValue());
        maxComputeUnits = buffer.getInt();
        return maxComputeUnits;
    }

    @Override
    public int getDeviceMaxClockFrequency() {
        if (deviceMaxClockFrequency != INIT_VALUE) {
            return deviceMaxClockFrequency;
        }
        queryOpenCLAPI(OCLDeviceInfo.CL_DEVICE_MAX_CLOCK_FREQUENCY.getValue());
        deviceMaxClockFrequency = buffer.getInt();
        return deviceMaxClockFrequency;
    }

    @Override
    public long getDeviceMaxAllocationSize() {
        if (maxAllocationSize != -1) {
            return maxAllocationSize;
        }
        queryOpenCLAPI(OCLDeviceInfo.CL_DEVICE_MAX_MEM_ALLOC_SIZE.getValue());
        maxAllocationSize = buffer.getLong();
        return maxAllocationSize;
    }

    @Override
    public long getDeviceGlobalMemorySize() {
        if (globalMemorySize != -1) {
            return globalMemorySize;
        }
        queryOpenCLAPI(OCLDeviceInfo.CL_DEVICE_GLOBAL_MEM_SIZE.getValue());
        globalMemorySize = buffer.getLong();
        return globalMemorySize;
    }

    @Override
    public long getDeviceLocalMemorySize() {
        if (localMemorySize != -1) {
            return localMemorySize;
        }
        queryOpenCLAPI(OCLDeviceInfo.CL_DEVICE_LOCAL_MEM_SIZE.getValue());
        localMemorySize = buffer.getLong();
        return localMemorySize;
    }

    public int getDeviceMaxWorkItemDimensions() {
        if (maxWorkItemDimensions != -1) {
            return maxWorkItemDimensions;
        }
        queryOpenCLAPI(OCLDeviceInfo.CL_DEVICE_MAX_WORK_ITEM_DIMENSIONS.getValue());
        maxWorkItemDimensions = buffer.getInt();
        return maxWorkItemDimensions;
    }

    @Override
    public long[] getDeviceMaxWorkItemSizes() {

        if (maxWorkItemSizes != null) {
            return maxWorkItemSizes;
        }

        final int elements = getDeviceMaxWorkItemDimensions();

        queryOpenCLAPI(OCLDeviceInfo.CL_DEVICE_MAX_WORK_ITEM_SIZES.getValue());
        buffer.rewind();
        maxWorkItemSizes = new long[elements];
        for (int i = 0; i < elements; i++) {
            maxWorkItemSizes[i] = buffer.getLong();
        }
        return maxWorkItemSizes;
    }

    private int getDeviceMaxWorkGroupSize_0() {
        queryOpenCLAPI(OCLDeviceInfo.CL_DEVICE_MAX_WORK_GROUP_SIZE.getValue());
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
        queryOpenCLAPI(OCLDeviceInfo.CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE.getValue());
        maxConstantBufferSize = buffer.getLong();
        return maxConstantBufferSize;
    }

    private long getDoubleFPConfig() {
        queryOpenCLAPI(OCLDeviceInfo.CL_DEVICE_DOUBLE_FP_CONFIG.getValue());
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
        queryOpenCLAPI(OCLDeviceInfo.CL_DEVICE_SINGLE_FP_CONFIG.getValue());
        singleFPConfig = buffer.getLong();
        return singleFPConfig;
    }

    public int getDeviceAddressBits() {
        if (deviceAddressBits != INIT_VALUE) {
            return deviceAddressBits;
        }
        queryOpenCLAPI(OCLDeviceInfo.CL_DEVICE_ADDRESS_BITS.getValue());
        deviceAddressBits = buffer.getInt();
        return deviceAddressBits;
    }

    public boolean hasDeviceUnifiedMemory() {
        queryOpenCLAPI(OCLDeviceInfo.CL_DEVICE_HOST_UNIFIED_MEMORY.getValue());
        return buffer.getInt() == OpenCL.CL_TRUE;
    }

    public OCLLocalMemType getDeviceLocalMemoryType() {
        if (localMemoryType != null) {
            return localMemoryType;
        }
        queryOpenCLAPI(OCLDeviceInfo.CL_DEVICE_LOCAL_MEM_TYPE.getValue());
        localMemoryType = OCLLocalMemType.toLocalMemType(buffer.getInt());
        return localMemoryType;
    }

    private int getDeviceEndianLittle() {
        queryOpenCLAPI(OCLDeviceInfo.CL_DEVICE_ENDIAN_LITTLE.getValue());
        deviceEndianLittle = buffer.getInt();
        return deviceEndianLittle;
    }

    @Override
    public boolean isLittleEndian() {
        if (deviceEndianLittle != -1) {
            return deviceEndianLittle == CL_TRUE;
        }
        getDeviceEndianLittle();
        return deviceEndianLittle == CL_TRUE;
    }

    @Override
    public OCLDeviceContextInterface getDeviceContext() {
        return this.deviceContext;
    }

    @Override
    public void setDeviceContext(OCLDeviceContextInterface deviceContext) {
        this.deviceContext = deviceContext;
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
        queryOpenCLAPI(OCLDeviceInfo.CL_DEVICE_VERSION.getValue());
        try {
            version = new String(buffer.array(), "ASCII").trim();
        } catch (UnsupportedEncodingException e) {
            version = "OpenCL 0.0";
        }
        return version;
    }

    private void queryOpenCLAPI(int value) {
        Arrays.fill(buffer.array(), (byte) 0);
        buffer.clear();
        clGetDeviceInfo(id, value, buffer.array());
    }

    @Override
    public String toString() {
        return String.format("id=0x%x, deviceName=%s, type=%s, available=%s", id, getDeviceName(), getDeviceType().toString(), isDeviceAvailable());
    }

    @Override
    public String getDeviceInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("id=0x%x, deviceName=%s, type=%s, available=%s\n", id, getDeviceName(), getDeviceType().toString(), isDeviceAvailable()));
        sb.append(String.format("Freq=%s, max compute units=%d\n", humanReadableFreq(getDeviceMaxClockFrequency()), getDeviceMaxComputeUnits()));
        sb.append(String.format("Global mem. size=%s, local mem. size=%s\n", RuntimeUtilities.humanReadableByteCount(getDeviceGlobalMemorySize(), false),
                humanReadableByteCount(getDeviceLocalMemorySize(), false)));
        sb.append(String.format("Extensions:\n"));
        for (String extension : getDeviceExtensions().split(" ")) {
            sb.append("\t" + extension + "\n");
        }
        sb.append(String.format("Unified memory   : %s\n", hasDeviceUnifiedMemory()));
        sb.append(String.format("Device vendor    : %s\n", getDeviceVendor()));
        sb.append(String.format("Device version   : %s\n", getDeviceVersion()));
        sb.append(String.format("Driver version   : %s\n", getDriverVersion()));
        sb.append(String.format("OpenCL C version : %s\n", getDeviceOpenCLCVersion()));
        sb.append(String.format("Endianness       : %s\n", isLittleEndian() ? "little" : "big"));
        sb.append(String.format("Address size     : %d\n", getDeviceAddressBits()));
        sb.append(String.format("Single fp config : %b\n", getDeviceSingleFPConfig()));
        sb.append(String.format("Double fp config : %b\n", isDeviceDoubleFPSupported()));
        return sb.toString();
    }
}
