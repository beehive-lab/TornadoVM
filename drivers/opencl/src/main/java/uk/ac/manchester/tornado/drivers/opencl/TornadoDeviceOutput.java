/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
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

import static uk.ac.manchester.tornado.runtime.TornadoRuntime.getTornadoRuntime;

import uk.ac.manchester.tornado.drivers.opencl.runtime.OCLTornadoDevice;
import uk.ac.manchester.tornado.runtime.TornadoDriver;
import uk.ac.manchester.tornado.runtime.api.TaskSchedule;

public class TornadoDeviceOutput {

    public static void main(String[] args) {
        final int numDrivers = getTornadoRuntime().getNumDrivers();
        System.out.printf("Current number of drivers: %d \n", numDrivers);
        for (int driverIndex = 0; driverIndex < numDrivers; driverIndex++) {
            TaskSchedule s0 = new TaskSchedule("s" + driverIndex);
            final TornadoDriver driver = getTornadoRuntime().getDriver(driverIndex);
            final int numDevices = driver.getDeviceCount();
            System.out.println(numDevices);
            for (int deviceIndex = 0; deviceIndex < numDevices; deviceIndex++) {
                System.out.println("s" + driverIndex + ".device=" + driverIndex + ":" + deviceIndex);
                // setProperty("s" + driverIndex + ".device=", driverIndex + ":" + deviceIndex);
                // s0.setDevice(driver.getDevice(deviceIndex));
                OCLTornadoDevice oclDevice = new OCLTornadoDevice(driverIndex, deviceIndex);
                // System.out.println(setDevice(driver.getDevice(deviceIndex));
                System.out.println(driver.getDevice(deviceIndex));

            }
        }
    }
}
