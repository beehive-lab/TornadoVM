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
package uk.ac.manchester.tornado.drivers.spirv.tests;

import uk.ac.manchester.tornado.drivers.spirv.SPIRVCodeCache;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVContext;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDeviceContext;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVLevelZeroCodeCache;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVPlatform;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVProxy;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVInstalledCode;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResult;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.tasks.meta.ScheduleMetaData;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class TestSPIRVTornadoCompiler {

    public static void main(String[] args) {

        SPIRVPlatform platform = SPIRVProxy.getPlatform(0);
        SPIRVContext context = platform.createContext();
        SPIRVDeviceContext deviceContext = context.getDeviceContext(0);
        SPIRVCodeCache codeCache = new SPIRVLevelZeroCodeCache(deviceContext);

        ScheduleMetaData scheduleMetaData = new ScheduleMetaData("SPIRV-Backend");
        TaskMetaData task = new TaskMetaData(scheduleMetaData, "saxpy");
        new SPIRVCompilationResult("saxpy", "saxpy", task);

        // byte[] binary = ...
        byte[] binary = new byte[100];
        SPIRVInstalledCode code = codeCache.installSPIRVBinary(task, "saxpy", "saxpy", binary);
        String generatedCode = code.getGeneratedSourceCode();

        if (TornadoOptions.PRINT_SOURCE) {
            System.out.println(generatedCode);
        }
    }
}
