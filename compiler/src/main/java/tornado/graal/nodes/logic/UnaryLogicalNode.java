package tornado.graal.nodes.logic;

import com.oracle.graal.graph.IterableNodeType;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.Canonicalizable;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.nodeinfo.InputType;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.LogicNode;

@NodeInfo
public abstract class UnaryLogicalNode extends LogicNode implements IterableNodeType, Canonicalizable.Unary<LogicNode>,LogicalCompareNode {
	public static final NodeClass<UnaryLogicalNode> TYPE = NodeClass.create(UnaryLogicalNode.class);
	
	@Input(InputType.Condition) LogicNode value;
	
	protected UnaryLogicalNode(NodeClass<? extends UnaryLogicalNode> type, LogicNode value){
		super(type);
		this.value = value;
	}

	@Override
	public Node canonical(CanonicalizerTool tool, LogicNode forValue) {
		return this;
	}

	@Override
	public LogicNode getValue() {
		return value;
	}

	@Override
	public Node canonical(CanonicalizerTool tool) {
		return this;
	}

}
