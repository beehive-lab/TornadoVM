/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, 2024, APT Group, Department of Computer Science,
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
 */

package uk.ac.manchester.tornado.drivers;

import java.util.Arrays;
import java.util.HashMap;

import uk.ac.manchester.tornado.api.TornadoBackend;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.drivers.common.utils.ColoursTerminal;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

/**
 * Program to query all devices reachable from TornadoVM.
 *
 * Run as follows:
 * <p>
 * <code>
 * $ tornado uk.ac.manchester.tornado.drivers.TornadoDeviceQuery
 * </code>
 * </p>
 *
 */
public class TornadoDeviceQuery {

    private static HashMap<TornadoVMBackendType, String> colourMapping;

    static {
        colourMapping = new HashMap<>();
        colourMapping.put(TornadoVMBackendType.OPENCL, ColoursTerminal.CYAN);
        colourMapping.put(TornadoVMBackendType.PTX, ColoursTerminal.GREEN);
        colourMapping.put(TornadoVMBackendType.SPIRV, ColoursTerminal.PURPLE);
        colourMapping.put(TornadoVMBackendType.METAL, ColoursTerminal.YELLOW);
    }

    private static String formatSize(long v) {
        if (v < 1024) {
            return v + " B";
        }
        int z = (63 - Long.numberOfLeadingZeros(v)) / 10;
        return String.format("%.1f %sB", (double) v / (1L << (z * 10)), " KMGTPE".charAt(z));
    }

    public static void main(String[] args) {

        String verboseFlag = "";

        if (args.length != 0) {
            verboseFlag = args[0];
        }

        StringBuilder deviceInfoBuffer = new StringBuilder().append("\n");
        final int numDrivers = TornadoRuntimeProvider.getTornadoRuntime().getNumBackends();
        deviceInfoBuffer.append("Number of Tornado drivers: " + numDrivers + "\n");

        for (int driverIndex = 0; driverIndex < numDrivers; driverIndex++) {
            final TornadoBackend driver = TornadoRuntimeProvider.getTornadoRuntime().getBackend(driverIndex);
            TornadoVMBackendType backendType = TornadoRuntimeProvider.getTornadoRuntime().getBackendType(driverIndex);
            String colour = colourMapping.getOrDefault(backendType, "");
            final int numDevices = driver.getNumDevices();
            deviceInfoBuffer.append("Driver: " + colour + driver.getName() + ColoursTerminal.RESET + "\n");
            deviceInfoBuffer.append("  Total number of " + driver.getName() + " devices  : " + numDevices + "\n");
            for (int deviceIndex = 0; deviceIndex < numDevices; deviceIndex++) {
                deviceInfoBuffer.append("  Tornado device=" + driverIndex + ":" + deviceIndex);
                if (driverIndex == 0 && deviceIndex == 0) {
                    deviceInfoBuffer.append("  (DEFAULT)");
                }
                deviceInfoBuffer.append("\n");
                deviceInfoBuffer.append("\t" + colour + backendType.toString() + ColoursTerminal.RESET + " -- " + driver.getDevice(deviceIndex)).append("\n");
                if (verboseFlag.equals("verbose")) {
                    deviceInfoBuffer.append("\t\t" + "Global Memory Size: " + formatSize(driver.getDevice(deviceIndex).getMaxGlobalMemory()) + "\n");
                    deviceInfoBuffer.append("\t\t" + "Local Memory Size: " + formatSize(driver.getDevice(deviceIndex).getDeviceLocalMemorySize()) + "\n");
                    deviceInfoBuffer.append("\t\t" + "Workgroup Dimensions: " + driver.getDevice(deviceIndex).getDeviceMaxWorkgroupDimensions().length + "\n");
                    if (!TornadoOptions.VIRTUAL_DEVICE_ENABLED) {
                        deviceInfoBuffer.append("\t\t" + "Total Number of Block Threads: " + Arrays.toString(driver.getDevice(deviceIndex).getPhysicalDevice().getDeviceMaxWorkGroupSize()) + "\n");
                    }
                    deviceInfoBuffer.append("\t\t" + "Max WorkGroup Configuration: " + Arrays.toString(driver.getDevice(deviceIndex).getDeviceMaxWorkgroupDimensions()) + "\n");
                    deviceInfoBuffer.append("\t\t" + "Device OpenCL C version: " + driver.getDevice(deviceIndex).getDeviceOpenCLCVersion() + "\n");
                }
                deviceInfoBuffer.append("\n");
            }
        }
        System.out.println(deviceInfoBuffer.toString());
    }
}
