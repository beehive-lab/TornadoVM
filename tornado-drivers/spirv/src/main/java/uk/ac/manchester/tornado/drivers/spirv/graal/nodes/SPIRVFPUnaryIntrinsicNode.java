/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2022-2023, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2009-2021, Oracle and/or its affiliates. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.spirv.graal.nodes;

import org.graalvm.compiler.core.common.type.FloatStamp;
import org.graalvm.compiler.core.common.type.PrimitiveStamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.UnaryNode;
import org.graalvm.compiler.nodes.spi.ArithmeticLIRLowerable;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.lir.SPIRVArithmeticTool;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVBuiltinTool;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIRStmt;
import uk.ac.manchester.tornado.runtime.graal.nodes.interfaces.MarkFloatingPointIntrinsicsNode;

@NodeInfo(nameTemplate = "{p#operation/s}")
public class SPIRVFPUnaryIntrinsicNode extends UnaryNode implements ArithmeticLIRLowerable, MarkFloatingPointIntrinsicsNode {

    public static final NodeClass<SPIRVFPUnaryIntrinsicNode> TYPE = NodeClass.create(SPIRVFPUnaryIntrinsicNode.class);
    protected final SPIRVUnaryOperation operation;

    protected SPIRVFPUnaryIntrinsicNode(ValueNode value, SPIRVUnaryOperation op, JavaKind kind) {
        super(TYPE, StampFactory.forKind(kind), value);
        assert value.stamp(NodeView.DEFAULT) instanceof FloatStamp && PrimitiveStamp.getBits(value.stamp(NodeView.DEFAULT)) == kind.getBitCount();
        this.operation = op;
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

    private static double computeAcosh(double value) {
        return Math.log(value + Math.sqrt(value * value - 1));
    }

    private static float computeAcosh(float value) {
        return (float) Math.log(value + Math.sqrt(value * value - 1));
    }

    private static double computeAsinh(double value) {
        return Math.log(value + Math.sqrt(value * value + 1));
    }

    private static float computeAsinh(float value) {
        return (float) Math.log(value + Math.sqrt(value * value + 1));
    }

    private static double doCompute(double value, SPIRVUnaryOperation op) {
        return switch (op) {
            case ACOS -> Math.acos(value);
            case ACOSH -> computeAcosh(value);
            case ASIN -> Math.asin(value);
            case ASINH -> computeAsinh(value);
            case FABS -> Math.abs(value);
            case EXP -> Math.exp(value);
            case SQRT -> Math.sqrt(value);
            case FLOOR -> Math.floor(value);
            case LOG -> Math.log(value);
            case COS -> Math.cos(value);
            case SIN -> Math.sin(value);
            case TAN -> Math.tan(value);
            default -> throw new TornadoInternalError("unable to compute op %s", op);
        };
    }

    private static float doCompute(float value, SPIRVUnaryOperation op) {
        return switch (op) {
            case ACOS -> (float) Math.acos(value);
            case ACOSH -> computeAcosh(value);
            case ASIN -> (float) Math.asin(value);
            case ASINH -> computeAsinh(value);
            case FABS -> Math.abs(value);
            case EXP -> (float) Math.exp(value);
            case SQRT -> (float) Math.sqrt(value);
            case FLOOR -> (float) Math.floor(value);
            case LOG -> (float) Math.log(value);
            case COS -> (float) Math.cos(value);
            case SIN -> (float) Math.sin(value);
            case TAN -> (float) Math.tan(value);
            default -> throw new TornadoInternalError("unable to compute op %s", op);
        };
    }

    @Override
    public String getOperation() {
        return operation.toString();
    }

    public SPIRVUnaryOperation getIntrinsicOperation() {
        return operation;
    }

    public SPIRVUnaryOperation operation() {
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
        SPIRVBuiltinTool gen = ((SPIRVArithmeticTool) lirGen).getGen().getSpirvBuiltinTool();
        Value input = builder.operand(getValue());
        Value result = switch (operation()) {
            case ACOS -> gen.genFloatACos(input);
            case ACOSH -> gen.genFloatACosh(input);
            case ASIN -> gen.genFloatASin(input);
            case ASINH -> gen.genFloatASinh(input);
            case CEIL -> gen.genFloatCeil(input);
            case FABS -> gen.genFloatAbs(input);
            case EXP -> gen.genFloatExp(input);
            case SIGN -> gen.generateSign(input);
            case SQRT -> gen.genFloatSqrt(input);
            case FLOOR -> gen.genFloatFloor(input);
            case LOG -> gen.genFloatLog(input);
            case COS -> gen.genFloatCos(input);
            case SIN -> gen.genFloatSin(input);
            case ATAN -> gen.genFloatATan(input);
            case TAN -> gen.genFloatTan(input);
            case TANH -> gen.genFloatTanh(input);
            case RADIANS -> gen.genFloatRadians(input);
            case COSPI -> gen.genFloatCospi(input);
            case SINPI -> gen.genFloatSinpi(input);
            default -> throw new RuntimeException("Operation not supported");
        };
        Variable assignResult = builder.getLIRGeneratorTool().newVariable(result.getValueKind());
        builder.getLIRGeneratorTool().append(new SPIRVLIRStmt.AssignStmt(assignResult, result));
        builder.setResult(this, assignResult);
    }

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
        RADIANS,
        REMQUO,
        RINT,
        ROUND,
        RSQRT,
        SIGN,
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
    //@formatter:on

}
