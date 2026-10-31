/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
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
package uk.ac.manchester.tornado.drivers.cuda;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

import uk.ac.manchester.tornado.api.options.CudaFlags;
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
     * How a host thread waits for the device: {@code tornado.cuda.sched} = {@code yield} (the
     * default), {@code spin}, {@code blocking} or {@code auto}. A waiting thread that spins or
     * yields keeps a core busy for as long as the device works; {@code blocking} puts it to sleep
     * until the device signals, which frees the host when many streams wait at once.
     */
    static int schedulingFlags() {
        return switch (CudaFlags.SCHEDULING.get()) {
            case "spin" -> CUDADriverAPI.CU_CTX_SCHED_SPIN;
            case "blocking" -> CUDADriverAPI.CU_CTX_SCHED_BLOCKING_SYNC;
            case "auto" -> CUDADriverAPI.CU_CTX_SCHED_AUTO;
            default -> CUDADriverAPI.CU_CTX_SCHED_YIELD;
        };
    }

    /**
     * Retains the primary context of one device and returns the handle the Java layer addresses it
     * by. The primary context is the one the CUDA runtime, and so cuBLAS, cuDNN or NCCL, use for
     * that device, which lets device pointers and streams cross between TornadoVM and those
     * libraries. Scheduling is set to yield first; the driver ignores the request when another user
     * already made the context active with other flags.
     */
    static long retainPrimaryContext(long deviceId) throws CUDAException {
        CUDAHandles.Device device = CUDAHandles.resolve(deviceId, CUDAHandles.Device.class);
        if (device == null) {
            return 0;
        }
        CUDADriverAPI.cuDevicePrimaryCtxSetFlags(device.device(), schedulingFlags());
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment context = FFMSupport.allocatePointer(arena);
            int result = CUDADriverAPI.cuDevicePrimaryCtxRetain(context, device.device());
            if (result != CUDADriverAPI.CUDA_SUCCESS) {
                throw new CUDAException(CUDADriverAPI.describe("cuDevicePrimaryCtxRetain", result));
            }
            long contextPointer = context.get(FFMSupport.C_POINTER, 0).address();
            return CUDAHandles.register(new CUDAHandles.Context(contextPointer, device.device(), device.ordinal()));
        }
    }

    public List<CUDATargetDevice> getDevices() {
        return devices;
    }

    public CUDAContext createContext() {
        return new CUDAContext(this, devices);
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
