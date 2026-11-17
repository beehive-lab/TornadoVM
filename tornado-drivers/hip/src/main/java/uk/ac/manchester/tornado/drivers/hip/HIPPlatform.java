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
package uk.ac.manchester.tornado.drivers.hip;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

import uk.ac.manchester.tornado.drivers.hip.enums.HIPDeviceType;
import uk.ac.manchester.tornado.drivers.hip.enums.HIPPlatformInfo;
import uk.ac.manchester.tornado.drivers.hip.exceptions.HIPException;
import uk.ac.manchester.tornado.drivers.hip.ffm.HIPDriverAPI;
import uk.ac.manchester.tornado.drivers.hip.ffm.HIPHandles;
import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;

public class HIPPlatform implements TornadoPlatformInterface {

    private final int index;
    private final long oclPlatformPtr;
    private final List<HIPTargetDevice> devices;

    private enum Vendor {
        CODEPLAY("Codeplay"), //
        INTEL("Intel"), //
        AMD("AMD"), //
        MESA("Mesa/X.org");

        final String vendorName;

        Vendor(String vendorName) {
            this.vendorName = vendorName;
        }

        String getVendorName() {
            return vendorName;
        }
    }

    public HIPPlatform(int index, long platformPointers) {
        this.index = index;
        this.oclPlatformPtr = platformPointers;
        this.devices = new ArrayList<>();

        final int deviceCount;

        if (isVendor(Vendor.MESA)) {
            deviceCount = clGetDeviceCount(platformPointers, HIPDeviceType.CL_DEVICE_TYPE_GPU.getValue());
        } else {
            deviceCount = clGetDeviceCount(platformPointers, HIPDeviceType.CL_DEVICE_TYPE_ALL.getValue());
        }

        final long[] ids = new long[deviceCount];
        if (isVendor(Vendor.MESA)) {
            clGetDeviceIDs(platformPointers, HIPDeviceType.CL_DEVICE_TYPE_GPU.getValue(), ids);
        } else {
            clGetDeviceIDs(platformPointers, HIPDeviceType.CL_DEVICE_TYPE_ALL.getValue(), ids);
        }
        for (int i = 0; i < ids.length; i++) {
            devices.add(new HIPDevice(i, ids[i]));
        }

    }

    private boolean isVendor(Vendor vendor) {
        return this.getVendor().toLowerCase().startsWith(vendor.getVendorName().toLowerCase());
    }

