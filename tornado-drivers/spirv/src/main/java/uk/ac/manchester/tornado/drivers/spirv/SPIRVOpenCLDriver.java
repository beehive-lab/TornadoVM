/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, 2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.spirv;

import java.util.ArrayList;
import java.util.List;

import uk.ac.manchester.tornado.drivers.opencl.OpenCL;
import uk.ac.manchester.tornado.drivers.opencl.TornadoPlatformInterface;

public class SPIRVOpenCLDriver implements SPIRVDispatcher {

    private final List<SPIRVPlatform> spirvOpenCLPlatforms;

    public SPIRVOpenCLDriver() {
        int numOpenCLPlatforms = OpenCL.getNumPlatforms();
        spirvOpenCLPlatforms = new ArrayList<>();

        // From all OpenCL platforms, we inspect which one/s contain device/s that support
        // SPIR-V >= 1.2
        for (int platformIndex = 0; platformIndex < numOpenCLPlatforms; platformIndex++) {
            TornadoPlatformInterface oclPlatform = OpenCL.getPlatform(platformIndex);
            if (oclPlatform.isSPIRVSupported()) {
                // We only add the platforms that support SPIR-V
                spirvOpenCLPlatforms.add(new SPIRVOpenCLPlatform(platformIndex, oclPlatform));
            }
        }
    }

    @Override
    public int getNumPlatforms() {
        return spirvOpenCLPlatforms.size();
    }

    @Override
    public SPIRVPlatform getPlatform(int index) {
        return spirvOpenCLPlatforms.get(index);
    }
}
