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
package uk.ac.manchester.tornado.drivers.metal;

import static uk.ac.manchester.tornado.drivers.metal.Metal.CL_TRUE;
import static uk.ac.manchester.tornado.runtime.common.RuntimeUtilities.humanReadableByteCount;
import static uk.ac.manchester.tornado.runtime.common.RuntimeUtilities.humanReadableFreq;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import uk.ac.manchester.tornado.drivers.metal.enums.MetalDeviceType;
import uk.ac.manchester.tornado.drivers.metal.enums.MetalLocalMemType;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;

public class MetalDevice implements MetalTargetDevice {

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
    private MetalDeviceType deviceType;

    private String deviceVendorName;
    private String driverVersion;
    private String deviceVersion;
    private String deviceExtensions;
    private int deviceMaxClockFrequency;
    private int deviceAddressBits;
    private MetalLocalMemType localMemoryType;
    private int deviceVendorID;
    private MetalDeviceContextInterface deviceContext;
    private float spirvVersion = SPIRV_VERSION_INIT;

    private static final int SPIRV_VERSION_INIT = -1;
    private static final int SPIRV_NOT_SUPPORTED = -2;
    private static final float SPIRV_SUPPPORTED = TornadoOptions.SPIRV_VERSION_SUPPORTED;

    public MetalDevice(int index, long devicePointer) {
        this.index = index;
        this.devicePtr = devicePointer;
        this.buffer = ByteBuffer.allocate(MAX_BUFFER_SIZE);
        this.buffer.order(Metal.BYTE_ORDER);
        initialValues();
        // obtainDeviceProperties();
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
        this.deviceType = MetalDeviceType.Unknown;
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
        getDeviceMetalCVersion();
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
    static native String mtGetDeviceName(long id);
    static native long mtGetDeviceGlobalMemorySize(long id);
    static native long mtGetDeviceLocalMemorySize(long id);
    static native int mtHasUnifiedMemory(long id);

    public long getDevicePointer() {
        return devicePtr;
    }

    public int getIndex() {
        return index;
    }

    public MetalDeviceType getDeviceType() {
        // always GPU for Metal, emulating CL semantics
        return MetalDeviceType.CL_DEVICE_TYPE_GPU;
    }

    public int getDeviceVendorId() {
        return 0x106B; // Apple PCI-SIG vendor ID, but irrelevant for Metal
    }

    public int getDeviceMemoryBaseAlignment() {
        // CL_DEVICE_MEM_BASE_ADDR_ALIGN = alignment in bits
        return 2048; // 256 bytes * 8 bits/byte
    }

    public boolean isDeviceAvailable() {
        return true;
    }

    @Override
    public String getDeviceName() {
        if (name != null) return name;
        name = mtGetDeviceName(devicePtr);
        return name;
    }

    public String getDeviceVendor() {
        return "Apple";
    }

    @Override
    public String getDriverVersion() {
        return System.getProperty("os.version");
    }

    public String getDeviceVersion() {
        return "Metal 3.0";
    }

    public String getDeviceMetalCVersion() {
        return "Metal Shading Language";
    }

    public String getDeviceExtensions() {
        return "";
    }

    @Override
    public int getDeviceMaxComputeUnits() {
        if (maxComputeUnits != INIT_VALUE) {
            return maxComputeUnits;
        }

        // Conservative default: assume a single compute unit to avoid over-optimistic scheduling
        maxComputeUnits = 1;
        return maxComputeUnits;
    }

    @Override
    public int getDeviceMaxClockFrequency() {
        if (deviceMaxClockFrequency != INIT_VALUE) {
            return deviceMaxClockFrequency;
        }

        // Conservative default in MHz: 1000 MHz (1 GHz)
        deviceMaxClockFrequency = 1000;
        return deviceMaxClockFrequency;
    }

    @Override
    public long getDeviceMaxAllocationSize() {
        if (maxAllocationSize != INIT_VALUE) {
            return maxAllocationSize;
        }

        // Try to base the maximum allocation size on the global memory size when available.
        long global = getDeviceGlobalMemorySize();
        if (global > 0) {
            // OpenCL often reports max allocation as 1/4 of global memory; use that as a conservative default
            maxAllocationSize = Math.max(1L, global / 4L);
            return maxAllocationSize;
        }

        // Fallback: use a conservative fixed size (1 MiB) if global memory is unavailable
        maxAllocationSize = 1L * 1024L * 1024L;
        return maxAllocationSize;
    }

    @Override
    @SuppressWarnings("deprecation")
    public long getDeviceGlobalMemorySize() {
        // Try native call first (returns 0 if not available)
        try {
            long size = mtGetDeviceGlobalMemorySize(devicePtr);
            if (size > 0) {
                globalMemorySize = size;
                return globalMemorySize;
            }
        } catch (UnsatisfiedLinkError e) {
            // native library not available, fall through to fallback
        }

        // Fallback: use total physical memory as an approximation
        try {
            com.sun.management.OperatingSystemMXBean os =
                    (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            long phys = os.getTotalPhysicalMemorySize();
            globalMemorySize = phys > 0 ? phys : Runtime.getRuntime().maxMemory();
        } catch (Throwable t) {
            // Last resort: JVM max memory
            globalMemorySize = Runtime.getRuntime().maxMemory();
        }

        return globalMemorySize;
    }

    @Override
    public long getDeviceLocalMemorySize() {
        if (localMemorySize != INIT_VALUE && localMemorySize != 0) {
            return localMemorySize;
        }

        // Try native call first (returns 0 if not available)
        try {
            long size = mtGetDeviceLocalMemorySize(devicePtr);
            if (size > 0) {
                localMemorySize = size;
                return localMemorySize;
            }
        } catch (UnsatisfiedLinkError e) {
            // native library not available, fall through to fallback
        }

        // Fallback: choose a conservative default for threadgroup/local memory.
        // Many GPUs provide 32KB or 64KB of threadgroup memory; use 32KB as a safe default.
        localMemorySize = 32L * 1024L;
        return localMemorySize;
    }

    public int getDeviceMaxWorkItemDimensions() {
        if (maxWorkItemDimensions != INIT_VALUE) {
            return maxWorkItemDimensions;
        }
        // Metal compute is 3D (threadgroups have width, height, depth)
        maxWorkItemDimensions = 3;
        return maxWorkItemDimensions;
    }

    @Override
    public long[] getDeviceMaxWorkItemSizes() {
        // Dummy value, conservative default
        maxWorkItemSizes = new long[] { 1024L, 1024L, 1024L };
        return maxWorkItemSizes;
    }

    private int getDeviceMaxWorkGroupSize_0() {
        if (maxWorkGroupSize != INIT_VALUE) {
            return maxWorkGroupSize;
        }

        // Conservative default: many GPUs allow 1024 or 512; choose 1024
        maxWorkGroupSize = 1024;
        return maxWorkGroupSize;
    }

    @Override
    public long[] getDeviceMaxWorkGroupSize() {
        if (maxWorkGroupSize != INIT_VALUE) {
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
        if (maxConstantBufferSize != INIT_VALUE) {
            return maxConstantBufferSize;
        }

        // Metal does not expose an OpenCL-style "max constant buffer size" directly.
        // Use a conservative default that matches common OpenCL defaults (64 KiB).
        maxConstantBufferSize = 64L * 1024L;
        return maxConstantBufferSize;
    }

    private long getDoubleFPConfig() {
        return 0; // not supported
    }

    public boolean isDeviceDoubleFPSupported() {
        return false;
    }

    private static final long FP_DENORM        = 1 << 0;
    private static final long FP_INF_NAN       = 1 << 1;
    private static final long FP_ROUND_TO_NEAREST = 1 << 2;
    private static final int FP_ROUND_TO_ZERO    = 1 << 3;
    private static final long FP_ROUND_TO_INF     = 1 << 4;
    private static final long FP_FMA          = 1 << 5;
    private static final long FP_CORRECTLY_ROUNDED_DIVIDE_SQRT = 1 << 6;

    private long getDeviceSingleFPConfig() {
        // Metal devices behave as IEEE 754 single-precision compliant
        // took bitfields from https://registry.khronos.org/OpenCL/sdk/3.0/docs/man/html/clGetDeviceInfo.html
        return FP_DENORM |
            FP_INF_NAN |
            FP_ROUND_TO_NEAREST |
            FP_FMA |
            FP_CORRECTLY_ROUNDED_DIVIDE_SQRT;
    }

    public int getDeviceAddressBits() {
        return 64;
    }

    public boolean hasDeviceUnifiedMemory() {
        try {
            int v = mtHasUnifiedMemory(devicePtr);
            return v == CL_TRUE;
        } catch (UnsatisfiedLinkError e) {
            // native not available -> conservative fallback: assume no unified memory
            return false;
        }
    }

    public MetalLocalMemType getDeviceLocalMemoryType() {
        if (localMemoryType != null) {
            return localMemoryType;
        }
        // Metal threadgroup memory behaves like OpenCL CL_LOCAL
        localMemoryType = MetalLocalMemType.CL_LOCAL;
        return localMemoryType;
    }

    private int getDeviceEndianLittle() {
        deviceEndianLittle = CL_TRUE;
        return deviceEndianLittle;
    }

    @Override
    public boolean isLittleEndian() {
        return true;
    }

    @Override
    public MetalDeviceContextInterface getDeviceContext() {
        return this.deviceContext;
    }

    @Override
    public void setDeviceContext(MetalDeviceContextInterface deviceContext) {
        this.deviceContext = deviceContext;
    }

    @Override
    public int deviceVersion() {
        return Integer.parseInt(getVersion().split(" ")[1].replace(".", "")) * 10;
    }

    @Override
    public boolean isSPIRVSupported() {
        // Metal does not use SPIR-V; it uses Metal Shading Language (MSL).
        return false;
    }

    public int getWordSize() {
        return getDeviceAddressBits() >> 3;
    }

    public ByteOrder getByteOrder() {
        return isLittleEndian() ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
    }

    public String getVersion() {
        return "Metal 3.0";
    }

    private void queryMetalAPI(int value) {
        throw TornadoInternalError.unimplementedMetal();
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
        sb.append(String.format("OpenCL C version : %s\n", getDeviceMetalCVersion()));
        sb.append(String.format("Endianness       : %s\n", isLittleEndian() ? "little" : "big"));
        sb.append(String.format("Address size     : %d\n", getDeviceAddressBits()));
        sb.append(String.format("Single fp config : %b\n", getDeviceSingleFPConfig()));
        sb.append(String.format("Double fp config : %b\n", isDeviceDoubleFPSupported()));
        return sb.toString();
    }
}
