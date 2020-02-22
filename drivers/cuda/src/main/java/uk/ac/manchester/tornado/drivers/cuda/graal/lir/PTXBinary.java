package uk.ac.manchester.tornado.drivers.cuda.graal.lir;

import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LabelRef;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.Variable;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler.PTXBinaryOp;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.PTXCompilationResultBuilder;

import static org.graalvm.compiler.lir.LIRInstruction.*;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler.*;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssemblerConstants.COMMA;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssemblerConstants.TAB;

public class PTXBinary {

    /**
     * Abstract operation which consumes two inputs
     */
    protected static class BinaryConsumer extends PTXLIROp {

        @Opcode
        protected final PTXBinaryOp opcode;

        @Use
        protected Value x;
        @Use
        protected Value y;

        protected BinaryConsumer(PTXBinaryOp opcode, LIRKind lirKind, Value x, Value y) {
            super(lirKind);
            this.opcode = opcode;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emit(PTXCompilationResultBuilder crb, PTXAssembler asm, Variable dest) {
            opcode.emit(crb, x, y, dest);
        }

        public Value getX() {
            return x;
        }

        public Value getY() {
            return y;
        }

        @Override
        public String toString() {
            return String.format("%s %s %s", opcode.toString(), x, y);
        }

    }

    public static class Expr extends BinaryConsumer {

        public Expr(PTXBinaryOp opcode, LIRKind lirKind, Value x, Value y) {
            super(opcode, lirKind, x, y);
        }
    }

    /**
     * PTX intrinsic call which consumes two inputs
     */
    public static class Intrinsic extends BinaryConsumer {

        public Intrinsic(PTXBinaryIntrinsic opcode, LIRKind lirKind, Value x, Value y) {
            super(opcode, lirKind, x, y);
        }

        @Override
        public String toString() {
            return String.format("%s(%s, %s)", opcode.toString(), x, y);
        }
    }

    public static class Selector extends Expr {

        public Selector(PTXBinaryOp opcode, LIRKind lirKind, Value x, Value y) {
            super(opcode, lirKind, x, y);
        }

        @Override
        public void emit(PTXCompilationResultBuilder crb, PTXAssembler asm, Variable dest) {
            asm.emit(opcode.toString());
            asm.emitSymbol(TAB);
            asm.emitValues(new Value[]{dest, x, y});
        }

        @Override
        public String toString() {
            return String.format("%s.%s", opcode.toString(), x, y);
        }

    }
}
