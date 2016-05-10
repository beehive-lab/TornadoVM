package tornado.graal.nodes;

import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.FloatingNode;

@NodeInfo
public abstract class AbstractParallelNode extends FloatingNode  implements Comparable<AbstractParallelNode> {
	

	public static final NodeClass<AbstractParallelNode>	TYPE	= NodeClass
																		.create(AbstractParallelNode.class);

	protected int										index;
	@Input protected ValueNode									value;

	protected AbstractParallelNode(NodeClass<? extends AbstractParallelNode> type, int index, ValueNode value) {
		super(type, StampFactory.forKind(Kind.Int));
		assert stamp != null;
		this.index = index;
		this.value = value;
	}

	public ValueNode value() {
		return value;
	}

	public int index() {
		return index;
	}
	
	@Override
	public int compareTo(AbstractParallelNode o) {
		return Integer.compare(index, o.index);
	}

}