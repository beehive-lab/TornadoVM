package uk.ac.manchester.tornado.drivers.spirv.graal.nodes.vector;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.spirv.common.SPIRVLogger;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVStamp;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVVectorElementSelect;

@NodeInfo(nameTemplate = "Op .s{p#lane}")
public class VectorElementOpNode extends FloatingNode implements LIRLowerable, Comparable<VectorElementOpNode> {

    public static final NodeClass<VectorElementOpNode> TYPE = NodeClass.create(VectorElementOpNode.class);

    @Input(InputType.Extension)
    ValueNode vector;

    @Input
    ValueNode lane;

    protected final SPIRVKind kind;

    public VectorElementOpNode(NodeClass<? extends FloatingNode> c, SPIRVKind kind, ValueNode vector, ValueNode lane) {
        super(c, StampFactory.forKind(kind.asJavaKind()));
        this.kind = kind;
        this.vector = vector;
        Stamp vectorStamp = vector.stamp(NodeView.DEFAULT);
        SPIRVKind vectorKind;
        if (vectorStamp instanceof SPIRVStamp) {
            final SPIRVStamp spirvStamp = (SPIRVStamp) vector.stamp(NodeView.DEFAULT);
            vectorKind = spirvStamp.getSPIRVKind();
            guarantee(vectorKind.isVector(), "Cannot apply vector operation to non-vector type: %s", vectorKind);
        } else if (vectorStamp instanceof ObjectStamp) {
            ObjectStamp objectStamp = (ObjectStamp) vector.stamp(NodeView.DEFAULT);
            if (objectStamp.type() != null) {
                vectorKind = SPIRVKind.fromResolvedJavaTypeToVectorKind(objectStamp.type());
                guarantee(vectorKind.isVector(), "Cannot apply vector operation to non-vector type: %s", vectorKind);
                guarantee(vectorKind.getVectorLength() >= laneId(), "Invalid lane %d on type %s", laneId(), kind);
            }
        } else {
            shouldNotReachHere("invalid type on vector operation: %s (stamp=%s (class=%s))", vector, vector.stamp(NodeView.DEFAULT), vector.stamp(NodeView.DEFAULT).getClass().getName());
        }

    }

    final public int laneId() {
        guarantee(lane instanceof ConstantNode, "Invalid lane: %s", lane);
        return (lane instanceof ConstantNode) ? lane.asJavaConstant().asInt() : -1;
    }

    @Override
    public int compareTo(VectorElementOpNode o) {
        return Integer.compare(laneId(), o.laneId());
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(StampFactory.forKind(kind.asJavaKind()));
    }

    public ValueNode getVector() {
        return vector;
    }

    public SPIRVKind getSPIRVKind() {
        return kind;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        guarantee(vector != null, "vector is null");
        Value targetVector = gen.operand(getVector());
        guarantee(targetVector != null, "vector is null");

        SPIRVLogger.traceBuildLIR("emitVectorElementOp: targetVector=%s, laneId=%d", targetVector, laneId());

        assert targetVector instanceof Variable;
        final SPIRVVectorElementSelect element = new SPIRVVectorElementSelect(LIRKind.value(targetVector.getPlatformKind()), (Variable) targetVector, laneId());
        gen.setResult(this, element);
    }
}
