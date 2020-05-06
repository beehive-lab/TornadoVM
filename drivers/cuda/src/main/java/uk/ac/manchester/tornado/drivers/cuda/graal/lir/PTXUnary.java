package uk.ac.manchester.tornado.drivers.cuda.graal.lir;

import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.Variable;
import uk.ac.manchester.tornado.drivers.cuda.graal.PTXArchitecture.PTXMemoryBase;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.PTXCompilationResultBuilder;

import static org.graalvm.compiler.lir.LIRInstruction.Use;
import static uk.ac.manchester.tornado.drivers.cuda.graal.PTXArchitecture.paramSpace;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler.PTXUnaryOp;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssemblerConstants.*;

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
        public void emit(PTXCompilationResultBuilder crb, PTXAssembler asm, Variable dest) {
            opcode.emit(crb, value, dest);
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

    public static class MemoryAccess extends UnaryConsumer {

        private final PTXMemoryBase base;
        private ConstantValue index;
        private String name;
        private Variable assignedTo;

        MemoryAccess(PTXMemoryBase base, Value value) {
            super(null, LIRKind.Illegal, value);
            this.base = base;
        }

        MemoryAccess(PTXMemoryBase base, Value value, ConstantValue index) {
            super(null, LIRKind.Illegal, value);
            this.base = base;

            this.index = index;
        }

        public MemoryAccess(String name) {
            super(null, LIRKind.Illegal, null);
            this.base = paramSpace;
            this.name = name;
        }

        private boolean shouldEmitRelativeAddress() {
            return false;
            //return needsBase || (!(base.memorySpace == PTXMemorySpace.LOCAL) && OPENCL_USE_RELATIVE_ADDRESSES);
        }

        @Override
        public void emit(PTXCompilationResultBuilder crb, PTXAssembler asm, Variable dest) {
            asm.emitSymbol(SQUARE_BRACKETS_OPEN);
            if (name != null) asm.emit(name);
            if (value != null) asm.emitValue(value);
            if (index != null && index.getJavaConstant().asInt() != 0) {
                asm.emitConstant(index);
            }
            asm.emitSymbol(SQUARE_BRACKETS_CLOSE);
        }

        public PTXMemoryBase getBase() {
            return base;
        }

        public void assignTo(Variable loadedTo) {
            assignedTo = loadedTo;
        }

        public Variable assignedTo() {
            return assignedTo;
        }

        @Override
        public String toString() {
            return String.format("%s", value);
        }
    }

}
