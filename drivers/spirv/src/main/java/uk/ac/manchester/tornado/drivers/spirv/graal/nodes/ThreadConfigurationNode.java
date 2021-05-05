package uk.ac.manchester.tornado.drivers.spirv.graal.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

@NodeInfo
public class ThreadConfigurationNode extends FixedWithNextNode implements LIRLowerable {

    @Successor
    LoopBeginNode loopBegin;

    @Input
    LocalWorkGroupDimensionsNode localWork;

    public static final NodeClass<ThreadConfigurationNode> TYPE = NodeClass.create(ThreadConfigurationNode.class);

    public ThreadConfigurationNode(LocalWorkGroupDimensionsNode localWork) {
        super(TYPE, StampFactory.forVoid());
        this.localWork = localWork;
    }

    /**
     * This an empty implementation for generating LIR for this node. This is due to
     * a fix template used during code generation for this node.
     */
    @Override
    public void generate(NodeLIRBuilderTool nodeLIRBuilderTool) {
    }
}