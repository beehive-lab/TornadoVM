package uk.ac.manchester.tornado.runtime.graal.phases;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.phases.BasePhase;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.runtime.graal.nodes.ThreadIdNode;

import java.util.ArrayList;

public class TornadoContextReplacement extends BasePhase<TornadoSketchTierContext> {

    private void introduceTornadoVMContext(StructuredGraph graph) {
        ArrayList<Node> nodesToBeRemoved = new ArrayList<>();
        graph.getNodes().filter(LoadFieldNode.class).forEach((node) -> {
            if (node instanceof LoadFieldNode) {
                String field = node.field().format("%H.%n");
                if (field.contains("threadId")) {
                    ThreadIdNode threadIdNode;
                    if (field.contains("threadIdx")) {
                        threadIdNode = new ThreadIdNode(node.getValue(), 0);
                    } else if (field.contains("threadIdy")) {
                        threadIdNode = new ThreadIdNode(node.getValue(), 1);
                    } else if (field.contains("threadIdz")) {
                        threadIdNode = new ThreadIdNode(node.getValue(), 2);
                    } else {
                        throw new TornadoRuntimeException("Unrecognized dimension");
                    }

                    for (Node n : node.successors()) {
                        for (Node input : n.inputs()) { // This should be NullNode
                            input.safeDelete();
                        }
                        for (Node usage : n.usages()) { // This should be PiNode
                            usage.safeDelete();
                        }
                        n.replaceAtPredecessor(n.successors().first());
                        n.safeDelete();
                    }

                    Node unboxNode = node.successors().first();
                    unboxNode.replaceAtUsages(node);

                    node.replaceFirstSuccessor(unboxNode, unboxNode.successors().first());
                    unboxNode.safeDelete();

                    graph.addWithoutUnique(threadIdNode);
                    threadIdNode.replaceFirstSuccessor(null, node.successors().first());
                    node.replaceAtUsages(threadIdNode);
                    node.replaceAtPredecessor(threadIdNode);
                    // If I delete the node here it will remove also the control flow edge of the
                    // threadIdNode
                    nodesToBeRemoved.add(node);
                }
            }
        });

        nodesToBeRemoved.forEach(node -> {
            node.clearSuccessors();
            node.clearInputs();
            node.safeDelete();
        });

    }

    public void execute(StructuredGraph graph, TornadoSketchTierContext context) {
        run(graph, context);
    }

    @Override
    protected void run(StructuredGraph graph, TornadoSketchTierContext context) {
        introduceTornadoVMContext(graph);
    }
}
