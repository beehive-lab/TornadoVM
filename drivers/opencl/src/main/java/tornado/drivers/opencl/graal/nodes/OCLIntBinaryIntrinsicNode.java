package tornado.drivers.opencl.graal.nodes;

import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.gen.ArithmeticLIRGeneratorTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.BinaryNode;
import com.oracle.graal.nodes.spi.ArithmeticLIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import tornado.common.exceptions.TornadoInternalError;
import tornado.drivers.opencl.graal.lir.OCLArithmeticTool;
import tornado.drivers.opencl.graal.lir.OCLBuiltinTool;
import tornado.drivers.opencl.graal.lir.OCLLIRInstruction.AssignStmt;

import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;

@NodeInfo(nameTemplate = "{p#operation}")
public class OCLIntBinaryIntrinsicNode extends BinaryNode implements ArithmeticLIRLowerable {

    protected OCLIntBinaryIntrinsicNode(ValueNode x, ValueNode y, Operation op, JavaKind kind) {
        super(TYPE, StampFactory.forKind(kind), x, y);
        this.operation = op;
    }

    public static final NodeClass<OCLIntBinaryIntrinsicNode> TYPE = NodeClass
            .create(OCLIntBinaryIntrinsicNode.class);
    protected final Operation operation;

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

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        return canonical(tool, getX(), getY());
    }

    @Override
    public Stamp foldStamp(Stamp stampX, Stamp stampY) {
        return stampX.join(stampY);
    }

    @Override
    public void generate(NodeLIRBuilderTool tool) {
        generate(tool, tool.getLIRGeneratorTool().getArithmetic());
    }

    public Operation operation() {
        return operation;
    }

    public static ValueNode create(ValueNode x, ValueNode y, Operation op, JavaKind kind) {
        ValueNode c = tryConstantFold(x, y, op, kind);
        if (c != null) {
            return c;
        }
        return new OCLIntBinaryIntrinsicNode(x, y, op, kind);
    }

    protected static ValueNode tryConstantFold(ValueNode x, ValueNode y, Operation op, JavaKind kind) {
        ConstantNode result = null;

        if (x.isConstant() && y.isConstant()) {
            if (kind == JavaKind.Int) {
                int ret = doCompute(x.asJavaConstant().asInt(), y.asJavaConstant().asInt(), op);
                result = ConstantNode.forInt(ret);
            } else if (kind == JavaKind.Long) {
                long ret = doCompute(x.asJavaConstant().asLong(), y.asJavaConstant().asLong(), op);
                result = ConstantNode.forLong(ret);
            }
        }
        return result;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool lirGen) {
        OCLBuiltinTool gen = ((OCLArithmeticTool) lirGen).getGen().getOCLBuiltinTool();
        Value x = builder.operand(getX());
        Value y = builder.operand(getY());
        Value result;
        switch (operation()) {
            case MIN:
                result = gen.genIntMin(x, y);
                break;
            case MAX:
                result = gen.genIntMax(x, y);
                break;
            default:
                throw shouldNotReachHere();
        }
        Variable var = builder.getLIRGeneratorTool().newVariable(result.getValueKind());
        builder.getLIRGeneratorTool().append(new AssignStmt(var, result));
        builder.setResult(this, var);

    }

    private static long doCompute(long x, long y, Operation op) {
        switch (op) {
            case MIN:
                return Math.min(x, y);
            case MAX:
                return Math.max(x, y);
            default:
                throw new TornadoInternalError("unknown op %s", op);
        }
    }

    private static int doCompute(int x, int y, Operation op) {
        switch (op) {
            case MIN:
                return Math.min(x, y);
            case MAX:
                return Math.max(x, y);
            default:
                throw new TornadoInternalError("unknown op %s", op);
        }
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode x, ValueNode y) {
        ValueNode c = tryConstantFold(x, y, operation(), getStackKind());
        if (c != null) {
            return c;
        }
        return this;
    }

}
