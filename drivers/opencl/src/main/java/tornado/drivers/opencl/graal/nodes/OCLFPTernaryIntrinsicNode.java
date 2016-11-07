package tornado.drivers.opencl.graal.nodes;

import tornado.common.exceptions.TornadoInternalError;

import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.Value;
import com.oracle.graal.compiler.common.GraalInternalError;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.lir.gen.ArithmeticLIRGenerator;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.TernaryNode;
import com.oracle.graal.nodes.spi.ArithmeticLIRLowerable;
import com.oracle.graal.nodes.spi.NodeMappableLIRBuilder;
import tornado.drivers.opencl.graal.lir.OCLBuiltinTool;

@NodeInfo(nameTemplate = "{p#operation}")
public class OCLFPTernaryIntrinsicNode extends TernaryNode implements ArithmeticLIRLowerable {

	protected OCLFPTernaryIntrinsicNode(ValueNode x, ValueNode y, ValueNode z, Operation op, Kind kind) {
		super(TYPE, StampFactory.forKind(kind), x, y, z);
		this.operation = op;
	}

	public static final NodeClass<OCLFPTernaryIntrinsicNode>	TYPE	= NodeClass
																			.create(OCLFPTernaryIntrinsicNode.class);
	protected final Operation								operation;

	public enum Operation {
		FMA, MAD, REMQUO
	}

	public Operation operation() {
		return operation;
	}

	public static ValueNode create(ValueNode x, ValueNode y, ValueNode z, Operation op, Kind kind) {
		ValueNode c = tryConstantFold(x, y, z, op, kind);
		if (c != null) {
			return c;
		}
		return new OCLFPTernaryIntrinsicNode(x, y, z, op, kind);
	}

	protected static ValueNode tryConstantFold(ValueNode x, ValueNode y, ValueNode z, Operation op, Kind kind) {
		ConstantNode result = null;

		if (x.isConstant() && y.isConstant() && z.isConstant()) {
			if (kind == Kind.Double) {
				double ret = doCompute(x.asJavaConstant().asDouble(),y.asJavaConstant().asDouble(), z.asJavaConstant().asDouble(), op);
				result = ConstantNode.forDouble(ret);
			} else if (kind == Kind.Float) {
				float ret = doCompute(x.asJavaConstant().asFloat(),y.asJavaConstant().asFloat(), z.asJavaConstant().asFloat(), op);
				result = ConstantNode.forFloat((float) ret);
			}
		}
		return result;
	}

	@Override
	public void generate(NodeMappableLIRBuilder builder, ArithmeticLIRGenerator lirGen) {
		OCLBuiltinTool gen = (OCLBuiltinTool) lirGen;
		Value x = builder.operand(getX());
		Value y = builder.operand(getY());
		Value z = builder.operand(getZ());
		Value result;
		switch (operation()) {
			case FMA:
				result = gen.emitFloatFMA(x, y, z);
				break;
			case MAD:
				result = gen.emitFloatMAD(x, y, z);
				break;
			case REMQUO:
				result = gen.emitFloatRemquo(x, y, z);
				break;
			default:
				throw TornadoInternalError.shouldNotReachHere();
		}
		builder.setResult(this, result);

	}

	private static double doCompute(double x, double y, double z, Operation op) {
		switch (op) {
			default:
				throw new TornadoInternalError("unable to compute op %s", op);
		}
	}

	private static float doCompute(float x, float y, float z, Operation op) {
		switch (op) {
			default:
				throw new GraalInternalError("unable to compute  op %s", op);
		}
	}

	@Override
	public Node canonical(CanonicalizerTool tool, ValueNode x, ValueNode y, ValueNode z) {
		ValueNode c = tryConstantFold(x,y,z, operation(), getKind());
		if (c != null) {
			return c;
		}
		return this;
	}

}
