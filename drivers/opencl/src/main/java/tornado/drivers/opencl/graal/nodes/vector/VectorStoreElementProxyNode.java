package tornado.drivers.opencl.graal.nodes.vector;

import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Node.OptionalInput;
import com.oracle.graal.graph.spi.Canonicalizable;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import tornado.drivers.opencl.graal.OCLStampFactory;
import tornado.drivers.opencl.graal.lir.OCLKind;

/**
 * The {@code StoreIndexedNode} represents a write to an array element.
 */
@NodeInfo(nameTemplate = "Store .s{p#lane}")
public final class VectorStoreElementProxyNode extends FixedWithNextNode implements Canonicalizable{

    public static final NodeClass<VectorStoreElementProxyNode> TYPE = NodeClass
            .create(VectorStoreElementProxyNode.class);

    @Input
    ValueNode value;

    @OptionalInput(InputType.Association)
    ValueNode origin;
    @OptionalInput(InputType.Association)
    ValueNode laneOrigin;

    public ValueNode value() {
        return value;
    }

    protected VectorStoreElementProxyNode(
            NodeClass<? extends VectorStoreElementProxyNode> c,
            OCLKind kind,
            ValueNode origin,
            ValueNode lane) {
        super(c, OCLStampFactory.getStampFor(kind));
        this.origin = origin;
        this.laneOrigin = lane;

    }

    public boolean tryResolve() {
        if (canResolve()) {
            /*
             * If we can resolve this node properly, this operation
	     * should be applied to the vector node and this node should be
	     * discarded.
             */
            final VectorValueNode vector = (VectorValueNode) origin;
            vector.setElement(((ConstantNode) laneOrigin).asJavaConstant().asInt(), value);
            clearInputs();
            return true;
        } else {
            return false;
        }

    }

    public VectorStoreElementProxyNode(
            OCLKind kind,
            ValueNode origin,
            ValueNode lane,
            ValueNode value) {
        this(TYPE, kind, origin, lane);
        this.value = value;
    }

    @Override
    public boolean inferStamp() {
        return true;//updateStamp(createStamp(origin, kind.getElementKind()));
    }

    public boolean canResolve() {
        return ((origin != null && laneOrigin != null) && origin instanceof VectorValueNode && laneOrigin instanceof ConstantNode);
    }

    public ValueNode getOrigin() {
        return origin;
    }

    public void setOrigin(ValueNode value) {
        origin = value;
    }

    public int getLane() {
//		System.out.printf("vector store proxy: this=%s, origin=%s\n",this,laneOrigin);
        return ((ConstantNode) laneOrigin).asJavaConstant().asInt();
    }

    @Override
    public Node canonical(CanonicalizerTool ct) {
        if(tryResolve()){
            return null;
        } else {
            return this;
        }
    }

}
