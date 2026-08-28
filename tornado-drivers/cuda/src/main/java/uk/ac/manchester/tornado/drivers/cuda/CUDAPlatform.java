/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.drivers.cuda.enums.CUDADeviceType;
import uk.ac.manchester.tornado.drivers.cuda.enums.CUDAPlatformInfo;
import uk.ac.manchester.tornado.drivers.cuda.exceptions.CUDAException;
import uk.ac.manchester.tornado.drivers.cuda.ffm.CUDADriverAPI;
import uk.ac.manchester.tornado.drivers.cuda.ffm.CUDAHandles;
import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;

public class CUDAPlatform implements TornadoPlatformInterface {

    private final int index;
    private final long oclPlatformPtr;
    private final List<CUDATargetDevice> devices;

    private enum Vendor {
        CODEPLAY("Codeplay"), //
        INTEL("Intel"), //
        AMD("AMD"), //
        NVIDIA("Nvidia"), //
        MESA("Mesa/X.org");

        final String vendorName;

        Vendor(String vendorName) {
            this.vendorName = vendorName;
        }

        String getVendorName() {
            return vendorName;
        }
    }

    public CUDAPlatform(int index, long platformPointers) {
        this.index = index;
        this.oclPlatformPtr = platformPointers;
        this.devices = new ArrayList<>();

        final int deviceCount;

        if (isVendor(Vendor.MESA)) {
            deviceCount = clGetDeviceCount(platformPointers, CUDADeviceType.CL_DEVICE_TYPE_GPU.getValue());
        } else {
            deviceCount = clGetDeviceCount(platformPointers, CUDADeviceType.CL_DEVICE_TYPE_ALL.getValue());
        }

        final long[] ids = new long[deviceCount];
        if (isVendor(Vendor.MESA)) {
            clGetDeviceIDs(platformPointers, CUDADeviceType.CL_DEVICE_TYPE_GPU.getValue(), ids);
        } else {
            clGetDeviceIDs(platformPointers, CUDADeviceType.CL_DEVICE_TYPE_ALL.getValue(), ids);
        }
        for (int i = 0; i < ids.length; i++) {
            devices.add(new CUDADevice(i, ids[i]));
        }

    }

    private boolean isVendor(Vendor vendor) {
        return this.getVendor().toLowerCase().startsWith(vendor.getVendorName().toLowerCase());
    }

    /**
     * CUDA-oriented platform strings. The version string must be of the form
     * {@code "<vendor> <major>.<minor>"} because {@link #getVersion()} parses {@code split(" ")[1]}.
     */
    String clGetPlatformInfo(long id, int info) {
        if (info == CUDAPlatformInfo.CL_PLATFORM_PROFILE.getValue()) {
            return "FULL_PROFILE";
        } else if (info == CUDAPlatformInfo.CL_PLATFORM_VERSION.getValue()) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment version = FFMSupport.allocateInt(arena);
                CUDADriverAPI.cuDriverGetVersion(version);
                int driverVersion = version.get(FFMSupport.C_INT, 0);
                return "CUDA " + (driverVersion / 1000) + "." + ((driverVersion % 1000) / 10);
            }
        } else if (info == CUDAPlatformInfo.CL_PLATFORM_NAME.getValue()) {
            return "NVIDIA CUDA";
        } else if (info == CUDAPlatformInfo.CL_PLATFORM_VENDOR.getValue()) {
            return "NVIDIA Corporation";
        }
        return "";
    }

    int clGetDeviceCount(long id, long type) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment count = FFMSupport.allocateInt(arena);
            if (CUDADriverAPI.cuDeviceGetCount(count) != CUDADriverAPI.CUDA_SUCCESS) {
                return 0;
            }
            return count.get(FFMSupport.C_INT, 0);
        }
    }

    /** Boxes each {@code CUdevice} behind a handle the Java layer addresses it by. */
    int clGetDeviceIDs(long id, long type, long[] devices) {
        int available = clGetDeviceCount(id, type);
        int count = Math.min(available, devices.length);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment device = FFMSupport.allocateInt(arena);
            for (int i = 0; i < count; i++) {
                // Device enumeration must degrade to "no devices" rather than abort discovery, so a
                // failing query leaves the ordinal's CUdevice at its zero default rather than throwing.
                CUDADriverAPI.cuDeviceGet(device, i);
                devices[i] = CUDAHandles.register(new CUDAHandles.Device(device.get(FFMSupport.C_INT, 0), i));
            }
        }
        return count;
    }

    /**
     * CUDA contexts are per-device, so a context is created for the first device in the array. The
     * per-device split that OpenCL allows is handled at the Java level (one CUDADeviceContext per
     * device) and each pins this context with {@code cuCtxSetCurrent}.
     */
    long clCreateContext(long platform, long[] devices) throws CUDAException {
        if (devices.length == 0) {
            return 0;
        }
        CUDAHandles.Device device = CUDAHandles.resolve(devices[0], CUDAHandles.Device.class);
        if (device == null) {
            return 0;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment context = FFMSupport.allocatePointer(arena);
            int result = CUDADriverAPI.cuCtxCreate(context, CUDADriverAPI.CU_CTX_SCHED_YIELD, device.device());
            if (result != CUDADriverAPI.CUDA_SUCCESS) {
                throw new CUDAException(CUDADriverAPI.describe("cuCtxCreate", result));
            }
            long contextPointer = context.get(FFMSupport.C_POINTER, 0).address();
            return CUDAHandles.register(new CUDAHandles.Context(contextPointer, device.device(), device.ordinal()));
        }
    }

    public List<CUDATargetDevice> getDevices() {
        return devices;
    }

    public CUDAContext createContext() {
        CUDAContext contextObject;
        final LongBuffer deviceIds = LongBuffer.allocate(devices.size());
        devices.stream().mapToLong(CUDATargetDevice::getDevicePointer).forEach(deviceIds::put);
        try {
            long contextPtr = clCreateContext(oclPlatformPtr, deviceIds.array());
            contextObject = new CUDAContext(this, contextPtr, devices);
        } catch (CUDAException e) {
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
        return contextObject;
    }

    public void cleanup() {
    }

    public String getProfile() {
        return clGetPlatformInfo(oclPlatformPtr, CUDAPlatformInfo.CL_PLATFORM_PROFILE.getValue());
    }

    @Override
    public String getVersion() {
        return clGetPlatformInfo(oclPlatformPtr, CUDAPlatformInfo.CL_PLATFORM_VERSION.getValue());
    }

    public String getName() {
        return clGetPlatformInfo(oclPlatformPtr, CUDAPlatformInfo.CL_PLATFORM_NAME.getValue());
    }

    public String getVendor() {
        return clGetPlatformInfo(oclPlatformPtr, CUDAPlatformInfo.CL_PLATFORM_VENDOR.getValue());
    }

    public String getExtensions() {
        return clGetPlatformInfo(oclPlatformPtr, CUDAPlatformInfo.CL_PLATFORM_EXTENSIONS.getValue());
    }

    @Override
    public String toString() {
        String sb = String.format("name=%s, num. devices=%d, ", getName(), devices.size()) + String.format("version=%s", getVersion());
        return sb.trim();
    }

    public int getIndex() {
        return index;
    }

}
