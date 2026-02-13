/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.drivers.ptx.graal.lir;

import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.BRANCH;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.DOT;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.TAB;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.UNI;

import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.LabelRef;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXCompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt.AbstractInstruction;

public class PTXControlFlow {

    protected static void emitBlockRef(LabelRef labelRef, PTXAssembler asm) {
        asm.emitBlock(labelRef.label().getBlockId());
    }

    public static class LoopLabel extends AbstractInstruction {
        public static final LIRInstructionClass<LoopLabel> TYPE = LIRInstructionClass.create(LoopLabel.class);

        private final int blockId;

        public LoopLabel(int blockId) {
            super(TYPE);
            this.blockId = blockId;
        }

        @Override
        public void emitCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            asm.emitLoopLabel(blockId);
        }
    }

    public static class LoopBreakOp extends Branch {

        public LoopBreakOp(LabelRef destination, boolean isConditional, boolean isLoopEdgeBack) {
            super(destination, isConditional, isLoopEdgeBack);
        }
    }

    public static class Branch extends AbstractInstruction {
        public static final LIRInstructionClass<Branch> TYPE = LIRInstructionClass.create(Branch.class);
        private final LabelRef destination;
        private final boolean isConditional;
        private final boolean isLoopEdgeBack;

        public Branch(LabelRef destination, boolean isConditional, boolean isLoopEdgeBack) {
            super(TYPE);
            this.destination = destination;
            this.isConditional = isConditional;
            this.isLoopEdgeBack = isLoopEdgeBack;
        }

        @Override
        public void emitCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            asm.emitSymbol(TAB);
            asm.emit(BRANCH);
            if (!isConditional) {
                asm.emit(DOT + UNI);
            }
            asm.emitSymbol(TAB);

            if (isLoopEdgeBack) {
                asm.emitLoop(destination.label().getBlockId());
            } else {
                emitBlockRef(destination, asm);
            }
            asm.delimiter();
            asm.eol();
        }
    }

    public static class DeoptOp extends AbstractInstruction {

        public static final LIRInstructionClass<DeoptOp> TYPE = LIRInstructionClass.create(DeoptOp.class);
        @Use
        private final Value actionAndReason;

        public DeoptOp(Value actionAndReason) {
            super(TYPE);
            this.actionAndReason = actionAndReason;
        }

        @Override
        public void emitCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            TornadoInternalError.unimplemented();
        }

    }
}
