package uk.ac.manchester.tornado.drivers.opencl.graal.phases;

import java.util.Optional;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.BasePhase;

import uk.ac.manchester.tornado.runtime.graal.nodes.NewArrayNonVirtualizableNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.PanamaPrivateMemoryNode;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoHighTierContext;

public class TornadoPrivateArrayPiRemoval extends BasePhase<TornadoHighTierContext> {

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {
        for (NewArrayNonVirtualizableNode fixedArray : graph.getNodes().filter(NewArrayNonVirtualizableNode.class)) {
            if (fixedArray.successors().filter(PanamaPrivateMemoryNode.class).isNotEmpty()) {
                for (PiNode p : fixedArray.usages().filter(PiNode.class)) {
                    p.replaceAtUsages(fixedArray);
                    p.safeDelete();
                }
                PanamaPrivateMemoryNode panamaPrivateMemoryNode = fixedArray.successors().filter(PanamaPrivateMemoryNode.class).first();
                removeFixed(panamaPrivateMemoryNode);
            }
        }
    }

    public static void removeFixed(Node n) {
        if (!n.isDeleted()) {
            Node pred = n.predecessor();
            Node suc = n.successors().first();

            n.replaceFirstSuccessor(suc, null);
            n.replaceAtPredecessor(suc);
            pred.replaceFirstSuccessor(n, suc);

            for (Node us : n.usages()) {
                n.removeUsage(us);
            }
            n.clearInputs();

            n.safeDelete();
        }
    }

}
