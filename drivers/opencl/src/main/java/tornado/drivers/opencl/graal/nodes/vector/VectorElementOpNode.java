package tornado.drivers.opencl.graal.nodes.vector;

import com.oracle.graal.compiler.common.LIRKind;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.lir.ConstantValue;
import com.oracle.graal.nodeinfo.InputType;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.FloatingNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;
import tornado.drivers.opencl.graal.lir.OCLKind;
import tornado.drivers.opencl.graal.lir.OCLVectorElementSelect;

import static tornado.common.exceptions.TornadoInternalError.guarantee;

/**
 * The {@code LoadIndexedNode} represents a read from an element of an array.
 */
@NodeInfo(nameTemplate = "Op .s{p#lane}")
public abstract class VectorElementOpNode extends FloatingNode implements LIRLowerable, Comparable<VectorElementOpNode> {

    public static final NodeClass<VectorElementOpNode> TYPE = NodeClass
            .create(VectorElementOpNode.class);

    @Input(InputType.Extension)
    ValueNode vector;

    @Input
    ValueNode lane;

    protected final OCLKind oclKind;

    protected VectorElementOpNode(NodeClass<? extends VectorElementOpNode> c, OCLKind kind, ValueNode vector, ValueNode lane) {
        super(c, StampFactory.forKind(kind.asJavaKind()));
        this.oclKind = kind;
        this.vector = vector;
        this.lane = lane;
    }

    @Override
    public int compareTo(VectorElementOpNode o) {
        return Integer.compare(laneId(), o.laneId());
    }

    @Override
    public boolean inferStamp() {
        return true;
        //return updateStamp(createStamp(vector, kind.getElementKind()));
    }

    public int laneId() {
        return (lane instanceof ConstantNode) ? lane.asJavaConstant().asInt() : -1;
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
        final OCLVectorElementSelect element = new OCLVectorElementSelect(gen.getLIRGeneratorTool().getLIRKind(stamp), targetVector, new ConstantValue(LIRKind.value(OCLKind.INT), JavaConstant.forInt(laneId())));
        gen.setResult(this, element);

    }

}
