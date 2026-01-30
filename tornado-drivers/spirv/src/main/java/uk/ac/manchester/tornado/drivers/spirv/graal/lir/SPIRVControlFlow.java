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

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.LabelRef;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.SwitchStrategy;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.beehivespirvtoolkit.lib.SPIRVInstScope;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpBranch;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpBranchConditional;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpLabel;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpLoad;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpLoopMerge;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpSwitch;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVLiteralInteger;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVLoopControl;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVMemoryAccess;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVMultipleOperands;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVOptionalOperand;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVPairLiteralIntegerIdRef;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResultBuilder;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

/**
 * SPIR-V Code Generation for all control-flow constructs.
 */
public class SPIRVControlFlow {

    public abstract static class BaseControlFlow extends SPIRVLIRStmt.AbstractInstruction {

        BaseControlFlow(LIRInstructionClass<? extends LIRInstruction> c) {
            super(c);
        }

        // We only declare the IDs
        protected SPIRVId getIfOfBranch(String blockName, SPIRVAssembler asm) {
            SPIRVId branch = asm.getLabel(blockName);
            if (branch == null) {
                branch = asm.registerBlockLabel(blockName);
            }
            return branch;
        }

        // We only declare the IDs
        protected SPIRVId getIdForBranch(LabelRef ref, SPIRVAssembler asm) {
            BasicBlock<?> targetBlock = ref.getTargetBlock();
            String blockName = targetBlock.toString();
            SPIRVId branch = asm.getLabel(blockName);
            if (branch == null) {
                branch = asm.registerBlockLabel(blockName);
            }
            return branch;
        }
    }

    public static class LoopBeginLabel extends BaseControlFlow {

        public static final LIRInstructionClass<LoopBeginLabel> TYPE = LIRInstructionClass.create(LoopBeginLabel.class);

        private final String blockId;

        public LoopBeginLabel(String blockName) {
            super(TYPE);
            this.blockId = blockName;
        }

