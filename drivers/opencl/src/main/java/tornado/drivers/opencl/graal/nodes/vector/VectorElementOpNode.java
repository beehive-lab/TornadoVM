package tornado.drivers.opencl.graal.nodes.vector;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.StampFactory;
import static com.oracle.graal.compiler.common.util.Util.guarantee;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodeinfo.InputType;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.FloatingNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import tornado.drivers.opencl.graal.lir.OCLAddressOps.OCLVectorElement;
import tornado.drivers.opencl.graal.lir.OCLKind;

/**
 * The {@code LoadIndexedNode} represents a read from an element of an array.
 */
@NodeInfo(nameTemplate = "Op .s{p#lane}")
public abstract class VectorElementOpNode extends FloatingNode implements LIRLowerable, Comparable<VectorElementOpNode> {

    public static final NodeClass<VectorElementOpNode> TYPE = NodeClass
            .create(VectorElementOpNode.class);

    @Input(InputType.Extension)
    ValueNode vector;

    protected final int lane;
    protected final OCLKind oclKind;

    protected VectorElementOpNode(NodeClass<? extends VectorElementOpNode> c, OCLKind kind, ValueNode vector, ValueNode lane) {
        super(c, StampFactory.forKind(kind.asJavaKind()));
        this.oclKind = kind;
        this.vector = vector;
        this.lane = lane.asJavaConstant().asInt();
    }

    @Override
    public int compareTo(VectorElementOpNode o) {
        return Integer.compare(lane, o.lane);
    }

    @Override
    public boolean inferStamp() {
        return true;
        //return updateStamp(createStamp(vector, kind.getElementKind()));
    }

    public int laneId() {
        return lane;
    }

    public ValueNode getVector() {
        return vector;
    }

    public OCLKind getOCLKind() {
        return oclKind;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        guarantee(vector != null, "vector is null");
//		System.out.printf("vector = %s, origin=%s\n",vector,vector.getOrigin());
        Value targetVector = gen.operand(getVector());
//        if (targetVector == null && vector.getOrigin() instanceof Invoke) {
//            targetVector = gen.operand(vector.getOrigin());
//        }

        guarantee(targetVector != null, "vector is null 2");
        final OCLVectorElement element = new OCLVectorElement(getOCLKind(), targetVector, lane);
        gen.setResult(this, element);

    }

}
