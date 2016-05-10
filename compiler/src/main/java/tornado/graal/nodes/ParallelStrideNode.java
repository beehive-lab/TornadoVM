package tornado.graal.nodes;

import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ValueNode;

@NodeInfo(nameTemplate = "Stride")
public class ParallelStrideNode extends AbstractParallelNode {
	public static final NodeClass<ParallelStrideNode>	TYPE	= NodeClass
															.create(ParallelStrideNode.class);
	
	public ParallelStrideNode(int index, ValueNode stride) {
		super(TYPE, index, stride);
	}
	
}
