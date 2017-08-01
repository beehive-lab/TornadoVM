/* 
 * Copyright 2012 James Clarkson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tornado.drivers.opencl.graal.nodes;

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
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.UnaryNode;
import org.graalvm.compiler.nodes.spi.ArithmeticLIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import tornado.common.exceptions.TornadoInternalError;
import tornado.drivers.opencl.graal.lir.OCLArithmeticTool;
import tornado.drivers.opencl.graal.lir.OCLBuiltinTool;
import tornado.drivers.opencl.graal.lir.OCLLIRStmt.AssignStmt;

import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;

@NodeInfo(nameTemplate = "{p#operation/s}")
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
