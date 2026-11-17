/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020-2023, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.drivers.hip;

import static uk.ac.manchester.tornado.drivers.hip.HIPDriver.CL_TRUE;
import static uk.ac.manchester.tornado.runtime.common.RuntimeUtilities.humanReadableByteCount;
import static uk.ac.manchester.tornado.runtime.common.RuntimeUtilities.humanReadableFreq;

import java.io.UnsupportedEncodingException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import uk.ac.manchester.tornado.drivers.hip.enums.HIPDeviceInfo;
import uk.ac.manchester.tornado.drivers.hip.enums.HIPDeviceType;
import uk.ac.manchester.tornado.drivers.hip.enums.HIPLocalMemType;
import uk.ac.manchester.tornado.drivers.hip.ffm.HIPCompiler;
import uk.ac.manchester.tornado.drivers.hip.ffm.HIPDriverAPI;
import uk.ac.manchester.tornado.drivers.hip.ffm.HIPHandles;
import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;

public class HIPDevice implements HIPTargetDevice {

    private static final int INIT_VALUE = -1;
    private static final int MAX_BUFFER_SIZE = 8192;
    private static final int DEVICE_NAME_BYTES = 256;
    private static final int AMD_PCI_VENDOR_ID = 0x1002;
    /** CL_FP_FMA, used only as a non-zero "supported" marker for the fp-config queries. */
    private static final long CL_FP_NONZERO = 0x20L;

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
    private HIPDeviceType deviceType;

    private String deviceVendorName;
    private String driverVersion;
    private String deviceVersion;
    private String deviceExtensions;
    private int deviceMaxClockFrequency;
    private int deviceAddressBits;
    private HIPLocalMemType localMemoryType;
    private int deviceVendorID;
    private HIPDeviceContextInterface deviceContext;
    private int asyncEngineCount = INIT_VALUE;
    private int concurrentKernels = INIT_VALUE;

