package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.LIRInstruction.Use;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.Variable;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpCompositeExtract;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpControlBarrier;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpConvertSToF;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpConvertUToPtr;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpExtInst;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpFConvert;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpFNegate;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpInBoundsPtrAccessChain;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpLoad;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpSConvert;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpSNegate;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpUConvert;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVLiteralExtInstInteger;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVLiteralInteger;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVMemoryAccess;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVMultipleOperands;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVOptionalOperand;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVOCLBuiltIn;
import uk.ac.manchester.tornado.drivers.spirv.common.SPIRVLogger;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVArchitecture;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVArchitecture.SPIRVMemoryBase;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler.SPIRVUnaryOp;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVBarrierNode;
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

        public LoadFromStackFrameExpr(LIRKind lirKind, SPIRVKind type, int indexFromStackFrame, int parameterIndex) {
            super(null, lirKind, null);
            this.type = type;
            this.indexFromStackFrame = indexFromStackFrame;
            this.parameterIndex = parameterIndex;
        }

        /**
         * This represents a load from a parameter from the stack-frame.
         * <p>
         * The equivalent in OpenCL is as follows:
         *
         * <code>
         * ulong_0 = (ulong) _frame[STACK_INDEX];
         * </code>
         * <p>
         * <p>
         * This an example of the target code to generate in SPIR-V:
         *
         * <code>
         * %24 = OpLoad %_ptr_CrossWorkgroup_ulong %_frame Aligned 8
         * %ptridx1 = OpInBoundsPtrAccessChain %_ptr_CrossWorkgroup_ulong %24 STACK_INDEX
         * %27 = OpLoad %ulong %ptridx1 Aligned 8
         * OpStore %ul_0 %27 Aligned 8
         * </code>
         */
        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            SPIRVLogger.traceCodeGen("µIns LoadFromStackFrame ");
            SPIRVId loadID = asm.module.getNextId();

            SPIRVId ptrCrossWorkGroupULong = null;
            if (type == SPIRVKind.OP_TYPE_INT_64) {
                ptrCrossWorkGroupULong = asm.ptrCrossWorkULong;
            }
            SPIRVId address = asm.frameId;
            int alignment = 8;
            asm.currentBlockScope().add(new SPIRVOpLoad( //
                    ptrCrossWorkGroupULong, //
                    loadID, //
                    address, //
                    new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(alignment))) //
            ));

            String constantValue = String.valueOf(indexFromStackFrame);
            SPIRVId index = asm.lookUpConstant(constantValue, SPIRVKind.OP_TYPE_INT_32);

            SPIRVId accessPTR = asm.module.getNextId();
            asm.currentBlockScope().add(new SPIRVOpInBoundsPtrAccessChain( //
                    ptrCrossWorkGroupULong, //
                    accessPTR, //
                    loadID, //
                    index, //
                    new SPIRVMultipleOperands<>()));

            SPIRVId ulong = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_64);

            // Load Address
            SPIRVId loadPtr = asm.module.getNextId();
            asm.currentBlockScope().add(new SPIRVOpLoad( //
                    ulong, //
                    loadPtr, //
                    accessPTR, //
                    new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(alignment))) //
            ));

            // The final store is emitted in the assignParameter
            asm.registerLIRInstructionValue(this, loadPtr);
        }
    }

    public static class LoadIndexValueFromStack extends Expr {

        protected SPIRVKind type;
        protected SPIRVId address;
        protected Value parameterIndex;

        public LoadIndexValueFromStack(LIRKind lirKind, SPIRVKind type, Value parameterIndex) {
            super(null, lirKind, null);
            this.type = type;
            this.parameterIndex = parameterIndex;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            SPIRVLogger.traceCodeGen("µIns LoadIndexValueFromStack ");
            SPIRVId loadID = asm.module.getNextId();

            SPIRVId ptrCrossWorkGroupULong = asm.ptrCrossWorkULong;

            SPIRVId address = asm.frameId;
            final int alignment = 8;
            asm.currentBlockScope().add(new SPIRVOpLoad( //
                    ptrCrossWorkGroupULong, //
                    loadID, //
                    address, //
                    new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(alignment))) //
            ));

            String constantValue = String.valueOf(0);
            SPIRVId index = asm.lookUpConstant(constantValue, SPIRVKind.OP_TYPE_INT_32);

            SPIRVId accessPTR = asm.module.getNextId();
            asm.currentBlockScope().add(new SPIRVOpInBoundsPtrAccessChain( //
                    ptrCrossWorkGroupULong, //
                    accessPTR, //
                    loadID, //
                    index, //
                    new SPIRVMultipleOperands<>()));

            SPIRVId ulong = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_64);

            // Load Address
            SPIRVId loadPtr = asm.module.getNextId();
            asm.currentBlockScope().add(new SPIRVOpLoad( //
                    ulong, //
                    loadPtr, //
                    accessPTR, //
                    new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(alignment))) //
            ));

            // The final store is emitted in the assignParameter
            asm.registerLIRInstructionValue(this, loadPtr);
        }
    }

    public static class MemoryAccess extends UnaryConsumer {

        private final SPIRVMemoryBase memoryRegion;

        private Value index;

        private Variable assignedTo;

        MemoryAccess(SPIRVMemoryBase base, Value value) {
            super(null, LIRKind.Illegal, value);
            this.memoryRegion = base;
        }

        public SPIRVMemoryBase getMemoryRegion() {
            return memoryRegion;
        }

        // In SPIR-V, this class does not generate code, but rather keeps data to be
        // used in other classes, such as the STORE
        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            throw new RuntimeException("Unimplemented");
        }

        public Value getIndex() {
            return index;
        }

        public void assignTo(Variable loadedTo) {
            this.assignedTo = loadedTo;
        }

        public Variable assignedTo() {
            return assignedTo;
        }

    }

    public static class MemoryIndexedAccess extends UnaryConsumer {

        private final SPIRVMemoryBase memoryRegion;

        private Value index;

        private Variable assignedTo;

        public MemoryIndexedAccess(SPIRVMemoryBase memoryRegion, Value baseValue, Value indexValue) {
            super(null, LIRKind.Illegal, baseValue);
            this.memoryRegion = memoryRegion;
            this.index = indexValue;
        }

        public Value getIndex() {
            return index;
        }

        public SPIRVMemoryBase getMemoryRegion() {
            return this.memoryRegion;
        }

        private void emitPrivateMemoryIndexedAccess(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            SPIRVId arrayAccessId = asm.module.getNextId();

            SPIRVId baseIndex = asm.lookUpConstant("0", SPIRVKind.OP_TYPE_INT_32);

            SPIRVId indexId;
            if (index instanceof ConstantValue) {
                indexId = asm.lookUpConstant(((ConstantValue) index).getConstant().toValueString(), (SPIRVKind) index.getPlatformKind());
            } else {
                indexId = asm.lookUpLIRInstructions(index);
            }

            SPIRVId baseId = asm.lookUpLIRInstructions(getValue());
            SPIRVKind kind = (SPIRVKind) getValue().getPlatformKind();
            SPIRVId type = asm.primitives.getPtrToTypePrimitive(kind);
            asm.currentBlockScope().add(new SPIRVOpInBoundsPtrAccessChain(type, arrayAccessId, baseId, baseIndex, new SPIRVMultipleOperands(indexId)));
            asm.registerLIRInstructionValue(this, arrayAccessId);
        }

        private void emitLocalMemoryIndexedAccess(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            SPIRVId arrayAccessId = asm.module.getNextId();

            SPIRVId baseIndex = asm.lookUpConstant("0", SPIRVKind.OP_TYPE_INT_32);

            SPIRVKind kind = (SPIRVKind) index.getPlatformKind();
            SPIRVId indexId;
            if (index instanceof ConstantValue) {
                indexId = asm.lookUpConstant(((ConstantValue) index).getConstant().toValueString(), (SPIRVKind) index.getPlatformKind());
            } else {

                SPIRVId loadId = asm.module.getNextId();
                indexId = asm.lookUpLIRInstructions(index);
                SPIRVId type = asm.primitives.getTypePrimitive(kind);
                asm.currentBlockScope().add(new SPIRVOpLoad( //
                        type, //
                        loadId, //
                        indexId, //
                        new SPIRVOptionalOperand<>(//
                                SPIRVMemoryAccess.Aligned(//
                                        new SPIRVLiteralInteger(kind.getSizeInBytes())))//
                ));
                SPIRVId typeLong = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_64);
                SPIRVId idConversion = asm.module.getNextId();
                asm.currentBlockScope().add(new SPIRVOpSConvert(typeLong, idConversion, loadId));
                indexId = idConversion;
            }

            SPIRVId baseId = asm.lookUpLIRInstructions(getValue());
            SPIRVId type = asm.primitives.getPtrToWorkGroupPrimitive(kind);
            asm.currentBlockScope().add(new SPIRVOpInBoundsPtrAccessChain(type, arrayAccessId, baseId, baseIndex, new SPIRVMultipleOperands(indexId)));
            asm.registerLIRInstructionValue(this, arrayAccessId);
        }

        private boolean isPrivateMemoryAccess() {
            return this.memoryRegion.number == SPIRVArchitecture.privateSpace.number;
        }

        private boolean isLocalMemoryAccess() {
            return this.memoryRegion.number == SPIRVArchitecture.localSpace.number;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

            if (isPrivateMemoryAccess()) {
                emitPrivateMemoryIndexedAccess(crb, asm);
            } else if (isLocalMemoryAccess()) {
                emitLocalMemoryIndexedAccess(crb, asm);
            } else {
                throw new RuntimeException("Indexed memory access not supported");
            }

        }

        public void emitForLoad(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            SPIRVId arrayAccessId = asm.module.getNextId();

            SPIRVId baseIndex = asm.lookUpConstant("0", SPIRVKind.OP_TYPE_INT_32);

            SPIRVId indexId;
            if (index instanceof ConstantValue) {
                indexId = asm.lookUpConstant(((ConstantValue) index).getConstant().toValueString(), (SPIRVKind) index.getPlatformKind());
            } else {
                indexId = asm.lookUpLIRInstructions(index);
                SPIRVId loadId = asm.module.getNextId();
                SPIRVId type = asm.primitives.getTypePrimitive((SPIRVKind) getValue().getPlatformKind());
                asm.currentBlockScope().add(new SPIRVOpLoad(type, loadId, indexId, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));

                SPIRVId typeLong = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_64);
                SPIRVId convertID = asm.module.getNextId();
                asm.currentBlockScope().add(new SPIRVOpSConvert(typeLong, convertID, loadId));
                indexId = convertID;
            }

            SPIRVId baseId = asm.lookUpLIRInstructions(getValue());
            SPIRVId type = asm.primitives.getPtrToWorkGroupPrimitive((SPIRVKind) getValue().getPlatformKind());
            asm.currentBlockScope().add(new SPIRVOpInBoundsPtrAccessChain(type, arrayAccessId, baseId, baseIndex, new SPIRVMultipleOperands(indexId)));
            asm.registerLIRInstructionValue(this, arrayAccessId);
        }
    }

    public static class SPIRVAddressCast extends UnaryConsumer {

        private final SPIRVMemoryBase base;

        private final Value address;

        public SPIRVAddressCast(Value address, SPIRVMemoryBase base, LIRKind valueKind) {
            super(null, valueKind, address);
            this.base = base;
            this.address = address;
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
            SPIRVLogger.traceCodeGen("emit SPIRVAddressCast with LIRKIND: " + getLIRKind().getPlatformKind());
            SPIRVId idLoad = asm.module.getNextId();

            // We force to load a pointer to long
            SPIRVId typeLoad = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_64);

            SPIRVId addressToLoad = asm.lookUpLIRInstructions(address);

            if (!TornadoOptions.OPTIMIZE_LOAD_STORE_SPIRV) {
                asm.currentBlockScope().add(new SPIRVOpLoad( //
                        typeLoad, //
                        idLoad, //
                        addressToLoad, //
                        new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(SPIRVKind.OP_TYPE_INT_64.getByteCount())))));
            } else {
                idLoad = addressToLoad;
            }

            SPIRVId ptrCrossGroup = asm.primitives.getPtrToCrossGroupPrimitive((SPIRVKind) getLIRKind().getPlatformKind());

            SPIRVId storeAddressID = asm.module.getNextId();
            asm.currentBlockScope().add(new SPIRVOpConvertUToPtr(ptrCrossGroup, storeAddressID, idLoad));
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
         * <p>
         * Example for get_global_id:
         *
         * <code>
         * int idx = get_global_id(dimensionIndex);
         * </code>
         *
         * <code>
         * %37 = OpLoad %v3ulong %__spirv_BuiltInGlobalInvocationId Aligned 32
         * %call = OpCompositeExtract %ulong %37 0
         * %conv = OpUConvert %uint %call
         * </code>
         */
        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            SPIRVLogger.traceCodeGen("emit OCL-SPIRV Intrinsic: " + builtIn);

            SPIRVId ulong = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_64);

            // All builtins have to be registered previous to this call
            SPIRVId idSPIRVBuiltin = asm.builtinTable.get(builtIn);

            SPIRVId v3long = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_VECTOR3_INT_64);

            // Call Thread-ID getGlobalId(0)
            SPIRVId id19 = asm.module.getNextId();
            asm.currentBlockScope().add(new SPIRVOpLoad( //
                    v3long, //
                    id19, //
                    idSPIRVBuiltin, //
                    new SPIRVOptionalOperand<>( //
                            SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(32))) //
            ));

            // Intrinsic call
            SPIRVId callIntrinsicId = asm.module.getNextId();

            int dimensionValue;
            if (dimension instanceof ConstantValue) {
                dimensionValue = Integer.parseInt(((ConstantValue) dimension).getConstant().toValueString());
            } else {
                throw new RuntimeException("Not supported");
            }

            asm.currentBlockScope().add( //
                    new SPIRVOpCompositeExtract( //
                            ulong, //
                            callIntrinsicId, //
                            id19, //
                            new SPIRVMultipleOperands<>( //
                                    new SPIRVLiteralInteger(dimensionValue)) //
                    ));

            SPIRVId conv = asm.module.getNextId();
            // FIXME check this
            SPIRVId uint = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_32);

            asm.currentBlockScope().add(new SPIRVOpUConvert(uint, conv, callIntrinsicId));

            // Store will be performed in the Assigment, if enabled.
            asm.registerLIRInstructionValue(this, conv);
        }
    }

    /*
     * FIXME: Possible refactor to merge this class with SIGNNarrowValue. Note that
     * SConvert in SPIRV works for Sign Extend as well as truncate.
     */
    public static class SignExtend extends UnaryConsumer {

        private int fromBits;
        private int toBits;

        public SignExtend(LIRKind lirKind, Value inputVal, int fromBits, int toBits) {
            super(null, lirKind, inputVal);
            this.fromBits = fromBits;
            this.toBits = toBits;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

            SPIRVLogger.traceCodeGen("emit SPIRVOpSConvert : " + fromBits + " -> " + toBits);

            // OpSConvert
            SPIRVId loadConvert = asm.module.getNextId();

            SPIRVId uint = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_32);
            SPIRVId param = asm.lookUpLIRInstructions(value);

            asm.currentBlockScope().add(new SPIRVOpLoad(//
                    uint, //
                    loadConvert, //
                    param, //
                    new SPIRVOptionalOperand<>( //
                            SPIRVMemoryAccess.Aligned( //
                                    new SPIRVLiteralInteger(4)))//
            ));

            SPIRVId ulong = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_64);
            SPIRVId result = asm.module.getNextId();
            asm.currentBlockScope().add(new SPIRVOpSConvert(ulong, result, loadConvert));

            asm.registerLIRInstructionValue(this, result);

        }
    }

    public static class SignNarrowValue extends UnaryConsumer {

        private int toBits;

        public SignNarrowValue(LIRKind lirKind, Value inputVal, int toBits) {
            super(null, lirKind, inputVal);
            this.toBits = toBits;
        }

        /**
         * Following this:
         * {@url https://www.khronos.org/registry/spir-v/specs/unified1/SPIRV.html#OpSConvert}
         *
         * <code>
         * Convert signed width. This is either a truncate or a sign extend.
         * </code>
         * <p>
         * OpSConvert can be used for sign extend as well as truncate. The "S" symbol
         * represents signed format.
         *
         * @param crb
         *            {@link SPIRVCompilationResultBuilder}
         * @param asm
         *            {@link SPIRVAssembler}
         */
        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

            SPIRVLogger.traceCodeGen("emit SPIRVOpSConvert : -> " + toBits);

            SPIRVKind spirvKind = (SPIRVKind) value.getPlatformKind();
            SPIRVId type = asm.primitives.getTypePrimitive(spirvKind);
            SPIRVId param = asm.lookUpLIRInstructions(value);

            SPIRVId loadConvert = asm.module.getNextId();
            asm.currentBlockScope().add(new SPIRVOpLoad(//
                    type, //
                    loadConvert, //
                    param, //
                    new SPIRVOptionalOperand<>( //
                            SPIRVMemoryAccess.Aligned( //
                                    new SPIRVLiteralInteger(spirvKind.getByteCount())))//
            ));

            // OpSConvert
            SPIRVId result = asm.module.getNextId();
            asm.currentBlockScope().add(new SPIRVOpSConvert(type, result, loadConvert));

            asm.registerLIRInstructionValue(this, result);
        }
    }

    public static class CastOperations extends UnaryConsumer {

        protected CastOperations(SPIRVUnaryOp opcode, LIRKind valueKind, Value value) {
            super(opcode, valueKind, value);
        }
    }

    public static class CastIToFloat extends CastOperations {

        private SPIRVKind toType;

        public CastIToFloat(LIRKind lirKind, Value inputVal, SPIRVKind toType) {
            super(null, lirKind, inputVal);
            this.toType = toType;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

            SPIRVLogger.traceCodeGen("emit SPIRVOpConvertSToF : -> ");

            SPIRVKind spirvKind = toType;
            SPIRVId type = asm.primitives.getTypePrimitive(spirvKind);
            SPIRVId param = asm.lookUpLIRInstructions(value);

            SPIRVId loadConvert = asm.module.getNextId();
            asm.currentBlockScope().add(new SPIRVOpLoad(//
                    type, //
                    loadConvert, //
                    param, //
                    new SPIRVOptionalOperand<>( //
                            SPIRVMemoryAccess.Aligned( //
                                    new SPIRVLiteralInteger(spirvKind.getByteCount())))//
            ));

            // OpSConvert
            SPIRVId result = asm.module.getNextId();
            asm.currentBlockScope().add(new SPIRVOpConvertSToF(type, result, loadConvert));

            asm.registerLIRInstructionValue(this, result);
        }
    }

    public static class CastFloatDouble extends CastOperations {

        private SPIRVKind toType;

        public CastFloatDouble(LIRKind lirKind, Value inputVal, SPIRVKind toType) {
            super(null, lirKind, inputVal);
            this.toType = toType;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

            SPIRVLogger.traceCodeGen("emit SPIRVOpFConvert");

            SPIRVKind spirvKind = toType;
            SPIRVId type = asm.primitives.getTypePrimitive(spirvKind);
            SPIRVId param = asm.lookUpLIRInstructions(value);

            SPIRVId loadConvert = asm.module.getNextId();
            asm.currentBlockScope().add(new SPIRVOpLoad(//
                    type, //
                    loadConvert, //
                    param, //
                    new SPIRVOptionalOperand<>( //
                            SPIRVMemoryAccess.Aligned( //
                                    new SPIRVLiteralInteger(spirvKind.getByteCount())))//
            ));

            // OpSConvert
            SPIRVId result = asm.module.getNextId();
            asm.currentBlockScope().add(new SPIRVOpFConvert(type, result, loadConvert));

            asm.registerLIRInstructionValue(this, result);
        }
    }

    public static class Intrinsic extends UnaryConsumer {

        /**
         * For obtaining the correct Int-Reference of the function:
         * <p>
         * https://www.khronos.org/registry/spir-v/specs/1.0/OpenCL.ExtendedInstructionSet.100.html
         */
        // @formatter:off
        public enum OpenCLIntrinsic {

            // Math extended instructions
            // https://www.khronos.org/registry/spir-v/specs/unified1/OpenCL.ExtendedInstructionSet.100.html#_a_id_math_a_math_extended_instructions
            ACOS("acos", 0),
            ACOSH("acosh", 1),
            ACOSPI("acospi", 2),
            ASIN("asin", 3),
            ASINH("asinh", 4),
            ASINPI("asinpi", 5),
            ATAN("atan", 6),
            ATAN2("atan2", 7),
            ATANH("atanh", 8),
            ATANPI("atanpi", 9),
            ATAN2PI("atan2pi", 10),
            CBRT("cbrt", 11),
            CEIL("ceil", 12),
            COPYSIGN("copysign", 13),
            COS("cos", 14),
            COSH("cosh", 15),
            COSPI("cospi", 16),
            ERFC("erfc", 17),
            ERF("erf", 18),
            EXP("exp", 19),
            EXP2("exp2", 20),
            EXP10("exp10", 21),
            EXPM1("expm1", 22),
            FABS("FABS", 23),
            FLOOR("floor", 25),
            FMA("fma", 26),
            FMAX("fmax", 27),
            FMIN("fmin", 28),
            FMOD("fmod", 29),
            HYPOT("hypot", 32),
            LOG("log", 37),
            LOG2("log2", 38),
            LOG10("log10", 39),
            POW("pow", 48),
            REMAINDER("remainder", 51),
            RSQRT("rsqrt", 56),
            SIN("sin", 57),
            SQRT("sqrt", 61),
            TAN("tan", 62),
            TANH("tanh", 63),
            NATIVE_COS("native_cos", 81),  // Optimization
            FCLAMP("flamp", 95),
            SABS("s_abs", 141),
            SCLAMP("s_clamp", 149),
            SMAX("s_max", 156),
            SMIN("s_min", 158),
            POPCOPUNT("popcount", 166),

            // Vector Loads/Stores
            // https://www.khronos.org/registry/spir-v/specs/unified1/OpenCL.ExtendedInstructionSet.100.html#_a_id_vector_a_vector_data_load_and_store_instructions
            VLOADN("vloadn", 171),
            VSTOREN("vstoren", 172),
            VLOAD_HALF("vload_half", 173),
            VLOAD_HALFN("vload_halfn", 174),
            VSTORE_HALF("vstore_half", 175),
            VSTORE_HALFN("vstore_halfn", 176);

            int value;
            String name;

            OpenCLIntrinsic(String name, int value) {
                this.value = value;
                this.name = name;
            }

            public String getName() {
                return this.name;
            }

            public int getValue() {
                return this.value;
            }
        }
        // @formatter:on

        public static final String COS = "cos";
        final private OpenCLIntrinsic builtIn;

        protected Intrinsic(OpenCLIntrinsic opcode, LIRKind valueKind, Value value) {
            super(null, valueKind, value);
            this.builtIn = opcode;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

            SPIRVLogger.traceCodeGen("emit SPIRVLiteralExtInstInteger: " + builtIn.name);

            SPIRVId type = asm.primitives.getTypePrimitive(getSPIRVPlatformKind());

            SPIRVId loadParam = loadSPIRVId(crb, asm, getValue());

            SPIRVId result = asm.module.getNextId();
            SPIRVId set = asm.getOpenclImport();
            SPIRVLiteralExtInstInteger intrinsic = new SPIRVLiteralExtInstInteger(builtIn.value, builtIn.name);
            asm.currentBlockScope().add(new SPIRVOpExtInst(type, result, set, intrinsic, new SPIRVMultipleOperands<>(loadParam)));
            asm.registerLIRInstructionValue(this, result);

        }
    }

    public static class Negate extends UnaryConsumer {

        boolean isInteger;
        String nameDebugInstruction;

        public Negate(LIRKind lirKind, Value inputVal) {
            super(null, lirKind, inputVal);
            if (getSPIRVPlatformKind().isInteger()) {
                isInteger = true;
                nameDebugInstruction = "SPIRVOpSNegate";
            } else if (getSPIRVPlatformKind().isFloatingPoint()) {
                nameDebugInstruction = "SPIRVOpFNegate";
            } else {
                throw new RuntimeException("Error - not valid type");
            }
        }

        protected SPIRVId getId(Value inputValue, SPIRVAssembler asm, SPIRVKind spirvKind) {
            if (inputValue instanceof ConstantValue) {
                SPIRVKind kind = (SPIRVKind) inputValue.getPlatformKind();
                return asm.lookUpConstant(((ConstantValue) inputValue).getConstant().toValueString(), kind);
            } else {
                SPIRVId param = asm.lookUpLIRInstructions(inputValue);
                if (!TornadoOptions.OPTIMIZE_LOAD_STORE_SPIRV) {
                    // We need to perform a load first
                    SPIRVLogger.traceCodeGen("emit LOAD Variable: " + inputValue);
                    SPIRVId load = asm.module.getNextId();
                    SPIRVId type = asm.primitives.getTypePrimitive(spirvKind);
                    asm.currentBlockScope().add(new SPIRVOpLoad(//
                            type, //
                            load, //
                            param, //
                            new SPIRVOptionalOperand<>( //
                                    SPIRVMemoryAccess.Aligned( //
                                            new SPIRVLiteralInteger(spirvKind.getByteCount())))//
                    ));
                    return load;
                } else {
                    return param;
                }
            }
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

            SPIRVLogger.traceCodeGen("emit " + nameDebugInstruction + getValue() + " with type: " + getSPIRVPlatformKind());

            SPIRVId valueID = getId(getValue(), asm, getSPIRVPlatformKind());
            SPIRVId type = asm.primitives.getTypePrimitive(getSPIRVPlatformKind());
            SPIRVId result = asm.module.getNextId();

            if (isInteger) {
                asm.currentBlockScope().add(new SPIRVOpSNegate(type, result, valueID));
            } else if (getSPIRVPlatformKind().isFloatingPoint()) {
                asm.currentBlockScope().add(new SPIRVOpFNegate(type, result, valueID));
            }

            asm.registerLIRInstructionValue(this, result);
        }
    }

    public static class Barrier extends UnaryConsumer {

        private SPIRVBarrierNode.SPIRVMemFenceFlags flags;

        public Barrier(SPIRVBarrierNode.SPIRVMemFenceFlags flags) {
            super(null, LIRKind.Illegal, null);
            this.flags = flags;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            SPIRVLogger.traceCodeGen("emit Barrier with FLAGS" + flags.toString() + " SEMANTICS: " + flags.getMemorySemantics());
            SPIRVId constant2 = asm.lookUpConstant("2", SPIRVKind.OP_TYPE_INT_32);
            SPIRVId constantSemantics = asm.lookUpConstant(Integer.toString(flags.getMemorySemantics()), SPIRVKind.OP_TYPE_INT_32);
            // SPIRVId constantSemantics = asm.lookUpConstant(Integer.toString(0x200 |
            // 0x10), SPIRVKind.OP_TYPE_INT_32);
            asm.currentBlockScope().add(new SPIRVOpControlBarrier(constant2, constant2, constantSemantics));
        }
    }
}
