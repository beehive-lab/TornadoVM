package uk.ac.manchester.tornado.runtime.graal.phases;

import jdk.vm.ci.meta.ResolvedJavaField;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.AccessFieldNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.phases.BasePhase;
import uk.ac.manchester.tornado.api.type.annotations.Payload;
import uk.ac.manchester.tornado.api.type.annotations.TornadoCollection;
import uk.ac.manchester.tornado.api.type.annotations.TornadoFieldVector;

public class TornadoVectorReplacement extends BasePhase<TornadoHighTierContext> {

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {
        graph.getNodes().filter(node -> {
            if (node instanceof LoadFieldNode) {
                AccessFieldNode accessFieldNode = (AccessFieldNode) node;
                ResolvedJavaField field = accessFieldNode.field();
                return (field.getDeclaringClass().isAnnotationPresent(TornadoCollection.class) && field.isAnnotationPresent(TornadoFieldVector.class))
                        || field.isAnnotationPresent(Payload.class);
            }
            return false;
        }).forEach(node -> {
            AccessFieldNode accessFieldNode = (AccessFieldNode) node;
            ValueNode object = accessFieldNode.object();
            if (object instanceof NewInstanceNode && ((NewInstanceNode) object).next() instanceof NewArrayNode) {
                node.replaceAtUsages(((NewInstanceNode) object).next());
                graph.removeFixed(accessFieldNode);
            } else {
                node.replaceAtUsages(object);
                graph.removeFixed(accessFieldNode);
            }
        });
    }
}
