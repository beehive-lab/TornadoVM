package tornado.drivers.opencl.graal.nodes.vector;

import tornado.common.exceptions.TornadoInternalError;
import tornado.drivers.opencl.graal.lir.OCLAddressOps.OCLVectorElement;
import tornado.graal.nodes.vector.VectorKind;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodeinfo.InputType;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.FloatingNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import com.oracle.graal.nodes.type.*;

/**
 * The {@code LoadIndexedNode} represents a read from an element of an array.
 */
@NodeInfo(nameTemplate = "Op .s{p#lane}")
public abstract class VectorElementOpNode extends FloatingNode implements LIRLowerable,Comparable<VectorElementOpNode>{
	public static final NodeClass<VectorElementOpNode>	TYPE	= NodeClass
			.create(VectorElementOpNode.class);
	
	@Input(InputType.Extension) VectorValueNode vector;
	
	protected final VectorKind kind;
    protected final int lane;
    
    protected VectorElementOpNode(NodeClass<? extends VectorElementOpNode> c, VectorKind kind, VectorValueNode vector, ValueNode lane) {
        super(c, createStamp(vector,kind.getElementKind()));
       this.kind = kind;
        this.vector = vector;
        this.lane=lane.asJavaConstant().asInt();
    }

    @Override
	public int compareTo(VectorElementOpNode o) {
		return Integer.compare(lane, o.lane);
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
	      return updateStamp(createStamp(vector, kind.getElementKind()));
	  }
	
	public int laneId() {
        return lane;
	}
	
	public VectorValueNode getVector(){
		return vector;
	}
	
	public VectorKind getVectorKind(){
		return kind;
	}
	
	
	@Override
	public void generate(NodeLIRBuilderTool gen) {	
		TornadoInternalError.guarantee(vector != null, "vector is null");
//		System.out.printf("vector = %s, origin=%s\n",vector,vector.getOrigin());
		Value targetVector = gen.operand(getVector());
		if(targetVector == null && vector.getOrigin() instanceof Invoke)
			targetVector = gen.operand(vector.getOrigin());
		
		TornadoInternalError.guarantee(targetVector != null, "vector is null 2");
		final OCLVectorElement element = new OCLVectorElement(kind.getElementKind(),targetVector,lane);
		gen.setResult(this, element);
		
	}

}

