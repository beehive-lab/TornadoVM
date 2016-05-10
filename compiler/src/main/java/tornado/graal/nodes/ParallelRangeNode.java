package tornado.graal.nodes;

import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.InputType;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ValueNode;

@NodeInfo(nameTemplate = "Range")
public class ParallelRangeNode extends AbstractParallelNode {
	public static final NodeClass<ParallelRangeNode>	TYPE	= NodeClass
															.create(ParallelRangeNode.class);

	@Input(InputType.Association) private ParallelOffsetNode offset;
	@Input(InputType.Association) private ParallelStrideNode stride;
	
	public ParallelRangeNode(int index, ValueNode range, ParallelOffsetNode offset, ParallelStrideNode stride) {
		super(TYPE,index,range);
		this.offset = offset;
		this.stride = stride;
	}
	
	public ParallelOffsetNode offset(){
		return offset;
	}
	
	public ParallelStrideNode stride(){
		return stride;
	}

}
