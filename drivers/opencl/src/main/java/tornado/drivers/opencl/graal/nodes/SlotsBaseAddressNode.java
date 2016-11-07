package tornado.drivers.opencl.graal.nodes;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.meta.JavaKind;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLNullaryOp;
import tornado.drivers.opencl.graal.lir.OCLNullary;

@NodeInfo
public class SlotsBaseAddressNode extends ValueNode implements LIRLowerable {

    public static final NodeClass<SlotsBaseAddressNode> TYPE = NodeClass.create(SlotsBaseAddressNode.class);

    public SlotsBaseAddressNode() {
        super(TYPE, StampFactory.forKind(JavaKind.Object));
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        gen.setResult(this, new OCLNullary.Expr(OCLNullaryOp.SLOTS_BASE_ADDRESS, gen.getLIRGeneratorTool().getLIRKind(stamp)));
    }

}
