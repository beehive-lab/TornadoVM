package uk.ac.manchester.tornado.drivers.spirv.graal.nodes.vector;

import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVStampFactory;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;
import uk.ac.manchester.tornado.runtime.graal.phases.MarkVectorValueNode;

// FIXME: <TODO>
@NodeInfo(nameTemplate = "{p#kind/s}")
public class SPIRVVectorValueNode extends FloatingNode implements LIRLowerable, MarkVectorValueNode {

    public static final NodeClass<SPIRVVectorValueNode> TYPE = NodeClass.create(SPIRVVectorValueNode.class);

    private SPIRVKind kind;

    @Input
    NodeInputList<ValueNode> values;

    public SPIRVVectorValueNode(SPIRVKind spirvVectorKind) {
        super(TYPE, SPIRVStampFactory.getStampFor(spirvVectorKind));
        this.kind = spirvVectorKind;
        this.values = new NodeInputList<>(this, kind.getVectorLength());
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        throw new RuntimeException("SPIRVVectorValueNode#generate Not implemented yet");
    }
}
