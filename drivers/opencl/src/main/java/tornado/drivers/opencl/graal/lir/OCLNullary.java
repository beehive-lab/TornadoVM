package tornado.drivers.opencl.graal.lir;

import com.oracle.graal.compiler.common.LIRKind;
import com.oracle.graal.lir.Opcode;
import tornado.drivers.opencl.graal.asm.OCLAssembler;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLNullaryIntrinsic;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLNullaryOp;
import tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;

public class OCLNullary {

    /**
     * Abstract operation which consumes no inputs
     */
    protected static class NullaryConsumer extends OCLEmitable {

        @Opcode
        protected final OCLNullaryOp opcode;

        protected NullaryConsumer(OCLNullaryOp opcode, LIRKind lirKind) {
            super(lirKind);
            this.opcode = opcode;
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            opcode.emit(crb);
        }

        @Override
        public String toString() {
            return String.format("%s", opcode.toString());
        }
    }

    public static class Expr extends NullaryConsumer {

        public Expr(OCLNullaryOp opcode, LIRKind lirKind) {
            super(opcode, lirKind);
        }
    }

    public static class Intrinsic extends NullaryConsumer {

        public Intrinsic(OCLNullaryIntrinsic opcode, LIRKind lirKind) {
            super(opcode, lirKind);
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            opcode.emit(crb);
            asm.emit("()");
        }

    }

}
