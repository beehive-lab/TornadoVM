package tornado.drivers.opencl.graal.lir;

import tornado.drivers.opencl.graal.asm.OpenCLAssembler.OCLBinaryIntrinsic;
import tornado.drivers.opencl.graal.asm.OpenCLAssembler.OCLBinaryOp;
import tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;

import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.LIRKind;
import com.oracle.graal.api.meta.PlatformKind;
import com.oracle.graal.api.meta.Value;
import com.oracle.graal.lir.LIRInstruction.Use;
import com.oracle.graal.lir.Opcode;

public class OCLBinary {

	/**
     * Abstract operation which consumes two inputs
     */
    protected static class BinaryConsumer implements OCLEmitable {
       
    	protected final Kind kind;
        protected final LIRKind lirKind;
        
        @Opcode protected final OCLBinaryOp opcode;

        @Use protected Value x;
        @Use protected Value y;

        protected BinaryConsumer(OCLBinaryOp opcode, Kind kind, LIRKind lirKind, Value x, Value y){
        	this.opcode = opcode;
        	this.kind = kind;
            this.lirKind = lirKind;
            this.x = x;
            this.y = y;
        }
        
        public BinaryConsumer(OCLBinaryOp opcode, LIRKind lirKind,  Value x, Value y) {
            this(opcode,Kind.Illegal,lirKind,x,y);
        }
        
        public BinaryConsumer(OCLBinaryOp opcode, Kind kind,  Value x, Value y) {
            this(opcode,kind,LIRKind.value(kind),x,y);
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
			opcode.emit(crb, x, y);		
		}
		
		public Value getX(){
			return x;
		}
		
		public Value getY(){
			return y;
		}

		public String toString(){
			return String.format("%s %s %s",opcode.toString(),x,y);
		}
      
    }
    
    public static class Expr extends BinaryConsumer {

		public Expr(OCLBinaryOp opcode, LIRKind lirKind, Value x, Value y) {
			super(opcode, lirKind, x, y);
		}
		
		public Expr(OCLBinaryOp opcode, Kind kind, Value x, Value y) {
			super(opcode, kind, x, y);
		}
    	
    }
    
    /**
     * OpenCL intrinsic call which consumes two inputs
     */
    public static class Intrinsic extends BinaryConsumer {
 

        public Intrinsic(OCLBinaryIntrinsic opcode, LIRKind lirKind,  Value x, Value y) {
        	super(opcode, lirKind, x, y);
        }
        
        public Intrinsic(OCLBinaryIntrinsic opcode, Kind kind, Value x, Value y) {
			super(opcode, kind, x, y);
		}

		public String toString(){
			return String.format("%s(%s, %s)",opcode.toString(),x,y);
		}
      
    }
}
