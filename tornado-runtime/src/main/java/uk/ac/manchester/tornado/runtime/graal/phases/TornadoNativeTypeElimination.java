package uk.ac.manchester.tornado.runtime.graal.phases;

import java.util.ArrayList;
import java.util.Optional;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.JavaReadNode;
import org.graalvm.compiler.nodes.extended.JavaWriteNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.phases.BasePhase;

import uk.ac.manchester.tornado.runtime.graal.nodes.WriteAtomicNode;

public class TornadoNativeTypeElimination extends BasePhase<TornadoHighTierContext> {

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {
        for (Node n : graph.getNodes().filter(LoadFieldNode.class)) {
            if (n.toString().contains("segment")) {
                if (!TornadoLocalArrayHeaderEliminator.nativeTypes) {
                    TornadoLocalArrayHeaderEliminator.nativeTypes = true;
                }

                // Remove FixedGuard nodes
                if (n.successors().filter(FixedGuardNode.class).isNotEmpty()) {
                    FixedGuardNode fx = n.successors().filter(FixedGuardNode.class).first();
                    removeFixedGuardNodes(fx, (LoadFieldNode) n);
                }

                for (Node in : n.inputs()) {
                    if (in instanceof PiNode) {
                        for (Node us : n.usages()) {
                            if (us instanceof OffsetAddressNode) { // USAGE IS PI
                                us.replaceFirstInput(n, in);
                            }
                        }
                        break;
                    }
                }
                deleteFixed(n);
            }
        }
        // check if reduction
        if (graph.getNodes().filter(WriteAtomicNode.class).isNotEmpty()) {
            TornadoLocalArrayHeaderEliminator.isReduction = true;
        }

    }

    public static void removeFixedGuardNodes(FixedGuardNode fx, LoadFieldNode lf) {
        ArrayList<Node> nodesToBeRemoved = new ArrayList<>();
        // identify the input nodes of fixedguardnode that need to be removed
        for (Node fxin : fx.inputs()) {
            if (fxin instanceof InstanceOfNode) {
                nodesToBeRemoved.add(fxin);
            }
        }

        // identify the usages that need to be removed
        for (Node fxuse : fx.usages()) {
            if (fxuse instanceof PiNode) {
                PiNode pi = (PiNode) fxuse;
                if (pi.usages().filter(OffsetAddressNode.class).isNotEmpty()) {
                    OffsetAddressNode off = pi.usages().filter(OffsetAddressNode.class).first();
                    // if this address node is used by a javaread/javawrite node
                    if (off.usages().filter(JavaReadNode.class).isNotEmpty() || off.usages().filter(JavaWriteNode.class).isNotEmpty() || off.usages().filter(WriteAtomicNode.class).isNotEmpty()) {
                        off.replaceFirstInput(pi, lf);
                        nodesToBeRemoved.add(pi);
                    }
                }
            }
        }

        for (int i = 0; i < nodesToBeRemoved.size(); i++) {
            nodesToBeRemoved.get(i).safeDelete();
        }

        deleteFixed(fx);

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
