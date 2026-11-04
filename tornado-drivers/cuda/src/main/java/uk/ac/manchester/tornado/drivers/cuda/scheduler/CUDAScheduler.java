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
package uk.ac.manchester.tornado.drivers.cuda.scheduler;

import uk.ac.manchester.tornado.api.options.TaskFlags;
import uk.ac.manchester.tornado.drivers.cuda.CUDADeviceContext;
import uk.ac.manchester.tornado.drivers.cuda.CUDATargetDevice;
import uk.ac.manchester.tornado.drivers.cuda.enums.CUDADeviceType;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

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
                return TaskFlags.BLOCK_SCHEDULER.get() ? new CUDACPUScheduler(context) : getInstanceGPUScheduler(context);
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