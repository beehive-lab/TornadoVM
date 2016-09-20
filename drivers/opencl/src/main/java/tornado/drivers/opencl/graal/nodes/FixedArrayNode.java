package tornado.drivers.opencl.graal.nodes;

import tornado.drivers.opencl.graal.asm.OpenCLAssembler.OCLBinaryTemplate;
import tornado.drivers.opencl.graal.lir.OCLBinary;
import tornado.drivers.opencl.graal.lir.OCLLIRInstruction;
import tornado.graal.nodes.ArrayKind;

import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.LIRKind;
import com.oracle.graal.api.meta.ResolvedJavaType;
import com.oracle.graal.api.meta.Value;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.calc.FloatingNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;

@NodeInfo
public class FixedArrayNode extends FloatingNode implements LIRLowerable {
	public static final NodeClass<FixedArrayNode>	TYPE	= NodeClass
			.create(FixedArrayNode.class);
	
	@Input protected ConstantNode length;
	protected final ArrayKind kind;
	
	public FixedArrayNode(ResolvedJavaType elementType, ConstantNode length) {
		super(TYPE, StampFactory.exactNonNull(elementType.getArrayClass()));
		this.length = length;
		this.kind = ArrayKind.fromKind(elementType.getKind());
	}

//	private  OCLBinaryOp resolveOp(){
//		switch(kind){
//			case INT:
//				return OCLUnaryTemplate.NEW_INT_ARRAY;
//			case LONG:
//				return OCLUnaryTemplate.NEW_LONG_ARRAY;
//			case FLOAT:
//				return OCLUnaryTemplate.NEW_FLOAT_ARRAY;
//			case DOUBLE:
//				return OCLUnaryTemplate.NEW_DOUBLE_ARRAY;
//			default:
//				TornadoInternalError.unimplemented("kind: "+ kind.toString());
//				break;
//		}
//		return null;
//	}
	
	@Override
	public void generate(NodeLIRBuilderTool gen) {	
		/*
		 * using as_T reinterprets the data as type T - consider: float x = (float) 1; and int value = 1, float x = &(value);
		 */
		final Value lengthValue = gen.operand(length);
		System.out.printf("gen operand: %s (%s)\n",lengthValue, lengthValue.getClass().getName());
		
		
		
		final Variable variable = gen.getLIRGeneratorTool().newVariable(LIRKind.value(Kind.Object));
		final OCLBinary.Expr declaration = new OCLBinary.Expr(OCLBinaryTemplate.NEW_ARRAY, Kind.Object, variable, lengthValue);
		
		
		
		final OCLLIRInstruction.ExprStmt expr = new OCLLIRInstruction.ExprStmt(declaration);
		
		System.out.printf("expr: %s\n",expr);
		
		gen.getLIRGeneratorTool().append(expr);
		
			gen.setResult(this,variable);
	}

}
