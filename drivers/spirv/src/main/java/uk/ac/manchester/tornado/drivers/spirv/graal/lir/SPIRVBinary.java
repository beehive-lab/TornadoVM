/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstruction.Use;
import org.graalvm.compiler.lir.Opcode;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVInstruction;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpDecorate;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpExtInst;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpIEqual;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpINotEqual;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpLoad;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpName;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpSGreaterThan;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpSLessThan;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpSelect;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpVariable;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVDecoration;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVLiteralExtInstInteger;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVLiteralInteger;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVLiteralString;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVMemoryAccess;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVMultipleOperands;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVOptionalOperand;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVStorageClass;
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
                if (param == null) {
                    throw new RuntimeException("LOADING PARAMETER: " + inputValue + " with NULL VALUE in SPIR-V Table");
                }
                if (!TornadoOptions.OPTIMIZE_LOAD_STORE_SPIRV) {
                    // We need to perform a load first
                    SPIRVLogger.traceCodeGen("emit LOAD Variable: " + inputValue + " ::: " + param);
                    SPIRVId load = asm.module.getNextId();
                    SPIRVId type = asm.primitives.getTypePrimitive(spirvKind);
                    SPIRVLogger.traceCodeGen("\t with type: " + spirvKind);

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
            SPIRVId b;
            if (y instanceof SPIRVVectorElementSelect) {
                ((SPIRVLIROp) y).emit(crb, asm);
                b = asm.lookUpLIRInstructions(y);
            } else {
                b = getId(y, asm, (SPIRVKind) y.getPlatformKind());
            }

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

    public static class PrivateArrayAllocation extends BinaryConsumer {

        private LIRKind lirKind;

        @LIRInstruction.Def
        private AllocatableValue resultArray;

        public PrivateArrayAllocation(LIRKind lirKind, AllocatableValue resultArray) {
            super(null, lirKind, null, null);
            this.lirKind = lirKind;
            this.resultArray = resultArray;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            SPIRVId idArrayResult = asm.lookUpLIRInstructionsName(resultArray.toString());
            asm.registerLIRInstructionValue(resultArray, idArrayResult);
        }
    }

    public static class LocalArrayAllocation extends BinaryConsumer {

        private LIRKind lirKind;

        @LIRInstruction.Def
        private AllocatableValue resultArray;

        @Use
        private Value length;

        public LocalArrayAllocation(LIRKind lirKind, AllocatableValue resultArray, Value lengthValue) {
            super(null, lirKind, null, null);
            this.lirKind = lirKind;
            this.resultArray = resultArray;
            this.length = lengthValue;
        }

        private SPIRVId addSPIRVIdLocalArrayInPreamble(SPIRVAssembler asm) {
            SPIRVId id = asm.module.getNextId();
            asm.module.add(new SPIRVOpName(id, new SPIRVLiteralString(resultArray.toString())));
            SPIRVKind kind = (SPIRVKind) resultArray.getPlatformKind();
            asm.module.add(new SPIRVOpDecorate(id, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(kind.getSizeInBytes()))));
            return id;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            SPIRVLogger.traceCodeGen("emit ArrayDeclaration: " + resultArray + "[" + length + "]");

            SPIRVId idResult = addSPIRVIdLocalArrayInPreamble(asm);

            SPIRVId primitiveType = asm.primitives.getTypePrimitive((SPIRVKind) lirKind.getPlatformKind());

            SPIRVId lengthId;
            if (length instanceof ConstantValue) {
                lengthId = asm.lookUpConstant(((ConstantValue) length).getConstant().toValueString(), SPIRVKind.OP_TYPE_INT_32);
            } else {
                // We cannot allocate space in local memory using a value not known at compile
                // time.
                throw new RuntimeException("Constant expected");
            }

            // Array declaration in local memory avoiding duplications
            SPIRVId resultArrayId = asm.declareArray((SPIRVKind) lirKind.getPlatformKind(), primitiveType, lengthId);
            SPIRVId functionPTR = asm.getFunctionPtrToLocalArray(resultArrayId);

            // Registration of the variable in the module level
            asm.module.add(new SPIRVOpVariable(functionPTR, idResult, SPIRVStorageClass.Workgroup(), new SPIRVOptionalOperand<>()));

            asm.registerLIRInstructionValue(resultArray, idResult);
        }
    }

    public static class Intrinsic extends BinaryConsumer {

        private SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic builtIn;

        public Intrinsic(SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic builtIn, LIRKind valueKind, Value x, Value y) {
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

    public static class TernaryCondition extends BinaryConsumer {

        @Use
        private Value leftVal;
        private Condition cond;

        @Use
        private Value right;

        @Use
        private Value trueValue;

        @Use
        private Value falseValue;

        public TernaryCondition(LIRKind lirKind, Value leftVal, Condition cond, Value right, Value trueValue, Value falseValue) {
            super(null, lirKind, trueValue, falseValue);
            this.cond = cond;
            this.leftVal = leftVal;
            this.right = right;
            this.trueValue = trueValue;
            this.falseValue = falseValue;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

            SPIRVLogger.traceCodeGen("emit TernaryBranch: " + leftVal + " " + cond + right + " ? " + trueValue + " : " + falseValue);

            SPIRVId idLeftVar = getId(leftVal, asm, (SPIRVKind) leftVal.getPlatformKind());
            SPIRVId idRightVar = getId(right, asm, (SPIRVKind) right.getPlatformKind());

            SPIRVId typeBoolean = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_BOOL);
            SPIRVId comparisonResult = asm.module.getNextId();

            switch (cond) {
                case EQ:
                    asm.currentBlockScope().add(new SPIRVOpIEqual(typeBoolean, comparisonResult, idLeftVar, idRightVar));
                    break;
                case NE:
                    asm.currentBlockScope().add(new SPIRVOpINotEqual(typeBoolean, comparisonResult, idLeftVar, idRightVar));
                    break;
                case LT:
                    asm.currentBlockScope().add(new SPIRVOpSLessThan(typeBoolean, comparisonResult, idLeftVar, idRightVar));
                    break;
                case GT:
                    asm.currentBlockScope().add(new SPIRVOpSGreaterThan(typeBoolean, comparisonResult, idLeftVar, idRightVar));
                    break;
                default:
                    throw new RuntimeException("Condition type not supported");
            }

            SPIRVId trueValueId = getId(trueValue, asm, (SPIRVKind) trueValue.getPlatformKind());
            SPIRVId falseValueId = getId(falseValue, asm, (SPIRVKind) falseValue.getPlatformKind());

            SPIRVKind kind = (SPIRVKind) getLIRKind().getPlatformKind();
            SPIRVId resultType = asm.primitives.getTypePrimitive(kind);
            SPIRVId resultSelectId = asm.module.getNextId();
            asm.currentBlockScope().add(new SPIRVOpSelect(resultType, resultSelectId, comparisonResult, trueValueId, falseValueId));

            asm.registerLIRInstructionValue(this, resultSelectId);
        }
    }

    public static class IntegerTestNode extends BinaryConsumer {
        public IntegerTestNode(SPIRVBinaryOp binaryOp, LIRKind lirKind, Value x, Value y) {
            super(binaryOp, lirKind, x, y);
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            SPIRVLogger.traceCodeGen("emit IntegerTestNode: (" + x + " &  " + y + ")  ==   0");

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
            SPIRVId b;
            if (y instanceof SPIRVVectorElementSelect) {
                ((SPIRVLIROp) y).emit(crb, asm);
                b = asm.lookUpLIRInstructions(y);
            } else {
                b = getId(y, asm, (SPIRVKind) y.getPlatformKind());
            }

            SPIRVId bitWiseAnd = asm.module.getNextId();

            SPIRVInstruction instruction = opcode.generateInstruction(typeOperation, bitWiseAnd, a, b);
            asm.currentBlockScope().add(instruction);

            SPIRVId compEqual = asm.module.getNextId();

            SPIRVId booleanType = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_BOOL);
            SPIRVId zeroConstant = asm.lookUpConstant("0", SPIRVKind.OP_TYPE_INT_32);

            asm.currentBlockScope().add(new SPIRVOpIEqual(booleanType, compEqual, bitWiseAnd, zeroConstant));

            asm.registerLIRInstructionValue(this, compEqual);
        }
    }
}