        @Override
        protected void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            SPIRVId branchId = getIfOfBranch(blockId, asm);
            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "\tOpBranch: " + blockId);
            SPIRVInstScope newScope = asm.currentBlockScope().add(new SPIRVOpBranch(branchId));
            asm.pushScope(newScope);
            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "\tLoopLabel : blockID " + blockId);
            SPIRVInstScope newScope2 = newScope.add(new SPIRVOpLabel(branchId));
            asm.pushScope(newScope2);
        }
    }

    public static class BranchConditional extends BaseControlFlow {

        public static final LIRInstructionClass<BranchConditional> TYPE = LIRInstructionClass.create(BranchConditional.class);

        @Use
        protected Value condition;

        private final LabelRef lirTrueBlock;
        private final LabelRef lirFalseBlock;

        private final int unrollFactor;

        public BranchConditional(Value condition, LabelRef lirTrueBlock, LabelRef lirFalseBlock, int unrollFactor) {
            super(TYPE);
            this.condition = condition;
            this.lirTrueBlock = lirTrueBlock;
            this.lirFalseBlock = lirFalseBlock;
            this.unrollFactor = unrollFactor;
        }

        /**
         * It emits the following pattern:
         *
         * <p>
         * <code>
         * OpBranchConditional %condition %trueBranch %falseBranch
         * </code>
         * </p>
         *
         * @param crb
         *     {@link SPIRVCompilationResultBuilder crb}
         * @param asm
         *     {@link SPIRVAssembler}
         */
        @Override
        protected void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

            SPIRVId conditionId = asm.lookUpLIRInstructions(condition);

            SPIRVId trueBranch = getIdForBranch(lirTrueBlock, asm);
            SPIRVId falseBranch = getIdForBranch(lirFalseBlock, asm);

            if (unrollFactor != 0) {
                emitLoopUnrollSuggestion(trueBranch, falseBranch, asm);
            }

            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "emit SPIRVOpBranchConditional: " + condition + "? " + lirTrueBlock + ":" + lirFalseBlock);

            asm.currentBlockScope().add(new SPIRVOpBranchConditional( //
                    conditionId, //
                    trueBranch, //
                    falseBranch, //
                    new SPIRVMultipleOperands<>()));

            // Note: we do not need to register a new ID, since this operation does not
            // generate one.
        }

        private void emitLoopUnrollSuggestion(SPIRVId trueBranch, SPIRVId falseBranch, SPIRVAssembler asm) {
            // With Loop-Unroll suggestion
            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "emit SPIRVOpLoopMerge: " + falseBranch + " - " + trueBranch + " - UNROLL");
            asm.currentBlockScope().add(new SPIRVOpLoopMerge(falseBranch, trueBranch, SPIRVLoopControl.Unroll()));
        }
    }

    public static class Branch extends BaseControlFlow {

        public static final LIRInstructionClass<Branch> TYPE = LIRInstructionClass.create(Branch.class);

        @Use
        private LabelRef branch;

        public Branch(LabelRef branch) {
            super(TYPE);
            this.branch = branch;
        }

        /**
         * It emits the following pattern:
         *
         * <code>
         * SPIRVOpBranch %branch
         * </code>
         *
         * @param crb
         * @param asm
         */
        @Override
        protected void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            SPIRVId branchId = getIdForBranch(branch, asm);
            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "emit SPIRVOpBranch: " + branch);
            asm.currentBlockScope().add(new SPIRVOpBranch(branchId));

        }
    }

    public static class BranchIf extends BaseControlFlow {

        public static final LIRInstructionClass<BranchIf> TYPE = LIRInstructionClass.create(BranchIf.class);
        private final boolean isConditional;
        private final boolean isLoopEdgeBack;
        @Use
        private LabelRef branch;

        public BranchIf(LabelRef branch, boolean isConditional, boolean isLoopEdgeBack) {
            super(TYPE);
            this.branch = branch;
            this.isConditional = isConditional;
            this.isLoopEdgeBack = isLoopEdgeBack;
        }

        /**
         * It emits the following pattern:
         *
         * <code>
         * SPIRVOpBranch %branch
         * </code>
         *
         * @param crb
         * @param asm
         */
        @Override
        protected void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            SPIRVId branchId = getIdForBranch(branch, asm);
            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "emit IF_CASE SPIRVOpBranch: " + branch);
            asm.currentBlockScope().add(new SPIRVOpBranch(branchId));

        }
    }

    public static class BranchLoopConditional extends BranchIf {

        public BranchLoopConditional(LabelRef branch, boolean isConditional, boolean isLoopEdgeBack) {
            super(branch, isConditional, isLoopEdgeBack);
        }
    }

    @Opcode("Switch")
    public static class SwitchStatement extends BaseControlFlow {

        public static final LIRInstructionClass<SwitchStatement> TYPE = LIRInstructionClass.create(SwitchStatement.class);

        @Use
        private AllocatableValue key;

        private SwitchStrategy strategy;

        @Use
        private LabelRef[] keytargets;

        @Use
        private LabelRef defaultTarget;

        public SwitchStatement(AllocatableValue key, SwitchStrategy strategy, LabelRef[] keyTargets, LabelRef defaultTarget) {
            super(TYPE);
            this.key = key;
            this.strategy = strategy;
            this.keytargets = keyTargets;
            this.defaultTarget = defaultTarget;
        }

        @Override
        protected void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "emit SWITCH(" + key + ")");

            SPIRVId valueKey = asm.lookUpLIRInstructions(key);

            SPIRVKind spirvKind = (SPIRVKind) key.getPlatformKind();
            SPIRVId typeKind = asm.primitives.getTypePrimitive(spirvKind);

            // Perform a Load of the key value
            SPIRVId loadId;
            if (TornadoOptions.OPTIMIZE_LOAD_STORE_SPIRV) {
                loadId = valueKey;
            } else {
                loadId = asm.module.getNextId();
                asm.currentBlockScope().add(new SPIRVOpLoad(//
                        typeKind, //
                        loadId, //
                        valueKey, //
                        new SPIRVOptionalOperand<>( //
                                SPIRVMemoryAccess.Aligned( //
                                        new SPIRVLiteralInteger(spirvKind.getSizeInBytes())))//
                ));
            }

            SPIRVId defaultSelector = getIdForBranch(defaultTarget, asm);

            SPIRVPairLiteralIntegerIdRef[] cases = new SPIRVPairLiteralIntegerIdRef[strategy.getKeyConstants().length];
            int i = 0;
            for (Constant keyConstant : strategy.getKeyConstants()) {
                SPIRVId labelCase = getIdForBranch(keytargets[i], asm);
                int caseIntValue = Integer.parseInt(keyConstant.toValueString());
                SPIRVPairLiteralIntegerIdRef pairId = new SPIRVPairLiteralIntegerIdRef(new SPIRVLiteralInteger(caseIntValue), labelCase);
                cases[i] = pairId;
                i++;
            }

            asm.currentBlockScope().add(new SPIRVOpSwitch(loadId, defaultSelector, new SPIRVMultipleOperands<>(cases)));
        }
    }
}
