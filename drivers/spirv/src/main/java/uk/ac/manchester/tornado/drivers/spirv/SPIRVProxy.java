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

import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

/**
 * Proxy Intermediate Class for Calling JNI methods that can dispatch SPIRV
 * code.
 * <p>
 * There are currently two ways:
 * <p>
 * - Via OpenCL
 * <p>
 * - Via Intel Level Zero
 * <p>
 * This class is the equivalent of OCL or PTX
 */
public class SPIRVProxy {

    private static SPIRVDispatcher dispatcher;

    static {
        try {
            if (TornadoOptions.USE_LEVELZERO_FOR_SPIRV) {
                /*
                 * Use the LevelZero SPIRV Dispatcher
                 */
                dispatcher = new SPIRVLevelZeroDriver();
            } else {
                /*
                 * Use the OpenCL SPIRV Dispatcher
                 */
                dispatcher = new SPIRVOpenCLDriver();
            }
        } catch (ExceptionInInitializerError e) {
            System.out.println("[ERROR] Level-Zero Initialization is not correct: " + e.getMessage());
        }
    }

    public static int getNumPlatforms() {
        return dispatcher.getNumPlatforms();
    }

    public static SPIRVPlatform getPlatform(int platformIndex) {
        return dispatcher.getPlatform(platformIndex);
    }
}
