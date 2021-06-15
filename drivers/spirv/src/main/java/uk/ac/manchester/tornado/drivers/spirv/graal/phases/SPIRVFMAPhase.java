package uk.ac.manchester.tornado.drivers.spirv.graal.phases;

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.phases.Phase;

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVFMANode;

public class SPIRVFMAPhase extends Phase {

    private boolean isValidType(ValueNode x) {
        return (x.getStackKind() == JavaKind.Float || x.getStackKind() == JavaKind.Double);
    }

    @Override
    protected void run(StructuredGraph graph) {

        graph.getNodes().filter(AddNode.class).forEach(addNode -> {
            MulNode mulNode = null;

            if (addNode.getX() instanceof MulNode) {
                mulNode = (MulNode) addNode.getX();
            } else if (addNode.getY() instanceof MulNode) {
                mulNode = (MulNode) addNode.getY();
            }

            if (mulNode != null) {
                ValueNode x = mulNode.getX();
                ValueNode y = mulNode.getY();

                if (isValidType(x) && isValidType(y)) {
                    MulNode finalMulNode = mulNode;
                    ValueNode z = (ValueNode) addNode.inputs().filter(node -> !node.equals(finalMulNode)).first();

                    SPIRVFMANode spirvfmaNode = new SPIRVFMANode(x, y, z);
                    graph.addOrUnique(spirvfmaNode);
                    mulNode.removeUsage(addNode);
                    if (mulNode.hasNoUsages()) {
                        mulNode.safeDelete();
                    }
                    addNode.replaceAtUsages(spirvfmaNode);
                    addNode.safeDelete();
                }
            }
        });

    }
}
