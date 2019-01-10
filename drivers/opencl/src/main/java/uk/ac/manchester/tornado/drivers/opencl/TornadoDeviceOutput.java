/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2019, APT Group, School of Computer Science,
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
 * Authors: Michalis Papadimitriou
 *
 */

package uk.ac.manchester.tornado.drivers.opencl;

import uk.ac.manchester.tornado.runtime.TornadoAcceleratorDriver;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;

/**
 * Run:
 * 
 * <p>
 * <code>
 * $ tornado uk.ac.manchester.tornado.drivers.opencl.TornadoDeviceOutput
 * </code>
 * </p>
 *
 */
public class TornadoDeviceOutput {

    public static void main(String[] args) {

        StringBuffer bufferDriversAndPlatforms = new StringBuffer().append("\n");
        StringBuffer bufferDevices = new StringBuffer();
        final int numDrivers = TornadoCoreRuntime.getTornadoRuntime().getNumDrivers();
        bufferDriversAndPlatforms.append("Number of Tornado drivers: " + numDrivers + "\n");

        for (int driverIndex = 0; driverIndex < numDrivers; driverIndex++) {
            final TornadoAcceleratorDriver driver = TornadoCoreRuntime.getTornadoRuntime().getDriver(driverIndex);
            final int numDevices = driver.getDeviceCount();
            bufferDriversAndPlatforms.append("Total number of devices  : " + numDevices + "\n");
            for (int deviceIndex = 0; deviceIndex < numDevices; deviceIndex++) {
                bufferDevices.append("Tornado device=" + driverIndex + ":" + deviceIndex + "\n");
                bufferDevices.append("\t" + driver.getDevice(deviceIndex)).append("\n\n");
            }
        }

        System.out.println(bufferDriversAndPlatforms.toString());
        bufferDevices.setLength(bufferDevices.length() - 1);
        System.out.println(bufferDevices.toString());
    }
}
