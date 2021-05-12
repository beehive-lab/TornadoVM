package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.LabelRef;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpBranchConditional;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVMultipleOperands;
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
            asm.emitBlockLabel(Integer.toString(blockId), asm.functionScope);
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

        @Override
        protected void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            SPIRVLogger.traceCodeGen("µIntr emit SPIRVOpBranchConditional");

            SPIRVId conditionId = asm.lookUpLIRInstructions(condition);

            SPIRVId trueBranch = null;
            SPIRVId falseBranch = null;

            // FIXME: Lookup of the branch IDs
            asm.currentBlockScope.add(new SPIRVOpBranchConditional( //
                    conditionId, //
                    trueBranch, //
                    falseBranch, //
                    new SPIRVMultipleOperands<>()));

            // Note: we do not need to register a new ID, since this operation does not
            // generate one.

        }
    }
}
