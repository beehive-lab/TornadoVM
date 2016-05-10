package tornado.drivers.opencl.graal.nodes.vector;

import tornado.common.Tornado;
import tornado.drivers.opencl.graal.asm.OpenCLAssembler.OCLBinaryIntrinsic;
import tornado.drivers.opencl.graal.lir.OCLBinary;
import tornado.drivers.opencl.graal.lir.OCLLIRInstruction.AssignStmt;
import tornado.drivers.opencl.graal.lir.OCLUnary.MemoryAccess;
import tornado.graal.nodes.vector.VectorKind;

import com.oracle.graal.api.meta.LIRKind;
import com.oracle.graal.api.meta.PrimitiveConstant;
import com.oracle.graal.api.meta.Value;
import com.oracle.graal.compiler.common.GraalInternalError;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.extended.LocationNode;
import com.oracle.graal.nodes.memory.FloatableAccessNode;
import com.oracle.graal.nodes.memory.FloatingAccessNode;
import com.oracle.graal.nodes.memory.MemoryNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;

@NodeInfo
public class VectorReadNode extends FloatableAccessNode implements LIRLowerable {

	public static final NodeClass<VectorReadNode>	TYPE	= NodeClass
																	.create(VectorReadNode.class);

	private final VectorKind						vectorKind;

	public VectorReadNode(
			VectorKind vectorKind,
			ValueNode object,
			ValueNode location,
			BarrierType barrierType) {
		super(TYPE, object, location, StampFactory.forVoid(), null, barrierType);
		this.vectorKind = vectorKind;
		setForceFixed(true);
	}

	@Override
	public boolean canNullCheck() {
		return false;
	}

	@Override
	public FloatingAccessNode asFloatingNode(MemoryNode arg0) {
		throw GraalInternalError.shouldNotReachHere("VectorReadNode asFloatingNode");
		// return null;
	}

	@Override
	public void generate(NodeLIRBuilderTool gen) {
		final LIRGeneratorTool tool = gen.getLIRGeneratorTool();

		OCLBinaryIntrinsic intrinsic = VectorUtil.resolveLoadIntrinsic(vectorKind);

		final Variable result = tool.newVariable(LIRKind.value(vectorKind));

		final LocationNode location = location();
		final Value object = gen.operand(object());

		final MemoryAccess addressOfObject = (MemoryAccess) location.generateAddress(gen, tool, object);
		addressOfObject.setKind(vectorKind.getElementKind());
		
		Tornado.trace("address: type=%s, %s\n",addressOfObject.getClass().getName(),addressOfObject);
		
		AssignStmt stmt = new AssignStmt(result, new OCLBinary.Intrinsic(intrinsic, LIRKind.value(vectorKind), PrimitiveConstant.INT_0,
				addressOfObject));
		
		tool.append(stmt);
		Tornado.trace("emitVectorLoad: %s = %s(%d, %s)", result, intrinsic.toString(), 0,
				addressOfObject);
		gen.setResult(this, result);

	}

}
