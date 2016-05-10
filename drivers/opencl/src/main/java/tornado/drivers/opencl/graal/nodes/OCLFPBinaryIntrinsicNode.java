package tornado.drivers.opencl.graal.nodes;

import tornado.common.exceptions.TornadoInternalError;
import tornado.drivers.opencl.graal.lir.OCLFPBuiltinFunctionLIRGenerator;

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
import com.oracle.graal.nodes.calc.BinaryNode;
import com.oracle.graal.nodes.spi.ArithmeticLIRLowerable;
import com.oracle.graal.nodes.spi.NodeMappableLIRBuilder;

@NodeInfo(nameTemplate = "{p#operation}")
public class OCLFPBinaryIntrinsicNode extends BinaryNode implements ArithmeticLIRLowerable {

	protected OCLFPBinaryIntrinsicNode(ValueNode x, ValueNode y, Operation op, Kind kind) {
		super(TYPE, StampFactory.forKind(kind), x, y);
		this.operation = op;
	}

	public static final NodeClass<OCLFPBinaryIntrinsicNode>	TYPE	= NodeClass
																			.create(OCLFPBinaryIntrinsicNode.class);
	protected final Operation								operation;

	public enum Operation {
		ATAN2, ATAN2PI, COPYSIGN, FDIM, FMA, FMAX, FMIN, FMOD, FRACT, FREXP, HYPOT, LDEXP, MAD, MAXMAG, MINMAG, MODF, NEXTAFTER, POW, POWN, POWR, REMAINDER, REMQUO, ROOTN, SINCOS
	}

	public Operation operation() {
		return operation;
	}

	public static ValueNode create(ValueNode x, ValueNode y, Operation op, Kind kind) {
		ValueNode c = tryConstantFold(x,y, op, kind);
		if (c != null) {
			return c;
		}
		return new OCLFPBinaryIntrinsicNode(x, y, op, kind);
	}

	protected static ValueNode tryConstantFold(ValueNode x, ValueNode y, Operation op, Kind kind) {
		ConstantNode result = null;

		if (x.isConstant() && y.isConstant()) {
			if (kind == Kind.Double) {
				double ret = doCompute(x.asJavaConstant().asDouble(),y.asJavaConstant().asDouble(), op);
				result = ConstantNode.forDouble(ret);
			} else if (kind == Kind.Float) {
				float ret = doCompute(x.asJavaConstant().asFloat(),y.asJavaConstant().asFloat(), op);
				result = ConstantNode.forFloat((float) ret);
			}
		}
		return result;
	}

	@Override
	public void generate(NodeMappableLIRBuilder builder, ArithmeticLIRGenerator lirGen) {
		OCLFPBuiltinFunctionLIRGenerator gen = (OCLFPBuiltinFunctionLIRGenerator) lirGen;
		Value x = builder.operand(getX());
		Value y = builder.operand(getY());
		Value result;
		switch (operation()) {
			case FMIN:
				result = gen.emitFloatMin(x, y);
				break;
			case FMAX:
				result = gen.emitFloatMax(x, y);
				break;
			default:
				throw TornadoInternalError.shouldNotReachHere();
		}
		builder.setResult(this, result);

	}

	private static double doCompute(double x, double y, Operation op) {
		switch (op) {
			case FMIN:
				return Math.min(x,y);
			case FMAX:
				return Math.max(x,y);
			default:
				throw new TornadoInternalError("unknown op %s", op);
		}
	}

	private static float doCompute(float x, float y, Operation op) {
		switch (op) {
			case FMIN:
				return Math.min(x,y);
			case FMAX:
				return Math.max(x,y);
			default:
				throw new GraalInternalError("unknown op %s", op);
		}
	}

	@Override
	public Node canonical(CanonicalizerTool tool, ValueNode x, ValueNode y) {
		ValueNode c = tryConstantFold(x,y, operation(), getKind());
		if (c != null) {
			return c;
		}
		return this;
	}

}
