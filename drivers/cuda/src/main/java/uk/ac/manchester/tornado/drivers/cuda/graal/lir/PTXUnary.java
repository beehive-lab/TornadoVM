package uk.ac.manchester.tornado.drivers.cuda.graal.lir;

import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.Variable;
import uk.ac.manchester.tornado.drivers.cuda.graal.PTXArchitecture.PTXMemoryBase;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.PTXCompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.cuda.graal.meta.PTXMemorySpace;

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

    public static class Barrier extends PTXUnary.UnaryConsumer {

        private final int ctaInstance;
        private final int numberOfThreads;

        public Barrier(PTXAssembler.PTXUnaryOp opcode, int ctaInstance, int numberOfThreads) {
            super(opcode, LIRKind.Illegal, null);
            this.ctaInstance = ctaInstance;
            this.numberOfThreads = numberOfThreads;
        }

        @Override
        public void emit(PTXCompilationResultBuilder crb, PTXAssembler asm, Variable dest) {
            opcode.emitOpcode(asm);
            asm.emitSymbol(TAB);
            asm.emitSymbol(String.valueOf(ctaInstance));
            if (numberOfThreads != -1) {
                asm.emitSymbol(COMMA);
                asm.emitSymbol(SPACE);
                asm.emitInt(numberOfThreads);
            }
        }

    }

    public static class MemoryAccess extends UnaryConsumer {

        private final PTXMemoryBase base;
        private Value index;
        private String name;
        private Variable assignedTo;

        MemoryAccess(PTXMemoryBase base, Value value) {
            super(null, LIRKind.Illegal, value);
            this.base = base;
        }

        MemoryAccess(PTXMemoryBase base, Value value, Value index) {
            super(null, LIRKind.Illegal, value);
            this.base = base;
            this.index = index;
        }

        public MemoryAccess(String name) {
            super(null, LIRKind.Illegal, null);
            this.base = paramSpace;
            this.name = name;
        }

        @Override
        public void emit(PTXCompilationResultBuilder crb, PTXAssembler asm, Variable dest) {
            if (isLocalOrSharedMemoryAccess()) {
                if (value != null) asm.emitValue(value);
                asm.emitSymbol(SQUARE_BRACKETS_OPEN);
                if (index != null) asm.emitValue(index);
                asm.emitSymbol(SQUARE_BRACKETS_CLOSE);
            } else {
                asm.emitSymbol(SQUARE_BRACKETS_OPEN);
                if (name != null) asm.emit(name);
                if (value != null) asm.emitValue(value);
                if (index != null && ((ConstantValue) index).getJavaConstant().asInt() != 0) {
                    asm.emitConstant((ConstantValue) index);
                }
                asm.emitSymbol(SQUARE_BRACKETS_CLOSE);
            }
        }

        public void emit(PTXAssembler asm, int index) {
            if (isLocalOrSharedMemoryAccess()) {
                if (value != null) asm.emitValue(value);
                asm.emitSymbol(SQUARE_BRACKETS_OPEN);
                asm.emitConstant(index);
            } else {
                asm.emitSymbol(SQUARE_BRACKETS_OPEN);
                if (name != null) asm.emit(name);
                if (value != null) asm.emitValue(value);
                if (index != 0) {
                    asm.emitConstant(index);
                }
            }
            asm.emitSymbol(SQUARE_BRACKETS_CLOSE);
        }

        private boolean isLocalOrSharedMemoryAccess() {
            return base.memorySpace.name().equals(PTXMemorySpace.LOCAL.name()) || base.memorySpace.name().equals(PTXMemorySpace.SHARED.name());
        }

        private boolean isVector() {
            return assignedTo != null && ((PTXKind) assignedTo.getPlatformKind()).isVector();
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
