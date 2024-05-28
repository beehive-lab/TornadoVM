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

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLDeviceType;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLPlatformInfo;
import uk.ac.manchester.tornado.drivers.opencl.exceptions.OCLException;

public class OCLPlatform implements TornadoPlatformInterface {

    private final int index;
    private final long oclPlatformId;
    private final List<OCLTargetDevice> devices;

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

    public OCLPlatform(int index, long id) {
        this.index = index;
        this.oclPlatformId = id;
        this.devices = new ArrayList<>();

        final int deviceCount;

        if (isVendor(Vendor.XILINX) || isVendor(Vendor.CODEPLAY)) {
            deviceCount = clGetDeviceCount(id, OCLDeviceType.CL_DEVICE_TYPE_ACCELERATOR.getValue());
        } else if (isVendor(Vendor.MESA)) {
            deviceCount = clGetDeviceCount(id, OCLDeviceType.CL_DEVICE_TYPE_GPU.getValue());
        } else {
            deviceCount = clGetDeviceCount(id, OCLDeviceType.CL_DEVICE_TYPE_ALL.getValue());
        }

        final long[] ids = new long[deviceCount];
        if (isVendor(Vendor.XILINX) || isVendor(Vendor.CODEPLAY)) {
            clGetDeviceIDs(id, OCLDeviceType.CL_DEVICE_TYPE_ACCELERATOR.getValue(), ids);
        } else if (isVendor(Vendor.MESA)) {
            clGetDeviceIDs(id, OCLDeviceType.CL_DEVICE_TYPE_GPU.getValue(), ids);
        } else {
            clGetDeviceIDs(id, OCLDeviceType.CL_DEVICE_TYPE_ALL.getValue(), ids);
        }
        for (int i = 0; i < ids.length; i++) {
            devices.add(new OCLDevice(i, ids[i]));
        }

    }

    private boolean isVendor(Vendor vendor) {
        return this.getVendor().toLowerCase().startsWith(vendor.getVendorName().toLowerCase());
    }

    static native String clGetPlatformInfo(long id, int info);

    static native int clGetDeviceCount(long id, long type);

    static native int clGetDeviceIDs(long id, long type, long[] devices);

    static native long clCreateContext(long platform, long[] devices) throws OCLException;

    public List<OCLTargetDevice> getDevices() {
        return devices;
    }

    public OCLContext createContext() {
        OCLContext contextObject;
        final LongBuffer deviceIds = LongBuffer.allocate(devices.size());
        for (OCLTargetDevice device : devices) {
            deviceIds.put(device.getId());
        }
        try {
            long contextPtr = clCreateContext(oclPlatformId, deviceIds.array());
            contextObject = new OCLContext(this, contextPtr, devices);
        } catch (OCLException e) {
            throw new TornadoBailoutRuntimeException(e.getMessage());

        }
        return contextObject;
    }

    public void cleanup() {
    }

    public String getProfile() {
        return clGetPlatformInfo(oclPlatformId, OCLPlatformInfo.CL_PLATFORM_PROFILE.getValue());
    }

    @Override
    public String getVersion() {
        return clGetPlatformInfo(oclPlatformId, OCLPlatformInfo.CL_PLATFORM_VERSION.getValue());
    }

    @Override
    public boolean isSPIRVSupported() {
        for (OCLTargetDevice device : devices) {
            // This indicates that this platform has at least one device with support for SPIR-V.
            return device.isSPIRVSupported();
        }
        return false;
    }

    public String getName() {
        return clGetPlatformInfo(oclPlatformId, OCLPlatformInfo.CL_PLATFORM_NAME.getValue());
    }

    public String getVendor() {
        return clGetPlatformInfo(oclPlatformId, OCLPlatformInfo.CL_PLATFORM_VENDOR.getValue());
    }

    public String getExtensions() {
        return clGetPlatformInfo(oclPlatformId, OCLPlatformInfo.CL_PLATFORM_EXTENSIONS.getValue());
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
