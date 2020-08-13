package uk.ac.manchester.tornado.drivers.ptx.graal.lir;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.Variable;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler.PTXNullaryOp;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXCompilationResultBuilder;

import static uk.ac.manchester.tornado.drivers.ptx.graal.PTXCodeUtil.getFPURoundingMode;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler.PTXNullaryTemplate;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.COMMA;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.CONVERT;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.DOT;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.MOVE;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.SPACE;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.TAB;

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
            PTXKind lhsKind = (PTXKind) dest.getPlatformKind();
            PTXKind rhsKind = (PTXKind) getLIRKind().getPlatformKind();

            if (lhsKind.isVector() && rhsKind.isVector()) {
                PTXVectorSplit destVectorSplit = new PTXVectorSplit(dest);
                PTXVectorSplit rhsVectorSplit = new PTXVectorSplit(opcode.toString(), rhsKind);
                PTXVectorAssign.doVectorToVectorAssign(asm, destVectorSplit, rhsVectorSplit);
            } else {
                asm.emitSymbol(TAB);
                if (lhsKind == rhsKind) {
                    asm.emit(MOVE + "." + lhsKind.toString());
                } else {
                    asm.emit(CONVERT + ".");
                    if ((lhsKind.isFloating() || rhsKind.isFloating()) && getFPURoundingMode(lhsKind, rhsKind) != null) {
                        asm.emit(getFPURoundingMode(lhsKind, rhsKind));
                        asm.emitSymbol(DOT);
                    }
                    asm.emit(lhsKind.toString());
                    asm.emitSymbol(DOT);
                    asm.emit(rhsKind.toString());
                }
                asm.emitSymbol(TAB);
                asm.emitValue(dest);
                asm.emitSymbol(COMMA);
                asm.emitSymbol(SPACE);
                asm.emit(opcode.toString());
            }
        }
    }

    public static class Expr extends NullaryConsumer {
        public Expr(PTXNullaryOp opcode, LIRKind lirKind) {
            super(opcode, lirKind);
        }
    }
}
