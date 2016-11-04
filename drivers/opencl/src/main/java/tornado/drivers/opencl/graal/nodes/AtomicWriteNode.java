package tornado.drivers.opencl.graal.nodes;

import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.Value;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.extended.LocationNode;
import com.oracle.graal.nodes.memory.AbstractWriteNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import tornado.common.Tornado;
import tornado.drivers.opencl.graal.asm.OpenCLAssembler.OCLBinaryIntrinsic;
import tornado.drivers.opencl.graal.lir.OCLBinary;
import tornado.drivers.opencl.graal.lir.OCLLIRInstruction;
import tornado.drivers.opencl.graal.lir.OCLUnary.MemoryAccess;

@NodeInfo(shortName="Atomic Write")
public class AtomicWriteNode extends AbstractWriteNode implements LIRLowerable {

	public static final NodeClass<AtomicWriteNode>	TYPE	= NodeClass
																	.create(AtomicWriteNode.class);

	OCLBinaryIntrinsic op;
	
	public AtomicWriteNode(
			OCLBinaryIntrinsic op,
			ValueNode object,
			ValueNode value,
			ValueNode location) {
		super(TYPE, object, value, location, BarrierType.NONE);
		this.op = op;
	}

	@Override
	public void generate(NodeLIRBuilderTool gen) {
		final LIRGeneratorTool tool = gen.getLIRGeneratorTool();

		final LocationNode location = location();

		final Value object = gen.operand(object());

		final MemoryAccess addressOfObject = (MemoryAccess) location.generateAddress(gen, tool,
				object);
//		addressOfObject.setKind(value().getKind());

		final Value valueToStore = gen.operand(value());

		tool.append(new OCLLIRInstruction.ExprStmt(new OCLBinary.Intrinsic(op, Kind.Illegal,
				addressOfObject, valueToStore)));
		Tornado.trace("emitAtomicWrite: %s(%s, %s)", op.toString(),
				addressOfObject, valueToStore);

	}

	@Override
	public boolean canNullCheck() {
		return false;
	}

}
