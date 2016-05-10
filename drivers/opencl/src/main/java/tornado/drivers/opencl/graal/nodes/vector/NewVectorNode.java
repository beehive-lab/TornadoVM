package tornado.drivers.opencl.graal.nodes.vector;

import tornado.graal.nodes.vector.VectorKind;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

/**
 * The {@code VectorLoadNode} represents a vector-read from a set of contiguous elements of an array.
 */
@NodeInfo(nameTemplate = "New Vector<{p#kind/s}>")
public class NewVectorNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<NewVectorNode> TYPE = NodeClass.create(NewVectorNode.class);

    private final VectorKind kind;
    
    /**
     * Creates a new LoadIndexedNode.
     *
     * @param array the instruction producing the array
     * @param index the instruction producing the index
     * @param elementKind the element type
     */
    public NewVectorNode(VectorKind kind) {
        super(TYPE, StampFactory.forVoid());
        this.kind = kind;
    }

    public ValueNode length() {
        return ConstantNode.forInt(kind.getVectorLength());
    }
    
    public Kind elementKind() {
        return kind.getElementKind();
    }

	@Override
	public void generate(NodeLIRBuilderTool gen) {
		final LIRGeneratorTool tool = gen.getLIRGeneratorTool();
		
		Variable result = tool.newVariable(LIRKind.value(kind));
		gen.setResult(this, result);
	}

}
