package uk.ac.manchester.tornado.drivers.spirv.graal.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

@NodeInfo
public class PragmaUnrollNode extends FixedWithNextNode implements LIRLowerable {

    @Successor
    LoopBeginNode loopBgNd;
    public static final NodeClass<PragmaUnrollNode> TYPE = NodeClass.create(PragmaUnrollNode.class);

    private int unroll;

    public PragmaUnrollNode(int unroll) {
        super(TYPE, StampFactory.forVoid());
        this.unroll = unroll;
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeLIRBuilderTool) {
        throw new RuntimeException("PRAGMA UNROLL NOT SUPPORTED YET");
    }
}
