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
package tornado.graal.compiler;

import jdk.vm.ci.meta.JavaKind;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.LocationIdentity;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;

import static tornado.common.exceptions.TornadoInternalError.unimplemented;

public class TornadoForeignCallsProvider implements ForeignCallsProvider {

    @Override
    public boolean isReexecutable(ForeignCallDescriptor fcd) {
        unimplemented();
        return false;
    }

    @Override
    public LocationIdentity[] getKilledLocations(ForeignCallDescriptor fcd) {
        unimplemented();
        return null;
    }

    @Override
    public boolean canDeoptimize(ForeignCallDescriptor fcd) {
        unimplemented();
        return false;
    }

    @Override
    public boolean isGuaranteedSafepoint(ForeignCallDescriptor fcd) {
        unimplemented();
        return false;
    }

    @Override
    public ForeignCallLinkage lookupForeignCall(ForeignCallDescriptor fcd) {
        unimplemented();
        return null;
    }

    @Override
    public LIRKind getValueKind(JavaKind jk) {
        unimplemented();
        return LIRKind.Illegal;
    }

}
