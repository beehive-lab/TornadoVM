/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.spirv.tests;

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVBackend;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVBackendImpl;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;

public class TestBackend {

    public void invokeSPIRVBackend() {

        // Get Tornado Runtime
        TornadoCoreRuntime tornadoRuntime = TornadoCoreRuntime.getTornadoRuntime();

        // Get the backend from TornadoVM
        SPIRVBackend spirvBackend = tornadoRuntime.getBackend(SPIRVBackendImpl.class).getDefaultBackend();

        TornadoDevice device = tornadoRuntime.getBackend(SPIRVBackendImpl.class).getDefaultDevice();

        System.out.println("Selecting Device: " + device.getPhysicalDevice().getDeviceName());

        System.out.println("BACKEND: " + spirvBackend);

    }

    public void test() {
        invokeSPIRVBackend();
    }

    public static void main(String[] args) {
        System.out.print("Running Native: uk.ac.manchester.tornado.drivers.spirv.tests.TestBackend");
        new TestBackend().test();
    }

}
