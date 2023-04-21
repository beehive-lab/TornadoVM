package uk.ac.manchester.tornado.drivers.opencl.graal.phases;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.phases.BasePhase;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoHighTierContext;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;

public class TornadoNativeTypeElimination extends BasePhase<TornadoHighTierContext> {

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {
        for (Node n : graph.getNodes().filter(LoadFieldNode.class)) {
            if (n.toString().contains("segment")) {
                for (Node in : n.inputs()) {
                    if (in instanceof PiNode) {
                        for (Node us : n.usages()) {
                            if (us instanceof OffsetAddressNode) {
                                us.replaceFirstInput(n, in);
                            }
                        }
                        break;
                    }
                }
                deleteFixed(n);
            }
        }
    }

    public static void deleteFixed(Node n) {
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
