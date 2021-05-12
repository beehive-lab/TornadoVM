package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import org.graalvm.compiler.lir.LIRInstructionClass;

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
}
