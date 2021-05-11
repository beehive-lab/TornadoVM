package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.ConstantValue;
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

            // The final store is emitted in the assignParameter
            asm.registerLIRInstructionValue(this, loadPtr);
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

        private final Value address;

        private final Value valueToStore;

        public SPIRVAddressCast(Value address, SPIRVMemoryBase base, LIRKind valueKind, Value valueToStore) {
            super(null, valueKind, address);
            this.base = base;
            this.address = address;
            this.valueToStore = valueToStore;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            SPIRVLogger.traceCodeGen("µInstr SPIRVAddressCast");
            SPIRVId idLoad = asm.module.getNextId();

            SPIRVKind spirvKind = (SPIRVKind) getPlatformKind();

            // We force to load a pointer to long
            SPIRVId typeLoad = asm.primitives.getTypeInt(SPIRVKind.OP_TYPE_INT_64);

            SPIRVId addressToLoad = asm.lookUpLIRInstructions(address);

            asm.currentBlockScope.add(new SPIRVOpLoad( //
                    typeLoad, //
                    idLoad, //
                    addressToLoad, //
                    new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(SPIRVKind.OP_TYPE_INT_64.getByteCount())))));

            SPIRVId ptrCrossWorkGroupUInt = asm.pointerToGlobalMemoryHeap;
            SPIRVId storeAddressID = asm.module.getNextId();
            asm.currentBlockScope.add(new SPIRVOpConvertUToPtr(ptrCrossWorkGroupUInt, storeAddressID, idLoad));

            SPIRVId value;
            if (valueToStore instanceof ConstantValue) {
                value = asm.constants.get(((ConstantValue) this.valueToStore).getConstant().toValueString());
            } else {
                value = asm.lookUpLIRInstructions(valueToStore);
            }

            asm.currentBlockScope.add(new SPIRVOpStore( //
                    storeAddressID, //
                    value, //
                    new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(spirvKind.getByteCount())) //
                    )));

            asm.registerLIRInstructionValue(this, storeAddressID);
        }
    }

}
