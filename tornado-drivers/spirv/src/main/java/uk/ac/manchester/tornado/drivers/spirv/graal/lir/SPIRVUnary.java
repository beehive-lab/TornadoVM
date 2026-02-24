/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021-2024, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.LIRInstruction.Def;
import jdk.graal.compiler.lir.LIRInstruction.Use;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.Variable;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.beehivespirvtoolkit.lib.SPIRVInstScope;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpCompositeExtract;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpControlBarrier;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpConvertFToS;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpConvertPtrToU;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpConvertSToF;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpConvertUToPtr;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpExtInst;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpFConvert;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpFNegate;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpInBoundsPtrAccessChain;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpLoad;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpReturnValue;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpSConvert;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpSNegate;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpUConvert;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVLiteralExtInstInteger;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVLiteralInteger;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVMemoryAccess;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVMultipleOperands;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVOptionalOperand;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVStorageClass;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVThreadBuiltIn;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVArchitecture;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVArchitecture.SPIRVMemoryBase;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler.SPIRVUnaryOp;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.spirv.graal.meta.SPIRVMemorySpace;
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

        @Def
        protected Variable result;

        protected UnaryConsumer(SPIRVUnaryOp opcode, Variable result, LIRKind valueKind, Value value) {
            super(valueKind);
            this.opcode = opcode;
            this.value = value;
            this.result = result;
        }

        protected SPIRVId obtainPhiValueIdIfNeeded(SPIRVAssembler asm) {
            SPIRVId operationId;
            if (!asm.isPhiMapEmpty() && asm.isResultInPhiMap(result)) {
                operationId = asm.getPhiId(result);
                AllocatableValue allocatableValue = result;
                while (operationId == null) {
                    // We loop-up the operation ID. In the case it's in the Phi Table, we look at
                    // the trace to obtain the root Phi Variable.
                    AllocatableValue tempValue = asm.getPhiTraceValue((Variable) allocatableValue);
                    operationId = asm.getPhiId((Variable) tempValue);
                    allocatableValue = tempValue;
                }
            } else {
                operationId = asm.module.getNextId();
            }
            return operationId;
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

        public Expr(SPIRVUnaryOp opcode, Variable result, LIRKind lirKind, Value value) {
            super(opcode, result, lirKind, value);
        }

    }

    public static class LoadFromStackFrameExpr extends Expr {

        protected SPIRVKind type;
        protected SPIRVId address;
        protected int indexFromStackFrame;
        protected int parameterIndex;

        public LoadFromStackFrameExpr(LIRKind lirKind, SPIRVKind type, int indexFromStackFrame, int parameterIndex) {
            super(null, null, lirKind, null);
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
            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "µIns LoadFromStackFrame ");
            SPIRVId loadID = asm.module.getNextId();

            SPIRVId ptrCrossWorkGroupULong = null;
            if (type == SPIRVKind.OP_TYPE_INT_64) {
                ptrCrossWorkGroupULong = asm.getPTrCrossWorkULong();
            }
            SPIRVId address = asm.getKernelContextId();
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

    public static class AssignLoadFromInputFrame extends Expr {

        protected SPIRVKind type;
        protected SPIRVId address;
        protected int indexFromKernelContext;
        protected int parameterIndex;

        /**
         * In OpenCL:
         *
         * ul_0 = (ulong) a;
         *
         * Equivalent generated code:
         *
         * <code>
         * %46 = OpLoad %_ptr_CrossWorkgroup_uchar %a_addr Aligned 8
         * %47 = OpConvertPtrToU %ulong %46
         * OpStore %ul_0 %47 Aligned 8
         * </code>
         */
        public AssignLoadFromInputFrame(LIRKind lirKind, SPIRVKind type, int indexFromKernelContext, int parameterIndex) {
            super(null, null, lirKind, null);
            this.type = type;
            this.indexFromKernelContext = indexFromKernelContext;
            this.parameterIndex = parameterIndex;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "µIns AssignLoadFromInputFrame with Index: " + parameterIndex);

            SPIRVId resultType = asm.primitives.getPtrToCrossWorkGroupPrimitive(type);

            SPIRVId address = asm.lookupParameterFromIndex(parameterIndex);

            final int alignment = 8;
            SPIRVId loadID = asm.module.getNextId();
            asm.currentBlockScope().add(new SPIRVOpLoad( //
                    resultType, //
                    loadID, //
                    address, //
                    new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(alignment))) //
            ));

            SPIRVId convertId = asm.module.getNextId();
            SPIRVId ulong = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_64);
            asm.currentBlockScope().add(new SPIRVOpConvertPtrToU( //
                    ulong, //
                    convertId, //
                    loadID));

            // The final store is emitted in the assignParameter
            asm.registerLIRInstructionValue(this, convertId);
        }
    }

    public static class LoadIndexValueFromKernelContext extends Expr {

        protected SPIRVKind type;
        protected SPIRVId address;
        protected Value parameterIndex;

        public LoadIndexValueFromKernelContext(LIRKind lirKind, SPIRVKind type, Value parameterIndex) {
            super(null, null, lirKind, null);
            this.type = type;
            this.parameterIndex = parameterIndex;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "µIns LoadIndexValueFromSPIRVStack ");
            SPIRVId loadID = asm.module.getNextId();

            SPIRVId ptrCrossWorkGroupULong = asm.getPTrCrossWorkULong();

            SPIRVId address = asm.getKernelContextId();
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

    public abstract static class AbstractMemoryAccess extends UnaryConsumer {

        protected AbstractMemoryAccess(SPIRVUnaryOp opcode, Variable result, LIRKind valueKind, Value value) {
            super(opcode, result, valueKind, value);
        }

        public abstract SPIRVMemoryBase getMemoryRegion();
    }

    public static class MemoryAccess extends AbstractMemoryAccess {

        private final SPIRVMemoryBase memoryRegion;

        @Use
        private Value index;

        MemoryAccess(SPIRVMemoryBase base, Value value) {
            super(null, null, LIRKind.Illegal, value);
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

    }

    public static class MemoryIndexedAccess extends AbstractMemoryAccess {

        private final SPIRVMemoryBase memoryRegion;

        @Use
        private Value index;

        public MemoryIndexedAccess(SPIRVMemoryBase memoryRegion, Value baseValue, Value indexValue) {
            super(null, null, LIRKind.Illegal, baseValue);
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
            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "\temit Private memory access");
            SPIRVId arrayAccessId = asm.module.getNextId();

            SPIRVId baseIndex = asm.lookUpConstant("0", SPIRVKind.OP_TYPE_INT_64);
            SPIRVId indexId;
            if (index instanceof ConstantValue) {
                indexId = asm.lookUpConstant(((ConstantValue) index).getConstant().toValueString(), (SPIRVKind) index.getPlatformKind());
            } else {
                SPIRVId loadId;
                indexId = asm.lookUpLIRInstructions(index);
                if (TornadoOptions.OPTIMIZE_LOAD_STORE_SPIRV) {
                    loadId = indexId;
                } else {
                    SPIRVKind kindIndex = (SPIRVKind) index.getPlatformKind();
                    loadId = asm.module.getNextId();
                    SPIRVId typeIndex = asm.primitives.getTypePrimitive(kindIndex);
                    asm.currentBlockScope().add(new SPIRVOpLoad( //
                            typeIndex, //
                            loadId, //
                            indexId, //
                            new SPIRVOptionalOperand<>(//
                                    SPIRVMemoryAccess.Aligned(//
                                            new SPIRVLiteralInteger(kindIndex.getSizeInBytes())))//
                    ));
                }
                SPIRVId typeLong = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_64);
                SPIRVId idConversion = asm.module.getNextId();
                asm.currentBlockScope().add(new SPIRVOpSConvert(typeLong, idConversion, loadId));
                indexId = idConversion;
            }

            SPIRVId baseId = asm.lookUpLIRInstructions(getValue());
            SPIRVKind spirvKind = (SPIRVKind) getValue().getPlatformKind();
            SPIRVId type = asm.primitives.getPtrOpTypePointerWithStorage(spirvKind, SPIRVStorageClass.Function());
            asm.currentBlockScope().add(new SPIRVOpInBoundsPtrAccessChain(type, arrayAccessId, baseId, baseIndex, new SPIRVMultipleOperands(indexId)));
            asm.registerLIRInstructionValue(this, arrayAccessId);
        }

        private void emitLocalMemoryIndexedAccess(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "emit LOCAL memory access");
            SPIRVId arrayAccessId = asm.module.getNextId();

            SPIRVId baseIndex = asm.lookUpConstant("0", SPIRVKind.OP_TYPE_INT_64);

            SPIRVKind kind = (SPIRVKind) index.getPlatformKind();
            SPIRVId indexId;
            if (index instanceof ConstantValue) {
                indexId = asm.lookUpConstant(((ConstantValue) index).getConstant().toValueString(), (SPIRVKind) index.getPlatformKind());
            } else {
                SPIRVId loadId;
                indexId = asm.lookUpLIRInstructions(index);
                if (TornadoOptions.OPTIMIZE_LOAD_STORE_SPIRV) {
                    loadId = indexId;
                } else {
                    loadId = asm.module.getNextId();
                    SPIRVId type = asm.primitives.getTypePrimitive(kind);
                    asm.currentBlockScope().add(new SPIRVOpLoad( //
                            type, //
                            loadId, //
                            indexId, //
                            new SPIRVOptionalOperand<>(//
                                    SPIRVMemoryAccess.Aligned(//
                                            new SPIRVLiteralInteger(kind.getSizeInBytes())))//
                    ));
                }
                SPIRVId typeLong = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_64);
                SPIRVId idConversion = asm.module.getNextId();
                asm.currentBlockScope().add(new SPIRVOpSConvert(typeLong, idConversion, loadId));
                indexId = idConversion;
            }

            SPIRVId baseId = asm.lookUpLIRInstructions(getValue());
            SPIRVKind spirvKind = (SPIRVKind) getValue().getPlatformKind();
            SPIRVId type = asm.primitives.getPtrOpTypePointerWithStorage(spirvKind, SPIRVStorageClass.Workgroup());
            asm.currentBlockScope().add(new SPIRVOpInBoundsPtrAccessChain(type, arrayAccessId, baseId, baseIndex, new SPIRVMultipleOperands(indexId)));
            asm.registerLIRInstructionValue(this, arrayAccessId);
        }

        private boolean isPrivateMemoryAccess() {
            return this.memoryRegion.getNumber() == SPIRVArchitecture.privateSpace.getNumber();
        }

        private boolean isLocalMemoryAccess() {
            return this.memoryRegion.getNumber() == SPIRVArchitecture.localSpace.getNumber();
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

        public void emitForLoad(SPIRVAssembler asm, SPIRVKind resultKind) {
            SPIRVId resultArrayAccessId = asm.module.getNextId();

            SPIRVId baseIndex = asm.lookUpConstant("0", SPIRVKind.OP_TYPE_INT_64);

            SPIRVId indexId;
            if (index instanceof ConstantValue) {
                indexId = asm.lookUpConstant(((ConstantValue) index).getConstant().toValueString(), (SPIRVKind) index.getPlatformKind());
            } else {

                SPIRVId loadId;
                indexId = asm.lookUpLIRInstructions(index);
                if (TornadoOptions.OPTIMIZE_LOAD_STORE_SPIRV) {
                    loadId = indexId;
                } else {
                    loadId = asm.module.getNextId();
                    SPIRVKind spirvKind = (SPIRVKind) index.getPlatformKind();
                    SPIRVId type = asm.primitives.getTypePrimitive(spirvKind);
                    asm.currentBlockScope().add(new SPIRVOpLoad( //
                            type, //
                            loadId, //
                            indexId, //
                            new SPIRVOptionalOperand<>(//
                                    SPIRVMemoryAccess.Aligned(//
                                            new SPIRVLiteralInteger(spirvKind.getSizeInBytes())))//
                    ));

                }

                SPIRVId typeLong = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_64);
                SPIRVId convertID = asm.module.getNextId();
                asm.currentBlockScope().add(new SPIRVOpSConvert(typeLong, convertID, loadId));
                indexId = convertID;
            }

            SPIRVId baseId = asm.lookUpLIRInstructions(getValue());

            SPIRVId type;
            if (getMemoryRegion().getMemorySpace() == SPIRVMemorySpace.LOCAL) {
                type = asm.primitives.getPtrOpTypePointerWithStorage((SPIRVKind) getValue().getPlatformKind(), SPIRVStorageClass.Workgroup());
            } else if (getMemoryRegion().getMemorySpace() == SPIRVMemorySpace.PRIVATE) {
                type = asm.primitives.getPtrToTypeFunctionPrimitive((SPIRVKind) getValue().getPlatformKind());
            } else {
                throw new RuntimeException("Memory access not valid for a SPIRVOpInBoundsPtrAccessChain instruction");
            }

            if (resultKind.isVector() && getMemoryRegion().getMemorySpace() == SPIRVMemorySpace.PRIVATE) {
                // When we copy a collection from private to local, the index is set in the
                // VLOAD intrinsic. Thus, as index, we choose 0 to comply with CLANG/LLVM
                indexId = asm.lookUpConstant("0", SPIRVKind.OP_TYPE_INT_64);
            }
            // } else {
            asm.currentBlockScope().add(new SPIRVOpInBoundsPtrAccessChain(type, resultArrayAccessId, baseId, baseIndex, new SPIRVMultipleOperands(indexId)));
            asm.registerLIRInstructionValue(this, resultArrayAccessId);
        }
    }

    public static class SPIRVAddressCast extends UnaryConsumer {

        private final Value address;

        public SPIRVAddressCast(Value address, SPIRVMemoryBase base, LIRKind valueKind) {
            super(null, null, valueKind, address);
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
         *     {@link SPIRVCompilationResultBuilder}
         * @param asm
         *     {@link SPIRVAssembler}
         */
        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "emit SPIRVAddressCast with LIRKIND: " + getLIRKind().getPlatformKind());

            final SPIRVId idLoad;
            SPIRVId addressId = asm.lookUpLIRInstructions(this.address);

            if (TornadoOptions.OPTIMIZE_LOAD_STORE_SPIRV) {
                idLoad = addressId;
            } else {
                // We force to load a pointer to long
                SPIRVId typeLoad = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_64);
                idLoad = asm.module.getNextId();
                asm.currentBlockScope().add(new SPIRVOpLoad( //
                        typeLoad, //
                        idLoad, //
                        addressId, //
                        new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(SPIRVKind.OP_TYPE_INT_64.getByteCount())))));
            }

            SPIRVId ptrCrossGroup = asm.primitives.getPtrToCrossWorkGroupPrimitive((SPIRVKind) getLIRKind().getPlatformKind());

            SPIRVId convertToPtrId = asm.module.getNextId();
            asm.currentBlockScope().add(new SPIRVOpConvertUToPtr(ptrCrossGroup, convertToPtrId, idLoad));
            asm.registerLIRInstructionValue(this, convertToPtrId);
        }
    }

    public static class ThreadBuiltinCallForSPIRV extends UnaryConsumer {

        protected SPIRVThreadBuiltIn builtIn;
        protected Value dimension;

        public ThreadBuiltinCallForSPIRV(SPIRVThreadBuiltIn builtIn, Variable result, LIRKind valueKind, Value dimension) {
            super(null, result, valueKind, dimension);
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
         * %37 = OpLoad %v3ulong %__spirv_BuiltInGlobalInvocationId Aligned 32 %call = OpCompositeExtract %ulong %37 0 %conv = OpUConvert %uint %call
         * </code>
         */
        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "emit Compute-SPIRV Intrinsic: " + builtIn);

            SPIRVId ulong = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_64);

            // All builtins have to be registered previous to this call
            SPIRVId idSPIRVBuiltin = asm.builtinTable.get(builtIn);

            SPIRVId v3long = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_VECTOR3_INT_64);

            // Call Thread-ID getGlobalId(0)
            SPIRVId idLoadResult = asm.module.getNextId();
            asm.currentBlockScope().add(new SPIRVOpLoad( //
                    v3long, //
                    idLoadResult, //
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
                            idLoadResult, //
                            new SPIRVMultipleOperands<>( //
                                    new SPIRVLiteralInteger(dimensionValue)) //
                    ));

            SPIRVId conv = obtainPhiValueIdIfNeeded(asm);
            SPIRVId uint = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_32);
            asm.currentBlockScope().add(new SPIRVOpUConvert(uint, conv, callIntrinsicId));

            // Store will be performed in the Assigment, if enabled.
            asm.registerLIRInstructionValue(this, conv);
        }
    }

    public abstract static class AbstractExtend extends UnaryConsumer {

        protected AbstractExtend(SPIRVUnaryOp opcode, Variable result, LIRKind valueKind, Value value) {
            super(opcode, result, valueKind, value);
        }

        protected SPIRVId loadConvertIfNeeded(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm, SPIRVId type, SPIRVKind spirvKind) {
            if (value instanceof SPIRVVectorElementSelect) {
                ((SPIRVLIROp) value).emit(crb, asm);
                return asm.lookUpLIRInstructions(value);
            } else {
                SPIRVId param = asm.lookUpLIRInstructions(value);

                if (TornadoOptions.OPTIMIZE_LOAD_STORE_SPIRV) {
                    return param;
                } else {
                    SPIRVId loadConvert = asm.module.getNextId();
                    asm.currentBlockScope().add(new SPIRVOpLoad(//
                            type, //
                            loadConvert, //
                            param, //
                            new SPIRVOptionalOperand<>( //
                                    SPIRVMemoryAccess.Aligned( //
                                            new SPIRVLiteralInteger(spirvKind.getByteCount())))//
                    ));
                    return loadConvert;
                }
            }
        }

    }

    /*
     * FIXME: Possible refactor to merge this class with SIGNNarrowValue. Note that
     * SConvert in SPIRV works for Sign Extend as well as truncate.
     */
    public static class SignExtend extends AbstractExtend {

        private int fromBits;
        private int toBits;

        public SignExtend(LIRKind lirKind, Variable result, Value inputVal, int fromBits, int toBits) {
            super(null, result, lirKind, inputVal);
            this.fromBits = fromBits;
            this.toBits = toBits;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "emit SPIRVOpSConvert : " + fromBits + " -> " + toBits);

            SPIRVKind spirvKind = (SPIRVKind) value.getPlatformKind();
            SPIRVId type = asm.primitives.getTypePrimitive(spirvKind);

            SPIRVId loadConvert = loadConvertIfNeeded(crb, asm, type, spirvKind);

            SPIRVId toTypeId = switch (toBits) {
                case 64 -> asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_64);
                case 32 -> asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_32);
                case 16 -> asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_16);
                default -> throw new TornadoRuntimeException("to Type not supported: " + toBits);
            };

            SPIRVId result = obtainPhiValueIdIfNeeded(asm);
            asm.currentBlockScope().add(new SPIRVOpSConvert(toTypeId, result, loadConvert));

            asm.registerLIRInstructionValue(this, result);

        }
    }

    public static class SignNarrowValue extends AbstractExtend {

        private int toBits;

        public SignNarrowValue(LIRKind lirKind, Variable result, Value inputVal, int toBits) {
            super(null, result, lirKind, inputVal);
            this.toBits = toBits;
        }

        /**
         * Following this: {@url https://www.khronos.org/registry/spir-v/specs/unified1/SPIRV.html#OpSConvert}
         *
         * <code>
         * Convert signed width. This is either a truncate or a sign extend.
         * </code>
         * <p>
         * OpSConvert can be used for sign extend as well as truncate. The "S" symbol represents signed format.
         *
         * @param crb
         *     {@link SPIRVCompilationResultBuilder}
         * @param asm
         *     {@link SPIRVAssembler}
         */
        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "emit SPIRVOpSConvert : -> " + toBits);

            SPIRVKind spirvKind = (SPIRVKind) value.getPlatformKind();
            SPIRVId type = asm.primitives.getTypePrimitive(spirvKind);

            SPIRVId loadConvert = loadConvertIfNeeded(crb, asm, type, spirvKind);

            if ((spirvKind.getSizeInBytes() * 8) == toBits) {
                // There is no conversion for types of the same width
                asm.registerLIRInstructionValue(this, loadConvert);
            } else {
                // OpSConvert
                SPIRVId toType;
                if (toBits == 32) {
                    toType = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_32);
                } else if (toBits == 16) {
                    toType = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_16);
                } else if (toBits == 8) {
                    toType = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_8);
                } else {
                    throw new RuntimeException("ToBits not supported");
                }
                SPIRVId result = obtainPhiValueIdIfNeeded(asm);
                asm.currentBlockScope().add(new SPIRVOpSConvert(toType, result, loadConvert));

                asm.registerLIRInstructionValue(this, result);
            }
        }
    }

    public static class ZeroExtend extends AbstractExtend {

        private int fromBits;
        private int toBits;

        public ZeroExtend(LIRKind lirKind, Variable result, Value inputVal, int fromBits, int toBits) {
            super(null, result, lirKind, inputVal);
            this.fromBits = fromBits;
            this.toBits = toBits;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "emit SPIRVOpUConvert : " + fromBits + " -> " + toBits);

            SPIRVKind spirvKind = (SPIRVKind) value.getPlatformKind();
            SPIRVId type = asm.primitives.getTypePrimitive(spirvKind);

            SPIRVId loadConvert = loadConvertIfNeeded(crb, asm, type, spirvKind);

            SPIRVId toTypeId = switch (toBits) {
                case 64 -> asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_64);
                case 32 -> asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_32);
                case 16 -> asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_16);
                case 8 -> asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_8);
                default -> throw new TornadoRuntimeException("to Type not supported: " + toBits);
            };

            SPIRVId result = obtainPhiValueIdIfNeeded(asm);
            asm.currentBlockScope().add(new SPIRVOpUConvert(toTypeId, result, loadConvert));

            asm.registerLIRInstructionValue(this, result);

        }
    }

    public static class CastOperations extends UnaryConsumer {

        protected CastOperations(SPIRVUnaryOp opcode, Variable result, LIRKind valueKind, Value value) {
            super(opcode, result, valueKind, value);
        }

        protected SPIRVId loadConvertIfNeeded(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm, SPIRVId fromTypeID, SPIRVKind spirvKind) {
            if (value instanceof SPIRVVectorElementSelect) {
                ((SPIRVLIROp) value).emit(crb, asm);
                return asm.lookUpLIRInstructions(value);
            } else {
                SPIRVId param = asm.lookUpLIRInstructions(value);
                if (TornadoOptions.OPTIMIZE_LOAD_STORE_SPIRV) {
                    if (asm.isPhiAcrossBlocksPresent((AllocatableValue) value)) {
                        return asm.getPhiIdAcrossBlock((AllocatableValue) value);
                    }
                    return param;
                }
                SPIRVId loadConvert = asm.module.getNextId();
                asm.currentBlockScope().add(new SPIRVOpLoad(//
                        fromTypeID, //
                        loadConvert, //
                        param, //
                        new SPIRVOptionalOperand<>( //
                                SPIRVMemoryAccess.Aligned( //
                                        new SPIRVLiteralInteger(spirvKind.getByteCount())))//
                ));
                return loadConvert;
            }
        }

    }

    public static class CastIToFloat extends CastOperations {

        private SPIRVKind toType;

        public CastIToFloat(LIRKind lirKind, Variable result, Value inputVal, SPIRVKind toType) {
            super(null, result, lirKind, inputVal);
            this.toType = toType;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "emit SPIRVOpConvertSToF : -> ");

            SPIRVKind spirvKind = toType;
            SPIRVId fromTypeID = asm.primitives.getTypePrimitive((SPIRVKind) value.getPlatformKind());
            SPIRVId toTypeId = asm.primitives.getTypePrimitive(toType);

            SPIRVId loadConvert = loadConvertIfNeeded(crb, asm, fromTypeID, spirvKind);

            // OpSConvert
            SPIRVId result = obtainPhiValueIdIfNeeded(asm);
            asm.currentBlockScope().add(new SPIRVOpConvertSToF(toTypeId, result, loadConvert));

            asm.registerLIRInstructionValue(this, result);
        }
    }

    public static class CastFloatDouble extends CastOperations {

        private SPIRVKind toType;

        public CastFloatDouble(LIRKind lirKind, Variable result, Value inputVal, SPIRVKind toType) {
            super(null, result, lirKind, inputVal);
            this.toType = toType;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "emit SPIRVOpFConvert from " + value.getPlatformKind() + " -> " + toType);

            SPIRVKind spirvKind = toType;
            SPIRVId fromType = asm.primitives.getTypePrimitive((SPIRVKind) value.getPlatformKind());
            SPIRVId toTypeId = asm.primitives.getTypePrimitive(toType);

            SPIRVId loadConvert = loadConvertIfNeeded(crb, asm, fromType, spirvKind);

            // OpFConvert
            SPIRVId result = obtainPhiValueIdIfNeeded(asm);
            asm.currentBlockScope().add(new SPIRVOpFConvert(toTypeId, result, loadConvert));

            asm.registerLIRInstructionValue(this, result);
        }
    }

    public static class CastFloatToLong extends CastOperations {

        private SPIRVKind toType;

        public CastFloatToLong(LIRKind lirKind, Variable result, Value inputVal, SPIRVKind toType) {
            super(null, result, lirKind, inputVal);
            this.toType = toType;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "emit SPIRVOpConvertSToF");

            SPIRVKind spirvKind = toType;
            SPIRVId fromTypeID = asm.primitives.getTypePrimitive((SPIRVKind) value.getPlatformKind());
            SPIRVId toTypeId = asm.primitives.getTypePrimitive(toType);

            SPIRVId loadConvert = loadConvertIfNeeded(crb, asm, fromTypeID, spirvKind);

            // OpSConvert
            SPIRVId result = obtainPhiValueIdIfNeeded(asm);
            asm.currentBlockScope().add(new SPIRVOpConvertSToF(toTypeId, result, loadConvert));

            asm.registerLIRInstructionValue(this, result);
        }
    }

    public static class CastFloatToInt extends CastOperations {

        private SPIRVKind toType;

        public CastFloatToInt(LIRKind lirKind, Variable result, Value inputVal, SPIRVKind toType) {
            super(null, result, lirKind, inputVal);
            this.toType = toType;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "emit SPIRVOpConvertFToS");

            SPIRVKind spirvKind = toType;
            SPIRVId fromTypeID = asm.primitives.getTypePrimitive((SPIRVKind) value.getPlatformKind());
            SPIRVId toTypeId = asm.primitives.getTypePrimitive(toType);

            SPIRVId loadConvert = loadConvertIfNeeded(crb, asm, fromTypeID, spirvKind);

            SPIRVId resultConversion = obtainPhiValueIdIfNeeded(asm);
            asm.currentBlockScope().add(new SPIRVOpConvertFToS(toTypeId, resultConversion, loadConvert));
            asm.registerLIRInstructionValue(this, resultConversion);
        }
    }

    /**
     * OpenCL Extended Instruction Set Intrinsics. As specified in the SPIR-V 1.0 standard, the following intrinsics in SPIR-V represents builtin functions from the OpenCL standard.
     *
     * For obtaining the correct Int-Reference of the function:
     *
     * <url>https://www.khronos.org/registry/spir-v/specs/1.0/OpenCL.ExtendedInstructionSet.100.html</url>
     */
    public static class Intrinsic extends UnaryConsumer {

        private final OpenCLExtendedIntrinsic builtIn;

        public Intrinsic(OpenCLExtendedIntrinsic opcode, LIRKind valueKind, Value value) {
            super(null, null, valueKind, value);
            this.builtIn = opcode;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "emit SPIRVLiteralExtInstInteger: " + builtIn.name);

            SPIRVId type = asm.primitives.getTypePrimitive(getSPIRVPlatformKind());

            SPIRVId loadParam = loadSPIRVId(crb, asm, getValue());

            SPIRVId result = asm.module.getNextId();
            SPIRVId set = asm.getOpenclImport();
            SPIRVLiteralExtInstInteger intrinsic = new SPIRVLiteralExtInstInteger(builtIn.value, builtIn.name);
            asm.currentBlockScope().add(new SPIRVOpExtInst(type, result, set, intrinsic, new SPIRVMultipleOperands<>(loadParam)));
            asm.registerLIRInstructionValue(this, result);
        }

        // @formatter:off
        public enum OpenCLExtendedIntrinsic {
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
            MAD("mad", 42),
            POW("pow", 48),
            REMAINDER("remainder", 51),
            RSQRT("rsqrt", 56),
            SIN("sin", 57),
            SINPI("sinpi", 60),
            SQRT("sqrt", 61),
            TAN("tan", 62),
            TANH("tanh", 63),
            NATIVE_COS("native_cos", 81),  // Optimization
            NATIVE_SIN("native_sin", 92),  // Optimization
            NATIVE_SQRT("native_sqrt", 93),  // Optimization
            NATIVE_TAN("native_tan", 94),  // Optimization
            FCLAMP("flamp", 95),
            RADIANS("radians", 100),
            SIGN("sign", 103),
            SABS("s_abs", 141),
            SCLAMP("s_clamp", 149),
            SMAX("s_max", 156),
            SMIN("s_min", 158),
            POPCOUNT("popcount", 166),

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

            OpenCLExtendedIntrinsic(String name, int value) {
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
    }

    public static class Negate extends UnaryConsumer {

        boolean isInteger;
        String nameDebugInstruction;

        public Negate(LIRKind lirKind, Variable result,  Value inputVal) {
            super(null, result, lirKind, inputVal);
            if (getSPIRVPlatformKind().isInteger() || isVectorElementInteger()) {
                isInteger = true;
                nameDebugInstruction = "SPIRVOpSNegate";
            } else if (getSPIRVPlatformKind().isFloatingPoint() || isVectorElementFloat()) {
                nameDebugInstruction = "SPIRVOpFNegate";
            } else {
                throw new RuntimeException("Error - not valid type");
            }
        }

        private boolean isVectorElementInteger() {
            if (getSPIRVPlatformKind().isVector()) {
                SPIRVKind kind = getSPIRVPlatformKind();
                return (kind.getElementKind().isInteger());
            }
            return false;
        }

        private boolean isVectorElementFloat() {
            if (getSPIRVPlatformKind().isVector()) {
                SPIRVKind kind = getSPIRVPlatformKind();
                return (kind.getElementKind().isFloatingPoint());
            }
            return false;
        }


        protected SPIRVId getId(Value inputValue, SPIRVAssembler asm, SPIRVKind spirvKind) {
            if (inputValue instanceof ConstantValue) {
                SPIRVKind kind = (SPIRVKind) inputValue.getPlatformKind();
                return asm.lookUpConstant(((ConstantValue) inputValue).getConstant().toValueString(), kind);
            } else {
                SPIRVId param = asm.lookUpLIRInstructions(inputValue);
                if (TornadoOptions.OPTIMIZE_LOAD_STORE_SPIRV) {
                    return param;
                }
                // We need to perform a load first
                Logger.traceCodeGen(Logger.BACKEND.SPIRV, "emit LOAD Variable: " + inputValue);
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
            }
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "emit " + nameDebugInstruction + getValue() + " with type: " + getSPIRVPlatformKind());
            SPIRVId valueID =  loadSPIRVId(crb, asm, getValue());
            SPIRVId type = asm.primitives.getTypePrimitive(getSPIRVPlatformKind());
            SPIRVId result = obtainPhiValueIdIfNeeded(asm);
            if (isInteger) {
                asm.currentBlockScope().add(new SPIRVOpSNegate(type, result, valueID));
            } else if (getSPIRVPlatformKind().isFloatingPoint() || isVectorElementFloat()) {
                asm.currentBlockScope().add(new SPIRVOpFNegate(type, result, valueID));
            }
            asm.registerLIRInstructionValue(this, result);
        }
    }

    public static class Barrier extends UnaryConsumer {

        private SPIRVBarrierNode.SPIRVMemFenceFlags flags;

        public Barrier(SPIRVBarrierNode.SPIRVMemFenceFlags flags) {
            super(null, null,  LIRKind.Illegal, null);
            this.flags = flags;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "emit Barrier with FLAGS" + flags.toString() + " SEMANTICS: " + flags.getMemorySemantics());
            SPIRVId constant2 = asm.lookUpConstant("2", SPIRVKind.OP_TYPE_INT_32);
            SPIRVId constantSemantics = asm.lookUpConstant(Integer.toString(flags.getMemorySemantics()), SPIRVKind.OP_TYPE_INT_32);
            asm.currentBlockScope().add(new SPIRVOpControlBarrier(constant2, constant2, constantSemantics));
        }
    }

    public static class LoadParameter extends SPIRVLIROp {

        @Use
        Local local;

        private int paramIndex;

        public LoadParameter(Local local, LIRKind lirKind, int paramIndex) {
            super(lirKind);
            this.local = local;
            this.paramIndex = paramIndex;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "Loading Method Parameter:" + local.getName());
            String variableName = local.getName() + "F" + asm.getMethodIndex();
            SPIRVId idLocal = asm.lookUpLIRInstructionsName(variableName);
            asm.registerLIRInstructionValue(this, idLocal);
        }
    }

    public static class ReturnWithValue extends SPIRVLIROp {

        private BasicBlock<?> currentBlock;

        @Use
        private Value input;

        public ReturnWithValue(LIRKind lirKind, Value input, BasicBlock<?> currentBlock) {
            super(lirKind);
            this.currentBlock = currentBlock;
            this.input = input;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            // Search the block
            String blockName = asm.composeUniqueLabelName(currentBlock.toString());
            SPIRVInstScope blockScope = asm.getBlockTable().get(blockName);

            // Add Block with Return
            SPIRVKind spirvKind = (SPIRVKind) input.getPlatformKind();
            SPIRVId valueToReturn = getId(input, asm, spirvKind);

            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "emit SPIRVOpReturnValue : " + currentBlock.toString() + " with value: " + input);
            asm.setReturnWithValue(true);
            blockScope.add(new SPIRVOpReturnValue(valueToReturn));
        }
    }
}
