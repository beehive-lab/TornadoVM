package tornado.drivers.opencl.graal.nodes;

import tornado.common.exceptions.TornadoInternalError;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLUnaryOp;
import tornado.drivers.opencl.graal.lir.OCLLIRInstruction;
import tornado.drivers.opencl.graal.lir.OCLUnary;

import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.LIRKind;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.FloatingNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;

@NodeInfo
public class CastNode extends FloatingNode implements LIRLowerable {
	public static final NodeClass<CastNode>	TYPE	= NodeClass
			.create(CastNode.class);
	
	@Input protected ValueNode value;
	protected final Kind kind;
	
	public CastNode(Kind kind, ValueNode value) {
		super(TYPE, StampFactory.forKind(kind));
		this.value = value;
		this.kind = kind;
	}

	private  OCLUnaryOp resolveOp(){
		switch(kind){
			case Int:
				return OCLUnaryOp.CAST_TO_INT;
			case Float:
				return OCLUnaryOp.CAST_TO_FLOAT;
			default:
				TornadoInternalError.unimplemented("kind: "+ kind.toString());
				break;
		}
		return null;
	}
	
	@Override
	public void generate(NodeLIRBuilderTool gen) {	
		/*
		 * using as_T reinterprets the data as type T - consider: float x = (float) 1; and int value = 1, float x = &(value);
		 */
		if(kind == Kind.Float){
			gen.setResult(this,new OCLUnary.Expr(resolveOp(), kind, gen.operand(value)));
		} else {
			
			final Variable result = gen.getLIRGeneratorTool().newVariable(LIRKind.value(Kind.Int));
			final OCLLIRInstruction.AssignStmt assign = new OCLLIRInstruction.AssignStmt(result, new OCLUnary.FloatCast(OCLUnaryOp.CAST_TO_INT, kind, gen.operand(value)));
			gen.getLIRGeneratorTool().append(assign);
			gen.setResult(this, result  );
		}
		
	}

}
