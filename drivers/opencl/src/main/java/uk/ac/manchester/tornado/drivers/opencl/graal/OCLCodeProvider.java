/*
 * Copyright (c) 2018, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.graal;

import static uk.ac.manchester.tornado.common.exceptions.TornadoInternalError.unimplemented;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.CompiledCode;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;
import uk.ac.manchester.tornado.drivers.opencl.OCLTargetDescription;

public class OCLCodeProvider implements CodeCacheProvider {

    private final TargetDescription target;

    public OCLCodeProvider(TargetDescription target) {
        this.target = target;
    }

    @Override
    public SpeculationLog createSpeculationLog() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public long getMaxCallTargetOffset(long l) {
        unimplemented();
        return -1;
    }

    @Override
    public int getMinimumOutgoingSize() {
        return 0;
    }

    @Override
    public RegisterConfig getRegisterConfig() {
        return new OCLRegisterConfig();
    }

    @Override
    public OCLTargetDescription getTarget() {
        return (OCLTargetDescription) target;
    }

    @Override
    public InstalledCode installCode(ResolvedJavaMethod rjm, CompiledCode cc, InstalledCode ic, SpeculationLog sl, boolean bln) {
        unimplemented("waiting for CompiledCode to be implemented first");
//  return addMethod(method, method.getName(), result.);
        return null;
    }

    @Override
    public void invalidateInstalledCode(InstalledCode ic) {
        ic.invalidate();
    }

    @Override
    public boolean shouldDebugNonSafepoints() {
        unimplemented();
        return false;
    }

}
