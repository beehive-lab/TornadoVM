/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
