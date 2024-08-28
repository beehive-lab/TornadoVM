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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import uk.ac.manchester.beehivespirvtoolkit.lib.SPIRVTool;
import uk.ac.manchester.beehivespirvtoolkit.lib.disassembler.Disassembler;
import uk.ac.manchester.beehivespirvtoolkit.lib.disassembler.SPIRVDisassemblerOptions;
import uk.ac.manchester.beehivespirvtoolkit.lib.disassembler.SPVFileReader;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.opencl.OCLErrorCode;
import uk.ac.manchester.tornado.drivers.opencl.OCLTargetDevice;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVInstalledCode;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVOCLInstalledCode;
import uk.ac.manchester.tornado.drivers.spirv.ocl.SPIRVOCLNativeDispatcher;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

public class SPIRVOCLCodeCache extends SPIRVCodeCache {

    public SPIRVOCLCodeCache(SPIRVDeviceContext deviceContext) {
        super(deviceContext);
    }

    private byte[] readFile(String spirvFile) {
        File file = new File(spirvFile);
        FileInputStream fileStream;
        try {
            fileStream = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        byte[] spirvBinary = new byte[(int) file.length()];
        try {
            fileStream.read(spirvBinary);
            fileStream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return spirvBinary;
    }

    @Override
    public SPIRVInstalledCode installSPIRVBinary(TaskDataContext meta, String id, String entryPoint, String pathToFile) {

        checkBinaryFileExists(pathToFile);

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

        byte[] binary = readFile(pathToFile);

        long contextId = deviceContext.getSpirvContext().getOpenCLLayer().getContextId();
        long programPointer;

        SPIRVOCLNativeDispatcher spirvoclNativeCompiler = new SPIRVOCLNativeDispatcher();
        int[] errorCode = new int[1];
        programPointer = spirvoclNativeCompiler.clCreateProgramWithIL(contextId, binary, new long[] { binary.length }, errorCode);
        if (errorCode[0] != OCLErrorCode.CL_SUCCESS) {
            throw new TornadoRuntimeException("[ERROR] - clCreateProgramWithIL failed");
        }

        OCLTargetDevice oclDevice = (OCLTargetDevice) deviceContext.getDevice().getDeviceRuntime();
        String compilerFlags = meta.getCompilerFlags(TornadoVMBackendType.OPENCL);
        TornadoLogger logger = new TornadoLogger(this.getClass());
        logger.debug("\tSPIR-V/OpenCL compiler flags = %s", meta.getCompilerFlags(TornadoVMBackendType.OPENCL));

        int status = spirvoclNativeCompiler.clBuildProgram(programPointer, 1, new long[] { oclDevice.getDevicePointer() }, compilerFlags);
        if (status != OCLErrorCode.CL_SUCCESS) {
            String log = spirvoclNativeCompiler.clGetProgramBuildInfo(programPointer, oclDevice.getDevicePointer());
            System.out.println(log);
            throw new TornadoRuntimeException("[ERROR] - clBuildProgram failed");
        }

        long kernelPointer = spirvoclNativeCompiler.clCreateKernel(programPointer, entryPoint, errorCode);
        if (errorCode[0] != OCLErrorCode.CL_SUCCESS) {
            throw new TornadoRuntimeException("[ERROR] - clCreateKernel failed");
        }

        SPIRVOCLModule module = new SPIRVOCLModule(kernelPointer, entryPoint, pathToFile);
        final SPIRVOCLInstalledCode installedCode = new SPIRVOCLInstalledCode(entryPoint, module, deviceContext);

        // Install code in the code cache
        cache.put(id + "-" + entryPoint, installedCode);
        return installedCode;
    }
}
