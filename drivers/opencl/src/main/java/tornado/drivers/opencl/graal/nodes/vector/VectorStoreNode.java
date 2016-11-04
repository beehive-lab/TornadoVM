package tornado.drivers.opencl.graal.nodes.vector;

import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.java.AccessIndexedNode;
import jdk.vm.ci.meta.JavaKind;
import tornado.drivers.opencl.graal.OCLStamp;
import tornado.drivers.opencl.graal.OCLStampFactory;
import tornado.drivers.opencl.graal.lir.OCLKind;

/**
 * The {@code VectorStoreNode} represents a vector-write to contiguous set of
 * array elements.
 */
@NodeInfo(nameTemplate = "VectorStore")
public final class VectorStoreNode extends AccessIndexedNode {

    public static final NodeClass<VectorStoreNode> TYPE = NodeClass.create(VectorStoreNode.class);

    @Input
    ValueNode value;

    public boolean hasSideEffect() {
        return true;
    }

    public ValueNode value() {
        return value;
    }

    public VectorStoreNode(OCLKind vectorKind, ValueNode array, ValueNode index, ValueNode value) {
        super(TYPE, OCLStampFactory.getStampFor(vectorKind), array, index, JavaKind.Illegal);
        this.value = value;
    }

    @Override
    public JavaKind elementKind() {
        return ((OCLStamp) stamp()).getOCLKind().getElementKind().asJavaKind();
    }

}
