/*
 * Copyright (c) 2021, 2022-2023, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.ptx.graal.nodes;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.UnaryNode;
import jdk.graal.compiler.nodes.spi.ArithmeticLIRLowerable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXLIRGenerator;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXArithmeticTool;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXBinary;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXBuiltinTool;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt.AssignStmt;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXTernary;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXUnary;
import uk.ac.manchester.tornado.runtime.graal.nodes.interfaces.MarkFloatingPointIntrinsicsNode;

@NodeInfo(nameTemplate = "{p#operation/s}")
public class PTXFPUnaryIntrinsicNode extends UnaryNode implements ArithmeticLIRLowerable, MarkFloatingPointIntrinsicsNode {

    public static final NodeClass<PTXFPUnaryIntrinsicNode> TYPE = NodeClass.create(PTXFPUnaryIntrinsicNode.class);
    protected final Operation operation;

    protected PTXFPUnaryIntrinsicNode(ValueNode value, Operation op, JavaKind kind) {
        super(TYPE, StampFactory.forKind(kind), value);
        assert value.stamp(NodeView.DEFAULT) instanceof FloatStamp && PrimitiveStamp.getBits(value.stamp(NodeView.DEFAULT)) == kind.getBitCount();
        this.operation = op;
    }

    public static ValueNode create(ValueNode value, Operation op, JavaKind kind) {
        ValueNode c = tryConstantFold(value, op, kind);
        if (c != null) {
            return c;
        }
        return new PTXFPUnaryIntrinsicNode(value, op, kind);
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
    // @formatter:on

    private static double doCompute(double value, Operation op) {
        return switch (op) {
            case FABS -> Math.abs(value);
            case EXP -> Math.exp(value);
            case SQRT -> Math.sqrt(value);
            case FLOOR -> Math.floor(value);
            case LOG -> Math.log(value);
            default -> throw new TornadoInternalError("unable to compute op %s", op);
        };
    }

    private static float doCompute(float value, Operation op) {
        return switch (op) {
            case FABS -> Math.abs(value);
            case EXP -> (float) Math.exp(value);
            case SQRT -> (float) Math.sqrt(value);
            case FLOOR -> (float) Math.floor(value);
            case LOG -> (float) Math.log(value);
            default -> throw new TornadoInternalError("unable to compute op %s", op);
        };
    }

    @Override
    public String getOperation() {
        return operation.toString();
    }

    public Operation operation() {
        return operation;
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
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitPTXFPUnaryIntrinsic: op=%s, x=%s", operation, getValue());
        PTXArithmeticTool lirGenPTX = (PTXArithmeticTool) lirGen;
        PTXBuiltinTool gen = lirGenPTX.getGen().getPtxBuiltinTool();
        Value initialInput = builder.operand(getValue());
        Value result;

        Value auxValue = initialInput;
        Variable auxVar;

        if (shouldConvertInput(initialInput)) {
            // sin, cos only operate on f32 values. We must convert
            auxVar = builder.getLIRGeneratorTool().newVariable(LIRKind.value(PTXKind.F32));
            auxValue = builder.getLIRGeneratorTool().append(new AssignStmt(auxVar, initialInput)).getResult();
        }

        switch (operation()) {
            case ATAN:
                result = gen.genFloatATan(auxValue);
                break;
            case CEIL:
                result = gen.genFloatCeil(auxValue);
                break;
            case COS:
                result = gen.genFloatCos(auxValue);
                break;
            case FABS:
                result = gen.genFloatAbs(auxValue);
                break;
            case EXP:
                generateExp(builder, lirGenPTX, gen, initialInput);
                return;
            case SIGN:
                generateSign(builder, lirGenPTX, initialInput);
                return;
            case SIN:
                result = gen.genFloatSin(auxValue);
                break;
            case SQRT:
                result = gen.genFloatSqrt(auxValue);
                break;
            case TAN:
                generateTan(builder, lirGenPTX, gen, initialInput);
                return;
            case TANH:
                result = gen.genFloatTanh(auxValue);
                break;
            case FLOOR:
                result = gen.genFloatFloor(auxValue);
                break;
            case LOG:
                generateLog(builder, lirGenPTX, gen, initialInput);
                return;
            case RADIANS:
                Value constantForRadians = getConstantValueForRadians(initialInput);
                result = gen.genFloatRadians(constantForRadians, auxValue);
                break;
            case COSPI:
                result = gen.genFloatCosPI(auxValue, createConstantForPI(), builder.getLIRGeneratorTool().newVariable(LIRKind.value(PTXKind.F32)), builder.getLIRGeneratorTool());
                break;
            case SINPI:
                result = gen.genFloatSinPI(auxValue, createConstantForPI(), builder.getLIRGeneratorTool().newVariable(LIRKind.value(PTXKind.F32)), builder.getLIRGeneratorTool());
                break;
            default:
                throw shouldNotReachHere();
        }

        auxVar = builder.getLIRGeneratorTool().newVariable(auxValue.getValueKind());
        auxValue = builder.getLIRGeneratorTool().append(new AssignStmt(auxVar, result)).getResult();

        if (shouldConvertInput(initialInput)) {
            // sin, cos only operate on f32 values. We must convert back
            auxVar = builder.getLIRGeneratorTool().newVariable(LIRKind.value(initialInput.getPlatformKind()));
            builder.getLIRGeneratorTool().append(new AssignStmt(auxVar, auxValue));
        }

        builder.setResult(this, auxVar);
    }

    private void generateTan(NodeLIRBuilderTool builder, PTXArithmeticTool lirGen, PTXBuiltinTool gen, Value x) {
        Value auxValue = x;
        Variable auxVar;
        if (shouldConvertInput(x)) {
            auxVar = builder.getLIRGeneratorTool().newVariable(LIRKind.value(PTXKind.F32));
            auxValue = builder.getLIRGeneratorTool().append(new AssignStmt(auxVar, x)).getResult();
        }

        // we use tan(a) = sin(a) / cos(a)
        Variable sinVar = builder.getLIRGeneratorTool().newVariable(LIRKind.value(PTXKind.F32));
        Variable cosVar = builder.getLIRGeneratorTool().newVariable(LIRKind.value(PTXKind.F32));
        Value sin = builder.getLIRGeneratorTool().append(new AssignStmt(sinVar, gen.genFloatSin(auxValue))).getResult();
        Value cos = builder.getLIRGeneratorTool().append(new AssignStmt(cosVar, gen.genFloatCos(auxValue))).getResult();
        Value result = lirGen.emitDiv(sin, cos, null);

        if (shouldConvertInput(x)) {
            auxVar = builder.getLIRGeneratorTool().newVariable(LIRKind.value(x.getPlatformKind()));
            result = builder.getLIRGeneratorTool().append(new AssignStmt(auxVar, result)).getResult();
        }
        builder.setResult(this, result);
    }

    /**
     * Generates the instructions to compute a signum function in Java: signum(x),
     * where x can be a NaN number or a positive or negative value of type float or
     * double.
     *
     * The intrinsic follows the implementation in Java and calculates first if the
     * input value is zero or a NaN value. If that condition is true, the result of
     * this intrinsic will take the input value. Otherwise, it takes the value of
     * the copySign function.
     *
     * return (x == 0.0f || Float.isNaN(x))? x : copySign(1.0f, x);
     */
    public void generateSign(NodeLIRBuilderTool builder, PTXArithmeticTool lirGen, Value x) {
        PTXLIRGenerator ptxlirGenerator = (PTXLIRGenerator) builder.getLIRGeneratorTool();

        Variable equalZeroPred = ptxlirGenerator.newVariable(LIRKind.value(PTXKind.PRED));
        ptxlirGenerator.append(new AssignStmt(equalZeroPred, new PTXBinary.Expr(PTXAssembler.PTXBinaryOp.SETP_EQ, LIRKind.value(PTXKind.F32), x, new ConstantValue(LIRKind.value(PTXKind.F32),
                PrimitiveConstant.FLOAT_0))));

        Variable isNanPred = ptxlirGenerator.newVariable(LIRKind.value(PTXKind.PRED));
        ptxlirGenerator.append(new PTXLIRStmt.AssignStmt(isNanPred, new PTXUnary.Expr(PTXAssembler.PTXUnaryOp.TESTP_NOTANUMBER, LIRKind.value(x.getPlatformKind()), x)));

        Value orPred = lirGen.emitOr(isNanPred, equalZeroPred);

        ConstantValue constantOne = null;
        if (((PTXKind) x.getPlatformKind()).isF32()) {
            constantOne = new ConstantValue(LIRKind.value(PTXKind.F32), PrimitiveConstant.FLOAT_1);
        } else if (((PTXKind) x.getPlatformKind()).isF64()) {
            constantOne = new ConstantValue(LIRKind.value(PTXKind.F64), PrimitiveConstant.DOUBLE_1);
        } else {
            shouldNotReachHere("The kind of the input parameter in the signum method is not float or double.");
        }
        Value copySign = lirGen.emitMathCopySign(constantOne, x);

        Variable result = builder.getLIRGeneratorTool().newVariable(LIRKind.value(x.getPlatformKind()));
        ptxlirGenerator.append(new PTXLIRStmt.AssignStmt(result, new PTXTernary.Expr(PTXAssembler.PTXTernaryOp.SELP, LIRKind.value(x.getPlatformKind()), x, copySign, orPred)));

        builder.setResult(this, result);
    }

    /**
     * Generates the instructions to compute exponential function in Java: e ^ a,
     * where e is Euler's constant and a is an arbitrary number.
     *
     * Because PTX cannot perform this computation directly, we use the functions
     * available to obtain the result. b = e ^ a = 2^(a * log2(e))
     *
     * Because the log function only operates on single precision FPU, we must
     * convert the input and output to and from double precision FPU, if necessary.
     */
    public void generateExp(NodeLIRBuilderTool builder, PTXArithmeticTool lirGen, PTXBuiltinTool gen, Value x) {
        Value auxValue = x;
        Variable auxVar;
        if (shouldConvertInput(x)) {
            auxVar = builder.getLIRGeneratorTool().newVariable(LIRKind.value(PTXKind.F32));
            auxValue = builder.getLIRGeneratorTool().append(new AssignStmt(auxVar, x)).getResult();
        }

        // we use e^a = 2^(a*log2(e))
        Value log2e = new ConstantValue(LIRKind.value(PTXKind.F32), JavaConstant.forFloat((float) (Math.log10(Math.exp(1)) / Math.log10(2))));
        Value aMulLog2e = lirGen.emitMul(auxValue, log2e, false);
        Value result = gen.genFloatExp2(aMulLog2e);

        auxVar = builder.getLIRGeneratorTool().newVariable(auxValue.getValueKind());
        auxValue = builder.getLIRGeneratorTool().append(new AssignStmt(auxVar, result)).getResult();

        if (shouldConvertInput(x)) {
            auxVar = builder.getLIRGeneratorTool().newVariable(LIRKind.value(x.getPlatformKind()));
            auxValue = builder.getLIRGeneratorTool().append(new AssignStmt(auxVar, auxValue)).getResult();
        }
        builder.setResult(this, auxValue);
    }

    /**
     * Generates the instructions to compute logarithmic function in Java: log_e(a),
     * where e is Euler's constant and a is an arbitrary number.
     *
     * Because PTX cannot perform this computation directly, we use the log_2
     * function to obtain the result. b = log_e(a) = log_2(a) / log_2(e)
     *
     * Because the log function only operates on single precision FPU, we must
     * convert the input and output to and from double precision FPU, if necessary.
     */
    public void generateLog(NodeLIRBuilderTool builder, PTXArithmeticTool lirGen, PTXBuiltinTool gen, Value x) {
        Value auxValue = x;
        Variable auxVar;
        if (shouldConvertInput(x)) {
            auxVar = builder.getLIRGeneratorTool().newVariable(LIRKind.value(PTXKind.F32));
            auxValue = builder.getLIRGeneratorTool().append(new AssignStmt(auxVar, x)).getResult();
        }

        // we use log_e(a) = log_2(a) / log_2(e)
        Variable var = builder.getLIRGeneratorTool().newVariable(LIRKind.value(PTXKind.F32));
        Value nominator = builder.getLIRGeneratorTool().append(new AssignStmt(var, gen.genFloatLog2(auxValue))).getResult();
        Value denominator = new ConstantValue(LIRKind.value(PTXKind.F32), JavaConstant.forFloat((float) (Math.log10(Math.exp(1)) / Math.log10(2))));
        Value result = lirGen.emitDiv(nominator, denominator, null);

        if (shouldConvertInput(x)) {
            auxVar = builder.getLIRGeneratorTool().newVariable(LIRKind.value(x.getPlatformKind()));
            result = builder.getLIRGeneratorTool().append(new AssignStmt(auxVar, result)).getResult();
        }
        builder.setResult(this, result);
    }

    private boolean shouldConvertInput(Value input) {
        return (operation() == Operation.TAN || operation() == Operation.TANH || operation() == Operation.COS || operation() == Operation.COSPI || operation() == Operation.SIN || operation() == Operation.SINPI || operation() == Operation.EXP || operation() == Operation.LOG) && !((PTXKind) input
                .getPlatformKind()).isF32();
    }

    /**
     * Returns a {@link ConstantValue} that corresponds to the (pi/180) value which
     * is represented by
     * {@value uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants#DEGREES_TO_RADIANS}.
     * This value is used as the first parameter for the multiplication (mul.rn.f32)
     * with a float value in order to convert an angle measured in degrees to an
     * approximately equivalent angle measured in radians.
     */
    public Value getConstantValueForRadians(Value x) {
        ConstantValue constantValue = null;
        if (((PTXKind) x.getPlatformKind()).isF32()) {
            constantValue = new ConstantValue(LIRKind.value(PTXKind.F32), JavaConstant.forFloat(PTXAssemblerConstants.DEGREES_TO_RADIANS));
        } else if (((PTXKind) x.getPlatformKind()).isF64()) {
            constantValue = new ConstantValue(LIRKind.value(PTXKind.F64), JavaConstant.forDouble(PTXAssemblerConstants.DEGREES_TO_RADIANS));
        } else {
            shouldNotReachHere("The kind of the input parameter in the radian method is not float.");
        }

        return constantValue;
    }

    public Value createConstantForPI() {
        return new ConstantValue(LIRKind.value(PTXKind.F32), JavaConstant.forFloat(PTXAssemblerConstants.PI));
    }

    // @formatter:off
    public enum Operation {
        ATAN,
        CEIL,
        COS,
        EXP,
        FABS,
        FLOOR,
        LOG,
        RADIANS,
        SIGN,
        SIN,
        SQRT,
        TAN,
        TANH,
        COSPI,
        SINPI,
    }
    // @formatter:on
}
