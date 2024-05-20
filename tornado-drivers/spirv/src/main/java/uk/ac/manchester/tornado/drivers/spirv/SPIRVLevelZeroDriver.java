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

import java.util.ArrayList;
import java.util.List;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDriver;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDriverHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeInitFlag;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeResult;

public class SPIRVLevelZeroDriver implements SPIRVDispatcher {

    private final ZeDriverHandle driversHandler;
    private final List<SPIRVPlatform> spirvPlatforms;

    public SPIRVLevelZeroDriver() {
        LevelZeroDriver driver = new LevelZeroDriver();
        int errorCode = driver.zeInit(ZeInitFlag.ZE_INIT_FLAG_GPU_ONLY);
        if (errorCode != ZeResult.ZE_RESULT_SUCCESS) {
            throw new TornadoRuntimeException("[ERROR] Level Zero Driver Not Found");
        }

        int[] numDrivers = new int[1];
        errorCode = driver.zeDriverGet(numDrivers, null);
        if (errorCode != ZeResult.ZE_RESULT_SUCCESS) {
            throw new TornadoRuntimeException("[ERROR] Level Zero Driver Not Found");
        }
        spirvPlatforms = new ArrayList<>();

        driversHandler = new ZeDriverHandle(numDrivers[0]);
        driver.zeDriverGet(numDrivers, driversHandler);
        for (int i = 0; i < numDrivers[0]; i++) {
            SPIRVPlatform platform = new SPIRVLevelZeroPlatform(driver, driversHandler, i);
            spirvPlatforms.add(platform);
        }
    }

    @Override
    public int getNumPlatforms() {
        return driversHandler.getNumDrivers();
    }

    @Override
    public SPIRVPlatform getPlatform(int index) {
        return spirvPlatforms.get(index);
    }

}
