package uk.ac.manchester.tornado.runtime.graal.phases;

import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.AccessIndexedNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.phases.BasePhase;
import uk.ac.manchester.tornado.runtime.graal.nodes.calc.TornadoAddressArithmeticNode;

public class TornadoFieldAccessFixup extends BasePhase<TornadoHighTierContext> {

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {
        graph.getNodes().filter(node -> {
            if (node instanceof LoadFieldNode) {
                LoadFieldNode loadField = (LoadFieldNode) node;
                if (loadField.object() instanceof ParameterNode) {
                    return loadField.usages().filter(AccessIndexedNode.class).isNotEmpty();
                } else if (loadField.object() instanceof PiNode && ((PiNode) loadField.object()).object() instanceof ParameterNode) {
                    return loadField.usages().filter(AccessIndexedNode.class).isNotEmpty();
                }
            }
            return false;
        }).forEach(node -> {
            LoadFieldNode loadField = (LoadFieldNode) node;
            loadField.usages().filter(AccessIndexedNode.class).forEach(loadStoreIndexed -> {
                ValueNode base = loadField.object();
                if (base instanceof PiNode) {
                    base = ((PiNode) base).object();
                }
                TornadoAddressArithmeticNode addNode = new TornadoAddressArithmeticNode(base, loadField);
                graph.addWithoutUnique(addNode);
                loadStoreIndexed.setArray(addNode);
            });
        });
    }
}
