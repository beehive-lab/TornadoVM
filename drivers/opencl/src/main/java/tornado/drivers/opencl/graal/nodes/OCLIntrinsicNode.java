package tornado.drivers.opencl.graal.nodes;

import tornado.common.exceptions.TornadoInternalError;
import tornado.drivers.opencl.graal.compiler.OCLLIRGenerator;
import tornado.graal.nodes.Floatable;
import tornado.graal.nodes.vector.VectorKind;

import com.oracle.graal.api.meta.LIRKind;
import com.oracle.graal.api.meta.Value;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.lir.gen.ArithmeticLIRGenerator;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.FixedNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.BinaryNode;
import com.oracle.graal.nodes.calc.FloatingNode;
import com.oracle.graal.nodes.spi.ArithmeticLIRLowerable;
import com.oracle.graal.nodes.spi.NodeMappableLIRBuilder;


public class OCLIntrinsicNode {

	public enum FloatingOp {
		ATAN2, ATAN2PI, COPYSIGN, FDIM, FMA, FMAX, FMIN, FMOD, FRACT, FREXP, HYPOT, LDEXP, MAD, MAXMAG, MINMAG, MODF, NEXTAFTER, POW, POWN, POWR, REMAINDER, REMQUO, ROOTN, SINCOS
	}
	
	public enum IntegerOp {
		ATAN2, ATAN2PI, COPYSIGN, FDIM, FMA, FMAX, FMIN, FMOD, FRACT, FREXP, HYPOT, LDEXP, MAD, MAXMAG, MINMAG, MODF, NEXTAFTER, POW, POWN, POWR, REMAINDER, REMQUO, ROOTN, SINCOS
	}
	
	public enum GeometricOp {
		CROSS, DISTANCE, DOT, LENGTH, NORMALISE, FAST_DISTANCE, FAST_LENGTH, FAST_NORMALISE
	}
	
	@NodeInfo(nameTemplate = "{p#operation}")
	public static final class FixedBinaryGeometricOp extends FixedNode implements Floatable{
		public static final NodeClass<FixedBinaryGeometricOp>	TYPE	= NodeClass
				.create(FixedBinaryGeometricOp.class);
		private final GeometricOp								operation;
		private final VectorKind vectorKind;
		@Input private ValueNode x;
		@Input private ValueNode y;
		
		public FixedBinaryGeometricOp(VectorKind kind, GeometricOp op, ValueNode x, ValueNode y) {
			super(TYPE, StampFactory.forKind(kind.getElementKind()));
			this.vectorKind = kind;
			this.operation = op;
			this.x = x;
			this.y = y;
		}
		
		public FloatingNode asFloating(){
			return new BinaryGeometricOp(vectorKind, operation, x, y);
		}
	}
	
	@NodeInfo(nameTemplate = "{p#operation}")
	public static class BinaryGeometricOp extends BinaryNode implements ArithmeticLIRLowerable{
		public static final NodeClass<BinaryGeometricOp>	TYPE	= NodeClass
				.create(BinaryGeometricOp.class);
		protected final GeometricOp								operation;
		protected final VectorKind vectorKind;
		public BinaryGeometricOp(VectorKind kind, GeometricOp op, ValueNode x, ValueNode y) {
			super(TYPE, StampFactory.forKind(kind.getElementKind()), x, y);
			this.vectorKind = kind;
			this.operation = op;
		}
		@Override
		public Node canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
			return this;
		}
		@Override
		public Node canonical(CanonicalizerTool tool) {
			return this;
		}
		@Override
		public void generate(NodeMappableLIRBuilder builder, ArithmeticLIRGenerator lirGen) {
			final OCLLIRGenerator gen = (OCLLIRGenerator) lirGen;
			final Value x = builder.operand(getX());
			final Value y = builder.operand(getY());
			Value result = null;
			switch(operation){
				case CROSS:
					result = gen.emitGeometricCross(LIRKind.value(vectorKind.getElementKind()),x,y);
					break;
				case DISTANCE:
					break;
				case DOT:
					result = gen.emitGeometricDot(LIRKind.value(vectorKind.getElementKind()),x,y);
					break;
				case FAST_DISTANCE:
					break;
				case FAST_LENGTH:
					break;
				case FAST_NORMALISE:
					break;
				case LENGTH:
					break;
				case NORMALISE:
					break;
				default:
					break;
				
			}
			if(result == null)
				TornadoInternalError.unimplemented();
			
			//System.out.printf("result: kind=%s, kind=%s\n",result.getKind(), vectorKind.getElementKind());
			builder.setResult(this, result);
			
		}
		
	}
	
	
}