    /**
     * HIP platform strings. The version string must be of the form
     * {@code "<vendor> <major>.<minor>"} because {@link #getVersion()} parses {@code split(" ")[1]}.
     */
    String clGetPlatformInfo(long id, int info) {
        if (info == HIPPlatformInfo.CL_PLATFORM_PROFILE.getValue()) {
            return "FULL_PROFILE";
        } else if (info == HIPPlatformInfo.CL_PLATFORM_VERSION.getValue()) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment version = FFMSupport.allocateInt(arena);
                HIPDriverAPI.hipDriverGetVersion(version);
                // HIP_VERSION encoding: major * 10000000 + minor * 100000 + patch.
                int driverVersion = version.get(FFMSupport.C_INT, 0);
                return "HIP " + (driverVersion / 10000000) + "." + ((driverVersion / 100000) % 100);
            }
        } else if (info == HIPPlatformInfo.CL_PLATFORM_NAME.getValue()) {
            return "AMD HIP";
        } else if (info == HIPPlatformInfo.CL_PLATFORM_VENDOR.getValue()) {
            return "Advanced Micro Devices, Inc.";
        }
        return "";
    }

    int clGetDeviceCount(long id, long type) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment count = FFMSupport.allocateInt(arena);
            if (HIPDriverAPI.hipGetDeviceCount(count) != HIPDriverAPI.HIP_SUCCESS) {
                return 0;
            }
            return count.get(FFMSupport.C_INT, 0);
        }
    }

    /** Boxes each {@code hipDevice_t} behind a handle the Java layer addresses it by. */
    int clGetDeviceIDs(long id, long type, long[] devices) {
        int available = clGetDeviceCount(id, type);
        int count = Math.min(available, devices.length);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment device = FFMSupport.allocateInt(arena);
            for (int i = 0; i < count; i++) {
                // Device enumeration must degrade to "no devices" rather than abort discovery, so a
                // failing query leaves the ordinal's hipDevice_t at its zero default rather than throwing.
                HIPDriverAPI.hipDeviceGet(device, i);
                devices[i] = HIPHandles.register(new HIPHandles.Device(device.get(FFMSupport.C_INT, 0), i));
            }
        }
        return count;
    }

    /**
     * How a host thread waits for the device: {@code tornado.hip.sched} = {@code yield} (the
     * default), {@code spin}, {@code blocking} or {@code auto}. A waiting thread that spins or
     * yields keeps a core busy for as long as the device works; {@code blocking} puts it to sleep
     * until the device signals, which frees the host when many streams wait at once.
     */
    static int schedulingFlags() {
        return switch (System.getProperty("tornado.hip.sched", "yield")) {
            case "spin" -> HIPDriverAPI.HIP_DEVICE_SCHEDULE_SPIN;
            case "blocking" -> HIPDriverAPI.HIP_DEVICE_SCHEDULE_BLOCKING_SYNC;
            case "auto" -> HIPDriverAPI.HIP_DEVICE_SCHEDULE_AUTO;
            default -> HIPDriverAPI.HIP_DEVICE_SCHEDULE_YIELD;
        };
    }

    /**
     * Retains the primary context of one device and returns the handle the Java layer addresses it
     * by. On HIP that is the device itself, which the ROCm libraries (hipBLAS, MIOpen, RCCL) use too,
     * so device pointers and streams cross between TornadoVM and those libraries. Scheduling is set to
     * yield first; the runtime ignores the request when the device is already active with other flags.
     */
    static long retainPrimaryContext(long deviceId) throws HIPException {
        HIPHandles.Device device = HIPHandles.resolve(deviceId, HIPHandles.Device.class);
        if (device == null) {
            return 0;
        }
        HIPDriverAPI.hipDevicePrimaryCtxSetFlags(device.device(), schedulingFlags());
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment context = FFMSupport.allocatePointer(arena);
            int result = HIPDriverAPI.hipDevicePrimaryCtxRetain(context, device.device());
            if (result != HIPDriverAPI.HIP_SUCCESS) {
                throw new HIPException(HIPDriverAPI.describe("hipDevicePrimaryCtxRetain", result));
            }
            long contextPointer = context.get(FFMSupport.C_POINTER, 0).address();
            return HIPHandles.register(new HIPHandles.Context(contextPointer, device.device(), device.ordinal()));
        }
    }

    public List<HIPTargetDevice> getDevices() {
        return devices;
    }

    public HIPContext createContext() {
        return new HIPContext(this, devices);
    }

    public void cleanup() {
    }

    public String getProfile() {
        return clGetPlatformInfo(oclPlatformPtr, HIPPlatformInfo.CL_PLATFORM_PROFILE.getValue());
    }

    @Override
    public String getVersion() {
        return clGetPlatformInfo(oclPlatformPtr, HIPPlatformInfo.CL_PLATFORM_VERSION.getValue());
    }

    public String getName() {
        return clGetPlatformInfo(oclPlatformPtr, HIPPlatformInfo.CL_PLATFORM_NAME.getValue());
    }

    public String getVendor() {
        return clGetPlatformInfo(oclPlatformPtr, HIPPlatformInfo.CL_PLATFORM_VENDOR.getValue());
    }

    public String getExtensions() {
        return clGetPlatformInfo(oclPlatformPtr, HIPPlatformInfo.CL_PLATFORM_EXTENSIONS.getValue());
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
