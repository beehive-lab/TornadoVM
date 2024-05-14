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

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRInstruction.Def;
import jdk.graal.compiler.lir.LIRInstruction.Use;
import jdk.graal.compiler.nodes.DirectCallTargetNode;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpFunctionCall;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVMultipleOperands;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVUtils;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResultBuilder;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

public class SPIRVDirectCall extends SPIRVLIROp {

    private DirectCallTargetNode targetNode;
    private LIRFrameState frameState;

    @Def
    private Value result;

    @Use
    private Value[] parameters;

    public SPIRVDirectCall(DirectCallTargetNode targetNode, Value result, Value[] parameters, LIRFrameState frameState) {
        super(LIRKind.value(result.getPlatformKind()));
        this.targetNode = targetNode;
        this.result = result;
        this.parameters = parameters;
        this.frameState = frameState;
    }

    @Override
    public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

        Logger.traceCodeGen(Logger.BACKEND.SPIRV, "emit OpFunctionCall for method: " + targetNode.targetMethod().getName());

        final String methodName = SPIRVUtils.makeMethodName(targetNode.targetMethod());

        SPIRVId[] idsForParameters;
        int paramIndex = 0;
        if (TornadoOptions.SPIRV_DIRECT_CALL_WITH_LOAD_HEAP) {
            SPIRVId[] ids = asm.loadHeapPointerAndFrameIndex();
            paramIndex = 2;
            idsForParameters = new SPIRVId[parameters.length + 2];
            idsForParameters[0] = ids[0];
            idsForParameters[1] = ids[1];
        } else {
            idsForParameters = new SPIRVId[parameters.length];
        }

        for (Value parameter : parameters) {
            SPIRVKind spirvKind = (SPIRVKind) parameter.getPlatformKind();
            idsForParameters[paramIndex++] = getId(parameter, asm, spirvKind);
        }

        SPIRVKind resultType = (SPIRVKind) result.getPlatformKind();
        SPIRVId resultTypeId = asm.primitives.getTypePrimitive(resultType);
        SPIRVId functionResult = asm.module.getNextId();
        SPIRVMultipleOperands<SPIRVId> operands = new SPIRVMultipleOperands<>(idsForParameters);

        // At this point we need to obtain the ID of the function to call.
        // If the ID already exists (because it was previously called),
        // then we obtain the function SPIRV-ID from a Symbol Table.
        // Otherwise, we create a new ID and register the function in the
        // symbol table.
        SPIRVId functionToCall = asm.getMethodRegistrationId(methodName);
        if (functionToCall == null) {
            functionToCall = asm.registerNewMethod(methodName);

            // Only if the function has not been registered before,
            // we add it in the queue for processing this function
            // at a later stage of the compilation process. Otherwise,
            // we get a duplicated ID error in SPIR-V.
            crb.addNonInlinedMethod(targetNode.targetMethod());
        }

        asm.currentBlockScope().add(new SPIRVOpFunctionCall( //
                resultTypeId, //
                functionResult, //
                functionToCall, //
                operands));

        asm.registerLIRInstructionValue(this, functionResult);

    }
}
