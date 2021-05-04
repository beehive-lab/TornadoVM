package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResultBuilder;

public class SPIRVLIRStmt {

    // FIXME <REFACTOR> Using Generics to refactor this class for the three backends
    // Base class for a LIRInstruction
    protected static abstract class AbstractInstruction extends LIRInstruction {

        public AbstractInstruction(LIRInstructionClass<? extends LIRInstruction> c) {
            super(c);
        }

        @Override
        public void emitCode(CompilationResultBuilder crb) {
            emitCode((SPIRVCompilationResultBuilder) crb, (SPIRVAssembler) crb.asm);
        }

        protected abstract void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm);
    }

    @Opcode("ASSIGN")
    public static class AssignStmt extends AbstractInstruction {

        public static final LIRInstructionClass<AssignStmt> TYPE = LIRInstructionClass.create(AssignStmt.class);

        @Def
        protected AllocatableValue lhs;
        @Use
        protected Value rhs;

        public AssignStmt(AllocatableValue lhs, Value rhs) {
            super(TYPE);
            this.lhs = lhs;
            this.rhs = rhs;
        }

        @Override
        protected void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            // Code emission for assignment

            // From my view, the code assembler should have access to the SPIRVModule (code
            // gen) and emit directly the instructions using the ASM API.

            // It could be a STORE OP from Left to right --> We need the ALGNMENT
        }

        public AllocatableValue getResult() {
            return lhs;
        }

        public Value getExpr() {
            return rhs;
        }
    }

    public static class ExprStmt extends AbstractInstruction {

        public static final LIRInstructionClass<ExprStmt> TYPE = LIRInstructionClass.create(ExprStmt.class);

        @Use
        protected Value expr;

        public ExprStmt(SPIRVLIROp expr) {
            super(TYPE);
            this.expr = expr;
        }

        @Override
        protected void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

            // Same as before, we pass the

        }

        public Value getExpr() {
            return expr;
        }
    }

}
