package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstruction.Use;
import org.graalvm.compiler.lir.Opcode;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVInstruction;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpDecorate;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpExtInst;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpLoad;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpName;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpTypeArray;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpTypePointer;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpVariable;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVDecoration;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVLiteralExtInstInteger;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVLiteralInteger;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVLiteralString;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVMemoryAccess;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVMultipleOperands;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVOptionalOperand;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVStorageClass;
import uk.ac.manchester.tornado.drivers.spirv.common.SPIRVLogger;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler.SPIRVBinaryOp;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResultBuilder;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

public class SPIRVBinary {

    /**
     * Generate SPIR-V for binary expressions.
     */
    protected static class BinaryConsumer extends SPIRVLIROp {

        @Opcode
        protected SPIRVBinaryOp opcode;

        @Use
        protected Value x;

        @Use
        protected Value y;

        protected BinaryConsumer(SPIRVBinaryOp instruction, LIRKind valueKind, Value x, Value y) {
            super(valueKind);
            this.opcode = instruction;
            this.x = x;
            this.y = y;
        }

        public SPIRVBinaryOp getInstruction() {
            return opcode;
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
            LIRKind lirKind = getLIRKind();
            SPIRVKind spirvKind = (SPIRVKind) lirKind.getPlatformKind();
            SPIRVId typeOperation = asm.primitives.getTypePrimitive(spirvKind);

            SPIRVId a;
            if (x instanceof SPIRVVectorElementSelect) {
                ((SPIRVLIROp) x).emit(crb, asm);
                a = asm.lookUpLIRInstructions(x);
            } else {
                a = getId(x, asm, (SPIRVKind) x.getPlatformKind());
            }
            // SPIRVId a = getId(x, asm, (SPIRVKind) x.getPlatformKind());
            SPIRVId b;
            if (y instanceof SPIRVVectorElementSelect) {
                ((SPIRVLIROp) y).emit(crb, asm);
                b = asm.lookUpLIRInstructions(y);
            } else {
                b = getId(y, asm, (SPIRVKind) y.getPlatformKind());
            }
            // SPIRVId b = getId(y, asm, (SPIRVKind) y.getPlatformKind());

            if (opcode instanceof SPIRVAssembler.SPIRVBinaryOpLeftShift) {
                if (y instanceof ConstantValue) {
                    SPIRVKind baseKind = (SPIRVKind) x.getPlatformKind();
                    SPIRVKind shiftKind = (SPIRVKind) y.getPlatformKind();
                    if (baseKind != shiftKind) {
                        // Create a new constant
                        ConstantValue c = (ConstantValue) y;
                        b = asm.lookUpConstant(c.getConstant().toValueString(), baseKind);
                    }
                }
            }

            SPIRVLogger.traceCodeGen("emitBinaryOperation " + opcode.getInstruction() + ":  " + x + " " + opcode.getOpcode() + " " + y);

            SPIRVId addId = asm.module.getNextId();

            SPIRVInstruction instruction = opcode.generateInstruction(typeOperation, addId, a, b);
            asm.currentBlockScope().add(instruction);

            asm.registerLIRInstructionValue(this, addId);
        }

    }

    public static class Expr extends BinaryConsumer {
        public Expr(SPIRVBinaryOp opcode, LIRKind lirKind, Value x, Value y) {
            super(opcode, lirKind, x, y);
        }
    }

    public static class PrivateAllocation extends BinaryConsumer {

        private LIRKind lirKind;

        @LIRInstruction.Def
        private AllocatableValue resultArray;

        @Use
        private Value length;

        public PrivateAllocation(LIRKind lirKind, AllocatableValue resultArray, Value lengthValue) {
            super(null, lirKind, null, null);
            this.lirKind = lirKind;
            this.resultArray = resultArray;
            this.length = lengthValue;
        }

        private SPIRVId addSPIRVIdInPreamble(SPIRVAssembler asm) {
            SPIRVId id = asm.module.getNextId();
            asm.module.add(new SPIRVOpName(id, new SPIRVLiteralString(resultArray.toString())));
            SPIRVKind kind = (SPIRVKind) resultArray.getPlatformKind();
            asm.module.add(new SPIRVOpDecorate(id, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(kind.getSizeInBytes()))));

            return id;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            SPIRVLogger.traceCodeGen("emit ArrayDeclaration: " + resultArray + "[" + length + "]");

            SPIRVId idResult = addSPIRVIdInPreamble(asm);

            SPIRVId primitiveType = asm.primitives.getTypePrimitive((SPIRVKind) lirKind.getPlatformKind());

            SPIRVId elementsId;
            if (length instanceof ConstantValue) {
                elementsId = asm.lookUpConstant(((ConstantValue) length).getConstant().toValueString(), SPIRVKind.OP_TYPE_INT_32);
            } else {
                throw new RuntimeException("Constant expected");
            }

            // Array declaration
            SPIRVId resultArrayId = asm.module.getNextId();
            asm.module.add(new SPIRVOpTypeArray(resultArrayId, primitiveType, elementsId));
            SPIRVId functionPTR = asm.module.getNextId();
            asm.module.add(new SPIRVOpTypePointer(functionPTR, SPIRVStorageClass.Function(), resultArrayId));

            // Registration of the variable in the block 0 of the code
            asm.blockZeroScope.add(new SPIRVOpVariable(functionPTR, idResult, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));

