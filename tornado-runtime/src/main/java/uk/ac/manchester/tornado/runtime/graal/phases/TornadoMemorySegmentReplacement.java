package uk.ac.manchester.tornado.runtime.graal.phases;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.phases.BasePhase;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.runtime.graal.nodes.MemorySegmentArrayNode;

import java.util.regex.Pattern;


public class TornadoMemorySegmentReplacement extends BasePhase<TornadoSketchTierContext> {

    private final String MEMORY_SEGMENT_CLASS_NAME = "jdk.internal.foreign.AbstractMemorySegmentImpl";
    private final Pattern METHOD_NAME_PATTERN = Pattern.compile("to.*Array");

    public void replaceMemorySegments(StructuredGraph graph) {
        graph.getNodes().filter(InvokeNode.class).forEach(invokeNode -> {
            CallTargetNode callTarget = invokeNode.callTarget();
            ResolvedJavaMethod targetMethod = callTarget.targetMethod();
            if (MEMORY_SEGMENT_CLASS_NAME.equals(targetMethod.getDeclaringClass().toJavaName())
                    && METHOD_NAME_PATTERN.matcher(targetMethod.getName()).matches()) {

                ValueNode argument = callTarget.arguments().first();
                FixedGuardNode guardNode = null;
                // There might be a Pi node with a guard attached to it.
                if (argument instanceof PiNode) {
                    if (((PiNode) argument).getGuard() instanceof FixedGuardNode) {
                        guardNode = (FixedGuardNode) ((PiNode) argument).getGuard();
                    }
                    ((PiNode) argument).setGuard(null);
                    argument = ((PiNode) argument).object();
                }
                TornadoInternalError.guarantee(argument instanceof ParameterNode, "Should be ParameterNode by this stage");

                MemorySegmentArrayNode segmentArrayNode = new MemorySegmentArrayNode((ParameterNode) argument);
                graph.add(segmentArrayNode);
                graph.replaceFixedWithFixed(invokeNode, segmentArrayNode);

                if (guardNode != null) {
                    graph.removeFixed(guardNode);
                }
            }
        });
    }

    @Override
    protected void run(StructuredGraph graph, TornadoSketchTierContext context) {
        replaceMemorySegments(graph);
    }
}
