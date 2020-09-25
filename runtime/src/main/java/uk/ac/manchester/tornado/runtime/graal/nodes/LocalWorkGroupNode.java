package uk.ac.manchester.tornado.runtime.graal.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;

@NodeInfo(shortName = "LocalWorkGroupId")
public class LocalWorkGroupNode extends FixedWithNextNode implements Lowerable {

    @Input
    ValueNode object;
    @Node.Input
    protected ConstantNode index;
    public static final NodeClass<LocalWorkGroupNode> TYPE = NodeClass.create(LocalWorkGroupNode.class);

    public ValueNode object() {
        return this.object;
    }

    public LocalWorkGroupNode(ValueNode index, ConstantNode value) {
        super(TYPE, StampFactory.forInteger(32));
        this.object = index;
        this.index = value;
    }

    public ConstantNode getIndex() {
        return index;
    }

    @Override
    public void lower(LoweringTool loweringTool) {
        loweringTool.getLowerer().lower(this, loweringTool);
    }
}