package tornado.drivers.opencl.graal.nodes.logic;

import tornado.drivers.opencl.graal.asm.OpenCLAssembler.OCLUnaryOp;
import tornado.drivers.opencl.graal.lir.OCLUnary;
import tornado.graal.nodes.logic.UnaryLogicalNode;

import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.LIRKind;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.LogicNode;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;

@NodeInfo(shortName = "!")
public class LogicalNotNode extends UnaryLogicalNode {

	public static final NodeClass<LogicalNotNode> TYPE = NodeClass.create(LogicalNotNode.class);
	
	public LogicalNotNode(LogicNode value){
		super(TYPE, value);
	}
	
	@Override
	public void generate(NodeLIRBuilderTool gen) {
		gen.setResult(this, new OCLUnary.Expr(OCLUnaryOp.LOGICAL_NOT, LIRKind.value(Kind.Boolean), gen.operand(getValue())));
		
	}
	
	
}
