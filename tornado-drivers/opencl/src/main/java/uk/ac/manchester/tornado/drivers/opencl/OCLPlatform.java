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
package uk.ac.manchester.tornado.drivers.opencl;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLDeviceType;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLPlatformInfo;
import uk.ac.manchester.tornado.drivers.opencl.exceptions.OCLException;
import uk.ac.manchester.tornado.drivers.opencl.ffm.OpenCLAPI;

public class OCLPlatform implements TornadoPlatformInterface {

    private final int index;
    private final long oclPlatformPtr;
    private final List<OCLTargetDevice> devices;

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

    public OCLPlatform(int index, long platformPointers) {
        this.index = index;
        this.oclPlatformPtr = platformPointers;
        this.devices = new ArrayList<>();

        final int deviceCount;

        if (isVendor(Vendor.MESA)) {
            deviceCount = clGetDeviceCount(platformPointers, OCLDeviceType.CL_DEVICE_TYPE_GPU.getValue());
        } else {
            deviceCount = clGetDeviceCount(platformPointers, OCLDeviceType.CL_DEVICE_TYPE_ALL.getValue());
        }

        final long[] ids = new long[deviceCount];
        if (isVendor(Vendor.MESA)) {
            clGetDeviceIDs(platformPointers, OCLDeviceType.CL_DEVICE_TYPE_GPU.getValue(), ids);
        } else {
            clGetDeviceIDs(platformPointers, OCLDeviceType.CL_DEVICE_TYPE_ALL.getValue(), ids);
        }
        for (int i = 0; i < ids.length; i++) {
            devices.add(new OCLDevice(i, ids[i]));
        }

    }

    private boolean isVendor(Vendor vendor) {
        return this.getVendor().toLowerCase().startsWith(vendor.getVendorName().toLowerCase());
    }

    /** Longest platform info string the query buffer makes room for. */
    private static final int MAX_INFO_BYTES = 1024;

    String clGetPlatformInfo(long id, int info) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment value = arena.allocate(MAX_INFO_BYTES, 1);
            value.fill((byte) 0);
            if (OpenCLAPI.clGetPlatformInfo(id, info, MAX_INFO_BYTES, value, MemorySegment.NULL) != OpenCLAPI.CL_SUCCESS) {
                return "";
            }
            return FFMSupport.readCString(value, MAX_INFO_BYTES);
        }
    }

    int clGetDeviceCount(long id, long type) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment count = FFMSupport.allocateInt(arena);
            if (OpenCLAPI.clGetDeviceIDs(id, type, 0, MemorySegment.NULL, count) != OpenCLAPI.CL_SUCCESS) {
                return 0;
            }
            return count.get(FFMSupport.C_INT, 0);
        }
    }

    int clGetDeviceIDs(long id, long type, long[] devices) {
        if (devices.length == 0) {
            return 0;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment deviceIds = FFMSupport.allocateArray(arena, FFMSupport.C_POINTER, devices.length);
            MemorySegment count = FFMSupport.allocateInt(arena);
            if (OpenCLAPI.clGetDeviceIDs(id, type, devices.length, deviceIds, count) != OpenCLAPI.CL_SUCCESS) {
                return 0;
            }
            int numDevices = Math.min(count.get(FFMSupport.C_INT, 0), devices.length);
            for (int i = 0; i < numDevices; i++) {
                devices[i] = deviceIds.get(FFMSupport.C_POINTER, i * FFMSupport.C_POINTER.byteSize()).address();
            }
            return numDevices;
        }
    }

    long clCreateContext(long platform, long[] devices) throws OCLException {
        try (Arena arena = Arena.ofConfined()) {
            // { CL_CONTEXT_PLATFORM, platform, 0 }
            MemorySegment properties = FFMSupport.allocateArray(arena, FFMSupport.C_LONG, 3);
            properties.set(FFMSupport.C_LONG, 0, OpenCLAPI.CL_CONTEXT_PLATFORM);
            properties.set(FFMSupport.C_LONG, FFMSupport.C_LONG.byteSize(), platform);
            properties.set(FFMSupport.C_LONG, 2 * FFMSupport.C_LONG.byteSize(), 0L);

            MemorySegment deviceIds = FFMSupport.allocateArray(arena, FFMSupport.C_POINTER, Math.max(devices.length, 1));
            for (int i = 0; i < devices.length; i++) {
                deviceIds.set(FFMSupport.C_POINTER, i * FFMSupport.C_POINTER.byteSize(), MemorySegment.ofAddress(devices[i]));
            }
            MemorySegment status = FFMSupport.allocateInt(arena);
            long context = OpenCLAPI.clCreateContext(properties, devices.length, deviceIds, OpenCLAPI.contextNotifyCallback(), MemorySegment.NULL, status);
            int result = status.get(FFMSupport.C_INT, 0);
            if (result != OpenCLAPI.CL_SUCCESS) {
                throw new OCLException("clCreateContext failed: CL error " + result);
            }
            return context;
        }
    }

    public List<OCLTargetDevice> getDevices() {
        return devices;
    }

    public OCLContext createContext() {
        OCLContext contextObject;
        final LongBuffer deviceIds = LongBuffer.allocate(devices.size());
        devices.stream().mapToLong(OCLTargetDevice::getDevicePointer).forEach(deviceIds::put);
        try {
            long contextPtr = clCreateContext(oclPlatformPtr, deviceIds.array());
            contextObject = new OCLContext(this, contextPtr, devices);
        } catch (OCLException e) {
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
        return contextObject;
    }

    public void cleanup() {
    }

    public String getProfile() {
        return clGetPlatformInfo(oclPlatformPtr, OCLPlatformInfo.CL_PLATFORM_PROFILE.getValue());
    }

    @Override
    public String getVersion() {
        return clGetPlatformInfo(oclPlatformPtr, OCLPlatformInfo.CL_PLATFORM_VERSION.getValue());
    }

    public String getName() {
        return clGetPlatformInfo(oclPlatformPtr, OCLPlatformInfo.CL_PLATFORM_NAME.getValue());
    }

    public String getVendor() {
        return clGetPlatformInfo(oclPlatformPtr, OCLPlatformInfo.CL_PLATFORM_VENDOR.getValue());
    }

    public String getExtensions() {
        return clGetPlatformInfo(oclPlatformPtr, OCLPlatformInfo.CL_PLATFORM_EXTENSIONS.getValue());
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
