package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.LabelRef;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpName;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVLiteralString;
import uk.ac.manchester.tornado.drivers.spirv.common.SPIRVLogger;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResultBuilder;

/**
 * SPIR-V Code Generation for all control-flow constructs.
 */
public class SPIRVControlFlow {

    public static class LoopLabel extends SPIRVLIRStmt.AbstractInstruction {

        public static final LIRInstructionClass<LoopLabel> TYPE = LIRInstructionClass.create(LoopLabel.class);

        private final int blockId;

        public LoopLabel(int blockId) {
            super(TYPE);
            this.blockId = blockId;
        }

        @Override
        protected void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            SPIRVLogger.traceCodeGen("LoopLabel Pending >>>>>>>>>>>> ");
            // asm.emitBlockLabel(Integer.toString(blockId), asm.functionScope);
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
            String id = targetBlock.toString();
            SPIRVId branch = asm.labelTable.get(id);
            if (branch == null) {
                branch = asm.module.getNextId();
                asm.currentBlockScope.add(new SPIRVOpName(branch, new SPIRVLiteralString(id)));
                asm.labelTable.put(id, branch);
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
         * @param asm
         */
        @Override
        protected void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            SPIRVLogger.traceCodeGen("emit SPIRVOpBranchConditional (pending)");

            // SPIRVId conditionId = asm.lookUpLIRInstructions(condition);
            //
            // SPIRVId trueBranch = getIfOfBranch(lirTrueBlock, asm);
            // SPIRVId falseBranch = getIfOfBranch(lirFalseBlock, asm);
            //
            // // FIXME: Lookup of the branch IDs
            // asm.currentBlockScope.add(new SPIRVOpBranchConditional( //
            // conditionId, //
            // trueBranch, //
            // falseBranch, //
            // new SPIRVMultipleOperands<>()));

            // Note: we do not need to register a new ID, since this operation does not
            // generate one.

        }
    }
}
