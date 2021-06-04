package uk.ac.manchester.tornado.drivers.spirv.graal.nodes;

import org.graalvm.compiler.core.common.type.FloatStamp;
import org.graalvm.compiler.core.common.type.PrimitiveStamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.UnaryNode;
import org.graalvm.compiler.nodes.spi.ArithmeticLIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.lir.SPIRVArithmeticTool;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVBuiltinTool;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIRStmt;
import uk.ac.manchester.tornado.runtime.graal.phases.MarkFloatingPointIntrinsicsNode;

@NodeInfo(nameTemplate = "{p#operation/s}")
public class SPIRVFPUnaryIntrinsicNode extends UnaryNode implements ArithmeticLIRLowerable, MarkFloatingPointIntrinsicsNode {

    public static final NodeClass<SPIRVFPUnaryIntrinsicNode> TYPE = NodeClass.create(SPIRVFPUnaryIntrinsicNode.class);
    protected final SPIRVUnaryOperation operation;

    // @formatter:off
    public enum SPIRVUnaryOperation {
        ACOS,
        ACOSH,
        ACOSPI,
        ASIN,
        ASINH,
        ASINPI,
        ATAN,
        ATANH,
        ATANPI,
        CBRT,
        CEIL,
        COS,
        COSH,
        COSPI,
        ERFC,
        ERF,
        EXP,
        EXP2,
        EXP10,
        EXPM1,
        FABS,
        FLOOR,
        ILOGB,
        LGAMMA,
        LOG,
        LOG2,
        LOG10,
        LOG1P,
        LOGB,
        NAN,
        REMQUO,
        RINT,
        ROUND,
        RSQRT,
        SIN,
        SINH,
        SINPI,
        SQRT,
        TAN,
        TANH,
        TANPI,
        TGAMMA,
        TRUNC
    }
    // @formatter:on

    protected SPIRVFPUnaryIntrinsicNode(ValueNode value, SPIRVUnaryOperation op, JavaKind kind) {
        super(TYPE, StampFactory.forKind(kind), value);
        assert value.stamp(NodeView.DEFAULT) instanceof FloatStamp && PrimitiveStamp.getBits(value.stamp(NodeView.DEFAULT)) == kind.getBitCount();
        this.operation = op;
    }

    @Override
    public String getOperation() {
        return operation.toString();
    }

    public SPIRVUnaryOperation operation() {
        return operation;
    }

    public static ValueNode create(ValueNode value, SPIRVUnaryOperation op, JavaKind kind) {
        ValueNode c = tryConstantFold(value, op, kind);
        if (c != null) {
            return c;
        }
        return new SPIRVFPUnaryIntrinsicNode(value, op, kind);
    }

    protected static ValueNode tryConstantFold(ValueNode value, SPIRVUnaryOperation op, JavaKind kind) {
        ConstantNode result = null;

        if (value.isConstant()) {
            if (kind == JavaKind.Double) {
                double ret = doCompute(value.asJavaConstant().asDouble(), op);
                result = ConstantNode.forDouble(ret);
            } else if (kind == JavaKind.Float) {
                float ret = doCompute(value.asJavaConstant().asFloat(), op);
                result = ConstantNode.forFloat(ret);
            }
        }
        return result;
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forValue) {
        ValueNode c = tryConstantFold(forValue, operation(), forValue.getStackKind());
        if (c != null) {
            return c;
        }
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool lirGen) {

        SPIRVBuiltinTool gen = ((SPIRVArithmeticTool) lirGen).getGen().getSpirvBuiltinTool();
        Value input = builder.operand(getValue());
        Value result;
        switch (operation()) {
            case COS:
                result = gen.genFloatCos(input);
                break;
            default:
                throw new RuntimeException("Operation not supported");
        }
        Variable assignResult = builder.getLIRGeneratorTool().newVariable(result.getValueKind());
        builder.getLIRGeneratorTool().append(new SPIRVLIRStmt.AssignStmt(assignResult, result));
        builder.setResult(this, assignResult);
    }

    private static double doCompute(double value, SPIRVUnaryOperation op) {
        switch (op) {
            case FABS:
                return Math.abs(value);
            case EXP:
                return Math.exp(value);
            case SQRT:
                return Math.sqrt(value);
            case FLOOR:
                return Math.floor(value);
            case LOG:
                return Math.log(value);
            default:
                throw new TornadoInternalError("unable to compute op %s", op);
        }
    }

    private static float doCompute(float value, SPIRVUnaryOperation op) {
        switch (op) {
            case FABS:
                return Math.abs(value);
            case EXP:
                return (float) Math.exp(value);
            case SQRT:
                return (float) Math.sqrt(value);
            case FLOOR:
                return (float) Math.floor(value);
            case LOG:
                return (float) Math.log(value);
            default:
                throw new TornadoInternalError("unable to compute op %s", op);
        }
    }

}
