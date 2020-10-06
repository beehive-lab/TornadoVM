package uk.ac.manchester.tornado.drivers.opencl.graal.phases;

import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.Phase;

import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLKind;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.IncAtomicNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.TornadoAtomicIntegerNode;

public class TornadoAtomicsParametersPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {

        NodeIterable<IncAtomicNode> filter = graph.getNodes().filter(IncAtomicNode.class);
        if (!filter.isEmpty()) {
            for (IncAtomicNode atomic : filter) {
                if (atomic.getAtomicNode() instanceof ParameterNode) {
                    TornadoAtomicIntegerNode newNode = new TornadoAtomicIntegerNode(OCLKind.INTEGER_ATOMIC_JAVA);
                    graph.addOrUnique(newNode);

                    final ConstantNode index = graph.addOrUnique(ConstantNode.forInt(0));
                    newNode.setInitialValue(index);

                    // Add the new control flow node
                    StartNode startNode = graph.start();
                    FixedNode first = (FixedNode) startNode.successors().first();
                    startNode.setNext(newNode);
                    newNode.setNext(first);

                    // Replace usages for this new node
                    ParameterNode parameter = (ParameterNode) atomic.getAtomicNode();
                    newNode.replaceAtMatchingUsages(atomic, node -> !node.equals(atomic));
                    parameter.replaceAtMatchingUsages(newNode, node -> node.equals(atomic));

                    assert graph.verify();
                }
            }
        }

    }

}
