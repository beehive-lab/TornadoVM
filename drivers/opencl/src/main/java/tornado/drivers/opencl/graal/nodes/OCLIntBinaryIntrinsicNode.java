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
import com.oracle.graal.nodes.calc.BinaryNode;
import com.oracle.graal.nodes.spi.ArithmeticLIRLowerable;
import com.oracle.graal.nodes.spi.NodeMappableLIRBuilder;

@NodeInfo(nameTemplate = "{p#operation}")
public class OCLIntBinaryIntrinsicNode extends BinaryNode implements ArithmeticLIRLowerable {

	protected OCLIntBinaryIntrinsicNode(ValueNode x, ValueNode y, Operation op, Kind kind) {
		super(TYPE, StampFactory.forKind(kind), x, y);
		this.operation = op;
	}

	public static final NodeClass<OCLIntBinaryIntrinsicNode>	TYPE	= NodeClass
																			.create(OCLIntBinaryIntrinsicNode.class);
	protected final Operation								operation;

	public enum Operation {
		ABS_DIFF,
		ABS_SAT,
		HADD,
		RHADD,
		MAX,
		MIN,
		MUL_HI,
		ROTATE,
		SUB_SAT,
		UPSAMPLE,
		MUL24
	}

	public Operation operation() {
		return operation;
	}

	public static ValueNode create(ValueNode x, ValueNode y, Operation op, Kind kind) {
		ValueNode c = tryConstantFold(x,y, op, kind);
		if (c != null) {
			return c;
		}
		return new OCLIntBinaryIntrinsicNode(x, y, op, kind);
	}

	protected static ValueNode tryConstantFold(ValueNode x, ValueNode y, Operation op, Kind kind) {
		ConstantNode result = null;

		if (x.isConstant() && y.isConstant()) {
			if (kind == Kind.Int) {
				int ret = doCompute(x.asJavaConstant().asInt(),y.asJavaConstant().asInt(), op);
				result = ConstantNode.forInt(ret);
			} else if (kind == Kind.Long) {
				long ret = doCompute(x.asJavaConstant().asLong(),y.asJavaConstant().asLong(), op);
				result = ConstantNode.forLong(ret);
			}
		}
		return result;
	}

	@Override
	public void generate(NodeMappableLIRBuilder builder, ArithmeticLIRGenerator lirGen) {
		OCLIntBuiltinFunctionLIRGenerator gen = (OCLIntBuiltinFunctionLIRGenerator) lirGen;
		Value x = builder.operand(getX());
		Value y = builder.operand(getY());
		Value result;
		switch (operation()) {
			case MIN:
				result = gen.emitIntMin(x, y);
				break;
			case MAX:
				result = gen.emitIntMax(x, y);
				break;
			default:
				throw GraalInternalError.shouldNotReachHere();
		}
		builder.setResult(this, result);

	}

	private static long doCompute(long x, long y, Operation op) {
		switch (op) {
			case MIN:
				return Math.min(x,y);
			case MAX:
				return Math.max(x,y);
			default:
				throw new GraalInternalError("unknown op %s", op);
		}
	}

	private static int doCompute(int x, int y, Operation op) {
		switch (op) {
			case MIN:
				return Math.min(x,y);
			case MAX:
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
