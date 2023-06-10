package uk.ac.manchester.tornado.drivers.opencl.graal.phases;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.phases.BasePhase;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoHighTierContext;

//TODO: Make this optional for native types
public class TornadoLocalArrayHeaderEliminator extends BasePhase<TornadoHighTierContext> {
    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {
        for (ReadNode r : graph.getNodes().filter(ReadNode.class)) {
            if (r.inputs().filter(OffsetAddressNode.class).isNotEmpty()) {
                OffsetAddressNode offsetAddressNode = r.inputs().filter(OffsetAddressNode.class).first();
                removeHeaderBytesOffset(offsetAddressNode);
            }
        }

        for (WriteNode wr : graph.getNodes().filter(WriteNode.class)) {
            if (wr.inputs().filter(OffsetAddressNode.class).isNotEmpty()) {
                OffsetAddressNode offsetAddressNode = wr.inputs().filter(OffsetAddressNode.class).first();
                removeHeaderBytesOffset(offsetAddressNode);
            }
        }
    }

    public void removeHeaderBytesOffset (OffsetAddressNode offsetAddressNode) {
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
