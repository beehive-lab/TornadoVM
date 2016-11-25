package tornado.graal.nodes;

import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ValueNode;

@NodeInfo(nameTemplate = "Offset")
public class ParallelOffsetNode extends AbstractParallelNode {

    public static final NodeClass<ParallelOffsetNode> TYPE = NodeClass
            .create(ParallelOffsetNode.class);

    public ParallelOffsetNode(int index, ValueNode offset) {
        super(TYPE, index, offset);
    }

}
