package tornado.drivers.opencl.graal.nodes.vector;

import com.oracle.graal.graph.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;

/**
 * The {@code LoadIndexedNode} represents a read from an element of an array.
 */
@NodeInfo(nameTemplate = "Load .s{p#lane}")
public class VectorLoadElementNode extends VectorElementOpNode{

    public static final NodeClass<VectorLoadElementNode> TYPE = NodeClass.create(VectorLoadElementNode.class);

    public VectorLoadElementNode(VectorValueNode vector, ValueNode lane) {
        super(TYPE, vector.getVectorKind(), vector, lane);
    }

}

