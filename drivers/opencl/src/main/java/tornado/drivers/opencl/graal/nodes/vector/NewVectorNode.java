package tornado.drivers.opencl.graal.nodes.vector;

import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import tornado.drivers.opencl.graal.OCLStampFactory;
import tornado.drivers.opencl.graal.lir.OCLKind;

/**
 * The {@code VectorLoadNode} represents a vector-read from a set of contiguous
 * elements of an array.
 */
@NodeInfo(nameTemplate = "New Vector<{p#kind/s}>")
public class NewVectorNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<NewVectorNode> TYPE = NodeClass.create(NewVectorNode.class);

    private final OCLKind kind;

    /**
     * Creates a new LoadIndexedNode.
     *
     * @param array       the instruction producing the array
     * @param index       the instruction producing the index
     * @param elementKind the element type
     */
    public NewVectorNode(OCLKind kind) {
        super(TYPE, OCLStampFactory.getStampFor(kind));
        this.kind = kind;
    }

    public ValueNode length() {
        return ConstantNode.forInt(kind.getVectorLength());
    }

    public OCLKind elementKind() {
        return kind.getElementKind();
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        final LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Variable result = tool.newVariable(tool.getLIRKind(stamp));
        gen.setResult(this, result);
    }

}
