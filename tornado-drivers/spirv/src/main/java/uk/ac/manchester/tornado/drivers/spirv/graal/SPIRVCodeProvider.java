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
package uk.ac.manchester.tornado.drivers.spirv.graal;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.CompiledCode;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVTargetDescription;

/**
 * Access to the code cache for SPIRV and interaction with JVMCI.
 */
public class SPIRVCodeProvider implements CodeCacheProvider {

    private SPIRVTargetDescription target;

    public SPIRVCodeProvider(SPIRVTargetDescription target) {
        this.target = target;
    }

    @Override
    public InstalledCode installCode(ResolvedJavaMethod method, CompiledCode compiledCode, InstalledCode installedCode, SpeculationLog log, boolean isDefault) {
        unimplemented("waiting for CompiledCode to be implemented first");
        return null;
    }

    @Override
    public void invalidateInstalledCode(InstalledCode installedCode) {
        installedCode.invalidate();
    }

    /**
     * Obtain a register configuration that will be used when compiling a given
     * method.
     * 
     * @return SPIRVRegisterConfig
     */
    @Override
    public RegisterConfig getRegisterConfig() {
        return new SPIRVRegisterConfig();
    }

    @Override
    public int getMinimumOutgoingSize() {
        return 0;
    }

    /**
     * A descriptor for the target architecture.
     * 
     * @return {@link TargetDescription}
     */
    @Override
    public TargetDescription getTarget() {
        return target;
    }

    @Override
    public SpeculationLog createSpeculationLog() {
        return null;
    }

    @Override
    public long getMaxCallTargetOffset(long address) {
        unimplemented("Max call target offset not implemented yet.");
        return -1;
    }

    @Override
    public boolean shouldDebugNonSafepoints() {
        return false;
    }
}
