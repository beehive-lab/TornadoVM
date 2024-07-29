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
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.Variable;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpCompositeExtract;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpLoad;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVLiteralInteger;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVMemoryAccess;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVMultipleOperands;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVOptionalOperand;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResultBuilder;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

@Opcode("VSEL")
public class SPIRVVectorElementSelect extends SPIRVLIROp {

    private final Variable vector;
    private final int laneId;

    private final LIRKind vectorKind;

    public SPIRVVectorElementSelect(LIRKind lirKind, LIRKind vectorKind, Variable vector, int laneId) {
        super(lirKind);
        this.vectorKind = vectorKind;
        this.vector = vector;
        this.laneId = laneId;
    }

    public int getLaneId() {
        return laneId;
    }

    public Variable getVector() {
        return this.vector;
    }

    protected SPIRVId getId(Value inputValue, SPIRVAssembler asm, SPIRVKind spirvKind) {
        if (inputValue instanceof ConstantValue) {
            SPIRVKind kind = (SPIRVKind) inputValue.getPlatformKind();
            return asm.lookUpConstant(((ConstantValue) inputValue).getConstant().toValueString(), kind);
        } else {
            SPIRVId param = asm.lookUpLIRInstructions(inputValue);
            if (TornadoOptions.OPTIMIZE_LOAD_STORE_SPIRV) {
                if (asm.isPhiAcrossBlocksPresent((AllocatableValue) inputValue)) {
                    return asm.getPhiIdAcrossBlock((AllocatableValue) inputValue);
                }
                return param;
            }

            // We need to perform a load first
            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "emit LOAD Variable: " + inputValue);
            SPIRVId load = asm.module.getNextId();
            SPIRVId type = asm.primitives.getTypePrimitive(spirvKind);
            asm.currentBlockScope().add(new SPIRVOpLoad(//
                    type, //
                    load, //
                    param, //
                    new SPIRVOptionalOperand<>( //
                            SPIRVMemoryAccess.Aligned( //
                                    new SPIRVLiteralInteger(spirvKind.getByteCount())))//
            ));
            return load;
        }
    }

    @Override
    public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
        SPIRVId vectorId = getId(vector, asm, (SPIRVKind) vectorKind.getPlatformKind());

        SPIRVId idElementKind = asm.primitives.getTypePrimitive(getSPIRVPlatformKind());
        Logger.traceCodeGen(Logger.BACKEND.SPIRV, "emit CompositeExtract: " + vector + " lane: " + laneId);
        SPIRVId resultSelect1 = asm.module.getNextId();
        asm.currentBlockScope().add(new SPIRVOpCompositeExtract(idElementKind, resultSelect1, vectorId, new SPIRVMultipleOperands<>(new SPIRVLiteralInteger(getLaneId()))));

        asm.registerLIRInstructionValue(this, resultSelect1);
    }

}
