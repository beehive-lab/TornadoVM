package tornado.graal.nodes.logic;

import com.oracle.graal.graph.IterableNodeType;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.Canonicalizable;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.nodeinfo.InputType;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.LogicNode;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.meta.Value;

@NodeInfo
public abstract class UnaryLogicalNode extends LogicNode implements IterableNodeType, Canonicalizable.Unary<LogicNode>, LogicalCompareNode {

    public static final NodeClass<UnaryLogicalNode> TYPE = NodeClass.create(UnaryLogicalNode.class);

    @Input(InputType.Condition)
    LogicNode value;

    protected UnaryLogicalNode(NodeClass<? extends UnaryLogicalNode> type, LogicNode value) {
        super(type);
        this.value = value;
    }

    @Override
    public final void generate(NodeLIRBuilderTool builder) {
        Value x = builder.operand(getValue());
        Value result = generate(builder.getLIRGeneratorTool(), x);
        builder.setResult(this, result);
    }

    abstract public Value generate(LIRGeneratorTool gen, Value x);

    @Override
    public Node canonical(CanonicalizerTool tool, LogicNode forValue) {
        return this;
    }

    @Override
    public LogicNode getValue() {
        return value;
    }

    @Override
    public LogicNode canonical(CanonicalizerTool tool) {
        return this;
    }

}
