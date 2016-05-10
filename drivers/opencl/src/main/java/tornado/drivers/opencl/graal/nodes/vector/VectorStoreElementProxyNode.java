package tornado.drivers.opencl.graal.nodes.vector;

import tornado.drivers.opencl.graal.lir.OCLAddressOps.OCLVectorElement;
import tornado.graal.nodes.vector.VectorKind;

import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.ResolvedJavaType;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Node.OptionalInput;
import com.oracle.graal.graph.spi.Canonicalizable;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import com.oracle.graal.nodes.type.StampTool;

/**
 * The {@code StoreIndexedNode} represents a write to an array element.
 */
@NodeInfo(nameTemplate = "Store .s{p#lane}")
public final class VectorStoreElementProxyNode extends FixedWithNextNode {

	public static final NodeClass<VectorStoreElementProxyNode>	TYPE	= NodeClass
																			.create(VectorStoreElementProxyNode.class);

	@Input
	ValueNode												value;
	
	@OptionalInput(InputType.Association)
	ValueNode												origin;
	@OptionalInput(InputType.Association)
	ValueNode												laneOrigin;

	protected final VectorKind								kind;

	public ValueNode value() {
		return value;
	}

	protected VectorStoreElementProxyNode(
			NodeClass<? extends VectorStoreElementProxyNode> c,
			VectorKind kind,
			ValueNode origin,
			ValueNode lane) {
		super(c, createStamp(origin, kind.getElementKind()));
		this.kind = kind;
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
			VectorKind vectorKind,
			ValueNode origin,
			ValueNode lane,
			ValueNode value) {
		this(TYPE, vectorKind, origin, lane);
		this.value = value;
	}

	protected static Stamp createStamp(ValueNode vector, Kind kind) {
		ResolvedJavaType type = (vector == null) ? null : StampTool.typeOrNull(vector);
		if (kind == Kind.Object && type != null) {
			return StampFactory.declaredTrusted(type.getComponentType());
		} else {
			return StampFactory.forKind(kind);
		}
	}

	@Override
	public boolean inferStamp() {
		return updateStamp(createStamp(origin, kind.getElementKind()));
	}

	public VectorKind getVectorKind() {
		return kind;
	}

	public boolean canResolve() {
		return ((origin != null && laneOrigin != null) && origin instanceof VectorValueNode && laneOrigin instanceof ConstantNode);
	}

	public ValueNode getOrigin() {
		return origin;
	}
	
	public void setOrigin(ValueNode value){
		origin = value;
	}
	
	public int getLane(){
//		System.out.printf("vector store proxy: this=%s, origin=%s\n",this,laneOrigin);
		return ((ConstantNode)laneOrigin).asJavaConstant().asInt();
	}

}
