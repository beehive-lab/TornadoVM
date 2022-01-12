/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
 */
package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import static uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssemblerConstants.STACK_BASE_OFFSET;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVTargetDescription;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVArchitecture;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVLIRGenerator;

import java.util.HashMap;

/**
 * This class specifies how to load a parameter to the kernel from the TornadoVM
 * Stack-Frame.
 */
public class SPIRVGenTool {

    protected SPIRVLIRGenerator generator;

    private final HashMap<ParameterNode, Variable> parameterToVariable = new HashMap<>();

    public SPIRVGenTool(SPIRVLIRGenerator gen) {
        this.generator = gen;
    }

    public Value emitParameterLoad(ParameterNode paramNode, int index) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "emitParameterLoad: stamp=%s", paramNode.stamp(NodeView.DEFAULT));
        LIRKind lirKind = generator.getLIRKind(paramNode.stamp(NodeView.DEFAULT));
        SPIRVKind spirvKind = (SPIRVKind) lirKind.getPlatformKind();

        SPIRVTargetDescription target = (SPIRVTargetDescription) generator.target();

        Variable result = (spirvKind.isVector()) ? generator.newVariable(LIRKind.value(target.getSPIRVKind(JavaKind.Object))) : generator.newVariable(lirKind);
        emitParameterLoad(result, index);
        parameterToVariable.put(paramNode, result);

        if (spirvKind.isVector()) {
            Variable vectorToLoad = generator.newVariable(lirKind);
            SPIRVArchitecture.SPIRVMemoryBase base = SPIRVArchitecture.globalSpace;
            SPIRVUnary.MemoryAccess address = new SPIRVUnary.MemoryAccess(base, result);
            SPIRVUnary.SPIRVAddressCast cast = new SPIRVUnary.SPIRVAddressCast(address, base, lirKind);
            generator.append(new SPIRVLIRStmt.LoadVectorStmt(vectorToLoad, cast, address));
            result = vectorToLoad;
        }

        return result;
    }

    private void emitParameterLoad(AllocatableValue resultValue, int index) {
        SPIRVKind spirvKind = (SPIRVKind) resultValue.getPlatformKind();
        LIRKind lirKind = LIRKind.value(spirvKind);

        // ASSIGN ( result, LOAD_FROM_STACK_FRAME_EXPR)
        SPIRVLIRStmt.ASSIGNParameter assignStmt = new SPIRVLIRStmt.ASSIGNParameter( //
                resultValue, //
                new SPIRVUnary.LoadFromStackFrameExpr( //
                        lirKind, //
                        SPIRVKind.OP_TYPE_INT_64, //
                        (STACK_BASE_OFFSET + index), //
                        index), //
                SPIRVKind.OP_TYPE_INT_64.getSizeInBytes(), //
                index); //

        generator.append(assignStmt);
    }

    public HashMap<ParameterNode, Variable> getParameterToVariable() {
        return parameterToVariable;
    }

}
