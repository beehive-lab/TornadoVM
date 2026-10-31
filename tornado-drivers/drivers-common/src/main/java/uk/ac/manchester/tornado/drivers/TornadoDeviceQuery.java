/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, 2024, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.drivers;

import java.util.Arrays;
import java.util.HashMap;

import uk.ac.manchester.tornado.api.TornadoBackend;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.options.RuntimeFlags;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.drivers.common.utils.ColoursTerminal;

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
        colourMapping.put(TornadoVMBackendType.CUDA, ColoursTerminal.GREEN);
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
                    if (!RuntimeFlags.VIRTUAL_DEVICE.get()) {
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
