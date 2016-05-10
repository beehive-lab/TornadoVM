package tornado.drivers.opencl.graal.nodes.vector;

import tornado.graal.nodes.vector.VectorKind;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.AccessIndexedNode;
import com.oracle.graal.nodes.type.*;

/**
 * The {@code VectorLoadNode} represents a vector-read from a set of contiguous elements of an
 * array.
 */
@NodeInfo(nameTemplate = "VectorLoad")
public class VectorLoadNode extends AccessIndexedNode {

	public static final NodeClass<VectorLoadNode>	TYPE	= NodeClass
																	.create(VectorLoadNode.class);

	private final VectorKind						vectorKind;

	/**
	 * Creates a new LoadIndexedNode.
	 *
	 * @param array
	 *            the instruction producing the array
	 * @param index
	 *            the instruction producing the index
	 * @param elementKind
	 *            the element type
	 */
	public VectorLoadNode(VectorKind vectorKind, ValueNode array, ValueNode index) {
		super(TYPE, createStamp(array, vectorKind.getElementKind()), array, index, vectorKind.getElementKind());
		this.vectorKind = vectorKind;
	}

	private static Stamp createStamp(ValueNode array, Kind kind) {
		ResolvedJavaType type = StampTool.typeOrNull(array);
		if (kind == Kind.Object && type != null) {
			return StampFactory.declaredTrusted(type.getComponentType());
		} else {
			return StampFactory.forKind(kind);
		}
	}

	@Override
	public boolean inferStamp() {
		return updateStamp(createStamp(array, vectorKind.getElementKind()));
	}

	public Node canonical(CanonicalizerTool tool) {
		return this;
	}

	public int length() {
		return vectorKind.getVectorLength();
	}

	public Kind elementType() {
		return vectorKind.getElementKind();
	}
	
	public VectorKind vectorKind() {
		return vectorKind;
	}
}
