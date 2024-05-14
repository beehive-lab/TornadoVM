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
import jdk.graal.compiler.core.common.cfg.BasicBlock;

import uk.ac.manchester.beehivespirvtoolkit.lib.SPIRVInstScope;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpBranch;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpName;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpReturn;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVLiteralString;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResultBuilder;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

/**
 * LIR Operations with no inputs
 */
public class SPIRVNullary {

    protected static class NullaryConsumer extends SPIRVLIROp {

        protected NullaryConsumer(LIRKind valueKind) {
            super(valueKind);
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

        }
    }

    public static class ReturnNoOperands extends NullaryConsumer {

        final BasicBlock<?> currentBLock;

        public ReturnNoOperands(LIRKind valueKind, BasicBlock<?> currentBLock) {
            super(valueKind);
            this.currentBLock = currentBLock;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "emit SPIRVOpReturn for block: " + currentBLock.toString());

            if (TornadoOptions.SPIRV_RETURN_LABEL) {
                SPIRVInstScope blockScope = asm.currentBlockScope();
                if (asm.getReturnLabel() == null) {
                    asm.setReturnLabel(asm.module.getNextId());
                    String blockName = asm.composeUniqueLabelName("return");
                    asm.module.add(new SPIRVOpName(asm.getReturnLabel(), new SPIRVLiteralString(blockName)));
                }
                blockScope.add(new SPIRVOpBranch(asm.getReturnLabel()));
            } else {
                // Search the block
                String blockName = asm.composeUniqueLabelName(currentBLock.toString());
                SPIRVInstScope blockScope = asm.getBlockTable().get(blockName);
                // Add Block Return
                blockScope.add(new SPIRVOpReturn());
            }
        }
    }

}
