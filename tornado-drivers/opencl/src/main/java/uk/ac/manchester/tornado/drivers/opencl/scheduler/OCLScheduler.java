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
package uk.ac.manchester.tornado.drivers.opencl.scheduler;

import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContext;
import uk.ac.manchester.tornado.drivers.opencl.OCLTargetDevice;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLDeviceType;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

public class OCLScheduler {

    private static final String AMD_VENDOR = "Advanced Micro Devices";
    private static final String NVIDIA = "NVIDIA";
    private static final int NVIDIA_MAJOR_VERSION_GENERIC_SCHEDULER = 550;
    private static final int NVIDIA_MINOR_VERSION_GENERIC_SCHEDULER = 67;

    private static boolean isDriverVersionCompatible(OCLTargetDevice device) {
        int majorVersion = Integer.parseInt(device.getDriverVersion().split("\\.")[0]);
        int minorVersion = Integer.parseInt(device.getDriverVersion().split("\\.")[1]);

        return majorVersion >= NVIDIA_MAJOR_VERSION_GENERIC_SCHEDULER && minorVersion >= NVIDIA_MINOR_VERSION_GENERIC_SCHEDULER;
    }

    private static OCLKernelScheduler getInstanceGPUScheduler(final OCLDeviceContext context) {
        OCLTargetDevice device = context.getDevice();
        if (device.getDeviceVendor().contains(AMD_VENDOR)) {
            return new OCLAMDScheduler(context);
        } else if (device.getDeviceVendor().contains(NVIDIA)) {
            if (isDriverVersionCompatible(device)) {
                return new OCLNVIDIAGPUScheduler(context);
            } else {
                return new OCLGenericGPUScheduler(context);
            }
        } else {
            return new OCLGenericGPUScheduler(context);
        }
    }

    public static OCLKernelScheduler instanceScheduler(OCLDeviceType type, final OCLDeviceContext context) {
        switch (type) {
            case CL_DEVICE_TYPE_GPU:
                return getInstanceGPUScheduler(context);
            case CL_DEVICE_TYPE_ACCELERATOR:
                return context.isPlatformFPGA() ? new OCLFPGAScheduler(context) : new OCLCPUScheduler(context);
            case CL_DEVICE_TYPE_CPU:
                return TornadoOptions.USE_BLOCK_SCHEDULER ? new OCLCPUScheduler(context) : getInstanceGPUScheduler(context);
            default:
                new TornadoLogger().fatal("No scheduler available for device: %s", context);
                break;
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
