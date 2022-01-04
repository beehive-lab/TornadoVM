/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2009-2021, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpConvertUToPtr;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpExtInst;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpLoad;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpStore;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpUConvert;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVLiteralExtInstInteger;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVLiteralInteger;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVMemoryAccess;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVMultipleOperands;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVOptionalOperand;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVArchitecture;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVUnary.MemoryAccess;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVUnary.SPIRVAddressCast;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

public class SPIRVLIRStmt {

    protected static abstract class AbstractInstruction extends LIRInstruction {

        public AbstractInstruction(LIRInstructionClass<? extends LIRInstruction> c) {
            super(c);
        }

        @Override
        public void emitCode(CompilationResultBuilder crb) {
            emitCode((SPIRVCompilationResultBuilder) crb, (SPIRVAssembler) crb.asm);
        }

        protected abstract void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm);
    }

    @Opcode("ASSIGN")
    public static class AssignStmt extends AbstractInstruction {

        public static final LIRInstructionClass<AssignStmt> TYPE = LIRInstructionClass.create(AssignStmt.class);

        @Def
        protected AllocatableValue lhs;
        @Use
        protected Value rhs;

        public AssignStmt(AllocatableValue lhs, Value rhs) {
            super(TYPE);
            this.lhs = lhs;
            this.rhs = rhs;
        }

        @Override
        protected void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

            // This call will register the lhs id in case is not in the lookupTable yet.
            asm.emitValue(crb, lhs);

            boolean performLoad = false;
            if (rhs instanceof SPIRVLIROp) {
                ((SPIRVLIROp) rhs).emit(crb, asm);
            } else {
                asm.emitValue(crb, rhs);
                performLoad = true;
            }

            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "emit Assignment : " + lhs + " = " + rhs.getClass());

            SPIRVId storeAddressID;
            if (TornadoOptions.OPTIMIZE_LOAD_STORE_SPIRV) {
                storeAddressID = asm.lookUpLIRInstructions(rhs);
            } else {
                SPIRVId value;
                boolean isConstant = rhs instanceof ConstantValue;
                if (isConstant) {
                    value = asm.lookUpConstant(((ConstantValue) this.rhs).getConstant().toValueString(), (SPIRVKind) rhs.getPlatformKind());
                } else {
                    value = asm.lookUpLIRInstructions(rhs);
                }

                if (performLoad && !isConstant) {
                    SPIRVKind spirvKind = (SPIRVKind) rhs.getPlatformKind();
                    SPIRVId resultType = asm.primitives.getTypePrimitive(spirvKind);
                    SPIRVId loadId = asm.module.getNextId();
                    asm.currentBlockScope().add(new SPIRVOpLoad( //
                            resultType, //
                            loadId, //
                            value, //
                            new SPIRVOptionalOperand<>( //
                                    SPIRVMemoryAccess.Aligned( //
                                            new SPIRVLiteralInteger( //
                                                    rhs.getPlatformKind().getSizeInBytes())) //
                            )));

                    value = loadId;
                }

                storeAddressID = asm.lookUpLIRInstructions(lhs);
                asm.currentBlockScope().add(new SPIRVOpStore( //
                        storeAddressID, //
                        value, //
                        new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(lhs.getPlatformKind().getSizeInBytes())) //
                        )));
            }

            asm.registerLIRInstructionValue(lhs, storeAddressID);
        }

        public AllocatableValue getResult() {
            return lhs;
        }
    }

    @Opcode("IGNORABLE-ASSIGN")
    public static class IgnorableAssignStmt extends AbstractInstruction {

        public static final LIRInstructionClass<IgnorableAssignStmt> TYPE = LIRInstructionClass.create(IgnorableAssignStmt.class);

        @Def
        protected AllocatableValue lhs;
        @Use
        protected Value rhs;

        public IgnorableAssignStmt(AllocatableValue lhs, Value rhs) {
            super(TYPE);
            this.lhs = lhs;
            this.rhs = rhs;
        }

        @Override
        protected void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            asm.emitValue(crb, lhs);
            if (rhs instanceof SPIRVLIROp) {
                ((SPIRVLIROp) rhs).emit(crb, asm);
            } else {
                asm.emitValue(crb, rhs);
            }
            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "emit IgnorableAssignment: " + lhs + " = " + rhs);
            SPIRVId storeAddressID = asm.lookUpLIRInstructions(rhs);
            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "Storing: " + storeAddressID);
            asm.registerLIRInstructionValue(lhs, storeAddressID);
        }

        public AllocatableValue getResult() {
            return lhs;
        }

    }

    @Opcode("StoreParameter")
    public static class StoreParameter extends AbstractInstruction {

        public static final LIRInstructionClass<StoreParameter> TYPE = LIRInstructionClass.create(StoreParameter.class);

        @Def
        protected AllocatableValue lhs;
        @Use
        protected Value rhs;

        public StoreParameter(AllocatableValue lhs, Value rhs) {
            super(TYPE);
            this.lhs = lhs;
            this.rhs = rhs;
        }

        @Override
        protected void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            asm.emitValue(crb, lhs);
            if (rhs instanceof SPIRVLIROp) {
                ((SPIRVLIROp) rhs).emit(crb, asm);
            } else {
                asm.emitValue(crb, rhs);
            }
            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "emit StoreParameter: " + lhs + " = " + rhs);
            SPIRVId storeValue = asm.lookUpLIRInstructions(rhs);
            SPIRVId lhsId = asm.lookUpLIRInstructions(lhs);

            asm.currentBlockScope().add(new SPIRVOpStore( //
                    lhsId, //
                    storeValue, //
                    new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(lhs.getPlatformKind().getSizeInBytes())) //
                    )));

            // We register the left part because we want the disassociation from the right
            // part (a not valid ID) from the left part.
            // From this point in the execution, we need the new ID (registered with Graal).
            // This is only for function parameters.

            // Example:
            // OpStore %spirv_i_0F1 %aF1 Aligned 4 // aF1 and aF2 are parameters, not
            // variables.
            // OpStore %spirv_i_1F1 %bF1 Aligned 4

            // In order to use later the spirv_XXX variables, we register the current ID
            // (left part) with its ID.

            asm.registerLIRInstructionValue(lhs, lhsId);
        }

        public AllocatableValue getResult() {
            return lhs;
        }

    }

    @Opcode("ASSIGNWithLoad")
    public static class AssignStmtWithLoad extends AbstractInstruction {

        public static final LIRInstructionClass<AssignStmtWithLoad> TYPE = LIRInstructionClass.create(AssignStmtWithLoad.class);

        @Def
        protected AllocatableValue lhs;
        @Use
        protected Value rhs;

        public AssignStmtWithLoad(AllocatableValue lhs, Value rhs) {
            super(TYPE);
            this.lhs = lhs;
            this.rhs = rhs;
        }

        @Override
        protected void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

            // This call will register the lhs id in case is not in the lookupTable yet.
            asm.emitValue(crb, lhs);

            if (rhs instanceof SPIRVLIROp) {
                ((SPIRVLIROp) rhs).emit(crb, asm);
            } else {
                asm.emitValue(crb, rhs);
            }

            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "emit ASSIGNWithLoad: " + lhs + " = " + rhs);

            SPIRVKind kindRhs = (SPIRVKind) rhs.getPlatformKind();
            SPIRVId typeRhs = asm.primitives.getTypePrimitive(kindRhs);

            // If the right hand side expression is a constant, we don't need to load the
            // constant, but rather just use is in the store
            SPIRVId loadId;
            if (rhs instanceof ConstantValue) {
                ConstantValue constantValue = (ConstantValue) rhs;
                loadId = asm.lookUpConstant(constantValue.getConstant().toValueString(), (SPIRVKind) rhs.getPlatformKind());
            } else {
                SPIRVId param = asm.lookUpLIRInstructions(rhs);
                loadId = asm.module.getNextId();
                asm.currentBlockScope().add(new SPIRVOpLoad(//
                        typeRhs, //
                        loadId, //
                        param, //
                        new SPIRVOptionalOperand<>( //
                                SPIRVMemoryAccess.Aligned( //
                                        new SPIRVLiteralInteger(kindRhs.getSizeInBytes())))//
                ));
            }

            SPIRVId storeAddressID = asm.lookUpLIRInstructions(lhs);
            SPIRVKind kindLHS = (SPIRVKind) lhs.getPlatformKind();
            asm.currentBlockScope().add(new SPIRVOpStore( //
                    storeAddressID, //
                    loadId, //
                    new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(kindLHS.getSizeInBytes())) //
                    )));

            // We can do this because the prev expression (right-hand side), register the
            // stores.
            asm.registerLIRInstructionValue(lhs, storeAddressID);
        }

        public AllocatableValue getResult() {
            return lhs;
        }

    }

    @Opcode("ASSIGNParameter")
    public static class ASSIGNParameter extends AbstractInstruction {

        public static final LIRInstructionClass<ASSIGNParameter> TYPE = LIRInstructionClass.create(ASSIGNParameter.class);

        @Def
        protected AllocatableValue lhs;
        @Use
        protected Value rhs;

        protected int alignment;

        protected int parameterIndex;

        public ASSIGNParameter(AllocatableValue lhs, Value rhs, int alignment, int parameterIndex) {
            super(TYPE);
            this.lhs = lhs;
            this.rhs = rhs;
            this.alignment = alignment;
            this.parameterIndex = parameterIndex;
        }

        /**
         * Emit the following SPIR-V structure:
         *
         * <code>
         * OpStore <address> <value> Aligned <alignment>
         * </code>
         *
         * @param crb
         *            {@link SPIRVCompilationResultBuilder}
         * @param asm
         *            {@link SPIRVAssembler}
         */
        @Override
        protected void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "µIns ASSIGNParameter");

            // This call will register the lhs id in case is not in the lookupTable yet.
            asm.emitValue(crb, lhs);

            if (rhs instanceof SPIRVLIROp) {
                ((SPIRVLIROp) rhs).emit(crb, asm);
            } else {
                asm.emitValue(crb, rhs);
            }

            // Emit Store
            // SPIRVId parameterID = asm.getParameterId(parameterIndex);
            SPIRVId parameterID = asm.lookUpLIRInstructions(lhs);
            SPIRVId idExpression = asm.lookUpLIRInstructions(rhs);
            asm.currentBlockScope().add(new SPIRVOpStore( //
                    parameterID, //
                    idExpression, //
                    new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(alignment))) //
            ));
            asm.registerLIRInstructionValue(lhs, parameterID);
        }

        public AllocatableValue getResult() {
            return lhs;
        }

    }

    @Opcode("ASSIGNIndexedParameter")
    public static class ASSIGNIndexedParameter extends AbstractInstruction {

        public static final LIRInstructionClass<ASSIGNIndexedParameter> TYPE = LIRInstructionClass.create(ASSIGNIndexedParameter.class);

        @Def
        protected AllocatableValue lhs;

        @Use
        protected Value rhs;

        public ASSIGNIndexedParameter(AllocatableValue lhs, Value rhs) {
            super(TYPE);
            this.lhs = lhs;
            this.rhs = rhs;
        }

        /**
         * Emit the following SPIR-V structure:
         *
         * <code>
         * OpStore <address> <value> Aligned <alignment>
         * </code>
         *
         * @param crb
         *            {@link SPIRVCompilationResultBuilder}
         * @param asm
         *            {@link SPIRVAssembler}
         */
        @Override
        protected void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "µIns ASSIGNIndexedParameter");

            // This call will register the lhs id in case is not in the lookupTable yet.
            asm.emitValue(crb, lhs);

            if (rhs instanceof SPIRVLIROp) {
                ((SPIRVLIROp) rhs).emit(crb, asm);
            } else {
                asm.emitValue(crb, rhs);
            }

            // Emit Store
            SPIRVId parameterID = asm.lookUpLIRInstructions(lhs);
            SPIRVId idExpression = asm.lookUpLIRInstructions(rhs);

            SPIRVId resultType = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_32);
            SPIRVId convertId = asm.module.getNextId();
            asm.currentBlockScope().add(new SPIRVOpUConvert(resultType, convertId, idExpression));

            asm.currentBlockScope().add(new SPIRVOpStore( //
                    parameterID, //
                    convertId, //
                    new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(4))) //
            ));
            asm.registerLIRInstructionValue(lhs, parameterID);
        }

        public AllocatableValue getResult() {
            return lhs;
        }

    }

    @Opcode("EXPR")
    public static class ExprStmt extends AbstractInstruction {

        public static final LIRInstructionClass<ExprStmt> TYPE = LIRInstructionClass.create(ExprStmt.class);

        @Use
        protected Value expr;

        public ExprStmt(SPIRVLIROp expr) {
            super(TYPE);
            this.expr = expr;
        }

        @Override
        protected void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "µIns EXPR emitCode generation ");
            if (expr instanceof SPIRVLIROp) {
                ((SPIRVLIROp) expr).emit(crb, asm);
            } else {
                asm.emitValue(crb, expr);
            }
        }

        public Value getExpr() {
            return expr;
        }
    }

    @Opcode("LOAD")
    public static class LoadStmt extends AbstractInstruction {

        public static final LIRInstructionClass<LoadStmt> TYPE = LIRInstructionClass.create(LoadStmt.class);

        @Def
        protected AllocatableValue result;

        @Use
        protected SPIRVAddressCast cast;

        @Use
        protected MemoryAccess base;

        public LoadStmt(AllocatableValue result, SPIRVAddressCast cast, MemoryAccess memoryRegion) {
            super(TYPE);
            this.result = result;
            this.cast = cast;
            this.base = memoryRegion;
        }

        @Override
        public void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

            // They key is in the cast.
            asm.emitValue(crb, cast);
            asm.emitValue(crb, base.value);
            asm.emitValue(crb, result);

            SPIRVId addressToLoad = asm.lookUpLIRInstructions(base.value);
            SPIRVId idLoad = asm.module.getNextId();
            SPIRVId idKindLoad = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_64);

            asm.currentBlockScope().add(new SPIRVOpLoad( //
                    idKindLoad, //
                    idLoad, //
                    addressToLoad, //
                    new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(SPIRVKind.OP_TYPE_INT_64.getByteCount())))));

            SPIRVId ptrCrossGroup = asm.primitives.getPtrToCrossWorkGroupPrimitive((SPIRVKind) result.getPlatformKind());
            SPIRVId storeAddressID = asm.module.getNextId();
            asm.currentBlockScope().add(new SPIRVOpConvertUToPtr(ptrCrossGroup, storeAddressID, idLoad));

            SPIRVId idKind = asm.primitives.getTypePrimitive(cast.getSPIRVPlatformKind());

            SPIRVId loadID = asm.module.getNextId();
            asm.currentBlockScope().add(new SPIRVOpLoad( //
                    idKind, //
                    loadID, //
                    storeAddressID, //
                    new SPIRVOptionalOperand<>( //
                            SPIRVMemoryAccess.Aligned( //
                                    new SPIRVLiteralInteger(cast.getPlatformKind().getSizeInBytes()))) //
            ));

            SPIRVId resultID = asm.lookUpLIRInstructions(result);

            asm.currentBlockScope().add(new SPIRVOpStore( //
                    resultID, //
                    loadID, //
                    new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(cast.getSPIRVPlatformKind().getByteCount())) //
                    )));
        }

    }

    @Opcode("LOAD_VECTOR")
    public static class LoadVectorStmt extends AbstractInstruction {

        public static final LIRInstructionClass<LoadVectorStmt> TYPE = LIRInstructionClass.create(LoadVectorStmt.class);

        @Def
        protected AllocatableValue result;

        @Use
        protected SPIRVAddressCast cast;

        @Use
        protected MemoryAccess base;

        public LoadVectorStmt(AllocatableValue result, SPIRVAddressCast cast, MemoryAccess memoryRegion) {
            super(TYPE);
            this.result = result;
            this.cast = cast;
            this.base = memoryRegion;
        }

        @Override
        public void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

            // They key is in the cast.
            asm.emitValue(crb, cast);
            asm.emitValue(crb, base.value);
            asm.emitValue(crb, result);

            SPIRVId addressToLoad = asm.lookUpLIRInstructions(base.value);
            SPIRVId idLoad = asm.module.getNextId();
            SPIRVId idKindLoad = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_64);

            asm.currentBlockScope().add(new SPIRVOpLoad( //
                    idKindLoad, //
                    idLoad, //
                    addressToLoad, //
                    new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(SPIRVKind.OP_TYPE_INT_64.getByteCount())))));

            SPIRVKind vectorElementKind = ((SPIRVKind) result.getPlatformKind()).getElementKind();
            SPIRVId ptrCrossGroup = asm.primitives.getPtrToCrossWorkGroupPrimitive(vectorElementKind);

            SPIRVId idLongToPtr = asm.module.getNextId();
            asm.currentBlockScope().add(new SPIRVOpConvertUToPtr(ptrCrossGroup, idLongToPtr, idLoad));

            SPIRVId idKind = asm.primitives.getTypePrimitive(cast.getSPIRVPlatformKind());

            SPIRVId vloadId = asm.module.getNextId();
            SPIRVId set = asm.getOpenclImport();

            SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic builtIn = SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.VLOADN;
            SPIRVId baseIndex = asm.lookUpConstant("0", SPIRVKind.OP_TYPE_INT_64);

            SPIRVLiteralExtInstInteger intrinsic = new SPIRVLiteralExtInstInteger(builtIn.getValue(), builtIn.getName());
            SPIRVMultipleOperands operandsIntrinsic = new SPIRVMultipleOperands(baseIndex, idLongToPtr, new SPIRVLiteralInteger(cast.getSPIRVPlatformKind().getVectorLength()));

            asm.currentBlockScope().add(new SPIRVOpExtInst(idKind, //
                    vloadId, //
                    set, //
                    intrinsic, //
                    operandsIntrinsic));

            SPIRVId resultID = asm.lookUpLIRInstructions(result);

            asm.currentBlockScope().add(new SPIRVOpStore( //
                    resultID, //
                    vloadId, //
                    new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(cast.getSPIRVPlatformKind().getByteCount())) //
                    )));
        }
    }

    @Opcode("STORE")
    public static class StoreStmt extends AbstractInstruction {

        public static final LIRInstructionClass<StoreStmt> TYPE = LIRInstructionClass.create(StoreStmt.class);

        @Use
        protected Value rhs;

        @Use
        protected SPIRVAddressCast cast;

        @Use
        protected MemoryAccess address;

        @Use
        protected Value index;

        public StoreStmt(SPIRVAddressCast cast, MemoryAccess address, Value rhs) {
            super(TYPE);
            this.cast = cast;
            this.address = address;
            this.rhs = rhs;
        }

        @Override
        protected void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

            cast.emit(crb, asm);

            SPIRVId value;
            if (rhs instanceof ConstantValue) {
                value = asm.lookUpConstant(((ConstantValue) this.rhs).getConstant().toValueString(), (SPIRVKind) rhs.getPlatformKind());
            } else {
                value = asm.lookUpLIRInstructions(rhs);
                SPIRVId resultType = asm.primitives.getTypePrimitive((SPIRVKind) rhs.getPlatformKind());
                SPIRVId loadID = asm.module.getNextId();
                asm.currentBlockScope().add(new SPIRVOpLoad( //
                        resultType, // type of load
                        loadID, // new id
                        value, // pointer
                        new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(rhs.getPlatformKind().getSizeInBytes()))) //
                ));
                value = loadID;
            }

            SPIRVKind spirvKind = (SPIRVKind) cast.getLIRKind().getPlatformKind();
            SPIRVId storeAddressID = asm.lookUpLIRInstructions(cast);

            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "emit StoreStmt in address: " + cast + " <- " + rhs);

            asm.currentBlockScope().add(new SPIRVOpStore( //
                    storeAddressID, //
                    value, //
                    new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(spirvKind.getByteCount())) //
                    )));
        }
    }

    @Opcode("STORE_VECTOR")
    public static class StoreVectorStmt extends AbstractInstruction {

        public static final LIRInstructionClass<StoreVectorStmt> TYPE = LIRInstructionClass.create(StoreVectorStmt.class);

        @Use
        protected Value rhs;

        @Use
        protected SPIRVAddressCast cast;

        @Use
        protected MemoryAccess address;

        @Use
        protected Value index;

        public StoreVectorStmt(SPIRVAddressCast cast, MemoryAccess address, Value rhs) {
            super(TYPE);
            this.cast = cast;
            this.address = address;
            this.rhs = rhs;
        }

        @Override
        protected void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

            SPIRVId idLoad = asm.module.getNextId();

            // We force to load a pointer to long since the arrays from a ptr to the
            // device's heap
            SPIRVId typeLoad = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_64);

            SPIRVId addressToLoad = asm.lookUpLIRInstructions(address.getValue());
            if (!TornadoOptions.OPTIMIZE_LOAD_STORE_SPIRV) {
                asm.currentBlockScope().add(new SPIRVOpLoad( //
                        typeLoad, //
                        idLoad, //
                        addressToLoad, //
                        new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(SPIRVKind.OP_TYPE_INT_64.getByteCount())))));
            } else {
                idLoad = addressToLoad;
            }

            SPIRVKind vectorElementKind = ((SPIRVKind) cast.getLIRKind().getPlatformKind()).getElementKind();
            SPIRVId ptrCrossGroup = asm.primitives.getPtrToCrossWorkGroupPrimitive(vectorElementKind);

            SPIRVId ptrConversionId = asm.module.getNextId();
            asm.currentBlockScope().add(new SPIRVOpConvertUToPtr(ptrCrossGroup, ptrConversionId, idLoad));
            asm.registerLIRInstructionValue(cast, ptrConversionId);

            SPIRVId value;
            if (rhs instanceof ConstantValue) {
                value = asm.lookUpConstant(((ConstantValue) this.rhs).getConstant().toValueString(), (SPIRVKind) rhs.getPlatformKind());
            } else {
                value = asm.lookUpLIRInstructions(rhs);
                SPIRVId resultType = asm.primitives.getTypePrimitive((SPIRVKind) rhs.getPlatformKind());
                SPIRVId loadID = asm.module.getNextId();
                asm.currentBlockScope().add(new SPIRVOpLoad( //
                        resultType, // type of load
                        loadID, // new id
                        value, // pointer
                        new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(rhs.getPlatformKind().getSizeInBytes()))) //
                ));
                value = loadID;
            }

            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "emit StoreVectorStmt in address: " + cast + " <- " + rhs);

            SPIRVId set = asm.getOpenclImport();
            SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic builtIn = SPIRVUnary.Intrinsic.OpenCLExtendedIntrinsic.VSTOREN;
            SPIRVId baseIndex = asm.lookUpConstant("0", SPIRVKind.OP_TYPE_INT_64);

            SPIRVLiteralExtInstInteger intrinsic = new SPIRVLiteralExtInstInteger(builtIn.getValue(), builtIn.getName());
            SPIRVMultipleOperands operandsIntrinsic = new SPIRVMultipleOperands(value, baseIndex, ptrConversionId);

            SPIRVId idVoid = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_VOID);
            SPIRVId result = asm.module.getNextId();
            asm.currentBlockScope().add(new SPIRVOpExtInst(idVoid, //
                    result, //
                    set, //
                    intrinsic, //
                    operandsIntrinsic));
        }
    }

    @Opcode("PRIVATE_ALLOC_ARRAY")
    public static class PrivateArrayAllocation extends AbstractInstruction {

        public static final LIRInstructionClass<PrivateArrayAllocation> TYPE = LIRInstructionClass.create(PrivateArrayAllocation.class);

        @Def
        AllocatableValue allocatableValue;

        @Use
        private Value privateAllocation;

        public PrivateArrayAllocation(AllocatableValue allocatableValue, Value privateAllocation) {
            super(TYPE);
            this.allocatableValue = allocatableValue;
            this.privateAllocation = privateAllocation;
        }

        @Override
        protected void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            if (privateAllocation instanceof SPIRVLIROp) {
                ((SPIRVLIROp) privateAllocation).emit(crb, asm);
            } else {
                asm.emitValue(crb, privateAllocation);
            }
        }
    }

    @Opcode("LOCAL_ALLOC_ARRAY")
    public static class LocalArrayAllocation extends AbstractInstruction {

        public static final LIRInstructionClass<LocalArrayAllocation> TYPE = LIRInstructionClass.create(LocalArrayAllocation.class);

        @Use
        private Value localArray;

        public LocalArrayAllocation(Value localArray) {
            super(TYPE);
            this.localArray = localArray;
        }

        @Override
        protected void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            if (localArray instanceof SPIRVLIROp) {
                ((SPIRVLIROp) localArray).emit(crb, asm);
            } else {
                asm.emitValue(crb, localArray);
            }
        }
    }

    @Opcode("STORE_INDEXED_ACCESS")
    public static class StoreIndexedMemAccess extends AbstractInstruction {

        public static final LIRInstructionClass<StoreIndexedMemAccess> TYPE = LIRInstructionClass.create(StoreIndexedMemAccess.class);

        @Use
        protected Value rhs;

        @Use
        protected SPIRVUnary.MemoryIndexedAccess memoryIndexedAccess;

        @Use
        protected Value index;

        public StoreIndexedMemAccess(SPIRVUnary.MemoryIndexedAccess memoryIndexedAccess, Value rhs) {
            super(TYPE);
            this.memoryIndexedAccess = memoryIndexedAccess;
            this.rhs = rhs;
        }

        private boolean isPrivateMemoryAccess() {
            return this.memoryIndexedAccess.getMemoryRegion().number == SPIRVArchitecture.privateSpace.number;
        }

        private boolean isLocalMemoryAccess() {
            return this.memoryIndexedAccess.getMemoryRegion().number == SPIRVArchitecture.localSpace.number;
        }

        private void emitStoreIndexedAccessPrivateMemory(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "emit [Private] IndexedMemAccess in address: " + memoryIndexedAccess + "[ " + rhs + "]");

            SPIRVId privateAccessId = asm.module.getNextId();

            SPIRVKind spirvKind = (SPIRVKind) rhs.getPlatformKind();
            SPIRVId type = asm.primitives.getTypePrimitive(spirvKind);

            if (rhs instanceof ConstantValue) {
                // If the right-hand-side value is a constant, we do not emit the load, but
                // rather the lookup in the constant table.
                SPIRVKind kind = (SPIRVKind) rhs.getPlatformKind();
                privateAccessId = asm.lookUpConstant(((ConstantValue) rhs).getConstant().toValueString(), kind);
            } else {
                SPIRVId input = asm.lookUpLIRInstructions(rhs);

                if (input == null) {
                    throw new RuntimeException("Input VALUE to access private array is NULL");
                }

                // Emit LOAD before generating local access
                asm.currentBlockScope().add(new SPIRVOpLoad(//
                        type, //
                        privateAccessId, //
                        input, //
                        new SPIRVOptionalOperand<>( //
                                SPIRVMemoryAccess.Aligned( //
                                        new SPIRVLiteralInteger(spirvKind.getByteCount())))//
                ));
            }

            memoryIndexedAccess.emit(crb, asm);

            SPIRVId addressId = asm.lookUpLIRInstructions(memoryIndexedAccess);

            asm.currentBlockScope().add(new SPIRVOpStore( //
                    addressId, //
                    privateAccessId, //
                    new SPIRVOptionalOperand<>(//
                            SPIRVMemoryAccess.Aligned( //
                                    new SPIRVLiteralInteger(spirvKind.getByteCount()) //
                            )) //
            ));
        }

        private void emitStoreIndexedAccessLocalMemory(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "emit IndexedMemAccess for LOCAL MEMORY in address: " + memoryIndexedAccess + "[ " + rhs + "]");

            SPIRVId loadArray = asm.module.getNextId();

            SPIRVKind spirvKind = (SPIRVKind) rhs.getPlatformKind();
            SPIRVId type = asm.primitives.getTypePrimitive(spirvKind);

            SPIRVId input;
            if (rhs instanceof ConstantValue) {
                ConstantValue constantValue = (ConstantValue) rhs;
                String value = constantValue.getConstant().toValueString();
                loadArray = asm.lookUpConstant(value, (SPIRVKind) rhs.getPlatformKind());
            } else {
                input = asm.lookUpLIRInstructions(rhs);
                asm.currentBlockScope().add(new SPIRVOpLoad(//
                        type, //
                        loadArray, //
                        input, //
                        new SPIRVOptionalOperand<>( //
                                SPIRVMemoryAccess.Aligned( //
                                        new SPIRVLiteralInteger(spirvKind.getByteCount())))//
                ));
            }

            memoryIndexedAccess.emit(crb, asm);

            SPIRVId addressId = asm.lookUpLIRInstructions(memoryIndexedAccess);

            asm.currentBlockScope().add(new SPIRVOpStore( //
                    addressId, //
                    loadArray, //
                    new SPIRVOptionalOperand<>(//
                            SPIRVMemoryAccess.Aligned( //
                                    new SPIRVLiteralInteger(spirvKind.getByteCount()) //
                            )) //
            ));
        }

        @Override
        protected void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            if (isPrivateMemoryAccess()) {
                emitStoreIndexedAccessPrivateMemory(crb, asm);
            } else if (isLocalMemoryAccess()) {
                emitStoreIndexedAccessLocalMemory(crb, asm);
            } else {
                throw new RuntimeException("Indexed Memory Access not supported");
            }
        }
    }

    @Opcode("INDEXED_LOAD_ACCESS")
    public static class IndexedLoadMemAccess extends AbstractInstruction {

        public static final LIRInstructionClass<IndexedLoadMemAccess> TYPE = LIRInstructionClass.create(IndexedLoadMemAccess.class);

        @Def
        protected AllocatableValue result;

        @Use
        protected SPIRVUnary.MemoryIndexedAccess address;

        @Use
        protected Value index;

        public IndexedLoadMemAccess(SPIRVUnary.MemoryIndexedAccess address, AllocatableValue result) {
            super(TYPE);
            this.address = address;
            this.result = result;
        }

        @Override
        protected void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

            Logger.traceCodeGen(Logger.BACKEND.SPIRV,
                    "emit IndexedLoadMemAccess in address: " + address + "[ " + address.getIndex() + "]  -- region: " + address.getMemoryRegion().memorySpace.getName());

            address.emitForLoad(crb, asm);

            SPIRVId addressId = asm.lookUpLIRInstructions(address);
            SPIRVKind spirvKind = (SPIRVKind) result.getPlatformKind();
            SPIRVId type = asm.primitives.getTypePrimitive(spirvKind);

            SPIRVId loadId = asm.module.getNextId();
            asm.currentBlockScope().add(new SPIRVOpLoad( //
                    type, //
                    loadId, //
                    addressId, //
                    new SPIRVOptionalOperand<>(//
                            SPIRVMemoryAccess.Aligned(//
                                    new SPIRVLiteralInteger(spirvKind.getByteCount())))//
            ));

            asm.emitValue(crb, result);
            SPIRVId storeId = asm.lookUpLIRInstructions(result);

            asm.currentBlockScope().add(new SPIRVOpStore( //
                    storeId, //
                    loadId, //
                    new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(spirvKind.getByteCount()))) //
            ));

            asm.registerLIRInstructionValue(result, storeId);

        }
    }
}
