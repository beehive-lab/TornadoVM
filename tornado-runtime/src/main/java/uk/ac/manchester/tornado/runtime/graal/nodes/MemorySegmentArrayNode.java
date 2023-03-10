package uk.ac.manchester.tornado.runtime.graal.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;

@NodeInfo(shortName = "MemorySegmentArray")
public class MemorySegmentArrayNode extends FixedWithNextNode implements Lowerable {

    @Input
    ParameterNode segmentParameter;

    public static final NodeClass<MemorySegmentArrayNode> TYPE = NodeClass.create(MemorySegmentArrayNode.class);

    public MemorySegmentArrayNode(ParameterNode segmentParameter) {
        super(TYPE, StampFactory.object());
        this.segmentParameter = segmentParameter;
    }

    public ParameterNode getSegmentParameter() {
        return segmentParameter;
    }

    @Override
    public void lower(LoweringTool loweringTool) {
        loweringTool.getLowerer().lower(this, loweringTool);
    }
}