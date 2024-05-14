/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, 2024, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.lir.ConstantValue;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpCompositeInsert;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpFConvert;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpLoad;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpUConvert;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVLiteralInteger;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVMemoryAccess;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVMultipleOperands;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVOptionalOperand;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResultBuilder;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

public class SPIRVVectorAssign {

    abstract static class AssignVector extends SPIRVLIROp {

        protected AssignVector(LIRKind valueKind) {
            super(valueKind);
        }

        protected SPIRVId getId(Value inputValue, SPIRVAssembler asm, SPIRVKind spirvKind) {
            if (inputValue instanceof ConstantValue) {
                SPIRVKind kind = (SPIRVKind) inputValue.getPlatformKind();
                return asm.lookUpConstant(((ConstantValue) inputValue).getConstant().toValueString(), kind);
            } else {
                SPIRVId param = asm.lookUpLIRInstructions(inputValue);
                SPIRVId load;
                if (TornadoOptions.OPTIMIZE_LOAD_STORE_SPIRV) {
                    load = param;
                } else {
                    // We need to perform a load first
                    Logger.traceCodeGen(Logger.BACKEND.SPIRV, "emit LOAD Variable from AssignVector :" + inputValue);
                    load = asm.module.getNextId();
                    SPIRVId type = asm.primitives.getTypePrimitive(spirvKind);
                    asm.currentBlockScope().add(new SPIRVOpLoad(//
                            type, //
                            load, //
                            param, //
                            new SPIRVOptionalOperand<>( //
                                    SPIRVMemoryAccess.Aligned( //
                                            new SPIRVLiteralInteger(spirvKind.getByteCount())))//
                    ));
                }

                // If the type loaded differ from the element kind type, then we need to do a
                // type conversion: (OpFConvert) for fp16 and (OpUConvert) for the rest of the types
                SPIRVKind kind = ((SPIRVKind) getLIRKind().getPlatformKind()).getElementKind();
                if (spirvKind.getByteCount() != kind.getByteCount()) {
                    SPIRVId resultConvert = asm.module.getNextId();
                    SPIRVId toKind = asm.primitives.getTypePrimitive(kind);
                    if (kind.isHalf()) {
                        asm.currentBlockScope().add(new SPIRVOpFConvert(toKind, resultConvert, load));
                    } else {
                        asm.currentBlockScope().add(new SPIRVOpUConvert(toKind, resultConvert, load));
                    }
                    load = resultConvert;
                }

                return load;
            }
        }
    }

    public static class AssignVectorExpr extends AssignVector {

        private final Value[] values;

        public AssignVectorExpr(LIRKind lirKind, Value... values) {
            super(lirKind);
            this.values = values;
        }

        private SPIRVId emitCompositeInsertN(SPIRVAssembler asm, SPIRVId composite, SPIRVId vectorType, int index) {
            SPIRVId spirvIdS1 = getId(values[index], asm, (SPIRVKind) values[index].getPlatformKind());
            SPIRVId compositeInsert = asm.module.getNextId();
            asm.currentBlockScope().add(new SPIRVOpCompositeInsert( //
                    vectorType, //
                    compositeInsert, //
                    spirvIdS1, //
                    composite, //
                    new SPIRVMultipleOperands<>(new SPIRVLiteralInteger(index))));
            return compositeInsert;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            SPIRVId vectorType = asm.primitives.getTypePrimitive(getSPIRVPlatformKind());
            SPIRVId composite0 = asm.primitives.getUndef(getSPIRVPlatformKind());
            for (int i = 0; i < values.length; i++) {
                composite0 = emitCompositeInsertN(asm, composite0, vectorType, i);
            }
            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "emit VectorComposite: " + this + ": " + values.length + getSPIRVPlatformKind());
            asm.registerLIRInstructionValue(this, composite0);
        }
    }
}
