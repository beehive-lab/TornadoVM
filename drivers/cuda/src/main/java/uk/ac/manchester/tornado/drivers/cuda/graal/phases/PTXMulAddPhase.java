package uk.ac.manchester.tornado.drivers.cuda.graal.phases;

import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.phases.Phase;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.PTXMultiplyAddNode;

public class PTXMulAddPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {
        graph.getNodes().filter(AddNode.class).forEach(addNode -> {
            NodeIterable<MulNode> inputMuls = addNode.inputs().filter(MulNode.class);
            if (inputMuls.count() > 0) {
                MulNode mul = inputMuls.first();

                ValueNode x = mul.getX();
                ValueNode y = mul.getY();
                ValueNode z = (ValueNode) addNode.inputs().filter(node -> !node.equals(mul)).first();
                PTXMultiplyAddNode newNode = new PTXMultiplyAddNode(x, y, z);
                graph.addWithoutUnique(newNode);

                mul.removeUsage(addNode);
                if (mul.hasNoUsages()) mul.safeDelete();
                addNode.replaceAtUsages(newNode);
                addNode.safeDelete();
            }
        });
    }
}
