package uk.ac.manchester.tornado.drivers.spirv.graal.nodes.vector;

import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;

import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;

/**
 * The {@code LoadIndexedNode} represents a read from an element of an array.
 */
@NodeInfo(nameTemplate = "Load .s{p#lane}")
public class VectorLoadElementNode extends VectorElementOpNode {

    public static final NodeClass<VectorLoadElementNode> TYPE = NodeClass.create(VectorLoadElementNode.class);

    public VectorLoadElementNode(SPIRVKind kind, ValueNode vector, ValueNode lane) {
        super(TYPE, kind, vector, lane);
    }
}
