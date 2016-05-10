package tornado.drivers.opencl.graal.lir;

import tornado.drivers.opencl.graal.asm.OpenCLAssembler.OCLNullaryIntrinsic;
import tornado.drivers.opencl.graal.asm.OpenCLAssembler.OCLNullaryOp;
import tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;

import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.LIRKind;
import com.oracle.graal.api.meta.PlatformKind;
import com.oracle.graal.api.meta.Value;
import com.oracle.graal.lir.Opcode;

public class OCLNullary {

	/**
     * Abstract operation which consumes no inputs
     */
    protected static class NullaryConsumer implements OCLEmitable {
       
    	protected final Kind kind;
        protected final LIRKind lirKind;
        
        @Opcode protected final OCLNullaryOp opcode;

        protected NullaryConsumer(OCLNullaryOp opcode, Kind kind, LIRKind lirKind){
        	this.opcode = opcode;
        	this.kind = kind;
            this.lirKind = lirKind;
        }
        
        public NullaryConsumer(OCLNullaryOp opcode, Kind kind) {
            this(opcode,kind,LIRKind.value(kind));
        }

        public NullaryConsumer(OCLNullaryOp opcode, LIRKind lirKind) {
           this(opcode,Kind.Illegal,lirKind);
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
			opcode.emit(crb);		
		}

		public String toString(){
			return String.format("%s",opcode.toString());
		}
    }
    
    public static class Expr extends NullaryConsumer {

		public Expr(OCLNullaryOp opcode, LIRKind lirKind) {
			super(opcode, lirKind);
		}
		
		public Expr(OCLNullaryOp opcode, Kind kind) {
			super(opcode, kind);
		}
    	
    }
    
    public static class Intrinsic extends NullaryConsumer {

		public Intrinsic(OCLNullaryIntrinsic opcode, LIRKind lirKind) {
			super(opcode, lirKind);
		}
		
		public Intrinsic(OCLNullaryOp opcode, Kind kind, Value value) {
			super(opcode, kind);
		}
		
		@Override
		public void emit(OCLCompilationResultBuilder crb) {
			opcode.emit(crb);
			crb.getAssembler().emit("()");
		}
    	
    }
	
}
