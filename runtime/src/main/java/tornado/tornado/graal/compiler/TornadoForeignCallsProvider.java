/* 
 * Copyright 2012 James Clarkson.
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
 */
package tornado.graal.compiler;

import com.oracle.graal.compiler.common.LIRKind;
import com.oracle.graal.compiler.common.LocationIdentity;
import com.oracle.graal.compiler.common.spi.ForeignCallDescriptor;
import com.oracle.graal.compiler.common.spi.ForeignCallLinkage;
import com.oracle.graal.compiler.common.spi.ForeignCallsProvider;
import jdk.vm.ci.meta.JavaKind;

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
