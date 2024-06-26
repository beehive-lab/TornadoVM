/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021-2022, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.spirv.graal;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;

import jdk.vm.ci.code.InstalledCode;
import uk.ac.manchester.beehivespirvtoolkit.lib.SPIRVTool;
import uk.ac.manchester.beehivespirvtoolkit.lib.disassembler.Disassembler;
import uk.ac.manchester.beehivespirvtoolkit.lib.disassembler.SPIRVDisassemblerOptions;
import uk.ac.manchester.beehivespirvtoolkit.lib.disassembler.SPVFileReader;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDeviceContext;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVModule;
import uk.ac.manchester.tornado.runtime.common.TornadoInstalledCode;

public abstract class SPIRVInstalledCode extends InstalledCode implements TornadoInstalledCode {

    protected SPIRVDeviceContext deviceContext;
    protected SPIRVModule spirvModule;

    protected SPIRVInstalledCode(String name, SPIRVModule spirvModule, SPIRVDeviceContext deviceContext) {
        super(name);
        this.deviceContext = deviceContext;
        this.spirvModule = spirvModule;
    }

    public SPIRVModule getSPIRVModule() {
        return this.spirvModule;
    }

    public SPIRVDeviceContext getDeviceContext() {
        return this.deviceContext;
    }

    /**
     * Gets the installed SPIR-V binary code and invokes the disassembler. It stores
     * the result in the same path with the asm extension.
     *
     * @param pathToFile
     *     Path of the input SPIR-V Binary code
     * @return String of the whole disassembled SPIR-V module
     * @throws IOException
     */
    private String getDisassembledCode(String pathToFile) throws IOException {
        PrintStream pr = new PrintStream(pathToFile + ".asm");
        SPVFileReader reader = null;
        try {
            reader = new SPVFileReader(pathToFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        SPIRVDisassemblerOptions disassemblerOptions = new SPIRVDisassemblerOptions(true, true, false, true, false);
        SPIRVTool spirvTool = new Disassembler(reader, pr, disassemblerOptions);
        try {
            spirvTool.run();
        } catch (Exception e) {
            e.printStackTrace();
        }

        pr.close();
        File file = new File(pathToFile + ".asm");
        BufferedReader br = new BufferedReader(new FileReader(file));
        String line;
        StringBuffer sb = new StringBuffer();
        final String EOL = System.getProperty("line.separator");
        while ((line = br.readLine()) != null) {
            sb.append(line + EOL);
        }
        br.close();
        return sb.toString();
    }

    /**
     * The SPIR-V backend generates a binary, not source code. This method is not
     * applicable for this backend.
     *
     * @return String.
     */
    public String getGeneratedSourceCode() {
        String spirvFile = spirvModule.getPathToSPIRVBinary();
        try {
            return getDisassembledCode(spirvFile);
        } catch (IOException e) {
            throw new TornadoRuntimeException(e);
        }
    }
}
