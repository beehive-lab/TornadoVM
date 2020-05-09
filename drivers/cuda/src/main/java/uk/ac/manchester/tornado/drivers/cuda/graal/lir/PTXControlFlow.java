package uk.ac.manchester.tornado.drivers.cuda.graal.lir;

import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.LabelRef;
import org.graalvm.compiler.nodes.cfg.Block;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.PTXCompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXLIRStmt.AbstractInstruction;

import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssemblerConstants.COLON;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssemblerConstants.TAB;

public class PTXControlFlow {

    protected static void emitBlockRef(LabelRef labelRef, PTXAssembler asm) {
        asm.emitBlock(labelRef.label().getBlockId());
    }

    public static class Branch extends AbstractInstruction {
        public static final LIRInstructionClass<Branch> TYPE = LIRInstructionClass.create(Branch.class);
        private final LabelRef destination;
        private final boolean isConditional;

        public Branch(LabelRef destination) {
            this(destination, true);
        }

        public Branch(LabelRef destination, boolean isConditional) {
            super(TYPE);
            this.destination = destination;
            this.isConditional = isConditional;
        }

        @Override
        public void emitCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            asm.emitSymbol(TAB);
            asm.emit("bra");
            if (!isConditional) asm.emit(".uni");
            asm.emitSymbol(TAB);

            emitBlockRef(destination, asm);
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
//            asm.emitSymbol(TAB);
//            asm.emit("slots[0] = (ulong) ");
//            asm.emitValue(crb, actionAndReason);
//            asm.delimiter();
//
//            asm.eol();
//            asm.ret();
        }

    }
}
