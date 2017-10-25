/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2017 APT Group, School of Computer Science, 
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
package tornado.drivers.opencl.graal.meta;

import org.graalvm.compiler.core.common.LIRKind;
import jdk.vm.ci.meta.Value;
import tornado.drivers.opencl.graal.OCLArchitecture;
import tornado.drivers.opencl.graal.asm.OCLAssemblerConstants;

import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;

public class OCLMemorySpace extends Value {
    // @formatter:off

    public static final OCLMemorySpace GLOBAL = new OCLMemorySpace(OCLAssemblerConstants.GLOBAL_MEM_MODIFIER);
//        public static final OCLMemorySpace SHARED = new OCLMemorySpace(OCLAssemblerConstants.SHARED_MEM_MODIFIER);
    public static final OCLMemorySpace LOCAL = new OCLMemorySpace(OCLAssemblerConstants.LOCAL_MEM_MODIFIER);
    public static final OCLMemorySpace PRIVATE = new OCLMemorySpace(OCLAssemblerConstants.PRIVATE_MEM_MODIFIER);
    public static final OCLMemorySpace CONSTANT = new OCLMemorySpace(OCLAssemblerConstants.CONSTANT_MEM_MODIFIER);
    public static final OCLMemorySpace HEAP = new OCLMemorySpace("heap");
    // @formatter:on

    private final String name;

    protected OCLMemorySpace(String name) {
        super(LIRKind.Illegal);
        this.name = name;
    }

    public OCLArchitecture.OCLMemoryBase getBase() {

        if (this == GLOBAL || this == HEAP) {
            return OCLArchitecture.hp;
        } else if (this == LOCAL) {
            return OCLArchitecture.lp;
        } else if (this == CONSTANT) {
            return OCLArchitecture.cp;
        } else if (this == PRIVATE) {
            return OCLArchitecture.pp;
        }

        shouldNotReachHere();
        return null;
    }

    public String name() {
        return name;
    }
}
