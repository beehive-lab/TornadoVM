package tornado.drivers.opencl.graal.nodes;

import static tornado.drivers.opencl.graal.asm.OpenCLAssembler.*;
import tornado.drivers.opencl.graal.lir.OCLNullary;

import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;

@NodeInfo
public class SlotsBaseAddressNode extends ValueNode implements LIRLowerable {
	public static final NodeClass<SlotsBaseAddressNode> TYPE = NodeClass.create(SlotsBaseAddressNode.class);

	public SlotsBaseAddressNode() {
		super(TYPE, StampFactory.forKind(Kind.Object));
	}
	
	@Override
	public void generate(NodeLIRBuilderTool gen) {
		gen.setResult(this, new OCLNullary.Expr(OCLNullaryOp.SLOTS_BASE_ADDRESS, getKind()));
	}

}
