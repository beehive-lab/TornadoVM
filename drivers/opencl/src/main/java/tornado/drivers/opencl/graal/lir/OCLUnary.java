package tornado.drivers.opencl.graal.lir;

import tornado.drivers.opencl.graal.asm.OpenCLAssembler;
import tornado.drivers.opencl.graal.asm.OpenCLAssembler.OCLMemorySpace;
import tornado.drivers.opencl.graal.asm.OpenCLAssembler.OCLUnaryOp;
import tornado.drivers.opencl.graal.backend.OCLBackend;
import tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;

import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.LIRKind;
import com.oracle.graal.api.meta.PlatformKind;
import com.oracle.graal.api.meta.Value;
import com.oracle.graal.lir.Opcode;
import com.oracle.graal.lir.LIRInstruction.Use;

public class OCLUnary {

	/**
     * Abstract operation which consumes one inputs
     */
    protected static class UnaryConsumer implements OCLEmitable {
       
    	protected final Kind kind;
        protected final LIRKind lirKind;
        
        @Opcode protected final OCLUnaryOp opcode;

        @Use protected Value value;

        protected UnaryConsumer(OCLUnaryOp opcode, Kind kind, LIRKind lirKind,  Value value) {
        	this.opcode = opcode;
        	this.kind = kind;
            this.lirKind = lirKind;
            this.value = value;
        }
        
        public UnaryConsumer(OCLUnaryOp opcode, LIRKind lirKind,  Value value) {
            this(opcode,Kind.Illegal,lirKind,value);
        }
        
        public UnaryConsumer(OCLUnaryOp opcode, Kind kind,  Value value) {
            this(opcode,kind, LIRKind.value(kind),value);
        }

        public Value getValue(){
        	return value;
        }
        
        public OCLUnaryOp getOpcode(){
        	return opcode;
        }
        
		@Override
		public Kind getKind() {
			return kind;
		}

		@Override
		public LIRKind getLIRKind() {
			return lirKind;
		}

		@Override
		public PlatformKind getPlatformKind() {
			return lirKind.getPlatformKind();
		}

		@Override
		public void emit(OCLCompilationResultBuilder crb) {
			opcode.emit(crb, value);		
		}

		public String toString(){
			return String.format("%s %s",opcode.toString(),value);
		}
      
    }

    public static class Expr extends UnaryConsumer {

		public Expr(OCLUnaryOp opcode, LIRKind lirKind, Value value) {
			super(opcode, lirKind, value);
		}
		
		public Expr(OCLUnaryOp opcode, Kind kind, Value value) {
			super(opcode, kind, value);
		}
    	
    }
    
    public static class Intrinsic extends UnaryConsumer {

		public Intrinsic(OCLUnaryOp opcode, LIRKind lirKind, Value value) {
			super(opcode, lirKind, value);
		}
		
		public Intrinsic(OCLUnaryOp opcode, Kind kind, Value value) {
			super(opcode, kind, value);
		}

		public String toString(){
			return String.format("%s(%s)",opcode.toString(),value);
		}
      
    }
    
    public static class FloatCast extends UnaryConsumer {
    	public FloatCast(OCLUnaryOp opcode, LIRKind lirKind, Value value) {
			super(opcode, lirKind, value);
		}
		
		public FloatCast(OCLUnaryOp opcode, Kind kind, Value value) {
			super(opcode, kind, value);
		}
		
		@Override
		public void emit(OCLCompilationResultBuilder crb) {
			final OpenCLAssembler asm = crb.getAssembler();
			asm.emit("isnan(");
			asm.value(crb,value);
			asm.emit(")? 0 : ");
			opcode.emit(crb, value);
			asm.delimiter();
			asm.eol();
		}

		public String toString(){
			return String.format("isnan(%s) ? 0 : %s %s",value,opcode.toString(),value);
		}
    }
    
    public static class MemoryAccess extends UnaryConsumer {
    	
    	private PlatformKind accessKind = Kind.Illegal;
    	
    	public MemoryAccess(OCLMemorySpace opcode, Value value) {
			super(opcode, Kind.Illegal, value);
		}
		
		@Override
		public void emit(OCLCompilationResultBuilder crb) {
			crb.getAssembler().emit(toString());		
		}
		
		public void setKind(PlatformKind kind){
			this.accessKind = kind;
		}
		
		public String toValueString(OpenCLAssembler asm){
			return String.format("(%s %s *) %s",opcode.toString(), OCLBackend.platformKindToOpenCLKind(accessKind),asm.toString(value));
		}

		public String toString(){
			return String.format("(%s %s *) %s",opcode.toString(), OCLBackend.platformKindToOpenCLKind(accessKind),value);
		}
    }
}
