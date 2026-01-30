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
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.Variable;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpExtInst;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVLiteralExtInstInteger;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVMultipleOperands;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResultBuilder;

public class SPIRVTernary {

    /**
     * Abstract operation which consumes two inputs
     */
    abstract static class TernaryConsumer extends SPIRVLIROp {

        @LIRInstruction.Use
        protected Value x;
        @LIRInstruction.Use
        protected Value y;
        @LIRInstruction.Use
        protected Value z;

        protected TernaryConsumer(LIRKind lirKind, Value x, Value y, Value z) {
            super(lirKind);
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public static class TernaryIntrinsic extends TernaryConsumer {

        private SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic builtIn;

        private Variable result;

        public TernaryIntrinsic(Variable result, SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic builtIn, LIRKind lirKind, Value x, Value y, Value z) {
            super(lirKind, x, y, z);
            this.builtIn = builtIn;
            this.result = result;
        }

        protected SPIRVId obtainPhiValueIdIfNeeded(SPIRVAssembler asm) {
            SPIRVId operationId;
            if (!asm.isPhiMapEmpty() && asm.isResultInPhiMap(result)) {
                operationId = asm.getPhiId(result);
                while (operationId == null) {
                    // Nested IF, We Keep Looking into the trace
                    AllocatableValue v = asm.getPhiTraceValue(result);
                    operationId = asm.getPhiId((Variable) v);
                }
            } else {
                operationId = asm.module.getNextId();
            }
            return operationId;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            LIRKind lirKind = getLIRKind();
            SPIRVKind resultSPIRVKind = (SPIRVKind) lirKind.getPlatformKind();

            SPIRVId typeOperation = asm.primitives.getTypePrimitive(resultSPIRVKind);

            SPIRVId a = loadSPIRVId(crb, asm, x);
            SPIRVId b = loadSPIRVId(crb, asm, y);
            SPIRVId c = loadSPIRVId(crb, asm, z);

            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "emit SPIRVLiteralExtInstInteger (Ternary Intrinsic): " + builtIn.getName() + " (" + x + "," + y + "," + z + ")");

            SPIRVId result = obtainPhiValueIdIfNeeded(asm);

            SPIRVId set = asm.getOpenclImport();
            SPIRVLiteralExtInstInteger intrinsic = new SPIRVLiteralExtInstInteger(builtIn.getValue(), builtIn.getName());
            asm.currentBlockScope().add(new SPIRVOpExtInst(typeOperation, result, set, intrinsic, new SPIRVMultipleOperands<>(a, b, c)));

            asm.registerLIRInstructionValue(this, result);

        }

    }
}
