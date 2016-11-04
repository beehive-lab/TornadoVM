package tornado.drivers.opencl.graal.lir;

import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.LIRKind;
import com.oracle.graal.api.meta.PlatformKind;
import com.oracle.graal.api.meta.Value;
import com.oracle.graal.lir.LIRInstruction.Use;
import com.oracle.graal.lir.Opcode;
import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLOp;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLOp2;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLOp3;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLOp4;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLOp8;
import tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;
import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;

public class OCLVectorAssign {

    /**
     * OpenCL vector assignment expression
     */
    public static class Assign2Expr implements OCLEmitable {

        protected OCLKind kind;

        @Opcode
        protected final OCLOp opcode;

        @Use
        protected Value s0;
        @Use
        protected Value s1;

        public Assign2Expr(OCLOp opcode, OCLKind kind, Value s0, Value s1) {
            this.opcode = opcode;
            this.s0 = s0;
            this.s1 = s1;
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb) {
            ((OCLOp2) opcode).emit(crb, s0, s1);
        }

        @Override
        public LIRKind getLIRKind() {
            return LIRKind.value(kind);
        }

        @Override
        public PlatformKind getPlatformKind() {
            return kind;
        }

        @Override
        public Kind getKind() {
            shouldNotReachHere();
            return Kind.Illegal;
        }

    }

    /**
     * OpenCL vector assignment expression
     */
    public static class Assign3Expr extends Assign2Expr {

        @Use
        protected Value s2;

        public Assign3Expr(OCLOp3 opcode, OCLKind kind, Value s0, Value s1, Value s2) {
            super(opcode, kind, s0, s1);
            this.s2 = s2;
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb) {
            ((OCLOp3) opcode).emit(crb, s0, s1, s2);
        }

    }

    /**
     * OpenCL vector assignment expression
     */
    public static class Assign4Expr extends Assign3Expr {

        @Use
        protected Value s3;

        public Assign4Expr(OCLOp4 opcode, OCLKind kind, Value s0, Value s1, Value s2, Value s3) {
            super(opcode, kind, s0, s1, s2);
            this.s3 = s3;
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb) {
            ((OCLOp4) opcode).emit(crb, s0, s1, s2, s3);
        }

    }

    /**
     * OpenCL vector assignment expression
     */
    public static class Assign8Expr extends Assign4Expr {

        @Use
        protected Value s4;
        @Use
        protected Value s5;
        @Use
        protected Value s6;
        @Use
        protected Value s7;

        public Assign8Expr(OCLOp8 opcode, OCLKind kind, Value s0, Value s1, Value s2, Value s3, Value s4, Value s5, Value s6, Value s7) {
            super(opcode, kind, s0, s1, s2, s3);
            this.s4 = s4;
            this.s5 = s5;
            this.s6 = s6;
            this.s7 = s7;
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb) {
            ((OCLOp8) opcode).emit(crb, s0, s1, s2, s3, s4, s5, s6, s7);
        }

    }

}
