package uk.ac.manchester.tornado.drivers.cuda.graal.lir;

import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.Opcode;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler.PTXTernaryOp;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.PTXCompilationResultBuilder;

import static org.graalvm.compiler.lir.LIRInstruction.Use;

public class PTXTernary {

    /**
     * Abstract operation which consumes one inputs
     */
    protected static class TernaryConsumer extends PTXLIROp {
        @Opcode
        protected final PTXTernaryOp opcode;

        @Use
        protected Value x;
        @Use
        protected Value y;
        @Use
        protected Value z;

        protected TernaryConsumer(PTXTernaryOp opcode, LIRKind lirKind, Value x, Value y, Value z) {
            super(lirKind);
            this.opcode = opcode;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public void emit(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            opcode.emit(crb, x, y, z);
        }

        @Override
        public String toString() {
            return String.format("%s %s %s %s", opcode.toString(), x, y, z);
        }
    }

    public static class Expr extends TernaryConsumer {
        public Expr(PTXTernaryOp opcode, LIRKind lirKind, Value x, Value y, Value z)  {
            super(opcode, lirKind, x, y, z);
        }
    }
}
