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
package uk.ac.manchester.tornado.drivers.opencl.scheduler;

import uk.ac.manchester.tornado.api.options.TaskFlags;
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContext;
import uk.ac.manchester.tornado.drivers.opencl.OCLTargetDevice;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLDeviceType;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class OCLScheduler {

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

    private static final int NVIDIA_MAJOR_VERSION_GENERIC_SCHEDULER = 550;
    private static final int NVIDIA_MINOR_VERSION_GENERIC_SCHEDULER = 67;

    private static boolean isDriverVersionCompatible(OCLTargetDevice device) {
        int majorVersion = Integer.parseInt(device.getDriverVersion().split("\\.")[0]);
        int minorVersion = Integer.parseInt(device.getDriverVersion().split("\\.")[1]);

        if (majorVersion == NVIDIA_MAJOR_VERSION_GENERIC_SCHEDULER && minorVersion >= NVIDIA_MINOR_VERSION_GENERIC_SCHEDULER) {
            return true;
        } else {
            return majorVersion > NVIDIA_MAJOR_VERSION_GENERIC_SCHEDULER;
        }
    }

    private static OCLKernelScheduler getInstanceGPUScheduler(final OCLDeviceContext context) {
        OCLTargetDevice device = context.getDevice();
        if (device.getDeviceVendor().contains(SUPPORTED_VENDORS.AMD.getName())) {
            return new OCLAMDScheduler(context);
        } else if (device.getDeviceVendor().contains(SUPPORTED_VENDORS.NVIDIA.getName())) {
            return isDriverVersionCompatible(device) ? new OCLNVIDIAGPUScheduler(context) : new OCLGenericGPUScheduler(context);
        } else {
            return new OCLGenericGPUScheduler(context);
        }
    }

    public static OCLKernelScheduler instanceScheduler(OCLDeviceType type, final OCLDeviceContext context) {
        switch (type) {
            case CL_DEVICE_TYPE_GPU -> {
                return getInstanceGPUScheduler(context);
            }
            case CL_DEVICE_TYPE_ACCELERATOR -> {
                if (context.getDevice().getDeviceVendor().contains(SUPPORTED_VENDORS.CODEPLAY.getName())) {
                    return getInstanceGPUScheduler(context);
                } else {
                    return new OCLCPUScheduler(context);
                }
            }
            case CL_DEVICE_TYPE_CPU -> {
                return TaskFlags.BLOCK_SCHEDULER.get() ? new OCLCPUScheduler(context) : getInstanceGPUScheduler(context);
            }
            default -> new TornadoLogger().fatal("No scheduler available for device: %s", context);
        }
        return null;
    }

    public static OCLKernelScheduler create(final OCLDeviceContext context) {
        if (context.getDevice().getDeviceType() != null) {
            OCLDeviceType type = context.getDevice().getDeviceType();
            return instanceScheduler(type, context);
        }
        return null;
    }
}