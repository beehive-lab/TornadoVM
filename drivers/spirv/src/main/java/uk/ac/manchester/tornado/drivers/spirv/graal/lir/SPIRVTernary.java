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

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LIRInstruction;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpExtInst;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVLiteralExtInstInteger;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVMultipleOperands;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResultBuilder;

public class SPIRVTernary {

    /**
     * Abstract operation which consumes two inputs
     */
    protected static class TernaryConsumer extends SPIRVLIROp {

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

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

        }
    }

    public static class TernaryIntrinsic extends TernaryConsumer {

        private SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic builtIn;

        public TernaryIntrinsic(SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic builtIn, LIRKind lirKind, Value x, Value y, Value z) {
            super(lirKind, x, y, z);
            this.builtIn = builtIn;
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

            SPIRVId result = asm.module.getNextId();
            SPIRVId set = asm.getOpenclImport();
            SPIRVLiteralExtInstInteger intrinsic = new SPIRVLiteralExtInstInteger(builtIn.getValue(), builtIn.getName());
            asm.currentBlockScope().add(new SPIRVOpExtInst(typeOperation, result, set, intrinsic, new SPIRVMultipleOperands<>(a, b, c)));
            asm.registerLIRInstructionValue(this, result);
        }

    }
}
