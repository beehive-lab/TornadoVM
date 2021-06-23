package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpConvertUToPtr;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpExtInst;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpInBoundsPtrAccessChain;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpLoad;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpStore;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVLiteralExtInstInteger;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVLiteralInteger;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVMemoryAccess;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVMultipleOperands;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVOptionalOperand;
import uk.ac.manchester.tornado.drivers.spirv.common.SPIRVLogger;
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

            if (rhs instanceof SPIRVLIROp) {
                ((SPIRVLIROp) rhs).emit(crb, asm);
            } else {
                asm.emitValue(crb, rhs);
            }

            SPIRVLogger.traceCodeGen("emit Assignment: " + lhs + " = " + rhs);

            SPIRVId storeAddressID;
            if (TornadoOptions.OPTIMIZE_LOAD_STORE_SPIRV) {
                storeAddressID = asm.lookUpLIRInstructions(rhs);
            } else {
                SPIRVId value;
                if (rhs instanceof ConstantValue) {
                    value = asm.lookUpConstant(((ConstantValue) this.rhs).getConstant().toValueString(), (SPIRVKind) rhs.getPlatformKind());
                } else {
                    value = asm.lookUpLIRInstructions(rhs);
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
            SPIRVLogger.traceCodeGen("emit IgnorableAssignment: " + lhs + " = " + rhs);
            SPIRVId storeAddressID = asm.lookUpLIRInstructions(rhs);
            asm.registerLIRInstructionValue(lhs, storeAddressID);
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

            SPIRVLogger.traceCodeGen("emit ASSIGNWithLoad: " + lhs + " = " + rhs);

            SPIRVId uint = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_32);

            // If the right hand side expression is a constant, we don't need to load the
            // constant, but rather just use is in the store
            SPIRVId loadId;
            if (rhs instanceof ConstantValue) {
                ConstantValue constantValue = (ConstantValue) rhs;
                loadId = asm.lookUpConstant(constantValue.getConstant().toValueString(), (SPIRVKind) rhs.getPlatformKind());
                System.out.println(">>>>>>>>>>>> LOAD ID: " + loadId);
            } else {
                SPIRVId param = asm.lookUpLIRInstructions(rhs);
                loadId = asm.module.getNextId();
                asm.currentBlockScope().add(new SPIRVOpLoad(//
                        uint, //
                        loadId, //
                        param, //
                        new SPIRVOptionalOperand<>( //
                                SPIRVMemoryAccess.Aligned( //
                                        new SPIRVLiteralInteger(4)))//
                ));
            }

            SPIRVId storeAddressID = asm.lookUpLIRInstructions(lhs);
            asm.currentBlockScope().add(new SPIRVOpStore( //
                    storeAddressID, //
                    loadId, //
                    new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(4)) //
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
            SPIRVLogger.traceCodeGen("µIns ASSIGNParameter");

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

    @Opcode("LoadFrame")
    public static class LoadFrame extends AbstractInstruction {

        public static final LIRInstructionClass<LoadFrame> TYPE = LIRInstructionClass.create(LoadFrame.class);

        protected SPIRVKind type;
        protected SPIRVId address;

        public LoadFrame(SPIRVKind type) {
            super(TYPE);
            this.type = type;
        }

        @Override
        protected void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            SPIRVLogger.traceCodeGen("µIns LoadStmt ");
            SPIRVId loadID = asm.module.getNextId();

            SPIRVId typeId = null;
            if (type == SPIRVKind.OP_TYPE_INT_64) {
                typeId = asm.primitives.getPtrToTypePrimitive(type);
            } else {
                throw new RuntimeException("Not supported");
            }
            SPIRVId address = asm.frameId;
            int alignment = 8;
            asm.currentBlockScope().add(new SPIRVOpLoad( //
                    typeId, //
                    loadID, //
                    address, //
                    new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(alignment))) //
            ));
            asm.prevId = loadID;
        }
    }

    @Deprecated
    @Opcode("LoadFromStackFrame")
    public static class LoadFromStackFrame extends AbstractInstruction {

        public static final LIRInstructionClass<LoadFromStackFrame> TYPE = LIRInstructionClass.create(LoadFromStackFrame.class);

        protected SPIRVKind type;
        protected SPIRVId address;
        protected int indexFromStackFrame;
        protected int parameterIndex;

        @Deprecated
        public LoadFromStackFrame(SPIRVKind type, int indexFromStackFrame, int parameterIndex) {
            super(TYPE);
            this.type = type;
            this.indexFromStackFrame = indexFromStackFrame;
            this.parameterIndex = parameterIndex;
        }

        @Deprecated
        @Override
        protected void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            SPIRVLogger.traceCodeGen("µIns LoadFromStackFrame ");
            SPIRVId loadID = asm.module.getNextId();

            SPIRVId ptrFUnctionULong = null;
            if (type == SPIRVKind.OP_TYPE_INT_64) {
                ptrFUnctionULong = asm.primitives.getPtrToTypePrimitive(type);
            } else {
                throw new RuntimeException("Not supported");
            }
            SPIRVId address = asm.frameId;
            int alignment = 8;
            asm.currentBlockScope().add(new SPIRVOpLoad( //
                    ptrFUnctionULong, //
                    loadID, //
                    address, //
                    new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(alignment))) //
            ));

            String values = String.valueOf(indexFromStackFrame);
            SPIRVId index = asm.lookUpConstant(values, SPIRVKind.OP_TYPE_INT_32);
            SPIRVId accessPTR = asm.module.getNextId();

            asm.currentBlockScope().add(new SPIRVOpInBoundsPtrAccessChain( //
                    asm.pointerToULongFunction, //
                    accessPTR, //
                    loadID, //
                    index, //
                    new SPIRVMultipleOperands<>()));

            // Load Address
            SPIRVId loadPtr = asm.module.getNextId();
            asm.currentBlockScope().add(new SPIRVOpLoad( //
                    ptrFUnctionULong, //
                    loadPtr, //
                    accessPTR, //
                    new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(alignment))) //
            ));

            SPIRVId parameterID = asm.getParameterId(parameterIndex); /// WARNING -> THIS CALL SHOULD BE DEPRECATED
            asm.currentBlockScope().add(new SPIRVOpStore( //
                    parameterID, //
                    accessPTR, //
                    new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(alignment))) //
            ));

        }
    }

    @Opcode("AccessPointerChain")
    public static class AccessPointerChain extends AbstractInstruction {

        public static final LIRInstructionClass<AccessPointerChain> TYPE = LIRInstructionClass.create(AccessPointerChain.class);

        int value;

        public AccessPointerChain(int value) {
            super(TYPE);
            this.value = value;

        }

        @Override
        protected void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            SPIRVLogger.traceCodeGen("µIns AccessPointerChain ");
            SPIRVId newID = asm.module.getNextId();

            // Note, it does not have to be necessary the prev. operation.
            SPIRVId prev = asm.prevId;

            String values = String.valueOf(value);
            SPIRVId index = asm.lookUpConstant(values, SPIRVKind.OP_TYPE_FLOAT_32);

            asm.currentBlockScope().add(new SPIRVOpInBoundsPtrAccessChain( //
                    asm.pointerToULongFunction, //
                    newID, //
                    prev, //
                    index, //
                    new SPIRVMultipleOperands<>()));
            asm.prevId = newID;
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
            SPIRVLogger.traceCodeGen("µIns EXPR emitCode generation ");
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

    @Opcode("MOVE")
    public static class MoveStmt extends AbstractInstruction {

        public static final LIRInstructionClass<MoveStmt> TYPE = LIRInstructionClass.create(MoveStmt.class);

        @Def
        protected AllocatableValue lhs;
        @Use
        protected Value rhs;

        public MoveStmt(AllocatableValue lhs, Value rhs) {
            super(TYPE);
            this.lhs = lhs;
            this.rhs = rhs;
        }

        @Override
        public void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

        }

        public AllocatableValue getResult() {
            return lhs;
        }

        public Value getExpr() {
            return rhs;
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
            memoryRegion.assignTo((Variable) result);
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

            SPIRVId ptrCrossGroup = asm.primitives.getPtrToCrossGroupPrimitive((SPIRVKind) result.getPlatformKind());
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

            // asm.registerLIRInstructionValue(result, loadID);
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
            memoryRegion.assignTo((Variable) result);
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
            SPIRVId ptrCrossGroup = asm.primitives.getPtrToCrossGroupPrimitive(vectorElementKind);

            SPIRVId idLongToPtr = asm.module.getNextId();
            asm.currentBlockScope().add(new SPIRVOpConvertUToPtr(ptrCrossGroup, idLongToPtr, idLoad));

            SPIRVId idKind = asm.primitives.getTypePrimitive(cast.getSPIRVPlatformKind());

            SPIRVId vloadId = asm.module.getNextId();
            SPIRVId set = asm.getOpenclImport();

            SPIRVUnary.Intrinsic.OpenCLIntrinsic builtIn = SPIRVUnary.Intrinsic.OpenCLIntrinsic.VLOADN;
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
                System.out.println("!!!!!!!!!!!!!!!! LOAD BEFORE STORE: ");
                value = asm.lookUpLIRInstructions(rhs);
                System.out.println("RHS: " + rhs);
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

            SPIRVLogger.traceCodeGen("emit StoreStmt in address: " + cast + " <- " + rhs);

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
            SPIRVId ptrCrossGroup = asm.primitives.getPtrToCrossGroupPrimitive(vectorElementKind);

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

            SPIRVLogger.traceCodeGen("emit StoreVectorStmt in address: " + cast + " <- " + rhs);

            SPIRVId set = asm.getOpenclImport();
            SPIRVUnary.Intrinsic.OpenCLIntrinsic builtIn = SPIRVUnary.Intrinsic.OpenCLIntrinsic.VSTOREN;
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

        @Use
        private Value privateAllocation;

        public PrivateArrayAllocation(Value privateAllocation) {
            super(TYPE);
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

    @Opcode("INDEXED_ACCESS")
    public static class IndexedMemAccess extends AbstractInstruction {

        public static final LIRInstructionClass<IndexedMemAccess> TYPE = LIRInstructionClass.create(IndexedMemAccess.class);

        @Use
        protected Value rhs;

        @Use
        protected SPIRVUnary.MemoryIndexedAccess address;

        @Use
        protected Value index;

        public IndexedMemAccess(SPIRVUnary.MemoryIndexedAccess address, Value rhs) {
            super(TYPE);
            this.address = address;
            this.rhs = rhs;
        }

        @Override
        protected void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            SPIRVLogger.traceCodeGen("emit IndexedMemAccess in address: " + address + "[ " + rhs + "]");

            SPIRVId loadArray = asm.module.getNextId();

            SPIRVKind spirvKind = (SPIRVKind) rhs.getPlatformKind();
            SPIRVId type = asm.primitives.getTypePrimitive(spirvKind);

            SPIRVId input = asm.lookUpLIRInstructions(rhs);

            asm.currentBlockScope().add(new SPIRVOpLoad(//
                    type, //
                    loadArray, //
                    input, //
                    new SPIRVOptionalOperand<>( //
                            SPIRVMemoryAccess.Aligned( //
                                    new SPIRVLiteralInteger(spirvKind.getByteCount())))//
            ));

            address.emit(crb, asm);

            SPIRVId addressId = asm.lookUpLIRInstructions(address);

            asm.currentBlockScope().add(new SPIRVOpStore( //
                    addressId, //
                    loadArray, //
                    new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(spirvKind.getByteCount()))) //
            ));

        }
    }

    @Opcode("INDEXED_LOAD_ACCESS")
    public static class IndexedLoadMemAccess extends AbstractInstruction {

        public static final LIRInstructionClass<IndexedLoadMemAccess> TYPE = LIRInstructionClass.create(IndexedLoadMemAccess.class);

        @Use
        protected Value rhs;

        @Use
        protected SPIRVUnary.MemoryIndexedAccess address;

        @Use
        protected Value index;

        public IndexedLoadMemAccess(SPIRVUnary.MemoryIndexedAccess address, Value rhs) {
            super(TYPE);
            this.address = address;
            this.rhs = rhs;
        }

        @Override
        protected void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

            asm.emitValue(crb, rhs);

            SPIRVLogger.traceCodeGen("emit IndexedLoadMemAccess in address: " + address + "[ " + rhs + "]");

            SPIRVId loadArray = asm.module.getNextId();

            SPIRVKind spirvKind = (SPIRVKind) rhs.getPlatformKind();
            SPIRVId type = asm.primitives.getTypePrimitive(spirvKind);

            SPIRVId input = asm.lookUpLIRInstructions(rhs);

            asm.currentBlockScope().add(new SPIRVOpLoad(//
                    type, //
                    loadArray, //
                    input, //
                    new SPIRVOptionalOperand<>( //
                            SPIRVMemoryAccess.Aligned( //
                                    new SPIRVLiteralInteger(spirvKind.getByteCount())))//
            ));

            address.emit(crb, asm);

            SPIRVId addressId = asm.lookUpLIRInstructions(address);

            asm.currentBlockScope().add(new SPIRVOpStore( //
                    addressId, //
                    loadArray, //
                    new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(spirvKind.getByteCount()))) //
            ));

        }
    }
}
