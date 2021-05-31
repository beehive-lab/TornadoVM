package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.LabelRef;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.spirvproto.lib.SPIRVInstScope;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpBranch;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpBranchConditional;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpLabel;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVMultipleOperands;
import uk.ac.manchester.tornado.drivers.spirv.common.SPIRVLogger;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResultBuilder;

/**
 * SPIR-V Code Generation for all control-flow constructs.
 */
public class SPIRVControlFlow {

    public static class LoopBeginLabel extends SPIRVLIRStmt.AbstractInstruction {

        public static final LIRInstructionClass<LoopBeginLabel> TYPE = LIRInstructionClass.create(LoopBeginLabel.class);

        private final String blockId;

        public LoopBeginLabel(String blockName) {
            super(TYPE);
            this.blockId = blockName;
        }

        // We only declare the IDs
        private SPIRVId getIfOfBranch(String blockName, SPIRVAssembler asm) {
            SPIRVId branch = asm.labelTable.get(blockName);
            if (branch == null) {
                branch = asm.emitBlockLabel(blockName);
            }
            return branch;
        }

        @Override
        protected void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            SPIRVLogger.traceCodeGen("LoopLabel : blockID " + blockId);
            SPIRVId branchId = getIfOfBranch(blockId, asm);
            SPIRVInstScope newScope = asm.currentBlockScope().add(new SPIRVOpBranch(branchId));
            asm.pushScope(newScope);
            SPIRVInstScope newScope2 = newScope.add(new SPIRVOpLabel(branchId));
            asm.pushScope(newScope2);
        }
    }

    public static class BranchConditional extends SPIRVLIRStmt.AbstractInstruction {

        public static final LIRInstructionClass<BranchConditional> TYPE = LIRInstructionClass.create(BranchConditional.class);

        @Use
        protected Value condition;

        private LabelRef lirTrueBlock;
        private LabelRef lirFalseBlock;

        public BranchConditional(Value condition, LabelRef lirTrueBlock, LabelRef lirFalseBlock) {
            super(TYPE);
            this.condition = condition;
            this.lirTrueBlock = lirTrueBlock;
            this.lirFalseBlock = lirFalseBlock;
        }

        // We only declare the IDs
        private SPIRVId getIfOfBranch(LabelRef ref, SPIRVAssembler asm) {
            AbstractBlockBase<?> targetBlock = ref.getTargetBlock();
            String blockName = targetBlock.toString();
            SPIRVId branch = asm.labelTable.get(blockName);
            if (branch == null) {
                branch = asm.emitBlockLabel(blockName);
            }
            return branch;
        }

        /**
         * It emits the following pattern:
         * 
         * <code>
         *     OpBranchConditional %condition %trueBranch %falseBranch
         * </code>
         * 
         * @param crb
         *            {@link SPIRVCompilationResultBuilder crb}
         * @param asm
         *            {@link SPIRVAssembler}
         */
        @Override
        protected void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

            SPIRVId conditionId = asm.lookUpLIRInstructions(condition);

            SPIRVId trueBranch = getIfOfBranch(lirTrueBlock, asm);
            SPIRVId falseBranch = getIfOfBranch(lirFalseBlock, asm);

            SPIRVLogger.traceCodeGen("emit SPIRVOpBranchConditional: " + condition + "? " + lirTrueBlock + ":" + lirFalseBlock);

            SPIRVId bool = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_BOOL);
            // SPIRVId resultLoad = asm.module.getNextId();
            //
            // asm.currentBlockScope().add(new SPIRVOpLoad( //
            // bool, //
            // resultLoad, //
            // conditionId, //
            // new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new
            // SPIRVLiteralInteger(condition.getPlatformKind().getSizeInBytes())))));

            asm.currentBlockScope().add(new SPIRVOpBranchConditional( //
                    conditionId, //
                    trueBranch, //
                    falseBranch, //
                    new SPIRVMultipleOperands<>()));

            // Note: we do not need to register a new ID, since this operation does not
            // generate one.

        }
    }

    public static class Branch extends SPIRVLIRStmt.AbstractInstruction {

        public static final LIRInstructionClass<Branch> TYPE = LIRInstructionClass.create(Branch.class);

        @Use
        private LabelRef branch;

        public Branch(LabelRef branch) {
            super(TYPE);
            this.branch = branch;
        }

        // We only declare the IDs
        private SPIRVId getIfOfBranch(LabelRef ref, SPIRVAssembler asm) {
            AbstractBlockBase<?> targetBlock = ref.getTargetBlock();
            String blockName = targetBlock.toString();
            SPIRVId branch = asm.labelTable.get(blockName);
            if (branch == null) {
                branch = asm.emitBlockLabel(blockName);
            }
            return branch;
        }

        /**
         * It emits the following pattern:
         *
         * <code>
         *     SPIRVOpBranch %branch
         * </code>
         *
         * @param crb
         * @param asm
         */
        @Override
        protected void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            SPIRVId branchId = getIfOfBranch(branch, asm);
            SPIRVLogger.traceCodeGen("emit SPIRVOpBranch: " + branch);
            asm.currentBlockScope().add(new SPIRVOpBranch(branchId));

        }
    }

    public static class BranchIf extends SPIRVLIRStmt.AbstractInstruction {

        public static final LIRInstructionClass<BranchIf> TYPE = LIRInstructionClass.create(BranchIf.class);

        @Use
        private LabelRef branch;
        private final boolean isConditional;
        private final boolean isLoopEdgeBack;

        public BranchIf(LabelRef branch, boolean isConditional, boolean isLoopEdgeBack) {
            super(TYPE);
            this.branch = branch;
            this.isConditional = isConditional;
            this.isLoopEdgeBack = isLoopEdgeBack;
        }

        // We only declare the IDs
        private SPIRVId getIfOfBranch(LabelRef ref, SPIRVAssembler asm) {
            AbstractBlockBase<?> targetBlock = ref.getTargetBlock();
            String blockName = targetBlock.toString();
            SPIRVId branch = asm.labelTable.get(blockName);
            if (branch == null) {
                branch = asm.emitBlockLabel(blockName);
            }
            return branch;
        }

        /**
         * It emits the following pattern:
         *
         * <code>
         *     SPIRVOpBranch %branch
         * </code>
         *
         * @param crb
         * @param asm
         */
        @Override
        protected void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            SPIRVId branchId = getIfOfBranch(branch, asm);
            SPIRVLogger.traceCodeGen("emit IF_CASE SPIRVOpBranch: " + branch);
            asm.currentBlockScope().add(new SPIRVOpBranch(branchId));

        }
    }
}
