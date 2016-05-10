package tornado.drivers.opencl.graal.nodes;

import tornado.drivers.opencl.graal.lir.OCLIntBuiltinFunctionLIRGenerator;

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
import com.oracle.graal.nodes.calc.UnaryNode;
import com.oracle.graal.nodes.spi.ArithmeticLIRLowerable;
import com.oracle.graal.nodes.spi.NodeMappableLIRBuilder;

@NodeInfo(nameTemplate = "{p#operation}")
public class OCLIntUnaryIntrinsicNode extends UnaryNode implements ArithmeticLIRLowerable {

	protected OCLIntUnaryIntrinsicNode(ValueNode x, Operation op, Kind kind) {
		super(TYPE, StampFactory.forKind(kind), x);
		this.operation = op;
	}

	public static final NodeClass<OCLIntUnaryIntrinsicNode>	TYPE	= NodeClass
																			.create(OCLIntUnaryIntrinsicNode.class);
	protected final Operation								operation;

	public enum Operation {
		ABS,
		CLZ,
		POPCOUNT
	}

	public Operation operation() {
		return operation;
	}

	public static ValueNode create(ValueNode x, Operation op, Kind kind) {
		ValueNode c = tryConstantFold(x, op, kind);
		if (c != null) {
			return c;
		}
		return new OCLIntUnaryIntrinsicNode(x, op, kind);
	}

	protected static ValueNode tryConstantFold(ValueNode x, Operation op, Kind kind) {
		ConstantNode result = null;

		if (x.isConstant()) {
			if (kind == Kind.Int) {
				int ret = doCompute(x.asJavaConstant().asInt(), op);
				result = ConstantNode.forInt(ret);
			} else if (kind == Kind.Long) {
				long ret = doCompute(x.asJavaConstant().asLong(), op);
				result = ConstantNode.forLong(ret);
			}
		}
		return result;
	}

	@Override
	public void generate(NodeMappableLIRBuilder builder, ArithmeticLIRGenerator lirGen) {
		OCLIntBuiltinFunctionLIRGenerator gen = (OCLIntBuiltinFunctionLIRGenerator) lirGen;
		Value x = builder.operand(getValue());
		Value result;
		switch (operation()) {
			case ABS:
				result = gen.emitIntAbs(x);
				break;
			case CLZ:
				result = gen.emitIntClz(x);
				break;
			case POPCOUNT:
				result = gen.emitIntPopcount(x);
				break;
			default:
				throw GraalInternalError.shouldNotReachHere();
		}
		builder.setResult(this, result);

	}

	private static long doCompute(long value, Operation op) {
		switch (op) {
			case ABS:
				return Math.abs(value);
			case CLZ:
				return Long.numberOfLeadingZeros(value);
			case POPCOUNT:
				return Long.bitCount(value);
			default:
				throw new GraalInternalError("unknown op %s", op);
		}
	}

	private static int doCompute(int value, Operation op) {
		switch (op) {
			case ABS:
				return Math.abs(value);
			case CLZ:
				return Integer.numberOfLeadingZeros(value);
			case POPCOUNT:
				return Integer.bitCount(value);
			default:
				throw new GraalInternalError("unknown op %s", op);
		}
	}

	@Override
	public Node canonical(CanonicalizerTool tool, ValueNode value) {
		ValueNode c = tryConstantFold(value, operation(), getKind());
		if (c != null) {
			return c;
		}
		return this;
	} 

}
