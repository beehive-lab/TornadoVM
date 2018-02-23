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
package tornado.drivers.opencl.graal.compiler;

import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.framemap.ReferenceMapBuilder;
import jdk.vm.ci.code.ReferenceMap;
import jdk.vm.ci.meta.Value;

import static tornado.common.exceptions.TornadoInternalError.unimplemented;

public class OCLReferenceMapBuilder extends ReferenceMapBuilder {

    @Override
    public void addLiveValue(Value value) {
        unimplemented();
    }

    @Override
    public ReferenceMap finish(LIRFrameState lirfs) {
        unimplemented();
        return new ReferenceMap() {

        };
    }

}
