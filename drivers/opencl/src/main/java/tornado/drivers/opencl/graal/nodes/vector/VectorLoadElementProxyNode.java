package tornado.drivers.opencl.graal.nodes.vector;

import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.ResolvedJavaType;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.type.StampTool;
import tornado.common.exceptions.TornadoInternalError;
import tornado.drivers.opencl.graal.OCLStampFactory;
import tornado.drivers.opencl.graal.lir.OCLKind;

/**
 * The {@code StoreIndexedNode} represents a write to an array element.
 */
@Deprecated()
@NodeInfo(nameTemplate = "Load .s{p#lane}")
public final class VectorLoadElementProxyNode extends FixedWithNextNode {

	public static final NodeClass<VectorLoadElementProxyNode>	TYPE	= NodeClass
																			.create(VectorLoadElementProxyNode.class);
	
	@OptionalInput(InputType.Association)
	ValueNode												origin;
	@OptionalInput(InputType.Association)
	ValueNode												laneOrigin;

	protected final OCLKind								kind;

	
	protected VectorLoadElementProxyNode(
			NodeClass<? extends VectorLoadElementProxyNode> c,
			OCLKind kind,
			ValueNode origin,
			ValueNode lane) {
		super(c, OCLStampFactory.getStampFor(kind));
		this.kind = kind;
		this.origin = origin;
		this.laneOrigin = lane;
	}

	public VectorLoadElementNode tryResolve() {
		VectorLoadElementNode loadNode = null;
		if (canResolve()) {
				/*
				 * If we can resolve this node properly, this operation
				 * should be applied to the vector node and this node should be
				 * discarded.
				 */
				VectorValueNode vector = null;
//				System.out.printf("origin: %s\n",origin);
				if(origin instanceof VectorValueNode)
					vector = (VectorValueNode) origin;
//				else if(origin instanceof ParameterNode){
//					vector = origin.graph().addOrUnique(new VectorValueNode(kind, origin));
				else
					TornadoInternalError.shouldNotReachHere();
				
				loadNode = new VectorLoadElementNode(vector, laneOrigin);
				clearInputs();
		} 
		
		return loadNode;
	}

	public VectorLoadElementProxyNode(VectorValueNode vector, ValueNode lane, ValueNode value) {
		this(TYPE, vector.getOCLKind(), vector, lane);
	}

	public VectorLoadElementProxyNode(
			OCLKind vectorKind,
			ValueNode origin,
			ValueNode lane) {
		this(TYPE, vectorKind, origin, lane);
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
            return true;
//return updateStamp(createStamp(origin, kind.getElementKind()));
	}

	public OCLKind getOCLKind() {
		return kind;
	}

	public boolean canResolve() {
		return (isOriginResolvable() && laneOrigin != null && laneOrigin instanceof ConstantNode);
	}
	
	private final boolean isOriginResolvable(){
		return (origin != null && (origin instanceof VectorValueNode));
	}

	public ValueNode getOrigin() {
		return origin;
	}
	
	public void setOrigin(ValueNode value){
		updateUsages(origin, value);
		origin = value;
	}
	
	public int getLane(){
		return ((ConstantNode)laneOrigin).asJavaConstant().asInt();
	}

}
