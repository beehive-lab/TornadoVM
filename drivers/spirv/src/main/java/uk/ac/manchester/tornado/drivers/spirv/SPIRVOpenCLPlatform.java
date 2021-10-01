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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.spirv;

import java.util.ArrayList;
import java.util.List;

import uk.ac.manchester.tornado.drivers.opencl.OCLExecutionEnvironment;
import uk.ac.manchester.tornado.drivers.opencl.TornadoPlatform;

public class SPIRVOpenCLPlatform implements SPIRVPlatform {

    private TornadoPlatform oclPlatform;
    private OCLExecutionEnvironment context;
    private List<SPIRVDevice> spirvDevices;

    public SPIRVOpenCLPlatform(int platformIndex, TornadoPlatform oclPlatform) {
        this.oclPlatform = oclPlatform;
        context = this.oclPlatform.createContext();

        spirvDevices = new ArrayList<>();

        for (int i = 0; i < context.getNumDevices(); i++) {
            SPIRVDevice spirvDevice = new SPIRVOCLDevice(platformIndex, i, context.devices().get(i));
            spirvDevices.add(spirvDevice);
        }

    }

    public TornadoPlatform getPlatform() {
        return this.oclPlatform;
    }

    @Override
    public int getNumDevices() {
        return context.getNumDevices();
    }

    @Override
    public SPIRVDevice getDevice(int deviceIndex) {
        return spirvDevices.get(deviceIndex);
    }

    @Override
    public SPIRVOCLContext createContext() {
        if (context == null) {
            context = oclPlatform.createContext();
        }
        return new SPIRVOCLContext(this, spirvDevices, context);
    }

}
