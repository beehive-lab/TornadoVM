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

import uk.ac.manchester.tornado.drivers.spirv.SPIRVCodeCache;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVContext;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDeviceContext;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVLevelZeroCodeCache;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVLevelZeroPlatform;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVOCLCodeCache;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVOpenCLPlatform;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVPlatform;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVRuntimeImpl;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVInstalledCode;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResult;
import uk.ac.manchester.tornado.runtime.tasks.meta.ScheduleContext;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

/**
 * How to test?
 *
 * <code>
 * $ tornado --printKernel uk.ac.manchester.tornado.drivers.spirv.tests.TestSPIRVTornadoCompiler
 * </code>
 */
public class TestSPIRVTornadoCompiler {

    public static void main(String[] args) {

        SPIRVPlatform platform = SPIRVRuntimeImpl.getInstance().getPlatform(0);
        SPIRVContext context = platform.createContext();
        SPIRVDeviceContext deviceContext = context.getDeviceContext(0);

        SPIRVCodeCache codeCache = null;
        if (platform instanceof SPIRVOpenCLPlatform) {
            codeCache = new SPIRVOCLCodeCache(deviceContext);
        } else if (platform instanceof SPIRVLevelZeroPlatform) {
            codeCache = new SPIRVLevelZeroCodeCache(deviceContext);
        }

        ScheduleContext scheduleMetaData = new ScheduleContext("SPIRV-Backend");
        TaskDataContext task = new TaskDataContext(scheduleMetaData, "saxpy");
        new SPIRVCompilationResult("saxpy", "saxpy", task);

        String tornadoSDK = System.getenv("TORNADOVM_HOME");
        String pathToSPIRVBinaryFile = tornadoSDK + "/examples/generated/add.spv";

        SPIRVInstalledCode code = codeCache.installSPIRVBinary(task, "add", "add", pathToSPIRVBinaryFile);
        String generatedCode = code.getGeneratedSourceCode();

        if (scheduleMetaData.isPrintKernelEnabled()) {
            System.out.println(generatedCode);
        }
    }
}
