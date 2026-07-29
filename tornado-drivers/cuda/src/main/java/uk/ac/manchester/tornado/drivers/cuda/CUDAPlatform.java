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

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.drivers.cuda.enums.CUDADeviceType;
import uk.ac.manchester.tornado.drivers.cuda.enums.CUDAPlatformInfo;
import uk.ac.manchester.tornado.drivers.cuda.exceptions.CUDAException;

public class CUDAPlatform implements TornadoPlatformInterface {

    private final int index;
    private final long oclPlatformPtr;
    private final List<CUDATargetDevice> devices;

    private enum Vendor {
        CODEPLAY("Codeplay"), //
        INTEL("Intel"), //
        AMD("AMD"), //
        NVIDIA("Nvidia"), //
        MESA("Mesa/X.org"), //
        XILINX("Xilinx");

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

        if (isVendor(Vendor.XILINX)) {
            deviceCount = clGetDeviceCount(platformPointers, CUDADeviceType.CL_DEVICE_TYPE_ACCELERATOR.getValue());
        } else if (isVendor(Vendor.MESA)) {
            deviceCount = clGetDeviceCount(platformPointers, CUDADeviceType.CL_DEVICE_TYPE_GPU.getValue());
        } else {
            deviceCount = clGetDeviceCount(platformPointers, CUDADeviceType.CL_DEVICE_TYPE_ALL.getValue());
        }

        final long[] ids = new long[deviceCount];
        if (isVendor(Vendor.XILINX)) {
            clGetDeviceIDs(platformPointers, CUDADeviceType.CL_DEVICE_TYPE_ACCELERATOR.getValue(), ids);
        } else if (isVendor(Vendor.MESA)) {
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

    native String clGetPlatformInfo(long id, int info);

    native int clGetDeviceCount(long id, long type);

    native int clGetDeviceIDs(long id, long type, long[] devices);

    native long clCreateContext(long platform, long[] devices) throws CUDAException;

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
