/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, 2024, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2009-2021, Oracle and/or its affiliates. All rights reserved.
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

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpLoad;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVLiteralInteger;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVMemoryAccess;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVOptionalOperand;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResultBuilder;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

public abstract class SPIRVLIROp extends Value {

    protected SPIRVLIROp(LIRKind valueKind) {
        super(valueKind);
    }

    public final void emit(SPIRVCompilationResultBuilder crb) {
        emit(crb, crb.getAssembler());
    }

    public abstract void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm);

    public LIRKind getLIRKind() {
        return (LIRKind) this.getValueKind();
    }

    public SPIRVKind getSPIRVPlatformKind() {
        PlatformKind kind = getPlatformKind();
        return (kind instanceof SPIRVKind) ? (SPIRVKind) kind : SPIRVKind.ILLEGAL;
    }

    protected SPIRVId getId(Value inputValue, SPIRVAssembler asm, SPIRVKind spirvKind) {
        if (inputValue instanceof ConstantValue) {
            SPIRVKind kind = (SPIRVKind) inputValue.getPlatformKind();
            return asm.lookUpConstant(((ConstantValue) inputValue).getConstant().toValueString(), kind);
        } else {
            SPIRVId param = asm.lookUpLIRInstructions(inputValue);
            if (TornadoOptions.OPTIMIZE_LOAD_STORE_SPIRV) {
                // Do not generate a load if Load/Store optimization is enabled.
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

    protected SPIRVId loadSPIRVId(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm, Value x) {
        SPIRVId a;
        if (x instanceof SPIRVVectorElementSelect) {
            ((SPIRVVectorElementSelect) x).emit(crb, asm);
            a = asm.lookUpLIRInstructions(x);
        } else {
            a = getId(x, asm, (SPIRVKind) x.getPlatformKind());
        }
        return a;
    }
}
