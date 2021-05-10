package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LIRInstruction.Use;
import org.graalvm.compiler.lir.Opcode;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpConvertUToPtr;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpInBoundsPtrAccessChain;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpLoad;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpStore;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVLiteralInteger;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVMemoryAccess;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVMultipleOperands;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVOptionalOperand;
import uk.ac.manchester.tornado.drivers.spirv.common.SPIRVLogger;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVArchitecture.SPIRVMemoryBase;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler.SPIRVUnaryOp;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResultBuilder;

/**
 * Operations for one Input
 */
public class SPIRVUnary {

    protected static class UnaryConsumer extends SPIRVLIROp {

        @Opcode
        protected final SPIRVUnaryOp opcode;

        @Use
        protected Value value;

        protected UnaryConsumer(SPIRVUnaryOp opcode, LIRKind valueKind, Value value) {
            super(valueKind);
            this.opcode = opcode;
            this.value = value;
        }

        public Value getValue() {
            return value;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            opcode.emit(crb, value);
        }

    }

    public static class Expr extends UnaryConsumer {

        public Expr(SPIRVUnaryOp opcode, LIRKind lirKind, Value value) {
            super(opcode, lirKind, value);
        }

    }

    public static class LoadFromStackFrameExpr extends Expr {

        protected SPIRVKind type;
        protected SPIRVId address;
        protected int indexFromStackFrame;
        protected int parameterIndex;

        public LoadFromStackFrameExpr(SPIRVUnaryOp opcode, LIRKind lirKind, Value value, SPIRVKind type, int indexFromStackFrame, int parameterIndex) {
            super(opcode, lirKind, value);
            this.type = type;
            this.indexFromStackFrame = indexFromStackFrame;
            this.parameterIndex = parameterIndex;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            SPIRVLogger.trace("µIns LoadFromStackFrame ");
            SPIRVId loadID = asm.module.getNextId();

            SPIRVId ptrFUnctionULong = null;
            if (type == SPIRVKind.OP_TYPE_INT_64) {
                ptrFUnctionULong = asm.pointerToULongFunction;
            }
            SPIRVId address = asm.frameId;
            int alignment = 8;
            asm.currentBlockScope.add(new SPIRVOpLoad( //
                    ptrFUnctionULong, //
                    loadID, //
                    address, //
                    new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(alignment))) //
            ));

            String values = String.valueOf(indexFromStackFrame);
            SPIRVId index = asm.constants.get(values);
            System.out.println("ID found for index: " + index);

            SPIRVId accessPTR = asm.module.getNextId();
            asm.currentBlockScope.add(new SPIRVOpInBoundsPtrAccessChain( //
                    asm.pointerToULongFunction, //
                    accessPTR, //
                    loadID, //
                    index, //
                    new SPIRVMultipleOperands<>()));

            // Load Address
            SPIRVId loadPtr = asm.module.getNextId();
            asm.currentBlockScope.add(new SPIRVOpLoad( //
                    ptrFUnctionULong, //
                    loadPtr, //
                    accessPTR, //
                    new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(alignment))) //
            ));

            SPIRVId parameterID = asm.getParameterId(parameterIndex);
            asm.currentBlockScope.add(new SPIRVOpStore( //
                    parameterID, //
                    loadPtr, //
                    new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(alignment))) //
            ));
        }
    }

    public static class Intrinsic extends UnaryConsumer {

        public Intrinsic(SPIRVUnaryOp opcode, LIRKind lirKind, Value value) {
            super(opcode, lirKind, value);
        }

        @Override
        public String toString() {
            return String.format("%s(%s)", opcode.toString(), value);
        }

    }

    public static class MemoryAccess extends UnaryConsumer {

        private final SPIRVMemoryBase base;
        private Value index;
        private AllocatableValue assignedTo;

        MemoryAccess(SPIRVMemoryBase base, Value value) {
            super(null, LIRKind.Illegal, value);
            this.base = base;
        }

        MemoryAccess(SPIRVMemoryBase base, Value value, Value index, boolean needsBase) {
            super(null, LIRKind.Illegal, value);
            this.base = base;
            this.index = index;
        }

        public SPIRVMemoryBase getBase() {
            return base;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            System.out.println("\n\n - &&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&  Generating memory access: " + base + index);
        }

        public Value getIndex() {
            return index;
        }

        public AllocatableValue assignedTo() {
            return assignedTo;
        }
    }

    public static class SPIRVAddressCast extends UnaryConsumer {

        private final SPIRVMemoryBase base;

        public SPIRVAddressCast(SPIRVMemoryBase base, LIRKind valueKind) {
            super(null, valueKind, null);
            this.base = base;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            SPIRVId id31 = asm.module.getNextId();

            SPIRVId ulong = asm.primitives.getTypeInt(SPIRVKind.OP_TYPE_INT_64);

            SPIRVId ul1 = asm.getParameterId(1);

            asm.currentBlockScope.add(new SPIRVOpLoad(ulong, id31, ul1, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));

            SPIRVId ptrCrossWorkGroupUInt = asm.pointerToGlobalMemoryHeap;
            SPIRVId id34 = asm.module.getNextId();
            asm.currentBlockScope.add(new SPIRVOpConvertUToPtr(ptrCrossWorkGroupUInt, id34, id31));

            SPIRVId uintConstant50 = asm.constants.get("50");
            asm.currentBlockScope.add(new SPIRVOpStore(id34, uintConstant50, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(4)))));
        }

    }

}
