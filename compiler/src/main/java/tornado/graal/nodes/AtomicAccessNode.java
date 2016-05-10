package tornado.graal.nodes;

import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.InputType;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.FloatingNode;

@NodeInfo(shortName="Atomic")
public class AtomicAccessNode extends FloatingNode {
	

	public static final NodeClass<AtomicAccessNode>	TYPE	= NodeClass
																		.create(AtomicAccessNode.class);

	@Input(InputType.Association) protected ValueNode									value;

	public AtomicAccessNode(ValueNode value) {
		super(TYPE, value.stamp());
		assert stamp != null;
		this.value = value;
	}

	public ValueNode value() {
		return value;
	}

}