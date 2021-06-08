package uk.ac.manchester.tornado.drivers.spirv.graal.nodes.vector;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;

import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.vector.VectorValueNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVStampFactory;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;

/**
 * The {@code StoreIndexedNode} represents a write to an array element.
 */
@NodeInfo(nameTemplate = "Store .s{p#lane}")
public class VectorStoreElementProxyNode extends FixedWithNextNode implements Canonicalizable {

    public static final NodeClass<VectorStoreElementProxyNode> TYPE = NodeClass.create(VectorStoreElementProxyNode.class);

    @Input
    ValueNode value;

    @OptionalInput(InputType.Association)
    ValueNode origin;
    @OptionalInput(InputType.Association)
    ValueNode laneOrigin;

    protected VectorStoreElementProxyNode(NodeClass<? extends VectorStoreElementProxyNode> c, SPIRVKind kind, ValueNode origin, ValueNode lane) {
        super(c, SPIRVStampFactory.getStampFor(kind));
        this.origin = origin;
        this.laneOrigin = lane;
    }

    public VectorStoreElementProxyNode(SPIRVKind kind, ValueNode origin, ValueNode lane, ValueNode value) {
        this(TYPE, kind, origin, lane);
        this.value = value;
    }

    public ValueNode value() {
        return value;
    }

    public boolean canResolve() {
        return ((origin != null && laneOrigin != null) && origin instanceof VectorValueNode && laneOrigin instanceof ConstantNode
                && ((VectorValueNode) origin).getOCLKind().getVectorLength() > laneOrigin.asJavaConstant().asInt());
    }

    public boolean tryResolve() {
        if (canResolve()) {
            /*
             * If we can resolve this node properly, this operation should be applied to the
             * vector node and this node should be discarded.
             */
            final VectorValueNode vector = (VectorValueNode) origin;
            vector.setElement(laneOrigin.asJavaConstant().asInt(), value);
            clearInputs();
            return true;
        } else {
            return false;
        }

    }

    @Override
    public boolean inferStamp() {
        return true;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (tryResolve()) {
            return null;
        } else {
            return this;
        }
    }
}
