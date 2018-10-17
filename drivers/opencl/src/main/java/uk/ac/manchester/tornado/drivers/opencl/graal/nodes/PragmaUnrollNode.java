package uk.ac.manchester.tornado.drivers.opencl.graal.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIRStmt;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLPragmaUnroll;

@NodeInfo
public class PragmaUnrollNode extends FloatingNode implements LIRLowerable {

    @Successor
    LoopBeginNode loopBgNd;
    public static final NodeClass<PragmaUnrollNode> TYPE = NodeClass.create(PragmaUnrollNode.class);

    private int unroll;

    public PragmaUnrollNode(int unroll) {
        super(TYPE, StampFactory.forVoid());
        this.unroll = unroll;
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeLIRBuilderTool) {
        nodeLIRBuilderTool.getLIRGeneratorTool().append(new OCLLIRStmt.ExprStmt(new OCLPragmaUnroll(unroll)));
    }
}
