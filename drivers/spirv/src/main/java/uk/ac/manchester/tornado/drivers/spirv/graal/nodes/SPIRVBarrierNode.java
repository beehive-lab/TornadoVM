package uk.ac.manchester.tornado.drivers.spirv.graal.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.memory.MemoryKill;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIRStmt;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVUnary;

/**
 * Instruction: OpMemoryBarrier
 */
@NodeInfo
public class SPIRVBarrierNode extends FixedWithNextNode implements LIRLowerable, MemoryKill {

    public static final NodeClass<SPIRVBarrierNode> TYPE = NodeClass.create(SPIRVBarrierNode.class);

    public enum SPIRVMemFenceFlags {
        GLOBAL(528), //
        LOCAL(252); //

        private int semantics;

        SPIRVMemFenceFlags(int semantics) {
            this.semantics = semantics;
        }

        public int getSemantics() {
            return semantics;
        }
    }

    private final SPIRVMemFenceFlags BARRIER_TYPE;

    public SPIRVBarrierNode(SPIRVMemFenceFlags flags) {
        super(TYPE, StampFactory.forVoid());
        this.BARRIER_TYPE = flags;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        generator.getLIRGeneratorTool().append(new SPIRVLIRStmt.ExprStmt(new SPIRVUnary.Barrier(BARRIER_TYPE)));
    }
}
