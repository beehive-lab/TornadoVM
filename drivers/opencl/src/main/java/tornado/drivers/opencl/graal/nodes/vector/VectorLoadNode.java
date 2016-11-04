package tornado.drivers.opencl.graal.nodes.vector;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.AccessIndexedNode;
import tornado.drivers.opencl.graal.OCLStamp;
import tornado.drivers.opencl.graal.OCLStampFactory;
import tornado.drivers.opencl.graal.lir.OCLKind;

/**
 * The {@code VectorLoadNode} represents a vector-read from a set of contiguous
 * elements of an array.
 */
@NodeInfo(nameTemplate = "VectorLoad")
public class VectorLoadNode extends AccessIndexedNode {

    public static final NodeClass<VectorLoadNode> TYPE = NodeClass
            .create(VectorLoadNode.class);

    private final OCLKind kind;

    /**
     * Creates a new LoadIndexedNode.
     *
     * @param kind the element type
     * @param array the instruction producing the array
     * @param index the instruction producing the index
     */
    public VectorLoadNode(OCLKind kind, ValueNode array, ValueNode index) {
        super(TYPE, OCLStampFactory.getStampFor(kind), array, index, Kind.Illegal);
        this.kind = kind;
    }

    public Node canonical(CanonicalizerTool tool) {
        return this;
    }

    public int length() {
        return kind.getVectorLength();
    }

    public OCLKind elementType() {
        return kind.getElementKind();
    }

    public OCLKind vectorKind() {
        return kind;
    }

    @Override
    public Kind elementKind() {
        return ((OCLStamp) stamp()).getOCLKind().getElementKind().asJavaKind();
    }
}
