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

import java.io.FileNotFoundException;

import uk.ac.manchester.beehivespirvtoolkit.lib.SPIRVTool;
import uk.ac.manchester.beehivespirvtoolkit.lib.disassembler.Disassembler;
import uk.ac.manchester.beehivespirvtoolkit.lib.disassembler.SPIRVDisassemblerOptions;
import uk.ac.manchester.beehivespirvtoolkit.lib.disassembler.SPVFileReader;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVInstalledCode;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVLevelZeroInstalledCode;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroContext;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDevice;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroKernel;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroModule;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeBuildLogHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeKernelDescriptor;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeKernelHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeModuleDescriptor;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeModuleFormat;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeModuleHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeResult;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.utils.LevelZeroUtils;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class SPIRVLevelZeroCodeCache extends SPIRVCodeCache {

    public SPIRVLevelZeroCodeCache(SPIRVDeviceContext deviceContext) {
        super(deviceContext);
    }

    @Override
    public synchronized SPIRVInstalledCode installSPIRVBinary(TaskMetaData meta, String id, String entryPoint, String pathToFile) {
        ZeModuleHandle module = new ZeModuleHandle();
        ZeModuleDescriptor moduleDesc = new ZeModuleDescriptor();
        ZeBuildLogHandle buildLog = new ZeBuildLogHandle();
        moduleDesc.setFormat(ZeModuleFormat.ZE_MODULE_FORMAT_IL_SPIRV);
        moduleDesc.setBuildFlags("-ze-opt-level 2 -ze-opt-large-register-file");

        checkBinaryFileExists(pathToFile);

        SPIRVContext spirvContext = deviceContext.getSpirvContext();
        SPIRVLevelZeroContext levelZeroContext = (SPIRVLevelZeroContext) spirvContext;
        LevelZeroContext context = levelZeroContext.getLevelZeroContext();

        SPIRVDevice spirvDevice = deviceContext.getDevice();
        SPIRVLevelZeroDevice levelZeroDevice = (SPIRVLevelZeroDevice) spirvDevice;
        LevelZeroDevice device = levelZeroDevice.getDeviceRuntime();

        int result = context.zeModuleCreate(context.getDefaultContextPtr(), device.getDeviceHandlerPtr(), moduleDesc, module, buildLog, pathToFile);
        LevelZeroUtils.errorLog("zeModuleCreate", result);

        if (result != ZeResult.ZE_RESULT_SUCCESS) {
            // Print Logs
            int[] sizeLog = new int[1];
            String[] errorMessage = new String[1];
            result = context.zeModuleBuildLogGetString(buildLog, sizeLog, errorMessage);
            LevelZeroUtils.errorLog("zeModuleBuildLogGetString", result);
            System.out.println("----------------");
            System.out.println("SPIR-V Kernel Errors from LevelZero:");
            System.out.println(errorMessage[0]);
            System.out.println("----------------");
            throw new TornadoBailoutRuntimeException(STR."[Build SPIR-V ERROR]\{errorMessage[0]}");
        }

        if (meta.isPrintKernelEnabled()) {
            SPVFileReader reader;
            try {
                reader = new SPVFileReader(pathToFile);
            } catch (FileNotFoundException e) {
                throw new TornadoBailoutRuntimeException(e.getMessage());
            }
            SPIRVDisassemblerOptions disassemblerOptions = new SPIRVDisassemblerOptions(true, true, false, true, false);
            SPIRVTool spirvTool = new Disassembler(reader, System.out, disassemblerOptions);
            try {
                spirvTool.run();
            } catch (Exception e) {
                throw new TornadoBailoutRuntimeException(e.getMessage());
            }
        }

        // Create Module Object
        LevelZeroModule levelZeroModule = new LevelZeroModule(module, moduleDesc, buildLog);

        // Destroy Log
        result = levelZeroModule.zeModuleBuildLogDestroy(buildLog);
        LevelZeroUtils.errorLog("zeModuleBuildLogDestroy", result);

        ZeKernelDescriptor kernelDesc = new ZeKernelDescriptor();
        ZeKernelHandle kernel = new ZeKernelHandle();
        if (TornadoOptions.DEBUG) {
            Logger.traceRuntime(Logger.BACKEND.SPIRV, STR."Set SPIR-V entry point: \{entryPoint}");
        }
        kernelDesc.setKernelName(entryPoint);
        result = levelZeroModule.zeKernelCreate(module.getPtrZeModuleHandle(), kernelDesc, kernel);
        LevelZeroUtils.errorLog("zeKernelCreate", result);

        // Create a Level Zero kernel Object
        LevelZeroKernel levelZeroKernel = new LevelZeroKernel(kernelDesc, kernel, levelZeroModule);

        SPIRVModule spirvModule = new SPIRVLevelZeroModule(levelZeroModule, levelZeroKernel, entryPoint, pathToFile);
        SPIRVInstalledCode installedCode = new SPIRVLevelZeroInstalledCode(id, spirvModule, deviceContext);

        // Install module in the code cache
        cache.put(STR."\{id}-\{entryPoint}", installedCode);
        return installedCode;
    }
}
