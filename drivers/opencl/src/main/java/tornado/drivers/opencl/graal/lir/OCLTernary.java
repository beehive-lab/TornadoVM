package tornado.drivers.opencl.graal.lir;

import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.LIRKind;
import com.oracle.graal.api.meta.PlatformKind;
import com.oracle.graal.api.meta.Value;
import com.oracle.graal.lir.LIRInstruction.Use;
import com.oracle.graal.lir.Opcode;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLTernaryOp;
import tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;

public class OCLTernary {

    /**
     * Abstract operation which consumes two inputs
     */
    protected static class TernaryConsumer implements OCLEmitable {

        protected final Kind kind;
        protected final LIRKind lirKind;

        @Opcode
        protected final OCLTernaryOp opcode;

        @Use
        protected Value x;
        @Use
        protected Value y;
        @Use
        protected Value z;

        protected TernaryConsumer(OCLTernaryOp opcode, Kind kind, LIRKind lirKind, Value x, Value y, Value z) {
            this.opcode = opcode;
            this.kind = kind;
            this.lirKind = lirKind;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public TernaryConsumer(OCLTernaryOp opcode, LIRKind lirKind, Value x, Value y, Value z) {
            this(opcode, Kind.Illegal, lirKind, x, y, z);
        }

        public TernaryConsumer(OCLTernaryOp opcode, Kind kind, Value x, Value y, Value z) {
            this(opcode, kind, LIRKind.value(kind), x, y, z);
        }

        @Override
        public Kind getKind() {
            return kind;
        }

        @Override
        public LIRKind getLIRKind() {
            return lirKind;
        }

        @Override
        public PlatformKind getPlatformKind() {
            return lirKind.getPlatformKind();
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb) {
            opcode.emit(crb, x, y, z);
        }

        public String toString() {
            return String.format("%s %s %s %s", opcode.toString(), x, y, z);
        }

    }

    public static class Expr extends TernaryConsumer {

        public Expr(OCLTernaryOp opcode, LIRKind lirKind, Value x, Value y, Value z) {
            super(opcode, lirKind, x, y, z);
        }

        public Expr(OCLTernaryOp opcode, Kind kind, Value x, Value y, Value z) {
            super(opcode, kind, x, y, z);
        }
    }

    /**
     * OpenCL intrinsic call which consumes three inputs
     */
    public static class Intrinsic extends TernaryConsumer {

        public Intrinsic(OCLTernaryOp opcode, LIRKind lirKind, Value x, Value y, Value z) {
            super(opcode, lirKind, x, y, z);
        }

        public Intrinsic(OCLTernaryOp opcode, Kind kind, Value x, Value y, Value z) {
            super(opcode, kind, x, y, z);
        }

        @Override
        public String toString() {
            return String.format("%s(%s, %s, %s)", opcode.toString(), x, y, z);
        }

    }
    
    

}
