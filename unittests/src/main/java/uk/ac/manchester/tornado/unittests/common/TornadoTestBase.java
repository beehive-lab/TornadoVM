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
 * Authors: Juan Fumero, Michalis Papadimitriou
 *
 */

package uk.ac.manchester.tornado.unittests.common;

import org.junit.Before;

import uk.ac.manchester.tornado.api.TargetDeviceType;
import uk.ac.manchester.tornado.api.runtinface.TornadoGenericDriver;
import uk.ac.manchester.tornado.runtime.TornadoRuntime;

public abstract class TornadoTestBase {

    @Before
    public void before() {
        for (int i = 0; i < TornadoRuntime.getTornadoRuntime().getNumDrivers(); i++) {
            final TornadoGenericDriver driver = uk.ac.manchester.tornado.api.runtinface.TornadoRuntime.getTornadoRuntime().getDriver(i);
            driver.getDefaultDevice().reset();
        }
    }

    public TargetDeviceType getDefaultDeviceType() {
        final TornadoGenericDriver driver = uk.ac.manchester.tornado.api.runtinface.TornadoRuntime.getTornadoRuntime().getDriver(0);
        return driver.getDeviceType();
        // TornadoDriver driver =
        // TornadoRuntime.getTornadoRuntime().getDriver(0);
        // OCLTornadoDevice defaultDevice = (OCLTornadoDevice)
        // driver.getDefaultDevice();
        // OCLDevice device = defaultDevice.getDevice();
        // return device.getDeviceType();
    }
}
