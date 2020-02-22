package uk.ac.manchester.tornado.drivers.cuda.graal.lir;

import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.PTXCompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXLIRStmt.AbstractInstruction;

public class PTXControlFlow {
    public static class LoopInitOp extends AbstractInstruction {
        public static final LIRInstructionClass<LoopInitOp> TYPE = LIRInstructionClass.create(LoopInitOp.class);

        public LoopInitOp() {
            super(TYPE);
        }

        @Override
        public void emitCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            asm.emit("LOOP_INIT");
        }
    }

    public static class LoopPostOp extends AbstractInstruction {

        public static final LIRInstructionClass<LoopPostOp> TYPE = LIRInstructionClass.create(LoopPostOp.class);

        public LoopPostOp() {
            super(TYPE);
        }

        @Override
        public void emitCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            asm.eol();
        }
    }

    public static class LoopConditionOp extends AbstractInstruction {

        public static final LIRInstructionClass<LoopConditionOp> TYPE = LIRInstructionClass.create(LoopConditionOp.class);
        @Use
        private final Value condition;

        @Use
        private Variable dest;

        public LoopConditionOp(Value condition, Variable dest) {
            super(TYPE);
            this.condition = condition;
            this.dest = dest;
        }

        @Override
        public void emitCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            if (asm.getByte(asm.position() - 1) == ',') {
                asm.emitString(" ", asm.position() - 1);
            }

            asm.delimiter();

            if (condition instanceof PTXLIROp) {
                ((PTXLIROp) condition).emit(crb, asm, dest);
            } else {
                asm.emitValue(condition);
            }

            if (((PTXKind) condition.getPlatformKind()).isInteger()) {
                asm.emit(" == 1");
            }
            asm.delimiter();
        }
    }

    public static class LoopBreakOp extends AbstractInstruction {

        public static final LIRInstructionClass<LoopBreakOp> TYPE = LIRInstructionClass.create(LoopBreakOp.class);

        public LoopBreakOp() {
            super(TYPE);
        }

        @Override
        public void emitCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            asm.loopBreak();
            asm.delimiter();
            asm.eol();
        }

    }

    public static class ConditionalBranchOp extends AbstractInstruction {

        public static final LIRInstructionClass<ConditionalBranchOp> TYPE = LIRInstructionClass.create(ConditionalBranchOp.class);
        @Use private final Value condition;

        public ConditionalBranchOp(Value condition) {
            super(TYPE);
            this.condition = condition;
        }

        @Override
        public void emitCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            asm.emitLine("if " + condition);
        }

    }
}
