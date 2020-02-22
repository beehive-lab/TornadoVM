package uk.ac.manchester.tornado.drivers.cuda.graal.lir;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.Variable;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler.PTXNullaryOp;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.PTXCompilationResultBuilder;

import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler.*;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssemblerConstants.STMT_DELIMITER;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssemblerConstants.TAB;

public class PTXNullary {
    /**
     * Abstract operation which consumes no inputs
     */
    protected static class NullaryConsumer extends PTXLIROp {

        @Opcode
        protected final PTXNullaryOp opcode;

        protected NullaryConsumer(PTXNullaryOp opcode, LIRKind lirKind) {
            super(lirKind);
            this.opcode = opcode;
        }

        @Override
        public void emit(PTXCompilationResultBuilder crb, PTXAssembler asm, Variable dest) {
            opcode.emit(crb, dest);
        }

        @Override
        public String toString() {
            return String.format("%s", opcode.toString());
        }

        public PTXNullaryOp getOpcode() {
            return opcode;
        }
    }

    public static class Parameter extends NullaryConsumer {
        public Parameter(String name, LIRKind lirKind) {
            super(new PTXNullaryTemplate(name), lirKind);
        }

        @Override
        public void emit(PTXCompilationResultBuilder crb, PTXAssembler asm, Variable dest) {
            asm.emit(opcode.toString());
            asm.emitSymbol(TAB);
            asm.emitValue(dest);
            asm.emitSymbol(STMT_DELIMITER);
            asm.eol();
        }
    }

    public static class Expr extends NullaryConsumer {
        public Expr(PTXNullaryOp opcode, LIRKind lirKind) {
            super(opcode, lirKind);
        }
    }
}
