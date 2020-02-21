package uk.ac.manchester.tornado.drivers.cuda.graal.lir;

import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LabelRef;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.cuda.graal.PTXArchitecture;
import uk.ac.manchester.tornado.drivers.cuda.graal.PTXArchitecture.PTXMemoryBase;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.PTXCompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.cuda.graal.meta.PTXMemorySpace;

import static org.graalvm.compiler.lir.LIRInstruction.*;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler.*;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssemblerConstants.*;
import static uk.ac.manchester.tornado.runtime.common.Tornado.OPENCL_USE_RELATIVE_ADDRESSES;

public class PTXUnary {

    /**
     * Abstract operation which consumes one input
     */
    protected static class UnaryConsumer extends PTXLIROp {
        @Use
        protected Value value;

        @Opcode
        protected PTXUnaryOp opcode;

        UnaryConsumer(PTXUnaryOp opcode, LIRKind lirKind, Value value) {
            super(lirKind);
            this.value = value;
            this.opcode = opcode;
        }

        public Value getValue() {
            return value;
        }

        public PTXUnaryOp getOpcode() {
            return opcode;
        }

        @Override
        public void emit(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            opcode.emit(crb, value);
        }
    }

    public static class Expr extends UnaryConsumer {
        public Expr(PTXUnaryOp opcode, LIRKind lirKind, Value value) {
            super(opcode, lirKind, value);
        }
    }

    public static class Intrinsic extends UnaryConsumer {
        public Intrinsic(PTXUnaryOp opCode, LIRKind lirKind, Value value) {
            super(opCode, lirKind, value);
        }

        @Override
        public String toString() {
            return String.format("%s(%s)", opcode.toString(), value);
        }
    }

    public static class Branch extends UnaryConsumer {

        private LabelRef ref;

        public Branch(PTXUnaryOp opcode, LIRKind lirKind, Value value, LabelRef ref) {
            super(opcode, lirKind, value);
            this.ref = ref;
        }

        @Override
        public void emit (PTXCompilationResultBuilder crb, PTXAssembler asm) {
            super.emit(crb, asm);
            asm.emitSymbol(TAB);
            asm.emit(asm.toString(ref));
        }
    }

    public static class MemoryAccess extends UnaryConsumer {

        private final PTXMemoryBase base;
        private final boolean needsBase;
        private Value index;

        MemoryAccess(PTXMemoryBase base, Value value, boolean needsBase) {
            super(null, LIRKind.Illegal, value);
            this.base = base;
            this.needsBase = needsBase;
        }

        MemoryAccess(PTXMemoryBase base, Value value, Value index, boolean needsBase) {
            super(null, LIRKind.Illegal, value);
            this.base = base;
            this.index = index;
            this.needsBase = needsBase;
        }

        private boolean shouldEmitRelativeAddress() {
            return false;
            //return needsBase || (!(base.memorySpace == PTXMemorySpace.LOCAL) && OPENCL_USE_RELATIVE_ADDRESSES);
        }

        @Override
        public void emit(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            if (shouldEmitRelativeAddress()) {
                asm.emit(base.getName());
                asm.emitSymbol(SQUARE_BRACKETS_OPEN);
                asm.emitValue(value);
                asm.emitSymbol(SQUARE_BRACKETS_CLOSE);
            } else {
                asm.emitValue(value);
            }
        }

        public PTXMemoryBase getBase() {
            return base;
        }

        public Value getIndex() {
            return index;
        }

        @Override
        public String toString() {
            return String.format("%s", value);
        }
    }

    public static class PTXAddressCast extends UnaryConsumer {
        private final PTXMemoryBase base;

        PTXAddressCast(PTXMemoryBase base, LIRKind lirKind) {
            super(PTXUnaryTemplate.CAST_TO_POINTER, lirKind, null);
            this.base = base;
        }

        @Override
        public void emit(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            PTXKind oclKind = (PTXKind) getPlatformKind();
            asm.emit(((PTXUnaryTemplate) opcode).getTemplate(), base.memorySpace.name() + " " + oclKind.toString());
        }

        PTXMemorySpace getMemorySpace() {
            return base.memorySpace;
        }
    }
}
