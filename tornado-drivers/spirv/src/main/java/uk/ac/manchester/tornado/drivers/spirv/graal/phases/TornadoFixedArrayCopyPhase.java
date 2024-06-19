package uk.ac.manchester.tornado.drivers.spirv.graal.phases;

import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.phases.BasePhase;

import uk.ac.manchester.tornado.runtime.graal.phases.TornadoLowTierContext;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVArchitecture;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.FixedArrayCopyNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.FixedArrayNode;

import java.util.Optional;

public class TornadoFixedArrayCopyPhase extends BasePhase<TornadoLowTierContext> {

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    protected void run(StructuredGraph graph, TornadoLowTierContext context) {
        for (ValuePhiNode phiNode : graph.getNodes().filter(ValuePhiNode.class)) {
            if (phiNode.usages().filter(OffsetAddressNode.class).isNotEmpty() && phiNode.values().filter(FixedArrayNode.class).isNotEmpty()) {
                FixedArrayNode fixedArrayNode = phiNode.values().filter(FixedArrayNode.class).first();
                ResolvedJavaType resolvedJavaType = fixedArrayNode.getElementType();
                SPIRVArchitecture.SPIRVMemoryBase oclMemoryBase = fixedArrayNode.getMemoryRegister();
                OffsetAddressNode offsetAddressNode = phiNode.usages().filter(OffsetAddressNode.class).first();
                FixedArrayCopyNode fixedArrayCopyNode = new FixedArrayCopyNode(phiNode, resolvedJavaType, oclMemoryBase);
                graph.addWithoutUnique(fixedArrayCopyNode);
                offsetAddressNode.replaceFirstInput(phiNode, fixedArrayCopyNode);
                // finally, since we know that the data accessed is a fixed array, fix the offset
                ValuePhiNode privateIndex = getPrivateArrayIndex(offsetAddressNode.getOffset());
                offsetAddressNode.setOffset(privateIndex);
                break;
            }
        }
    }

    private static ValuePhiNode getPrivateArrayIndex(Node node) {
        // identify the index
        for (Node input : node.inputs()) {
            if (input instanceof ValuePhiNode phiNode) {
                return phiNode;
            } else {
                return getPrivateArrayIndex(input);
            }
        }
        return null;
    }

}
