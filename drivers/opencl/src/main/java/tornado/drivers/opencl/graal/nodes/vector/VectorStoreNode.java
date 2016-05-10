package tornado.drivers.opencl.graal.nodes.vector;

import tornado.graal.nodes.vector.VectorKind;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.AccessIndexedNode;

/**
 * The {@code VectorStoreNode} represents a vector-write to contiguous set of array elements.
 */
@NodeInfo(nameTemplate = "VectorStore")
public final class VectorStoreNode extends AccessIndexedNode {

    public static final NodeClass<VectorStoreNode> TYPE = NodeClass.create(VectorStoreNode.class);
    
    @Input ValueNode value;
    
    private final VectorKind vectorKind;
    
    public boolean hasSideEffect() {
        return true;
    }

    public ValueNode value() {
        return value;
    }

    public VectorStoreNode(ValueNode array, ValueNode index, VectorValueNode value) {
        this(value.getVectorKind(), array, index, value);
    }
    
    public VectorStoreNode(VectorKind vectorKind, ValueNode array, ValueNode index, ValueNode value){
    	super(TYPE, StampFactory.forVoid(), array, index, vectorKind.getElementKind());
    	this.vectorKind = vectorKind;
    	this.value = value;
    }

    public int length() {
        return vectorKind.getVectorLength();
    }
    
    public Kind elementKind() {
        return vectorKind.getElementKind();
    }

	public VectorKind vectorKind() {
		return vectorKind;
	}

}