            asm.registerLIRInstructionValue(resultArray, idResult);
        }
    }

    public static class LocalAllocation extends BinaryConsumer {

        private LIRKind lirKind;

        @LIRInstruction.Def
        private AllocatableValue resultArray;

        @Use
        private Value length;

        public LocalAllocation(LIRKind lirKind, AllocatableValue resultArray, Value lengthValue) {
            super(null, lirKind, null, null);
            this.lirKind = lirKind;
            this.resultArray = resultArray;
            this.length = lengthValue;
        }

        private SPIRVId addSPIRVIdInPreamble(SPIRVAssembler asm) {
            SPIRVId id = asm.module.getNextId();
            asm.module.add(new SPIRVOpName(id, new SPIRVLiteralString(resultArray.toString())));
            SPIRVKind kind = (SPIRVKind) resultArray.getPlatformKind();
            asm.module.add(new SPIRVOpDecorate(id, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(kind.getSizeInBytes()))));
            return id;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            SPIRVLogger.traceCodeGen("emit ArrayDeclaration: " + resultArray + "[" + length + "]");

            SPIRVId idResult = addSPIRVIdInPreamble(asm);

            SPIRVId primitiveType = asm.primitives.getTypePrimitive((SPIRVKind) lirKind.getPlatformKind());

            SPIRVId elementsId;
            if (length instanceof ConstantValue) {
                elementsId = asm.lookUpConstant(((ConstantValue) length).getConstant().toValueString(), SPIRVKind.OP_TYPE_INT_32);
            } else {
                throw new RuntimeException("Constant expected");
            }

            // Array declaration
            SPIRVId resultArrayId = asm.module.getNextId();
            asm.module.add(new SPIRVOpTypeArray(resultArrayId, primitiveType, elementsId));
            SPIRVId functionPTR = asm.module.getNextId();
            asm.module.add(new SPIRVOpTypePointer(functionPTR, SPIRVStorageClass.Workgroup(), resultArrayId));

            // Registration of the variable in the block 0 of the code
            asm.blockZeroScope.add(new SPIRVOpVariable(functionPTR, idResult, SPIRVStorageClass.Workgroup(), new SPIRVOptionalOperand<>()));

            asm.registerLIRInstructionValue(resultArray, idResult);
        }
    }

    public static class Intrinsic extends BinaryConsumer {

        private SPIRVUnary.Intrinsic.OpenCLIntrinsic builtIn;

        public Intrinsic(SPIRVUnary.Intrinsic.OpenCLIntrinsic builtIn, LIRKind valueKind, Value x, Value y) {
            super(null, valueKind, x, y);
            this.builtIn = builtIn;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

            LIRKind lirKind = getLIRKind();
            SPIRVKind spirvKind = (SPIRVKind) lirKind.getPlatformKind();
            SPIRVId typeOperation = asm.primitives.getTypePrimitive(spirvKind);

            SPIRVId a = loadSPIRVId(crb, asm, x);
            SPIRVId b = loadSPIRVId(crb, asm, y);

            SPIRVLogger.traceCodeGen("emit SPIRVLiteralExtInstInteger: " + builtIn.getName() + " (" + x + "," + y + ")");

            SPIRVId result = asm.module.getNextId();
            SPIRVId set = asm.getOpenclImport();
            SPIRVLiteralExtInstInteger intrinsic = new SPIRVLiteralExtInstInteger(builtIn.getValue(), builtIn.getName());
            asm.currentBlockScope().add(new SPIRVOpExtInst(typeOperation, result, set, intrinsic, new SPIRVMultipleOperands<>(a, b)));
            asm.registerLIRInstructionValue(this, result);

        }
    }

    public static class VectorOperation extends BinaryConsumer {

        public VectorOperation(SPIRVBinaryOp opcode, LIRKind lirKind, Value x, Value y) {
            super(opcode, lirKind, x, y);
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

            SPIRVId resultSelect1;
            if (x instanceof SPIRVVectorElementSelect) {
                ((SPIRVLIROp) x).emit(crb, asm);
                resultSelect1 = asm.lookUpLIRInstructions(x);
            } else {
                resultSelect1 = getId(x, asm, (SPIRVKind) x.getPlatformKind());
            }

            SPIRVId resultSelect2;
            if (y instanceof SPIRVVectorElementSelect) {
                ((SPIRVLIROp) y).emit(crb, asm);
                resultSelect2 = asm.lookUpLIRInstructions(y);
            } else {
                resultSelect2 = getId(y, asm, (SPIRVKind) y.getPlatformKind());
            }

            LIRKind lirKind = getLIRKind();
            SPIRVKind spirvKind = (SPIRVKind) lirKind.getPlatformKind();
            SPIRVId typeOperation = asm.primitives.getTypePrimitive(spirvKind.getElementKind()); /// Vector Selection -> Element Kind

            SPIRVLogger.traceCodeGen("emitVectorOperation " + opcode.getInstruction() + ":  " + x + " " + opcode.getOpcode() + " " + y);

            SPIRVId binaryVectorOperationResult = asm.module.getNextId();

            SPIRVInstruction instruction = opcode.generateInstruction(typeOperation, binaryVectorOperationResult, resultSelect1, resultSelect2);
            asm.currentBlockScope().add(instruction);

            asm.registerLIRInstructionValue(this, binaryVectorOperationResult);
        }
    }
}
