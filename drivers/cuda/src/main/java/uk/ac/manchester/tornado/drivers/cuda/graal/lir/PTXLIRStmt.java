package uk.ac.manchester.tornado.drivers.cuda.graal.lir;

import jdk.vm.ci.meta.*;
import org.graalvm.compiler.lir.*;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.PTXCompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXUnary.PTXAddressCast;

import static uk.ac.manchester.tornado.drivers.cuda.graal.PTXArchitecture.paramSpace;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler.PTXBinaryIntrinsic;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssemblerConstants.*;

public class PTXLIRStmt {

    protected static abstract class AbstractInstruction extends LIRInstruction {
        protected AbstractInstruction(LIRInstructionClass<? extends AbstractInstruction> c) {
            super(c);
        }

        @Override
        public final void emitCode(CompilationResultBuilder crb) {
            emitCode((PTXCompilationResultBuilder) crb, (PTXAssembler) crb.asm);
        }

        public abstract void emitCode(PTXCompilationResultBuilder crb, PTXAssembler asm);
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
        public void emitCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            if (rhs instanceof PTXLIROp) {
                ((PTXLIROp) rhs).emit(crb, asm, (Variable) lhs);
            }
            else {
                PTXKind lhsKind = (PTXKind) lhs.getPlatformKind();
                PTXKind rhsKind = (PTXKind) rhs.getPlatformKind();

                asm.emitSymbol(TAB);
                if (lhsKind == rhsKind) {
                    asm.emit("mov." + lhsKind.toString());
                }
                else {
                    asm.emit("cvt.");
                    asm.emit(lhsKind.toString());
                    asm.emitSymbol(DOT);
                    asm.emit(rhsKind.toString());
                }
                asm.emitSymbol(TAB);
                asm.emitValue(lhs);
                asm.emitSymbol(", ");
                asm.emitValue(rhs);
            }
            asm.delimiter();
            asm.eol();
        }

        public AllocatableValue getResult() {
            return lhs;
        }

        public Value getExpr() {
            return rhs;
        }
    }

    @Opcode("EXPR")
    public static class ExprStmt extends AbstractInstruction {
        public static final LIRInstructionClass<ExprStmt> TYPE = LIRInstructionClass.create(ExprStmt.class);

        @Use
        protected Value expr;

        public ExprStmt(PTXLIROp expr) {
            super(TYPE);
            this.expr = expr;
        }

        @Override
        public void emitCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            if (expr instanceof PTXLIROp) {
                ((PTXLIROp) expr).emit(crb, asm, null);
            } else {
                asm.emitValue(expr);
            }
            asm.delimiter();
            asm.eol();
        }

        public Value getExpr() {
            return expr;
        }
    }

    @Opcode("LOAD")
    public static class LoadStmt extends AbstractInstruction {
        public static final LIRInstructionClass<LoadStmt> TYPE = LIRInstructionClass.create(LoadStmt.class);

        @Use
        protected Variable dest;

        @Use
        PTXUnary.MemoryAccess address;

        @Use
        protected ConstantValue index;

        public LoadStmt(PTXUnary.MemoryAccess address, Variable dest) {
            super(TYPE);

            this.dest = dest;
            this.address = address;
        }

        public LoadStmt(PTXUnary.MemoryAccess address, Variable dest, ConstantValue index) {
            super(TYPE);

            this.address = address;
            this.dest = dest;
            this.index = index;
        }

        @Override
        public void emitCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            //ld.u64 	%rd9, [%rd8];
            asm.emitSymbol(TAB);
            asm.emit("ld.");
            if (address.getBase() == paramSpace) {
                asm.emit(address.getBase().memorySpace.name());
                asm.emitSymbol(DOT);
            }
            asm.emit(dest.getPlatformKind().toString());
            asm.emitSymbol(TAB);

            asm.emitValue(dest);
            asm.emitSymbol(COMMA);
            asm.space();
            address.emit(crb, asm, null);
            if (index != null) {
                asm.emitConstant(index);
            }
            asm.delimiter();
            asm.eol();
        }
    }

    @Opcode("STORE")
    public static class StoreStmt extends AbstractInstruction {

        public static final LIRInstructionClass<StoreStmt> TYPE = LIRInstructionClass.create(StoreStmt.class);

        @Use
        protected Value rhs;
        @Use
        protected PTXUnary.MemoryAccess address;
        @Use
        protected Value index;

        public StoreStmt(PTXUnary.MemoryAccess address, Value rhs) {
            super(TYPE);
            this.rhs = rhs;
            this.address = address;
        }

        public StoreStmt(PTXUnary.MemoryAccess address, Value rhs, Value index) {
            super(TYPE);
            this.rhs = rhs;
            this.address = address;
            this.index = index;
        }

        public void emitNormalCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {

            // st.global.u32 		[%rd19], %r10;
            asm.emitSymbol(TAB);
            asm.emit("st.");
            asm.emit(address.getBase().memorySpace.name());
            asm.emitSymbol(DOT);
            asm.emit(rhs.getPlatformKind().toString());
            asm.emitSymbol(TAB);

            address.emit(crb, asm, null);
            asm.emitSymbol(COMMA);
            asm.space();

            asm.emitValue(rhs);
            asm.delimiter();
            asm.eol();
        }

        @Override
        public void emitCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            emitNormalCode(crb, asm);
        }

        public Value getRhs() {
            return rhs;
        }

        public PTXUnary.MemoryAccess getAddress() {
            return address;
        }
    }

    @Opcode("VLOAD")
    public static class VectorLoadStmt extends AbstractInstruction {

        public static final LIRInstructionClass<VectorLoadStmt> TYPE = LIRInstructionClass.create(VectorLoadStmt.class);

        @Def
        protected AllocatableValue lhs;
        @Use
        protected PTXAddressCast cast;
        @Use
        protected PTXUnary.MemoryAccess address;

        @Use
        protected Value index;

        protected PTXBinaryIntrinsic op;

        public VectorLoadStmt(AllocatableValue lhs,
                              PTXBinaryIntrinsic op,
                              Value index,
                              PTXAddressCast cast,
                              PTXUnary.MemoryAccess address) {
            super(TYPE);
            this.lhs = lhs;
            this.cast = cast;
            this.address = address;
            this.op = op;
            this.index = index;
        }

        @Override
        public void emitCode(PTXCompilationResultBuilder crb, PTXAssembler asm) {
            asm.emitValue(lhs);
            asm.space();
            asm.assign();
            asm.space();
            asm.emit(op.toString());
            asm.emit("(");
            asm.emitValue(index);
            asm.emit(", ");
            cast.emit(crb, asm, null);
            asm.space();
            address.emit(crb, asm, null);
            asm.emit(")");
            asm.delimiter();
            asm.eol();
        }

        public Value getResult() {
            return lhs;
        }

        public PTXAddressCast getCast() {
            return cast;
        }

        public PTXUnary.MemoryAccess getAddress() {
            return address;
        }

        public PTXBinaryIntrinsic getOp() {
            return op;
        }
    }
}
