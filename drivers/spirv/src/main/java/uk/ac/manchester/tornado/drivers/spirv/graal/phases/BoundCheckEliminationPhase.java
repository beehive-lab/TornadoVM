package uk.ac.manchester.tornado.drivers.spirv.graal.phases;

import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.GuardNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.AccessIndexedNode;
import org.graalvm.compiler.phases.Phase;

import jdk.vm.ci.meta.DeoptimizationReason;

public class BoundCheckEliminationPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {

        NodeIterable<GuardNode> guardNodes = graph.getNodes().filter(GuardNode.class);
        if (guardNodes.count() == 0) {
            return;
        }

        for (GuardNode guardNode : guardNodes) {
            DeoptimizationReason deoptReason = guardNode.getReason();
            if (deoptReason == DeoptimizationReason.BoundsCheckException) {
                if (!(guardNode.getAnchor() instanceof AccessIndexedNode)) {
                    guardNode.safeDelete();
                }
            }
        }
    }
}
