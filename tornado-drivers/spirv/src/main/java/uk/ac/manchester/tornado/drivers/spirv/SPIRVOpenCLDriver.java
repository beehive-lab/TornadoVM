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

import uk.ac.manchester.tornado.drivers.opencl.OpenCL;
import uk.ac.manchester.tornado.drivers.opencl.TornadoPlatform;

/**
 * Proof of Concept plugin in the OpenCL driver for SPIRV dispatch.
 * 
 * Initially we focus on the level-zero. However we will keep this place-holder
 * for later use of OpenCL as a SPIRV dispatcher.
 * 
 */
public class SPIRVOpenCLDriver implements SPIRVDispatcher {

    private List<SPIRVPlatform> spirvPlatforms;

    public SPIRVOpenCLDriver() {
        int numOpenCLPlatforms = OpenCL.getNumPlatforms();
        spirvPlatforms = new ArrayList<>();
        for (int platformIndex = 0; platformIndex < numOpenCLPlatforms; platformIndex++) {
            TornadoPlatform oclPlatform = OpenCL.getPlatform(platformIndex);
            SPIRVOpenCLPlatform spirvOCLPlatform = new SPIRVOpenCLPlatform(platformIndex, oclPlatform);
            spirvPlatforms.add(spirvOCLPlatform);
        }
    }

    @Override
    public int getNumPlatforms() {
        return OpenCL.getNumPlatforms();
    }

    @Override
    public SPIRVPlatform getPlatform(int index) {
        return spirvPlatforms.get(index);
    }
}
