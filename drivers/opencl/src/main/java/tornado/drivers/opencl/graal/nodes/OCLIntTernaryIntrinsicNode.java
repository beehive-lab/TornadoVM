package tornado.drivers.opencl.graal.nodes;


import tornado.collections.math.TornadoMath;
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
import com.oracle.graal.nodes.calc.TernaryNode;
import com.oracle.graal.nodes.spi.ArithmeticLIRLowerable;
import com.oracle.graal.nodes.spi.NodeMappableLIRBuilder;

@NodeInfo(nameTemplate = "{p#operation}")
public class OCLIntTernaryIntrinsicNode extends TernaryNode implements ArithmeticLIRLowerable {

	protected OCLIntTernaryIntrinsicNode(ValueNode x, ValueNode y, ValueNode z, Operation op, Kind kind) {
		super(TYPE, StampFactory.forKind(kind), x, y, z);
		this.operation = op;
	}

	public static final NodeClass<OCLIntTernaryIntrinsicNode>	TYPE	= NodeClass
																			.create(OCLIntTernaryIntrinsicNode.class);
	protected final Operation								operation;

	public enum Operation {
		CLAMP,
		MAD_HI,
		MAD_SAT,
		MAD24
	}

	public Operation operation() {
		return operation;
	}

	public static ValueNode create(ValueNode x, ValueNode y, ValueNode z, Operation op, Kind kind) {
		ValueNode c = tryConstantFold(x, y, z, op, kind);
		if (c != null) {
			return c;
		}
		return new OCLIntTernaryIntrinsicNode(x, y, z, op, kind);
	}

	protected static ValueNode tryConstantFold(ValueNode x, ValueNode y, ValueNode z, Operation op, Kind kind) {
		ConstantNode result = null;

		if (x.isConstant() && y.isConstant() && z.isConstant()) {
			if (kind == Kind.Int) {
				int ret = doCompute(x.asJavaConstant().asInt(),y.asJavaConstant().asInt(),z.asJavaConstant().asInt(), op);
				result = ConstantNode.forInt(ret);
			} else if (kind == Kind.Long) {
				long ret = doCompute(x.asJavaConstant().asLong(),y.asJavaConstant().asLong(),z.asJavaConstant().asInt(), op);
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
		Value z = builder.operand(getZ());
		Value result;
		switch (operation()) {
			case CLAMP:
				result = gen.emitIntClamp(x, y, z);
				break;
			case MAD24:
				result = gen.emitIntMad24(x, y, z);
				break;
			case MAD_HI:
				result = gen.emitIntMadHi(x, y, z);
				break;
			case MAD_SAT:
				result = gen.emitIntMadSat(x, y, z);
				break;
			default:
				throw GraalInternalError.shouldNotReachHere();
		}
		builder.setResult(this, result);

	}

	private static long doCompute(long x, long y, long z, Operation op) {
		switch (op) {
			default:
				throw new GraalInternalError("unknown op %s", op);
		}
	}

	private static int doCompute(int x, int y, int z, Operation op) {
		switch (op) {
			case CLAMP:
				return TornadoMath.clamp(x, y, z);
				
			default:
				throw new GraalInternalError("unknown op %s", op);
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
