package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.LIRInstruction.Use;
import org.graalvm.compiler.lir.Opcode;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpCompositeExtract;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpConvertUToPtr;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpInBoundsPtrAccessChain;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpLoad;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpTypeVector;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpUConvert;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVLiteralInteger;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVMemoryAccess;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVMultipleOperands;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVOptionalOperand;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVOCLBuiltIn;
import uk.ac.manchester.tornado.drivers.spirv.common.SPIRVLogger;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVArchitecture.SPIRVMemoryBase;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler.SPIRVUnaryOp;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResultBuilder;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

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
            SPIRVLogger.traceCodeGen("µInstr MemoryAccess (EMPTY IMPLEMENTATION)");
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

        /**
         * Generates the following SPIR-V code:
         *
         * <code>
         * %34 = OpConvertUToPtr %_ptr_CrossWorkgroup_uchar %32
         * </code>
         *
         * @param crb
         * @param asm
         */
        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            SPIRVLogger.traceCodeGen("µInstr SPIRVAddressCast");
            SPIRVId idLoad = asm.module.getNextId();

            // We force to load a pointer to long
            SPIRVId typeLoad = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_64);

            SPIRVId addressToLoad = asm.lookUpLIRInstructions(address);

            if (!TornadoOptions.OPTIMIZE_LOAD_STORE_SPIRV) {
                asm.currentBlockScope.add(new SPIRVOpLoad( //
                        typeLoad, //
                        idLoad, //
                        addressToLoad, //
                        new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(SPIRVKind.OP_TYPE_INT_64.getByteCount())))));
            } else {
                idLoad = addressToLoad;
            }

            SPIRVId ptrCrossWorkGroupUInt = asm.pointerToGlobalMemoryHeap;
            SPIRVId storeAddressID = asm.module.getNextId();
            asm.currentBlockScope.add(new SPIRVOpConvertUToPtr(ptrCrossWorkGroupUInt, storeAddressID, idLoad));

            asm.registerLIRInstructionValue(this, storeAddressID);
        }
    }

    public static class OpenCLBuiltinCallForSPIRV extends UnaryConsumer {

        protected SPIRVOCLBuiltIn builtIn;
        protected Value dimension;

        public OpenCLBuiltinCallForSPIRV(SPIRVOCLBuiltIn builtIn, LIRKind valueKind, Value dimension) {
            super(null, valueKind, dimension);
            this.dimension = dimension;
            this.builtIn = builtIn;
        }

        /**
         * Equivalent OpenCL Code:
         *
         * <code>
         * int idx = get_global_id(dimensionIndex);
         * </code>
         *
         * <code>
         *        %37 = OpLoad %v3ulong %__spirv_BuiltInGlobalInvocationId Aligned 32
         *      %call = OpCompositeExtract %ulong %37 0
         *      %conv = OpUConvert %uint %call
         * </code>
         */
        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            SPIRVLogger.traceCodeGen("µInstr ThreadID");

            SPIRVId ulong = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_64);

            // All builtins have to be registered previous to this call
            SPIRVId idSPIRVBuiltin = asm.builtinTable.get(builtIn);

            SPIRVId v3long = asm.module.getNextId();
            asm.module.add(new SPIRVOpTypeVector( //
                    v3long, //
                    ulong, new SPIRVLiteralInteger(3)));

            // Call Thread-ID getGlobalId(0)
            SPIRVId id19 = asm.module.getNextId();
            asm.currentBlockScope.add(new SPIRVOpLoad(v3long, id19, idSPIRVBuiltin, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(32)))));

            // Intrinsic call
            SPIRVId callIntrinsicId = asm.module.getNextId();

            int dimensionValue;
            if (dimension instanceof ConstantValue) {
                dimensionValue = Integer.parseInt(((ConstantValue) dimension).getConstant().toValueString());
            } else {
                throw new RuntimeException("Not supported");
            }

            asm.currentBlockScope.add(new SPIRVOpCompositeExtract(ulong, callIntrinsicId, id19, new SPIRVMultipleOperands<>(new SPIRVLiteralInteger(dimensionValue))));

            SPIRVId conv = asm.module.getNextId();
            // FIXME check this
            SPIRVId uint = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_32);

            asm.currentBlockScope.add(new SPIRVOpUConvert(uint, conv, callIntrinsicId));

            // XXX: Store will be performed in the Assigment, if enabled.

            asm.registerLIRInstructionValue(this, conv);
        }
    }
}
