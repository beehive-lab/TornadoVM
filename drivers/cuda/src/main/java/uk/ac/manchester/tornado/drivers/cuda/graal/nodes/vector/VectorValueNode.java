package uk.ac.manchester.tornado.drivers.cuda.graal.nodes.vector;

import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.drivers.cuda.graal.PTXStampFactory;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXKind;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

@NodeInfo(nameTemplate = "{p#kind/s}")
public class VectorValueNode extends FloatingNode implements LIRLowerable {
    public static final NodeClass<VectorValueNode> TYPE = NodeClass.create(VectorValueNode.class);

    public VectorValueNode(PTXKind kind) {
        super(TYPE, PTXStampFactory.getStampFor(kind));
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        unimplemented();
    }
}
