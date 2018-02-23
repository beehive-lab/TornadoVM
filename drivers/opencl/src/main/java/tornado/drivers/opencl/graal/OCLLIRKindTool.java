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
package tornado.drivers.opencl.graal;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.spi.LIRKindTool;
import tornado.drivers.opencl.OCLTargetDescription;
import tornado.drivers.opencl.graal.lir.OCLKind;

import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;
import static tornado.common.exceptions.TornadoInternalError.unimplemented;

public class OCLLIRKindTool implements LIRKindTool {

    private final OCLTargetDescription target;

    public OCLLIRKindTool(OCLTargetDescription target) {
        this.target = target;
    }

    @Override
    public LIRKind getIntegerKind(int numBits) {
        if (numBits <= 8) {
            return LIRKind.value(OCLKind.CHAR);
        } else if (numBits <= 16) {
            return LIRKind.value(OCLKind.SHORT);
        } else if (numBits <= 32) {
            return LIRKind.value(OCLKind.INT);
        } else if (numBits <= 64) {
            return LIRKind.value(OCLKind.LONG);
        } else {
            throw shouldNotReachHere();
        }
    }

    @Override
    public LIRKind getFloatingKind(int numBits) {
        switch (numBits) {
            case 32:
                return LIRKind.value(OCLKind.FLOAT);
            case 64:
                return LIRKind.value(OCLKind.DOUBLE);
            default:
                throw shouldNotReachHere();
        }
    }

    @Override
    public LIRKind getNarrowOopKind() {
        unimplemented();
        return null;
    }

    @Override
    public LIRKind getNarrowPointerKind() {
        unimplemented();
        return null;
    }

    @Override
    public LIRKind getObjectKind() {
        return getWordKind();
    }

    @Override
    public LIRKind getWordKind() {
        return LIRKind.value(target.getArch().getWordKind());
    }

}
