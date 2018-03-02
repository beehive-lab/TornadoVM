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
package uk.ac.manchester.tornado.drivers.opencl.graal.lir;

import static uk.ac.manchester.tornado.common.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssemblerConstants.STACK_BASE_OFFSET;
import static uk.ac.manchester.tornado.graal.compiler.TornadoCodeGenerator.trace;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.nodes.ParameterNode;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.opencl.OCLTargetDescription;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLArchitecture;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLArchitecture.OCLMemoryBase;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler.OCLBinaryIntrinsic;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler.OCLUnaryOp;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler.OCLUnaryTemplate;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLLIRGenerator;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIRStmt.AssignStmt;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIRStmt.VectorLoadStmt;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLUnary.MemoryAccess;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLUnary.OCLAddressCast;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.vector.VectorUtil;

public class OCLGenTool {

    protected OCLLIRGenerator gen;

    public OCLGenTool(OCLLIRGenerator gen) {
        this.gen = gen;
    }

    public void emitVectorLoad(AllocatableValue result, OCLBinaryIntrinsic op, Value index, OCLAddressCast cast, MemoryAccess address) {
        trace("emitVectorLoad: %s = (%s) %s", result.toString(), result.getPlatformKind().toString(), address.toString());
        gen.append(new VectorLoadStmt(result, op, index, cast, address));
    }

    public Value emitParameterLoad(ParameterNode paramNode, int index) {

        trace("emitParameterLoad: stamp=%s", paramNode.stamp());

        // assert !(paramValue instanceof Variable) : "Creating a copy of a variable via this method is not supported (and potentially a bug): " + paramValue;
        LIRKind lirKind = gen.getLIRKind(paramNode.stamp());

        OCLKind oclKind = (OCLKind) lirKind.getPlatformKind();

        OCLTargetDescription oclTarget = gen.target();

        Variable result = (oclKind.isVector()) ? gen.newVariable(LIRKind.value(oclTarget.getOCLKind(JavaKind.Object))) : gen.newVariable(lirKind);
        emitParameterLoad(result, index);

        if (oclKind.isVector()) {

            Variable vector = gen.newVariable(lirKind);
            OCLMemoryBase base = OCLArchitecture.hp;
            OCLBinaryIntrinsic intrinsic = VectorUtil.resolveLoadIntrinsic(oclKind);
            OCLAddressCast cast = new OCLAddressCast(base, LIRKind.value(oclKind.getElementKind()));
            MemoryAccess address = new MemoryAccess(base, result, false);

            emitVectorLoad(vector, intrinsic, new ConstantValue(LIRKind.value(OCLKind.INT), PrimitiveConstant.INT_0), cast, address);
            result = vector;
        }

        return result;
    }

    private OCLUnaryOp getParameterLoadOp(OCLKind type) {

        if (type.isVector()) {
            return OCLUnaryTemplate.LOAD_PARAM_ULONG;
        }

        switch (type) {

            case DOUBLE:
                return OCLUnaryTemplate.LOAD_PARAM_DOUBLE;
            case FLOAT:
                return OCLUnaryTemplate.LOAD_PARAM_FLOAT;
            case INT:
                return OCLUnaryTemplate.LOAD_PARAM_INT;
            case UINT:
                return OCLUnaryTemplate.LOAD_PARAM_UINT;
            case LONG:
                return OCLUnaryTemplate.LOAD_PARAM_LONG;
            case ULONG:
                return OCLUnaryTemplate.LOAD_PARAM_ULONG;
            default:
                unimplemented("parameter load: type=%s", type);
                break;
        }
        return null;
    }

    private void emitParameterLoad(AllocatableValue dst, int index) {
        OCLKind oclKind = (OCLKind) dst.getPlatformKind();
        LIRKind lirKind = LIRKind.value(oclKind);
        final OCLUnaryOp op = getParameterLoadOp(oclKind);
        gen.append(new AssignStmt(dst, new OCLUnary.Expr(op, lirKind, new ConstantValue(LIRKind.value(OCLKind.INT), JavaConstant.forInt(index + STACK_BASE_OFFSET)))));
    }
}
