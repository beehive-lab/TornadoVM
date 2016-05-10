package tornado.drivers.opencl.graal.nodes.logic;

import tornado.drivers.opencl.graal.asm.OpenCLAssembler.OCLBinaryOp;
import tornado.drivers.opencl.graal.lir.OCLBinary;
import tornado.graal.nodes.logic.BinaryLogicalNode;

import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.LIRKind;
import com.oracle.graal.api.meta.Value;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.LogicNode;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;

@NodeInfo(shortName = "||")
public class LogicalOrNode extends BinaryLogicalNode {

	public static final NodeClass<LogicalOrNode> TYPE = NodeClass.create(LogicalOrNode.class);
	
	public LogicalOrNode(LogicNode x, LogicNode y){
		super(TYPE, x,y);
	}
	
	@Override
	public void generate(NodeLIRBuilderTool gen) {
		final LIRGeneratorTool tool = gen.getLIRGeneratorTool();
		gen.setResult(this, generate(tool, gen.operand(getX()), gen.operand(getY())));
		
	}
	
	@Override
	public Value generate(LIRGeneratorTool gen, Value x, Value y) {
		return new OCLBinary.Expr(OCLBinaryOp.LOGICAL_OR,LIRKind.value(Kind.Boolean), x, y);
	}
	
}
