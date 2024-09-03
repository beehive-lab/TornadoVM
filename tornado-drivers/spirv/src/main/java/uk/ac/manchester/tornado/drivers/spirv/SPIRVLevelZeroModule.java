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
package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroKernel;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroModule;

public class SPIRVLevelZeroModule implements SPIRVModule {

    private final LevelZeroModule levelZeroModule;
    private final LevelZeroKernel kernel;
    private final String entryPoint;
    private final String pathToSPIRVBinary;

    public SPIRVLevelZeroModule(LevelZeroModule levelZeroModule, LevelZeroKernel kernel, String entryPoint, String pathToSPIRVBinary) {
        this.levelZeroModule = levelZeroModule;
        this.kernel = kernel;
        this.entryPoint = entryPoint;
        this.pathToSPIRVBinary = pathToSPIRVBinary;
    }

    public LevelZeroModule getLevelZeroModule() {
        return levelZeroModule;
    }

    public LevelZeroKernel getKernel() {
        return kernel;
    }

    @Override
    public String getEntryPoint() {
        return entryPoint;
    }

    @Override
    public String getPathToSPIRVBinary() {
        return pathToSPIRVBinary;
    }

}
