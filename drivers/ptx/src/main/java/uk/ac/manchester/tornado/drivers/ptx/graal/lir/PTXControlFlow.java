package uk.ac.manchester.tornado.drivers.ptx.graal.lir;

import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.LabelRef;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXCompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt.AbstractInstruction;

import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.TAB;

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
            asm.emit("bra");
            if (!isConditional) asm.emit(".uni");
            asm.emitSymbol(TAB);

            if (isLoopEdgeBack) asm.emitLoop(destination.label().getBlockId());
            else emitBlockRef(destination, asm);
            asm.delimiter();
            asm.eol();
        }
    }

    public static class DeoptOp extends AbstractInstruction {

        public static final LIRInstructionClass<DeoptOp> TYPE = LIRInstructionClass.create(DeoptOp.class);
        @Use private final Value actionAndReason;

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
