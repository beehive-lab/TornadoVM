package uk.ac.manchester.tornado.drivers.spirv.graal.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.memory.MemoryKill;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

/**
 * Instruction: OpMemoryBarrier
 */
@NodeInfo
public class SPIRVBarrierNode extends FixedWithNextNode implements LIRLowerable, MemoryKill {

    public static final NodeClass<SPIRVBarrierNode> TYPE = NodeClass.create(SPIRVBarrierNode.class);

    public enum SPIRVMemFenceFlags {
        GLOBAL, //
        LOCAL; //
    }

    private final SPIRVMemFenceFlags flags;

    public SPIRVBarrierNode(SPIRVMemFenceFlags flags) {
        super(TYPE, StampFactory.forVoid());
        this.flags = flags;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        throw new RuntimeException("Operation not supported");
    }
}
