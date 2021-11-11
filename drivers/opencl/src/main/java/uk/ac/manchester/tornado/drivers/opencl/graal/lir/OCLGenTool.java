/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.opencl.graal.lir;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

import java.util.HashMap;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.opencl.OCLTargetDescription;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLArchitecture;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLArchitecture.OCLMemoryBase;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler.OCLBinaryIntrinsic;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler.OCLUnaryOp;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler.OCLUnaryTemplate;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssemblerConstants;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLLIRGenerator;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIRStmt.AssignStmt;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIRStmt.VectorLoadStmt;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLUnary.MemoryAccess;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLUnary.OCLAddressCast;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.vector.VectorUtil;

public class OCLGenTool {

    protected OCLLIRGenerator gen;

    private final HashMap<ParameterNode, Variable> parameterToVariable = new HashMap<>();

    public OCLGenTool(OCLLIRGenerator gen) {
        this.gen = gen;
    }

    public void emitVectorLoad(AllocatableValue result, OCLBinaryIntrinsic op, Value index, OCLAddressCast cast, MemoryAccess address) {
        Logger.traceBuildLIR(Logger.BACKEND.OpenCL, "emitVectorLoad: %s = (%s) %s", result.toString(), result.getPlatformKind().toString(), address.toString());
        gen.append(new VectorLoadStmt(result, op, index, cast, address));
    }

    public Value emitParameterLoad(ParameterNode paramNode, int index) {

        Logger.traceBuildLIR(Logger.BACKEND.OpenCL, "emitParameterLoad: stamp=%s", paramNode.stamp(NodeView.DEFAULT));

        // assert !(paramValue instanceof Variable) : "Creating a copy of a
        // variable via this method is not supported (and potentially a bug): "
        // + paramValue;
        LIRKind lirKind = gen.getLIRKind(paramNode.stamp(NodeView.DEFAULT));
        OCLKind oclKind = (OCLKind) lirKind.getPlatformKind();
        OCLTargetDescription oclTarget = gen.target();

        Variable result = (oclKind.isVector()) ? gen.newVariable(LIRKind.value(oclTarget.getOCLKind(JavaKind.Object))) : gen.newVariable(lirKind);
        emitParameterLoad(result, index);
        parameterToVariable.put(paramNode, result);

        if (oclKind.isVector()) {

            Variable vector = gen.newVariable(lirKind);
            OCLMemoryBase base = OCLArchitecture.globalSpace;
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

    /**
     * This represents a load from a parameter.
     *
     * This an example of the target code to generate:
     *
     * <code>
     *      ulong0 = (ulong) frame[3];
     * </code>
     *
     * @param resultValue
     *            result
     * @param index
     *            Parameter index to be loaded.
     *
     */
    private void emitParameterLoad(AllocatableValue resultValue, int index) {
        OCLKind oclKind = (OCLKind) resultValue.getPlatformKind();
        LIRKind lirKind = LIRKind.value(oclKind);
        final OCLUnaryOp op = getParameterLoadOp(oclKind);
        gen.append(new AssignStmt(resultValue, new OCLUnary.Expr(op, lirKind, new ConstantValue(LIRKind.value(OCLKind.INT), JavaConstant.forInt(index + OCLAssemblerConstants.STACK_BASE_OFFSET)))));
    }

    public HashMap<ParameterNode, Variable> getParameterToVariable() {
        return parameterToVariable;
    }
}
