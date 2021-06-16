package uk.ac.manchester.tornado.drivers.spirv.graal.phases;

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.phases.Phase;

import uk.ac.manchester.tornado.runtime.graal.nodes.NewArrayNonVirtualizableNode;

public class TornadoNewArrayDevirtualizationReplacement extends Phase {

    @Override
    protected void run(StructuredGraph graph) {
        graph.getNodes().filter(NewArrayNode.class).forEach(newArrayNode -> {
            NewArrayNonVirtualizableNode newArrayNonVirtualNode = new NewArrayNonVirtualizableNode(newArrayNode.elementType(), newArrayNode.length(), false);

            graph.addOrUnique(newArrayNonVirtualNode);

            newArrayNode.replaceAtUsages(newArrayNonVirtualNode);

            graph.replaceFixed(newArrayNode, newArrayNonVirtualNode);

            if (!newArrayNode.hasNoUsages()) {
                newArrayNode.safeDelete();
            }
        });
    }
}
