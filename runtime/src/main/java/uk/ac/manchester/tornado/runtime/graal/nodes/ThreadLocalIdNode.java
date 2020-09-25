package uk.ac.manchester.tornado.runtime.graal.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;

@NodeInfo(shortName = "LocalThreadId")
public class ThreadLocalIdNode extends FixedWithNextNode implements Lowerable {

    @Input
    ValueNode object;
    private final int dimension;
    public static final NodeClass<ThreadLocalIdNode> TYPE = NodeClass.create(ThreadLocalIdNode.class);

    public ValueNode object() {
        return this.object;
    }

    public ThreadLocalIdNode(ValueNode index, int dimension) {
        super(TYPE, StampFactory.forInteger(32));
        this.object = index;
        this.dimension = dimension;
    }

    public int getDimension() {
        return dimension;
    }

    @Override
    public void lower(LoweringTool loweringTool) {
        loweringTool.getLowerer().lower(this, loweringTool);
    }
}