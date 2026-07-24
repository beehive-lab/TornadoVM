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
package uk.ac.manchester.tornado.drivers.cuda.scheduler;

import uk.ac.manchester.tornado.drivers.cuda.CUDADeviceContext;
import uk.ac.manchester.tornado.drivers.cuda.CUDATargetDevice;
import uk.ac.manchester.tornado.drivers.cuda.enums.CUDADeviceType;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

public class CUDAScheduler {

    /**
     * Supported Vendors for specific Thread-Schedulers
     */
    public enum SUPPORTED_VENDORS {
        AMD("Advanced Micro Devices"), //
        CODEPLAY("Codeplay"), //
        INTEL("Intel"), //
        NVIDIA("NVIDIA");

        private String name;

        SUPPORTED_VENDORS(String name) {
            this.name = name;
        }

        String getName() {
            return this.name;
        }
    }

    // CL_DRIVER_VERSION on the CUDA backend reports the CUDA API version returned by
    // cuDriverGetVersion() (e.g. "12.4" / "13.0"), NOT the NVIDIA display-driver version.
    // The tiled NVIDIA scheduler caps 2D/3D block dimensions so register-heavy kernels stay
    // within the per-block register file; the generic fallback can request 1024-thread blocks
    // that fail to launch with CUDA_ERROR_LAUNCH_OUT_OF_RESOURCES. Select the tiled scheduler
    // for every CUDA version this NVRTC backend supports.
    private static final int MIN_CUDA_MAJOR_FOR_TILED_SCHEDULER = 12;

    private static boolean isDriverVersionCompatible(CUDATargetDevice device) {
        try {
            int cudaMajorVersion = Integer.parseInt(device.getDriverVersion().split("\\.")[0]);
            return cudaMajorVersion >= MIN_CUDA_MAJOR_FOR_TILED_SCHEDULER;
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return false;
        }
    }

    private static CUDAKernelScheduler getInstanceGPUScheduler(final CUDADeviceContext context) {
        CUDATargetDevice device = context.getDevice();
        if (device.getDeviceVendor().contains(SUPPORTED_VENDORS.AMD.getName())) {
            return new CUDAAMDScheduler(context);
        } else if (device.getDeviceVendor().contains(SUPPORTED_VENDORS.NVIDIA.getName())) {
            return isDriverVersionCompatible(device) ? new CUDANVIDIAGPUScheduler(context) : new CUDAGenericGPUScheduler(context);
        } else {
            return new CUDAGenericGPUScheduler(context);
        }
    }

    public static CUDAKernelScheduler instanceScheduler(CUDADeviceType type, final CUDADeviceContext context) {
        switch (type) {
            case CL_DEVICE_TYPE_GPU -> {
                return getInstanceGPUScheduler(context);
            }
            case CL_DEVICE_TYPE_ACCELERATOR -> {
                if (context.getDevice().getDeviceVendor().contains(SUPPORTED_VENDORS.CODEPLAY.getName())) {
                    return getInstanceGPUScheduler(context);
                } else {
                    return new CUDACPUScheduler(context);
                }
            }
            case CL_DEVICE_TYPE_CPU -> {
                return TornadoOptions.USE_BLOCK_SCHEDULER ? new CUDACPUScheduler(context) : getInstanceGPUScheduler(context);
            }
            default -> new TornadoLogger().fatal("No scheduler available for device: %s", context);
        }
        return null;
    }

    public static CUDAKernelScheduler create(final CUDADeviceContext context) {
        if (context.getDevice().getDeviceType() != null) {
            CUDADeviceType type = context.getDevice().getDeviceType();
            return instanceScheduler(type, context);
        }
        return null;
    }
}