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

import static uk.ac.manchester.tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.common.exceptions.TornadoInternalError.unimplemented;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.spi.LIRKindTool;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLKind;

public class OCLStamp extends ObjectStamp {

    private OCLKind oclKind;

    public OCLStamp(OCLKind lirKind) {
        super(null, true, true, false);
        this.oclKind = lirKind;
    }

    @Override
    public Stamp constant(Constant cnstnt, MetaAccessProvider map) {
        shouldNotReachHere();
        return this;
    }

    @Override
    public Stamp empty() {
        return this;
    }

    @Override
    public LIRKind getLIRKind(LIRKindTool lirKindTool) {
        return LIRKind.value(oclKind);
    }

    public OCLKind getOCLKind() {
        return oclKind;
    }

    @Override
    public JavaKind getStackKind() {
        if (oclKind.isPrimitive()) {
            switch (oclKind) {
                case BOOL:
                    return JavaKind.Boolean;
                case CHAR:
                case UCHAR:
                    return JavaKind.Byte;
                case SHORT:
                case USHORT:
                    return JavaKind.Short;
                case UINT:
                case INT:
                    return JavaKind.Int;
                case LONG:
                case ULONG:
                    return JavaKind.Long;
                case FLOAT:
                    return JavaKind.Float;
                case DOUBLE:
                    return JavaKind.Double;
                default:
                    return JavaKind.Illegal;
            }
        } else if (oclKind.isVector()) {
            return JavaKind.Object;
        }
        return JavaKind.Illegal;
    }

    @Override
    public boolean hasValues() {
        //shouldNotReachHere();
        return true;
    }

    @Override
    public Stamp improveWith(Stamp stamp) {
        return this;
    }

    @Override
    public boolean isCompatible(Constant constant) {

        shouldNotReachHere();
        return false;
    }

    @Override
    public boolean isCompatible(Stamp stamp) {
        if (stamp instanceof OCLStamp && ((OCLStamp) stamp).oclKind == oclKind) {
            return true;
        }

        unimplemented("stamp iscompat: %s + %s", this, stamp);
        return false;
    }

    @Override
    public ResolvedJavaType javaType(MetaAccessProvider metaAccess) {
        if (oclKind.getJavaClass() != null) {
            return metaAccess.lookupJavaType(oclKind.getJavaClass());
        }
        shouldNotReachHere();

        return null;
    }

    @Override
    public Stamp join(Stamp stamp) {
        if (stamp instanceof OCLStamp && ((OCLStamp) stamp).oclKind == oclKind) {
            return this;
        }
        return this;
    }

    @Override
    public Stamp meet(Stamp stamp) {
        return this;
    }

    @Override
    public Constant readConstant(MemoryAccessProvider metaAccess, Constant constant, long displacment) {
        shouldNotReachHere();
        return null;
    }

    @Override
    public String toString() {
        return "ocl: " + oclKind.name();
    }

    @Override
    public Stamp unrestricted() {
        return this;
    }

    @Override
    protected ObjectStamp copyWith(ResolvedJavaType rjt, boolean bln, boolean bln1, boolean bln2) {
        return this;
    }
}
