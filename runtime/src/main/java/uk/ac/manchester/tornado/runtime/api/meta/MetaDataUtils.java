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
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.runtime.api.meta;

import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getTornadoRuntime;

import uk.ac.manchester.tornado.runtime.TornadoAcceleratorDriver;
import uk.ac.manchester.tornado.runtime.common.TornadoAcceleratorDevice;

public final class MetaDataUtils {

    public static TornadoAcceleratorDevice resolveDevice(String device) {
        final String[] ids = device.split(":");
        final TornadoAcceleratorDriver driver = getTornadoRuntime().getDriver(Integer.parseInt(ids[0]));
        return (TornadoAcceleratorDevice) driver.getDevice(Integer.parseInt(ids[1]));
    }

    public static int[] resolveDriverDeviceIndexes(String device) {
        final String[] ids = device.split(":");
        return new int[] { Integer.parseInt(ids[0]), Integer.parseInt(ids[1]) };
    }
}