    public HIPDevice(int index, long devicePointer) {
        this.index = index;
        this.devicePtr = devicePointer;
        this.buffer = ByteBuffer.allocate(MAX_BUFFER_SIZE);
        this.buffer.order(HIPDriver.BYTE_ORDER);
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
        this.deviceType = HIPDeviceType.Unknown;
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
        doubleFPConfig = getDoubleFPConfig();
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

    /**
     * Answers the OpenCL-style {@code cl_device_info} queries the Java layer makes by mapping each
     * one onto the HIP device attribute that carries the same meaning, and writing the result into
     * {@code buffer} in the little-endian binary layout the callers parse back out.
     *
     * <p>
     * A failing attribute query leaves its zero default rather than throwing: device enumeration
     * must degrade to a device that reports nothing rather than abort discovery altogether.
     */
    static void clGetDeviceInfo(long id, int info, byte[] buffer) {
        Arrays.fill(buffer, (byte) 0);
        HIPHandles.Device boxed = HIPHandles.resolve(id, HIPHandles.Device.class);
        if (boxed == null) {
            return;
        }
        int device = boxed.device();
        ByteBuffer out = ByteBuffer.wrap(buffer).order(HIPDriver.BYTE_ORDER);

        if (info == HIPDeviceInfo.CL_DEVICE_TYPE.getValue()) {
            putLong(out, HIPDeviceType.CL_DEVICE_TYPE_GPU.getValue());
        } else if (info == HIPDeviceInfo.CL_DEVICE_VENDOR_ID.getValue()) {
            putInt(out, AMD_PCI_VENDOR_ID);
        } else if (info == HIPDeviceInfo.CL_DEVICE_MAX_COMPUTE_UNITS.getValue()) {
            putInt(out, attribute(device, HIPDriverAPI.HIP_DEVICE_ATTRIBUTE_MULTIPROCESSOR_COUNT));
        } else if (info == HIPDeviceInfo.CL_DEVICE_MAX_WORK_ITEM_DIMENSIONS.getValue()) {
            putInt(out, 3);
        } else if (info == HIPDeviceInfo.CL_DEVICE_MAX_WORK_GROUP_SIZE.getValue()) {
            putInt(out, attribute(device, HIPDriverAPI.HIP_DEVICE_ATTRIBUTE_MAX_THREADS_PER_BLOCK));
        } else if (info == HIPDeviceInfo.CL_DEVICE_MAX_WORK_ITEM_SIZES.getValue()) {
            if (buffer.length >= 3 * Long.BYTES) {
                out.putLong(attribute(device, HIPDriverAPI.HIP_DEVICE_ATTRIBUTE_MAX_BLOCK_DIM_X));
                out.putLong(attribute(device, HIPDriverAPI.HIP_DEVICE_ATTRIBUTE_MAX_BLOCK_DIM_Y));
                out.putLong(attribute(device, HIPDriverAPI.HIP_DEVICE_ATTRIBUTE_MAX_BLOCK_DIM_Z));
            }
        } else if (info == HIPDeviceInfo.CL_DEVICE_MAX_CLOCK_FREQUENCY.getValue()) {
            putInt(out, attribute(device, HIPDriverAPI.HIP_DEVICE_ATTRIBUTE_CLOCK_RATE) / 1000); // kHz -> MHz
        } else if (info == HIPDeviceInfo.CL_DEVICE_ADDRESS_BITS.getValue()) {
            putInt(out, 64);
        } else if (info == HIPDeviceInfo.CL_DEVICE_MAX_MEM_ALLOC_SIZE.getValue() || info == HIPDeviceInfo.CL_DEVICE_GLOBAL_MEM_SIZE.getValue()) {
            putLong(out, totalMemory(device));
        } else if (info == HIPDeviceInfo.CL_DEVICE_MEM_BASE_ADDR_ALIGN.getValue()) {
            putInt(out, 4096 * 8); // textureAlignment in bits, generous default
        } else if (info == HIPDeviceInfo.CL_DEVICE_SINGLE_FP_CONFIG.getValue() || info == HIPDeviceInfo.CL_DEVICE_DOUBLE_FP_CONFIG.getValue()) {
            // Only ever read as a non-zero "supported" marker; every AMD GPU HIP supports has both.
            putLong(out, CL_FP_NONZERO);
        } else if (info == HIPDeviceInfo.CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE.getValue()) {
            putLong(out, attribute(device, HIPDriverAPI.HIP_DEVICE_ATTRIBUTE_TOTAL_CONSTANT_MEMORY));
        } else if (info == HIPDeviceInfo.CL_DEVICE_LOCAL_MEM_TYPE.getValue()) {
            putInt(out, (int) HIPLocalMemType.CL_LOCAL.getValue());
        } else if (info == HIPDeviceInfo.CL_DEVICE_LOCAL_MEM_SIZE.getValue()) {
            putLong(out, attribute(device, HIPDriverAPI.HIP_DEVICE_ATTRIBUTE_MAX_SHARED_MEMORY_PER_BLOCK));
        } else if (info == HIPDeviceInfo.CL_DEVICE_AVAILABLE.getValue()) {
            buffer[0] = 1;
        } else if (info == HIPDeviceInfo.CL_DEVICE_NAME.getValue()) {
            putString(buffer, deviceName(device));
        } else if (info == HIPDeviceInfo.CL_DEVICE_VENDOR.getValue()) {
            putString(buffer, "Advanced Micro Devices, Inc.");
        } else if (info == HIPDeviceInfo.CL_DRIVER_VERSION.getValue()) {
            putString(buffer, driverVersion());
        } else if (info == HIPDeviceInfo.CL_DEVICE_PROFILE.getValue()) {
            putString(buffer, "FULL_PROFILE");
        } else if (info == HIPDeviceInfo.CL_DEVICE_VERSION.getValue()) {
            // Reported as the gfx major and minor HIP gives as its "compute capability", so the Java
            // parser (split(" ")[1] -> "X.Y") yields a version number; the architecture itself is getArchitecture().
            putString(buffer, "HIP " + attribute(device, HIPDriverAPI.HIP_DEVICE_ATTRIBUTE_COMPUTE_CAPABILITY_MAJOR) + "." + attribute(device,
                    HIPDriverAPI.HIP_DEVICE_ATTRIBUTE_COMPUTE_CAPABILITY_MINOR));
        } else if (info == HIPDeviceInfo.CL_DEVICE_OPENCL_C_VERSION.getValue()) {
            putString(buffer, "HIP C++");
        } else if (info == HIPDeviceInfo.CL_DEVICE_EXTENSIONS.getValue()) {
            // Every AMD GPU HIP supports has double and half arithmetic.
            putString(buffer, "cl_khr_fp64 cl_khr_fp16");
        } else if (info == HIPDeviceInfo.CL_DEVICE_HOST_UNIFIED_MEMORY.getValue()) {
            putInt(out, 0);
        } else if (info == HIPDeviceInfo.CL_DEVICE_ENDIAN_LITTLE.getValue()) {
            putInt(out, 1);
        } else if (info == HIPDeviceInfo.CL_DEVICE_IL_VERSION.getValue()) {
            // No SPIR-V ingestion path for HIP; report empty (Java treats it as unsupported).
            putString(buffer, "");
        } else if (info == HIPDeviceInfo.TORNADO_DEVICE_ASYNC_ENGINE_COUNT.getValue()) {
            // Number of async DMA copy engines: 0 = no copy/compute overlap, 1 = one direction,
            // >= 2 = H2D and D2H can both overlap compute (and each other).
            putInt(out, attribute(device, HIPDriverAPI.HIP_DEVICE_ATTRIBUTE_ASYNC_ENGINE_COUNT));
        } else if (info == HIPDeviceInfo.TORNADO_DEVICE_CONCURRENT_KERNELS.getValue()) {
            // 1 if the device can execute multiple kernels from the same context concurrently.
            putInt(out, attribute(device, HIPDriverAPI.HIP_DEVICE_ATTRIBUTE_CONCURRENT_KERNELS));
        }
        // Unknown / unsupported info key: leave the buffer zeroed.
    }

    private static int attribute(int device, int attribute) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment value = FFMSupport.allocateInt(arena);
            if (HIPDriverAPI.hipDeviceGetAttribute(value, attribute, device) != HIPDriverAPI.HIP_SUCCESS) {
                return 0;
            }
            return value.get(FFMSupport.C_INT, 0);
        }
    }

    private static long totalMemory(int device) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment bytes = FFMSupport.allocateLong(arena);
            if (HIPDriverAPI.hipDeviceTotalMem(bytes, device) != HIPDriverAPI.HIP_SUCCESS) {
                return 0;
            }
            return bytes.get(FFMSupport.C_LONG, 0);
        }
    }

    private static String deviceName(int device) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment name = arena.allocate(DEVICE_NAME_BYTES, 1);
            if (HIPDriverAPI.hipDeviceGetName(name, DEVICE_NAME_BYTES, device) != HIPDriverAPI.HIP_SUCCESS) {
                return "";
            }
            return FFMSupport.readCString(name, DEVICE_NAME_BYTES);
        }
    }

    private static String driverVersion() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment version = FFMSupport.allocateInt(arena);
            if (HIPDriverAPI.hipDriverGetVersion(version) != HIPDriverAPI.HIP_SUCCESS) {
                return "0.0";
            }
            // HIP_VERSION encoding: major * 10000000 + minor * 100000 + patch.
            int driverVersion = version.get(FFMSupport.C_INT, 0);
            return (driverVersion / 10000000) + "." + ((driverVersion / 100000) % 100) + "." + (driverVersion % 100000);
        }
    }

    private static void putInt(ByteBuffer out, int value) {
        if (out.remaining() >= Integer.BYTES) {
            out.putInt(value);
        }
    }

    private static void putLong(ByteBuffer out, long value) {
        if (out.remaining() >= Long.BYTES) {
            out.putLong(value);
        }
    }

    /** Writes a NUL-terminated string; the Java readers substring up to the first NUL. */
    private static void putString(byte[] buffer, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        int length = Math.min(bytes.length, buffer.length - 1);
        System.arraycopy(bytes, 0, buffer, 0, length);
        buffer[length] = 0;
    }

    public long getDevicePointer() {
        return devicePtr;
    }

    public int getIndex() {
        return index;
    }

    public HIPDeviceType getDeviceType() {
        if (deviceType != HIPDeviceType.Unknown) {
            return deviceType;
        }
        queryOpenCLAPI(HIPDeviceInfo.CL_DEVICE_TYPE.getValue());
        long type = buffer.getLong();
        deviceType = HIPDeviceType.toDeviceType(type);
        return deviceType;
    }

    public int getDeviceVendorId() {
        if (deviceVendorID == INIT_VALUE) {
            queryOpenCLAPI(HIPDeviceInfo.CL_DEVICE_VENDOR_ID.getValue());
            deviceVendorID = buffer.getInt();
        }
        return deviceVendorID;
    }

    public int getDeviceMemoryBaseAlignment() {
        if (deviceMemoryBaseAlignment != INIT_VALUE) {
            return deviceMemoryBaseAlignment;
        }
        queryOpenCLAPI(HIPDeviceInfo.CL_DEVICE_MEM_BASE_ADDR_ALIGN.getValue());
        deviceMemoryBaseAlignment = buffer.getInt();
        return deviceMemoryBaseAlignment;
    }

    public boolean isDeviceAvailable() {
        queryOpenCLAPI(HIPDeviceInfo.CL_DEVICE_AVAILABLE.getValue());
        return (buffer.get() == 1);
    }

    @Override
    public String getDeviceName() {
        if (name == null) {
            queryOpenCLAPI(HIPDeviceInfo.CL_DEVICE_NAME.getValue());
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
        queryOpenCLAPI(HIPDeviceInfo.CL_DEVICE_VENDOR.getValue());
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
        queryOpenCLAPI(HIPDeviceInfo.CL_DRIVER_VERSION.getValue());
        try {
            driverVersion = new String(buffer.array(), "ASCII").trim();
        } catch (UnsupportedEncodingException e) {
            driverVersion = "unknown";
        }
        return driverVersion;
    }

    public String getDeviceVersion() {
        if (deviceVersion == null) {
            queryOpenCLAPI(HIPDeviceInfo.CL_DEVICE_VERSION.getValue());
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
            queryOpenCLAPI(HIPDeviceInfo.CL_DEVICE_OPENCL_C_VERSION.getValue());
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
        queryOpenCLAPI(HIPDeviceInfo.CL_DEVICE_EXTENSIONS.getValue());
        try {
            deviceExtensions = new String(buffer.array(), "ASCII").trim();
        } catch (UnsupportedEncodingException e) {
            deviceExtensions = "unknown";
        }
        return deviceExtensions;
    }

    /** The GPU architecture as hipRTC names it ({@code gfx942}), without feature suffixes. */
    @Override
    public String getArchitecture() {
        HIPHandles.Device boxed = HIPHandles.resolve(devicePtr, HIPHandles.Device.class);
        return boxed == null ? "" : HIPCompiler.architecture(boxed.device());
    }

    /** The wavefront size: 64 on CDNA GPUs, 32 on RDNA GPUs. */
    public int getWarpSize() {
        HIPHandles.Device boxed = HIPHandles.resolve(devicePtr, HIPHandles.Device.class);
        return boxed == null ? 64 : attribute(boxed.device(), HIPDriverAPI.HIP_DEVICE_ATTRIBUTE_WARP_SIZE);
    }

    @Override
    public int getDeviceMaxComputeUnits() {
        if (maxComputeUnits != -1) {
            return maxComputeUnits;
        }
        queryOpenCLAPI(HIPDeviceInfo.CL_DEVICE_MAX_COMPUTE_UNITS.getValue());
        maxComputeUnits = buffer.getInt();
        return maxComputeUnits;
    }

    @Override
    public int getDeviceMaxClockFrequency() {
        if (deviceMaxClockFrequency != INIT_VALUE) {
            return deviceMaxClockFrequency;
        }
        queryOpenCLAPI(HIPDeviceInfo.CL_DEVICE_MAX_CLOCK_FREQUENCY.getValue());
        deviceMaxClockFrequency = buffer.getInt();
        return deviceMaxClockFrequency;
    }

    @Override
    public long getDeviceMaxAllocationSize() {
        if (maxAllocationSize != -1) {
            return maxAllocationSize;
        }
        queryOpenCLAPI(HIPDeviceInfo.CL_DEVICE_MAX_MEM_ALLOC_SIZE.getValue());
        maxAllocationSize = buffer.getLong();
        return maxAllocationSize;
    }

    @Override
    public long getDeviceGlobalMemorySize() {
        if (globalMemorySize != -1) {
            return globalMemorySize;
        }
        queryOpenCLAPI(HIPDeviceInfo.CL_DEVICE_GLOBAL_MEM_SIZE.getValue());
        globalMemorySize = buffer.getLong();
        return globalMemorySize;
    }

    @Override
    public long getDeviceLocalMemorySize() {
        if (localMemorySize != -1) {
            return localMemorySize;
        }
        queryOpenCLAPI(HIPDeviceInfo.CL_DEVICE_LOCAL_MEM_SIZE.getValue());
        localMemorySize = buffer.getLong();
        return localMemorySize;
    }

    public int getDeviceMaxWorkItemDimensions() {
        if (maxWorkItemDimensions != -1) {
            return maxWorkItemDimensions;
        }
        queryOpenCLAPI(HIPDeviceInfo.CL_DEVICE_MAX_WORK_ITEM_DIMENSIONS.getValue());
        maxWorkItemDimensions = buffer.getInt();
        return maxWorkItemDimensions;
    }

    @Override
    public long[] getDeviceMaxWorkItemSizes() {

        if (maxWorkItemSizes != null) {
            return maxWorkItemSizes;
        }

        final int elements = getDeviceMaxWorkItemDimensions();

        queryOpenCLAPI(HIPDeviceInfo.CL_DEVICE_MAX_WORK_ITEM_SIZES.getValue());
        buffer.rewind();
        maxWorkItemSizes = new long[elements];
        for (int i = 0; i < elements; i++) {
            maxWorkItemSizes[i] = buffer.getLong();
        }
        return maxWorkItemSizes;
    }

    private int getDeviceMaxWorkGroupSize_0() {
        queryOpenCLAPI(HIPDeviceInfo.CL_DEVICE_MAX_WORK_GROUP_SIZE.getValue());
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
        queryOpenCLAPI(HIPDeviceInfo.CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE.getValue());
        maxConstantBufferSize = buffer.getLong();
        return maxConstantBufferSize;
    }

    private long getDoubleFPConfig() {
        queryOpenCLAPI(HIPDeviceInfo.CL_DEVICE_DOUBLE_FP_CONFIG.getValue());
        return buffer.getLong();
    }

    public synchronized boolean isDeviceDoubleFPSupported() {
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
        queryOpenCLAPI(HIPDeviceInfo.CL_DEVICE_SINGLE_FP_CONFIG.getValue());
        singleFPConfig = buffer.getLong();
        return singleFPConfig;
    }

    public int getDeviceAddressBits() {
        if (deviceAddressBits != INIT_VALUE) {
            return deviceAddressBits;
        }
        queryOpenCLAPI(HIPDeviceInfo.CL_DEVICE_ADDRESS_BITS.getValue());
        deviceAddressBits = buffer.getInt();
        return deviceAddressBits;
    }

    public boolean hasDeviceUnifiedMemory() {
        queryOpenCLAPI(HIPDeviceInfo.CL_DEVICE_HOST_UNIFIED_MEMORY.getValue());
        return buffer.getInt() == HIPDriver.CL_TRUE;
    }

    public HIPLocalMemType getDeviceLocalMemoryType() {
        if (localMemoryType != null) {
            return localMemoryType;
        }
        queryOpenCLAPI(HIPDeviceInfo.CL_DEVICE_LOCAL_MEM_TYPE.getValue());
        localMemoryType = HIPLocalMemType.toLocalMemType(buffer.getInt());
        return localMemoryType;
    }

    private int getDeviceEndianLittle() {
        queryOpenCLAPI(HIPDeviceInfo.CL_DEVICE_ENDIAN_LITTLE.getValue());
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
    public HIPDeviceContextInterface getDeviceContext() {
        return this.deviceContext;
    }

    @Override
    public void setDeviceContext(HIPDeviceContextInterface deviceContext) {
        this.deviceContext = deviceContext;
    }

    @Override
    public int deviceVersion() {
        return Integer.parseInt(getVersion().split(" ")[1].replace(".", "")) * 10;
    }

    @Override
    public int getAsyncEngineCount() {
        if (asyncEngineCount == INIT_VALUE) {
            queryOpenCLAPI(HIPDeviceInfo.TORNADO_DEVICE_ASYNC_ENGINE_COUNT.getValue());
            asyncEngineCount = buffer.getInt();
        }
        return asyncEngineCount;
    }

    @Override
    public boolean supportsConcurrentKernels() {
        if (concurrentKernels == INIT_VALUE) {
            queryOpenCLAPI(HIPDeviceInfo.TORNADO_DEVICE_CONCURRENT_KERNELS.getValue());
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

    public synchronized String getVersion() {
        if (version != null) {
            return version;
        }
        queryOpenCLAPI(HIPDeviceInfo.CL_DEVICE_VERSION.getValue());
        try {
            version = new String(buffer.array(), "ASCII").trim();
        } catch (UnsupportedEncodingException e) {
            version = "HIPDriver 0.0";
        }
        return version;
    }

    private synchronized void queryOpenCLAPI(int value) {
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
        sb.append(String.format("HIPDriver C version : %s\n", getDeviceOpenCLCVersion()));
        sb.append(String.format("Endianness       : %s\n", isLittleEndian() ? "little" : "big"));
        sb.append(String.format("Address size     : %d\n", getDeviceAddressBits()));
        sb.append(String.format("Single fp config : %b\n", getDeviceSingleFPConfig()));
        sb.append(String.format("Double fp config : %b\n", isDeviceDoubleFPSupported()));
        return sb.toString();
    }
}
