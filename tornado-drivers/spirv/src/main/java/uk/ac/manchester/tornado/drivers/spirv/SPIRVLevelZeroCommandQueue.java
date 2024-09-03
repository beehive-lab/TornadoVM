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

import uk.ac.manchester.tornado.drivers.common.CommandQueue;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroCommandList;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroCommandQueue;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDevice;

public class SPIRVLevelZeroCommandQueue extends CommandQueue {

    private LevelZeroCommandQueue commandQueue;
    private LevelZeroCommandList commandList;
    private LevelZeroDevice device;

    public SPIRVLevelZeroCommandQueue(LevelZeroCommandQueue commandQueue, LevelZeroCommandList commandList, LevelZeroDevice device) {
        this.commandQueue = commandQueue;
        this.commandList = commandList;
        this.device = device;
    }

    public LevelZeroCommandQueue getCommandQueue() {
        return commandQueue;
    }

    public LevelZeroCommandList getCommandList() {
        return commandList;
    }

    public LevelZeroDevice getDevice() {
        return device;
    }
}
