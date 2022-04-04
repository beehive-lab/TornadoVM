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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.spirv.graal;

import jdk.vm.ci.code.InstalledCode;
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
     * The SPIR-V backend generates a binary, not source code. This method is not
     * applicable for this backend.
     *
     * @return String.
     */
    public String getGeneratedSourceCode() {
        return " NOT IMPLEMENTED YET";
    }
}
