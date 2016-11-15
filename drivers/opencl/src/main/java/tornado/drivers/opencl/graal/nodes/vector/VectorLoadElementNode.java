package tornado.drivers.opencl.graal.nodes.vector;

import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ValueNode;
import tornado.drivers.opencl.graal.lir.OCLKind;

/**
 * The {@code LoadIndexedNode} represents a read from an element of an array.
 */
@NodeInfo(nameTemplate = "Load .s{p#lane}")
public class VectorLoadElementNode extends VectorElementOpNode {

    public static final NodeClass<VectorLoadElementNode> TYPE = NodeClass.create(VectorLoadElementNode.class);

    public VectorLoadElementNode(OCLKind kind, ValueNode vector, ValueNode lane) {
        super(TYPE, kind, vector, lane);
    }

}
