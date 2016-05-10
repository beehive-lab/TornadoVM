package tornado.drivers.opencl.graal.lir;

import tornado.drivers.opencl.graal.asm.OpenCLAssembler;
import tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;

import com.oracle.graal.api.meta.AllocatableValue;
import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.LIRKind;
import com.oracle.graal.api.meta.PlatformKind;
import com.oracle.graal.api.meta.Value;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.LIRInstructionClass;
import com.oracle.graal.lir.Opcode;
import com.oracle.graal.lir.asm.CompilationResultBuilder;

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

		@Def protected AllocatableValue lhs;
		@Use protected Value rhs;
		
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
		
		public AllocatableValue getResult(){
			return lhs;
		}
		
		public Value getExpr(){
			return rhs;
		}
	}
	
	@Opcode("STORE")
	public static class StoreStmt extends AbstractInstruction {
		
		 public static final LIRInstructionClass<StoreStmt> TYPE = LIRInstructionClass.create(StoreStmt.class);

		@Def protected Value lhs;
		@Use protected Value rhs;
		
		public StoreStmt(Value lhs, Value rhs) {
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
		
		public Value getResult(){
			return lhs;
		}
		
		public Value getExpr(){
			return rhs;
		}
	}
	
	@Opcode("EXPR")
	public static class ExprStmt extends AbstractInstruction {
		
		 public static final LIRInstructionClass<ExprStmt> TYPE = LIRInstructionClass.create(ExprStmt.class);
		 
		@Use protected Value expr;
		
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
		
		public Value getExpr(){
			return expr;
		}
	}

}
