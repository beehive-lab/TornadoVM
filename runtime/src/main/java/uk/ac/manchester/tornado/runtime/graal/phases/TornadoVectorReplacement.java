package uk.ac.manchester.tornado.runtime.graal.phases;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.AccessFieldNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.phases.BasePhase;

import static uk.ac.manchester.tornado.runtime.common.RuntimeUtilities.COLLECTION_TYPES_PACKAGE;

public class TornadoVectorReplacement extends BasePhase<TornadoHighTierContext> {

    private static final String storageFieldName = "storage";

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {
        graph.getNodes().filter(node -> {
            if (node instanceof LoadFieldNode) {
                AccessFieldNode accessFieldNode = (AccessFieldNode) node;
                ResolvedJavaField field = accessFieldNode.field();
                String packageName = getPackageName(field.getDeclaringClass());
                return COLLECTION_TYPES_PACKAGE.equals(packageName) && storageFieldName.equals(field.getName());
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

    private String getPackageName(ResolvedJavaType javaType) {
        String javaName = javaType.toJavaName();
        return javaName.substring(0, javaName.lastIndexOf("."));
    }
}
