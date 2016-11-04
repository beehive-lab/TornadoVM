package tornado.drivers.opencl.graal.nodes.vector;

import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ValueNode;
import tornado.drivers.opencl.graal.OCLStamp;

/**
 * The {@code LoadIndexedNode} represents a read from an element of an array.
 */
@NodeInfo(nameTemplate = "Load .s{p#lane}")
public class VectorLoadElementNode extends VectorElementOpNode{

    public static final NodeClass<VectorLoadElementNode> TYPE = NodeClass.create(VectorLoadElementNode.class);

    public VectorLoadElementNode(ValueNode vector, ValueNode lane) {
        super(TYPE, ((OCLStamp)vector.stamp()).getOCLKind().getElementKind(), vector, lane);
    }

}

