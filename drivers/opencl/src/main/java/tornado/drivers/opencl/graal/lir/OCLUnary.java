package tornado.drivers.opencl.graal.lir;

import com.oracle.graal.compiler.common.LIRKind;
import com.oracle.graal.lir.LIRInstruction.Use;
import com.oracle.graal.lir.Opcode;
import jdk.vm.ci.meta.Value;
import tornado.drivers.opencl.graal.OCLArchitecture.OCLMemoryBase;
import tornado.drivers.opencl.graal.asm.OCLAssembler;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLUnaryOp;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLUnaryTemplate;
import tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;
import tornado.drivers.opencl.graal.meta.OCLMemorySpace;
import tornado.drivers.opencl.graal.nodes.OCLBarrierNode.OCLMemFenceFlags;

import static tornado.common.Tornado.OPENCL_USE_RELATIVE_ADDRESSES;
import static tornado.drivers.opencl.graal.asm.OCLAssemblerConstants.*;

public class OCLUnary {

    /**
     * Abstract operation which consumes one inputs
     */
    protected static class UnaryConsumer extends OCLLIROp {

        @Opcode
        protected final OCLUnaryOp opcode;

        @Use
        protected Value value;

        protected UnaryConsumer(OCLUnaryOp opcode, LIRKind lirKind, Value value) {
            super(lirKind);
            this.opcode = opcode;
            this.value = value;
        }

        public Value getValue() {
            return value;
        }

        public OCLUnaryOp getOpcode() {
            return opcode;
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            opcode.emit(crb, value);
        }

        @Override
        public String toString() {
            return String.format("%s %s", opcode.toString(), value);
        }

    }

    public static class Expr extends UnaryConsumer {

        public Expr(OCLUnaryOp opcode, LIRKind lirKind, Value value) {
            super(opcode, lirKind, value);
        }

    }

    public static class Intrinsic extends UnaryConsumer {

        public Intrinsic(OCLUnaryOp opcode, LIRKind lirKind, Value value) {
            super(opcode, lirKind, value);
        }

        @Override
        public String toString() {
            return String.format("%s(%s)", opcode.toString(), value);
        }

    }

    public static class Barrier extends UnaryConsumer {

        OCLMemFenceFlags flags;

        public Barrier(OCLUnaryOp opcode, OCLMemFenceFlags flags) {
            super(opcode, LIRKind.Illegal, null);
            this.flags = flags;
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            asm.emit(toString());
        }

        @Override
        public String toString() {
            return String.format("%s(CLK_%s_MEM_FENCE)", opcode.toString(), flags.toString().toUpperCase());
        }

    }

    public static class FloatCast extends UnaryConsumer {

        public FloatCast(OCLUnaryOp opcode, LIRKind lirKind, Value value) {
            super(opcode, lirKind, value);
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            asm.emit("isnan(");
            asm.value(crb, value);
            asm.emit(")? 0 : ");
            opcode.emit(crb, value);
        }

        @Override
        public String toString() {
            return String.format("isnan(%s) ? 0 : %s %s", value, opcode.toString(), value);
        }
    }

    public static class MemoryAccess extends UnaryConsumer {

        private final OCLMemoryBase base;
        private final boolean needsBase;

        public MemoryAccess(OCLMemoryBase base, Value value, boolean needsBase) {
            super(null, LIRKind.Illegal, value);
            this.base = base;
            this.needsBase = needsBase;
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {

            if (needsBase || OPENCL_USE_RELATIVE_ADDRESSES) {
                asm.emitSymbol(ADDRESS_OF);
                asm.emit(base.name);
                asm.emitSymbol(SQUARE_BRACKETS_OPEN);
                asm.value(crb, value);
                asm.emitSymbol(SQUARE_BRACKETS_CLOSE);
            } else {
                asm.value(crb, value);
            }
        }

        public OCLMemoryBase getBase() {
            return base;
        }

        @Override
        public String toString() {
            return String.format("%s", value);
        }
    }

    public static class OCLAddressCast extends UnaryConsumer {

        private final OCLMemoryBase base;

        public OCLAddressCast(OCLMemoryBase base, LIRKind lirKind) {
            super(OCLUnaryTemplate.CAST_TO_POINTER, lirKind, null);
            this.base = base;
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            OCLKind oclKind = (OCLKind) getPlatformKind();
            asm.emit(((OCLUnaryTemplate) opcode).getTemplate(), base.memorySpace.name() + " " + oclKind.toString());
        }

        public OCLMemorySpace getMemorySpace() {
            return base.memorySpace;
        }

    }

}
