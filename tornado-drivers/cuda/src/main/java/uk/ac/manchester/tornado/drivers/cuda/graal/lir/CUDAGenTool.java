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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.cuda.graal.lir;

import java.util.HashMap;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.cuda.CUDATargetDescription;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDAArchitecture;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDAArchitecture.CUDAMemoryBase;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDABinaryIntrinsic;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAUnaryOp;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.CUDALIRGenerator;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt.AssignStmt;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt.VectorLoadStmt;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAUnary.MemoryAccess;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAUnary.CUDAAddressCast;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.vector.VectorUtil;
import uk.ac.manchester.tornado.runtime.common.CUDATokens;

public class CUDAGenTool {

    protected CUDALIRGenerator gen;

    private final HashMap<ParameterNode, Variable> parameterToVariable = new HashMap<>();

    public CUDAGenTool(CUDALIRGenerator gen) {
        this.gen = gen;
    }

    public void emitVectorLoad(AllocatableValue result, CUDABinaryIntrinsic op, Value index, CUDAAddressCast cast, MemoryAccess address) {
        Logger.traceBuildLIR(Logger.BACKEND.OpenCL, "emitVectorLoad: %s = (%s) %s", result.toString(), result.getPlatformKind().toString(), address.toString());
        gen.append(new VectorLoadStmt(result, op, index, cast, address));
    }

    private String getParameterName(Local local) {
        String parameterName = local.getName();
        if (CUDATokens.cudaTokens.contains(parameterName)) {
            parameterName = "_" + parameterName;
        }
        return parameterName;
    }

    public Value emitParameterLoad(Local local, ParameterNode paramNode) {

        Logger.traceBuildLIR(Logger.BACKEND.OpenCL, "emitParameterLoad: stamp=%s", paramNode.stamp(NodeView.DEFAULT));

        LIRKind lirKind = gen.getLIRKind(paramNode.stamp(NodeView.DEFAULT));
        CUDAKind oclKind = (CUDAKind) lirKind.getPlatformKind();
        CUDATargetDescription oclTarget = gen.target();

        Variable result = (oclKind.isVector()) ? gen.newVariable(LIRKind.value(oclTarget.getCUDAKind(JavaKind.Object))) : gen.newVariable(lirKind);
        String parameterName = getParameterName(local);
        gen.append(new AssignStmt(result, new CUDANullary.Parameter(CUDAUnaryOp.CAST_TO_ULONG + parameterName, lirKind)));
        parameterToVariable.put(paramNode, result);

        if (oclKind.isVector()) {

            Variable vector = gen.newVariable(lirKind);
            CUDAMemoryBase base = CUDAArchitecture.globalSpace;
            CUDABinaryIntrinsic intrinsic = VectorUtil.resolveLoadIntrinsic(oclKind);
            CUDAAddressCast cast = new CUDAAddressCast(base, LIRKind.value(oclKind.getElementKind()));
            MemoryAccess address = new MemoryAccess(base, result);

            emitVectorLoad(vector, intrinsic, new ConstantValue(LIRKind.value(CUDAKind.INT), PrimitiveConstant.INT_0), cast, address);
            result = vector;
        }

        return result;
    }

    public HashMap<ParameterNode, Variable> getParameterToVariable() {
        return parameterToVariable;
    }
}
