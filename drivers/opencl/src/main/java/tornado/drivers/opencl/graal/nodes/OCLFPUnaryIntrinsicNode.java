package tornado.drivers.opencl.graal.nodes;

import com.oracle.graal.compiler.common.type.FloatStamp;
import com.oracle.graal.compiler.common.type.PrimitiveStamp;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.gen.ArithmeticLIRGeneratorTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.UnaryNode;
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
public class OCLFPUnaryIntrinsicNode extends UnaryNode implements ArithmeticLIRLowerable {

    protected OCLFPUnaryIntrinsicNode(ValueNode value, Operation op, JavaKind kind) {
        super(TYPE, StampFactory.forKind(kind), value);
        assert value.stamp() instanceof FloatStamp && PrimitiveStamp.getBits(value.stamp()) == kind.getBitCount();
        this.operation = op;
    }

    public static final NodeClass<OCLFPUnaryIntrinsicNode> TYPE = NodeClass.create(OCLFPUnaryIntrinsicNode.class);
    protected final Operation operation;

    public enum Operation {
        ACOS,
        ACOSH,
        ACOSPI,
        ASIN,
        ASINH,
        ASINPI,
        ATAN,
        //ATAN2,
        ATANH,
        ATANPI,
        //ATAN2PI,
        CBRT,
        CEIL,
        //COPYSIGN,
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
        //FDIM,
        FLOOR,
        //FMA,
        //FMAX,
        //FMIN,
        //FMOD,
        //FRACT,
        //FREXP,
        //HYPOT,
        ILOGB,
        //LDEXP,
        LGAMMA,
        LOG,
        LOG2,
        LOG10,
        LOG1P,
        LOGB,
        //MAD,
        //MAXMAG,
        //MINMAG,
        //MODF,
        NAN,
        //NEXTAFTER,
        //POW,
        //POWN,
        //POWR,
        //REMAINDER,
        REMQUO,
        RINT,
        //ROOTN,
        ROUND,
        RSQRT,
        SIN,
        //SINCOS,
        SINH,
        SINPI,
        SQRT,
        TAN,
        TANH,
        TANPI,
        TGAMMA,
        TRUNC
    }

    public Operation operation() {
        return operation;
    }

    public static ValueNode create(ValueNode value, Operation op, JavaKind kind) {
        ValueNode c = tryConstantFold(value, op, kind);
        if (c != null) {
            return c;
        }
        return new OCLFPUnaryIntrinsicNode(value, op, kind);
    }

    protected static ValueNode tryConstantFold(ValueNode value, Operation op, JavaKind kind) {
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
        OCLBuiltinTool gen = ((OCLArithmeticTool) lirGen).getGen().getOCLBuiltinTool();
        Value input = builder.operand(getValue());
        Value result;
        switch (operation()) {
            case FABS:
                result = gen.genFloatAbs(input);
                break;
            case EXP:
                result = gen.genFloatExp(input);
                break;
            case SQRT:
                result = gen.genFloatSqrt(input);
                break;
            case FLOOR:
                result = gen.genFloatFloor(input);
                break;
            case LOG:
                result = gen.genFloatLog(input);
                break;
            default:
                throw shouldNotReachHere();
        }
        Variable x = builder.getLIRGeneratorTool().newVariable(result.getValueKind());
        builder.getLIRGeneratorTool().append(new AssignStmt(x, result));
        builder.setResult(this, x);

    }

    private static double doCompute(double value, Operation op) {
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

    private static float doCompute(float value, Operation op) {
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
