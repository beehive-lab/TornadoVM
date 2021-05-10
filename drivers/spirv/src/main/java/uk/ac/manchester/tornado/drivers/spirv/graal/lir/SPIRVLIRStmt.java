package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpInBoundsPtrAccessChain;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpLoad;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpStore;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVLiteralInteger;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVMemoryAccess;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVMultipleOperands;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVOptionalOperand;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIROp;
import uk.ac.manchester.tornado.drivers.spirv.common.SPIRVLogger;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVUnary.MemoryAccess;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVUnary.SPIRVAddressCast;

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
            System.out.println("µIns Assignment");
            // Code emission for assignment
            System.out.println("rhs??? : " + rhs);
            asm.emitValue(crb, lhs);
            if (rhs instanceof SPIRVLIROp) {
                ((SPIRVLIROp) rhs).emit(crb, asm);
            } else {
                asm.emitValue(crb, rhs);
            }
        }

        public AllocatableValue getResult() {
            return lhs;
        }

        public Value getExpr() {
            return rhs;
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
            System.out.println("µIns LoadStmt ");
            SPIRVId loadID = asm.module.getNextId();

            SPIRVId typeId = null;
            if (type == SPIRVKind.OP_TYPE_INT_64) {
                typeId = asm.pointerToULongFunction;
            }
            SPIRVId address = asm.frameId;
            int alignment = 8;
            asm.currentBlockScope.add(new SPIRVOpLoad( //
                    typeId, //
                    loadID, //
                    address, //
                    new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(alignment))) //
            ));
            asm.prevId = loadID;
        }
    }

    @Opcode("LoadFromStackFrame")
    public static class LoadFromStackFrame extends AbstractInstruction {

        public static final LIRInstructionClass<LoadFromStackFrame> TYPE = LIRInstructionClass.create(LoadFromStackFrame.class);

        protected SPIRVKind type;
        protected SPIRVId address;
        protected int indexFromStackFrame;
        protected int parameterIndex;

        public LoadFromStackFrame(SPIRVKind type, int indexFromStackFrame, int parameterIndex) {
            super(TYPE);
            this.type = type;
            this.indexFromStackFrame = indexFromStackFrame;
            this.parameterIndex = parameterIndex;
        }

        @Override
        protected void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
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
                    accessPTR, //
                    new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(alignment))) //
            ));
        }
    }

    @Opcode("AccessPointerChain")
    public static class AccessPointerChain extends AbstractInstruction {

        // IDEA:
        // Include a hashTable in the ASM that maps LIRInstructions with SPIRVId
        // SO each microInstruction receives the LIRInstructions to play with, and then
        // we can lookup the corresponding IDs in the hash table.

        public static final LIRInstructionClass<AccessPointerChain> TYPE = LIRInstructionClass.create(AccessPointerChain.class);

        int value;

        public AccessPointerChain(int value) {
            super(TYPE);
            this.value = value;

        }

        @Override
        protected void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            System.out.println("µIns AccessPointerChain ");
            SPIRVId newID = asm.module.getNextId();

            // Note, it does not have to be necessary the prev. operation.
            SPIRVId prev = asm.prevId;

            String values = String.valueOf(value);
            SPIRVId index = asm.constants.get(values);

            asm.currentBlockScope.add(new SPIRVOpInBoundsPtrAccessChain( //
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
            System.out.println("µIns EXPR emitCode generation ---?? ");
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
        protected AllocatableValue lhs;

        @Use
        protected Value index;

        public LoadStmt(AllocatableValue lhs) {
            super(TYPE);
            this.lhs = lhs;
        }

        @Override
        public void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

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
            System.out.println("EMIT STORE WITH ASM MODULE");
        }
    }

    @Opcode("Pragma")
    public static class PragmaExpr extends AbstractInstruction {

        public static final LIRInstructionClass<PragmaExpr> TYPE = LIRInstructionClass.create(PragmaExpr.class);

        @Use
        protected Value prg;

        public PragmaExpr(OCLLIROp prg) {
            super(TYPE);
            this.prg = prg;
        }

        @Override
        public void emitCode(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

        }
    }

}
