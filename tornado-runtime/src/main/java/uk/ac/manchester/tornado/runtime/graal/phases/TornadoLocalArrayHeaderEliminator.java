package uk.ac.manchester.tornado.runtime.graal.phases;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.phases.BasePhase;

import java.util.Optional;

public class TornadoLocalArrayHeaderEliminator extends BasePhase<TornadoHighTierContext> {

    public static boolean isReduction = false;
    public static boolean nativeTypes = false;

    public static boolean anyIdentityNodeOrReduction(Node n) {
        boolean anyIdentity = false;
        if (n instanceof ReadNode) {
            anyIdentity = ((ReadNode) n).getLocationIdentity().isAny();
        } else if (n instanceof WriteNode) {
            anyIdentity = ((WriteNode) n).getLocationIdentity().isAny();
        }
        return anyIdentity || (isReduction && nativeTypes);
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {
        for (ReadNode r : graph.getNodes().filter(ReadNode.class)) {
            if (r.inputs().filter(OffsetAddressNode.class).isNotEmpty() && anyIdentityNodeOrReduction(r)/*&& r.getLocationIdentity().isAny()*/) {
                OffsetAddressNode offsetAddressNode = r.inputs().filter(OffsetAddressNode.class).first();
                removeHeaderBytesOffset(offsetAddressNode);
            }
        }

        for (WriteNode wr : graph.getNodes().filter(WriteNode.class)) {
            if (wr.inputs().filter(OffsetAddressNode.class).isNotEmpty() && anyIdentityNodeOrReduction(wr) /*&& wr.getLocationIdentity().isAny()*/) {
                OffsetAddressNode offsetAddressNode = wr.inputs().filter(OffsetAddressNode.class).first();
                removeHeaderBytesOffset(offsetAddressNode);
            }
        }
        isReduction = false;
        nativeTypes = false;
    }

    public void removeHeaderBytesOffset(OffsetAddressNode offsetAddressNode) {
        if (offsetAddressNode.inputs().filter(AddNode.class).isNotEmpty()) {
            AddNode addNode = offsetAddressNode.inputs().filter(AddNode.class).first();
            for (Node in : addNode.inputs()) {
                if (in instanceof LeftShiftNode) {
                    offsetAddressNode.replaceFirstInput(addNode, in);
                    addNode.clearInputs();
                    addNode.safeDelete();
                    return;
                }
            }
        }
    }
}
