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
package uk.ac.manchester.tornado.drivers.metal.scheduler;

import uk.ac.manchester.tornado.drivers.metal.MetalDeviceContext;
import uk.ac.manchester.tornado.drivers.metal.MetalTargetDevice;
import uk.ac.manchester.tornado.drivers.metal.enums.MetalDeviceType;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

public class MetalScheduler {

    /**
     * Supported Vendors for specific Thread-Schedulers
     */
    public enum SUPPORTED_VENDORS {
        AMD("Advanced Micro Devices"), //
        CODEPLAY("Codeplay"), //
        INTEL("Intel"), //
        NVIDIA("NVIDIA"), //
        XILINX("Xilinx");

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

    private static boolean isDriverVersionCompatible(MetalTargetDevice device) {
        int majorVersion = Integer.parseInt(device.getDriverVersion().split("\\.")[0]);
        int minorVersion = Integer.parseInt(device.getDriverVersion().split("\\.")[1]);

        if (majorVersion == NVIDIA_MAJOR_VERSION_GENERIC_SCHEDULER && minorVersion >= NVIDIA_MINOR_VERSION_GENERIC_SCHEDULER) {
            return true;
        } else {
            return majorVersion > NVIDIA_MAJOR_VERSION_GENERIC_SCHEDULER;
        }
    }

    private static MetalKernelScheduler getInstanceGPUScheduler(final MetalDeviceContext context) {
        MetalTargetDevice device = context.getDevice();
        if (device.getDeviceVendor().contains(SUPPORTED_VENDORS.AMD.getName())) {
            return new MetalAMDScheduler(context);
        } else if (device.getDeviceVendor().contains(SUPPORTED_VENDORS.NVIDIA.getName())) {
            return isDriverVersionCompatible(device) ? new MetalNVIDIAGPUScheduler(context) : new MetalGenericGPUScheduler(context);
        } else {
            return new MetalGenericGPUScheduler(context);
        }
    }

    public static MetalKernelScheduler instanceScheduler(MetalDeviceType type, final MetalDeviceContext context) {
        switch (type) {
            case CL_DEVICE_TYPE_GPU -> {
                return getInstanceGPUScheduler(context);
            }
            case CL_DEVICE_TYPE_ACCELERATOR -> {
                if (context.getDevice().getDeviceVendor().contains(SUPPORTED_VENDORS.CODEPLAY.getName())) {
                    return getInstanceGPUScheduler(context);
                } else if (context.isPlatformFPGA()) {
                    return new MetalFPGAScheduler(context);
                } else {
                    return new MetalCPUScheduler(context);
                }
            }
            case CL_DEVICE_TYPE_CPU -> {
                return TornadoOptions.USE_BLOCK_SCHEDULER ? new MetalCPUScheduler(context) : getInstanceGPUScheduler(context);
            }
            default -> new TornadoLogger().fatal("No scheduler available for device: %s", context);
        }
        return null;
    }

    public static MetalKernelScheduler create(final MetalDeviceContext context) {
        if (context.getDevice().getDeviceType() != null) {
            MetalDeviceType type = context.getDevice().getDeviceType();
            return instanceScheduler(type, context);
        }
        return null;
    }
}