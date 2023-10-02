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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.drivers.opencl;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import uk.ac.manchester.tornado.drivers.opencl.enums.OCLDeviceType;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLPlatformInfo;
import uk.ac.manchester.tornado.drivers.opencl.exceptions.OCLException;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class OCLPlatform extends TornadoLogger implements TornadoPlatform {

    private final int index;
    private final long id;
    private final List<OCLTargetDevice> devices;

    // FIXME <REVISIT> It seems that this object is no longer needed
    private final Set<OCLContext> contexts;

    public OCLPlatform(int index, long id) {
        this.index = index;
        this.id = id;
        this.devices = new ArrayList<>();
        this.contexts = new HashSet<>();

        final int deviceCount;

        if (isVendor("Xilinx") || isVendor("Codeplay")) {
            deviceCount = clGetDeviceCount(id, OCLDeviceType.CL_DEVICE_TYPE_ACCELERATOR.getValue());
        } else if (isVendor("Mesa/X.org")) {
            deviceCount = clGetDeviceCount(id, OCLDeviceType.CL_DEVICE_TYPE_GPU.getValue());
        } else {
            deviceCount = clGetDeviceCount(id, OCLDeviceType.CL_DEVICE_TYPE_ALL.getValue());
        }

        final long[] ids = new long[deviceCount];
        if (isVendor("Xilinx") || isVendor("Codeplay")) {
            clGetDeviceIDs(id, OCLDeviceType.CL_DEVICE_TYPE_ACCELERATOR.getValue(), ids);
        } else if (isVendor("Mesa/X.org")) {
            clGetDeviceIDs(id, OCLDeviceType.CL_DEVICE_TYPE_GPU.getValue(), ids);
        } else {
            clGetDeviceIDs(id, OCLDeviceType.CL_DEVICE_TYPE_ALL.getValue(), ids);
        }
        for (int i = 0; i < ids.length; i++) {
            devices.add(new OCLDevice(i, ids[i]));
        }

    }

    private boolean isVendor(String vendorName) {
        return this.getVendor().toLowerCase().startsWith(vendorName.toLowerCase());
    }

    static native String clGetPlatformInfo(long id, int info);

    static native int clGetDeviceCount(long id, long type);

    static native int clGetDeviceIDs(long id, long type, long[] devices);

    static native long clCreateContext(long platform, long[] devices) throws OCLException;

    public List<OCLTargetDevice> getDevices() {
        return devices;
    }

    public OCLContext createContext() {
        OCLContext contextObject = null;
        final LongBuffer deviceIds = LongBuffer.allocate(devices.size());
        for (OCLTargetDevice device : devices) {
            deviceIds.put(device.getId());
        }

        try {
            long contextId = clCreateContext(id, deviceIds.array());
            contextObject = new OCLContext(this, contextId, devices);
            contexts.add(contextObject);
        } catch (OCLException e) {
            error(e.getMessage());
            e.printStackTrace();
        }
        return contextObject;
    }

    public void cleanup() {
        for (OCLContext context : contexts) {
            if (context != null) {
                context.cleanup();
            }
        }
    }

    public String getProfile() {
        return clGetPlatformInfo(id, OCLPlatformInfo.CL_PLATFORM_PROFILE.getValue());
    }

    @Override
    public String getVersion() {
        return clGetPlatformInfo(id, OCLPlatformInfo.CL_PLATFORM_VERSION.getValue());
    }

    public String getName() {
        return clGetPlatformInfo(id, OCLPlatformInfo.CL_PLATFORM_NAME.getValue());
    }

    public String getVendor() {
        return clGetPlatformInfo(id, OCLPlatformInfo.CL_PLATFORM_VENDOR.getValue());
    }

    public String getExtensions() {
        return clGetPlatformInfo(id, OCLPlatformInfo.CL_PLATFORM_EXTENSIONS.getValue());
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
