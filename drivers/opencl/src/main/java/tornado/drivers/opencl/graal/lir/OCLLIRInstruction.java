package tornado.drivers.opencl.graal.lir;

import com.oracle.graal.api.meta.AllocatableValue;
import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.LIRKind;
import com.oracle.graal.api.meta.PlatformKind;
import com.oracle.graal.api.meta.Value;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.LIRInstructionClass;
import com.oracle.graal.lir.Opcode;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.asm.CompilationResultBuilder;
import tornado.drivers.opencl.graal.asm.OpenCLAssembler;
import tornado.drivers.opencl.graal.asm.OpenCLAssembler.OCLBinaryIntrinsic;
import tornado.drivers.opencl.graal.asm.OpenCLAssembler.OCLTernaryIntrinsic;
import tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;
import tornado.drivers.opencl.graal.lir.OCLUnary.MemoryAccess;
import tornado.drivers.opencl.graal.lir.OCLUnary.OCLAddressCast;

public class OCLLIRInstruction {
    
    protected static abstract class AbstractInstruction extends LIRInstruction implements
            OCLEmitable {
        
        protected AbstractInstruction(LIRInstructionClass<? extends AbstractInstruction> c) {
            super(c);
        }
        
        @Override
        public void emitCode(CompilationResultBuilder crb) {
            emitCode((OCLCompilationResultBuilder) crb);
        }
        
        public void emitCode(OCLCompilationResultBuilder crb) {
            emit(crb);
        }
        
        @Override
        public LIRKind getLIRKind() {
            return LIRKind.Illegal;
        }
        
        @Override
        public PlatformKind getPlatformKind() {
            return Kind.Illegal;
        }
        
        @Override
        public Kind getKind() {
            return Kind.Illegal;
        }
        
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
        public void emit(OCLCompilationResultBuilder crb) {
            final OpenCLAssembler asm = crb.getAssembler();
            asm.indent();
            asm.value(crb, lhs);
            asm.space();
            asm.assign();
            asm.space();
            asm.value(crb, rhs);
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
    
    @Opcode("LOAD")
    public static class LoadStmt extends AbstractInstruction {
        
        public static final LIRInstructionClass<LoadStmt> TYPE = LIRInstructionClass.create(LoadStmt.class);
        
        @Def
        protected Value lhs;
        @Use
        protected Value cast;
        @Use
        protected Value address;
        
        public LoadStmt(Value lhs, Value cast, Value address) {
            super(TYPE);
            this.lhs = lhs;
            this.cast = cast;
            this.address = address;
        }
        
        @Override
        public void emit(OCLCompilationResultBuilder crb) {
            final OpenCLAssembler asm = crb.getAssembler();
            asm.indent();
            asm.value(crb, lhs);
            asm.space();
            asm.assign();
            asm.space();
            asm.emit("*(");
            ((OCLAddressCast) cast).emit(crb);
            asm.space();
            asm.value(crb, address);
            asm.emit(")");
            asm.delimiter();
            asm.eol();
        }
        
        public Value getResult() {
            return lhs;
        }
        
        public Value getCast() {
            return cast;
        }
        
        public Value getAddress() {
            return address;
        }
    }
    
    @Opcode("VLOAD")
    public static class VectorLoadStmt extends AbstractInstruction {
        
        public static final LIRInstructionClass<VectorLoadStmt> TYPE = LIRInstructionClass.create(VectorLoadStmt.class);
        
        @Def
        protected Value lhs;
        @Use
        protected Value cast;
        @Use
        protected Value address;
        
        @Use
        protected Value index;
        
        protected OCLBinaryIntrinsic op;
        
        public VectorLoadStmt(Value lhs, OCLBinaryIntrinsic op, Value index, Value cast, Value address) {
            super(TYPE);
            this.lhs = lhs;
            this.cast = cast;
            this.address = address;
            this.op = op;
            this.index = index;
        }
        
        @Override
        public void emit(OCLCompilationResultBuilder crb) {
            final OpenCLAssembler asm = crb.getAssembler();
            asm.indent();
            asm.value(crb, lhs);
            asm.space();
            asm.assign();
            asm.space();
            asm.emit(op.toString());
            asm.emit("(");
            asm.value(crb, index);
            asm.emit(", ");
            ((OCLAddressCast) cast).emit(crb);
            asm.space();
            asm.value(crb, address);
            asm.emit(")");
            asm.delimiter();
            asm.eol();
        }
        
        public Value getResult() {
            return lhs;
        }
        
        public Value getCast() {
            return cast;
        }
        
        public Value getAddress() {
            return address;
        }
        
        public OCLBinaryIntrinsic getOp() {
            return op;
        }
    }
    
    @Opcode("STORE")
    public static class StoreStmt extends AbstractInstruction {
        
        public static final LIRInstructionClass<StoreStmt> TYPE = LIRInstructionClass.create(StoreStmt.class);
        
        @Use
        protected Value rhs;
        @Use
        protected Value cast;
        @Use
        protected Value address;
        
        public StoreStmt(Value cast, Value address, Value rhs) {
            super(TYPE);
            this.rhs = rhs;
            this.cast = cast;
            this.address = address;
        }
        
        @Override
        public void emit(OCLCompilationResultBuilder crb) {
            final OpenCLAssembler asm = crb.getAssembler();
            asm.indent();

            //asm.space();
            asm.emit("*(");
            ((OCLAddressCast) cast).emit(crb);
            asm.space();
            asm.value(crb, address);
            asm.emit(")");
            asm.space();
            asm.assign();
            asm.space();
            asm.value(crb, rhs);
            asm.delimiter();
            asm.eol();
        }
        
        public Value getRhs() {
            return rhs;
        }
        
        public Value getCast() {
            return cast;
        }
        
        public Value getAddress() {
            return address;
        }
    }
    
    @Opcode("VSTORE")
    public static class VectorStoreStmt extends AbstractInstruction {
        
        public static final LIRInstructionClass<VectorStoreStmt> TYPE = LIRInstructionClass.create(VectorStoreStmt.class);
        
        @Use
        protected Value rhs;
        @Use
        protected Value cast;
        @Use
        protected Value address;
        @Use
        protected Value index;
        
        protected OCLTernaryIntrinsic op;
        
        public VectorStoreStmt(OCLTernaryIntrinsic op, Value index, Value cast, Value address, Value rhs) {
            super(TYPE);
            this.rhs = rhs;
            this.cast = cast;
            this.address = address;
            this.op = op;
            this.index = index;
        }
        
        @Override
        public void emit(OCLCompilationResultBuilder crb) {
            final OpenCLAssembler asm = crb.getAssembler();
            asm.indent();

            //asm.space();
            asm.emit(op.toString());
            asm.emit("(");
            asm.value(crb, rhs);
            asm.emit(", ");
            asm.value(crb, index);
            asm.emit(", ");
            ((OCLAddressCast) cast).emit(crb);
            asm.space();
            if (address instanceof MemoryAccess) {
                ((MemoryAccess) address).emit(crb);
            } else if (address instanceof Variable) {
                asm.value(crb, address);
            }
            asm.emit(")");
            asm.delimiter();
            asm.eol();
        }
        
        public Value getRhs() {
            return rhs;
        }
        
        public Value getCast() {
            return cast;
        }
        
        public Value getAddress() {
            return address;
        }
        
        public Value getIndex() {
            return index;
        }
        
        public OCLTernaryIntrinsic getOp() {
            return op;
        }
    }
    
    @Opcode("EXPR")
    public static class ExprStmt extends AbstractInstruction {
        
        public static final LIRInstructionClass<ExprStmt> TYPE = LIRInstructionClass.create(ExprStmt.class);
        
        @Use
        protected Value expr;
        
        public ExprStmt(Value expr) {
            super(TYPE);
            this.expr = expr;
        }
        
        @Override
        public void emit(OCLCompilationResultBuilder crb) {
            final OpenCLAssembler asm = crb.getAssembler();
            asm.indent();
            asm.value(crb, expr);
            asm.delimiter();
            asm.eol();
        }
        
        public Value getExpr() {
            return expr;
        }
    }
    
}
