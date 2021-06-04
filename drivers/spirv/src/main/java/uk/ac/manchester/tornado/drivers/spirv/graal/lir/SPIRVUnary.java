package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.LIRInstruction.Use;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.Variable;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpCompositeExtract;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpConvertSToF;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpConvertUToPtr;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpExtInst;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpInBoundsPtrAccessChain;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpLoad;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpSConvert;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpUConvert;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVLiteralExtInstInteger;
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
            SPIRVLogger.trace("µIns LoadFromStackFrame ");
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

    public static class MemoryAccess extends UnaryConsumer {

        private final SPIRVMemoryBase memoryRegion;

        private Value index;

        private Variable assignedTo;

        MemoryAccess(SPIRVMemoryBase base, Value value) {
            super(null, LIRKind.Illegal, value);
            this.memoryRegion = base;
        }

        MemoryAccess(SPIRVMemoryBase base, Value value, Value index) {
            super(null, LIRKind.Illegal, value);
            this.memoryRegion = base;
            this.index = index;
        }

        public SPIRVMemoryBase getMemoryRegion() {
            return memoryRegion;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            SPIRVLogger.traceCodeGen("µInstr MemoryAccess (EMPTY IMPLEMENTATION)");
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

            // SPIRVId ptrCrossWorkGroupUInt = asm.ptrCrossWorkUInt;
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
         *
         * Example for get_global_id:
         * 
         * <code>
         *     int idx = get_global_id(dimensionIndex);
         * </code>
         *
         * <code>
         *     %37 = OpLoad %v3ulong %__spirv_BuiltInGlobalInvocationId Aligned 32
         *   %call = OpCompositeExtract %ulong %37 0
         *   %conv = OpUConvert %uint %call
         * </code>
         */
        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            SPIRVLogger.traceCodeGen("emit OCL Intrinsic: " + builtIn);

            SPIRVId ulong = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_64);

            // All builtins have to be registered previous to this call
            SPIRVId idSPIRVBuiltin = asm.builtinTable.get(builtIn);

            SPIRVId v3long = asm.v3ulong;

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

            if (fromBits == 32 && toBits == 64) {
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

            } else {
                throw new RuntimeException("Conversion not supported");
            }

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
         *     Convert signed width. This is either a truncate or a sign extend.
         * </code>
         * 
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

            // We will keep this check until we guarantee all unittests can pass
            if (toBits != 16) {
                throw new RuntimeException("Not supported yet: " + toBits);
            }

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

        public CastIToFloat(LIRKind lirKind, Value inputVal) {
            super(null, lirKind, inputVal);
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

            SPIRVLogger.traceCodeGen("emit SPIRVOpConvertSToF : -> ");

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
            asm.currentBlockScope().add(new SPIRVOpConvertSToF(type, result, loadConvert));

            asm.registerLIRInstructionValue(this, result);
        }
    }

    public static class Intrinsic extends UnaryConsumer {

        /**
         * For obtaining the correct Int-Reference of the function:
         *
         * https://www.khronos.org/registry/spir-v/specs/1.0/OpenCL.ExtendedInstructionSet.100.html
         *
         */
        // @formatter:off
        public enum OpenCLIntrinsic {
            ACOS("acos", 0),
            ACOSH("acosh", 1),
            ACOSPI("acospi", 2),
            ASIN("asin", 3),
            ASINH("asinh", 4),
            ASINP("asinp", 5),
            ATAN("atan", 6),
            ATAN2("atan2", 7),
            ATANH("atanh", 8),
            ATANPI("atanpi", 9),
            ATAN2PI("atan2pi", 10),
            CBRT("cbrt", 11),
            CEIL("ceil", 12),
            COPYSIGN("copysign", 13),
            COS("cos", 14),
            NATIVE_COS("native_cos", 81);  // Optimization

            int value;
            String name;

            OpenCLIntrinsic(String name, int value) {
                this.value = value;
            }

            String getName() {
                return this.name;
            }

            int getValue() {
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

            System.out.println("GENERARTING:::::::: + COS : " + builtIn);

            SPIRVId type = asm.primitives.getTypePrimitive(getSPIRVPlatformKind());
            SPIRVId input = asm.lookUpLIRInstructions(value);
            SPIRVKind spirvKind = (SPIRVKind) value.getPlatformKind();

            SPIRVId loadParam = asm.module.getNextId();
            asm.currentBlockScope().add(new SPIRVOpLoad(//
                    type, //
                    loadParam, //
                    input, //
                    new SPIRVOptionalOperand<>( //
                            SPIRVMemoryAccess.Aligned( //
                                    new SPIRVLiteralInteger(spirvKind.getByteCount())))//
            ));

            SPIRVId result = asm.module.getNextId();
            SPIRVId set = asm.getOpenclImport();
            SPIRVLiteralExtInstInteger intrinsic = new SPIRVLiteralExtInstInteger(builtIn.value, builtIn.name);
            asm.currentBlockScope().add(new SPIRVOpExtInst(type, result, set, intrinsic, new SPIRVMultipleOperands<>(loadParam)));
            asm.registerLIRInstructionValue(this, result);

        }
    }
}
