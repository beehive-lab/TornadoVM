/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.metal.graal.nodes;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;

import tornado.graal.compiler.core.common.type.FloatStamp;
import tornado.graal.compiler.core.common.type.PrimitiveStamp;
import tornado.graal.compiler.core.common.type.StampFactory;
import tornado.graal.compiler.graph.Node;
import tornado.graal.compiler.graph.NodeClass;
import tornado.graal.compiler.lir.Variable;
import tornado.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import tornado.graal.compiler.nodeinfo.NodeInfo;
import tornado.graal.compiler.nodes.ConstantNode;
import tornado.graal.compiler.nodes.NodeView;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.calc.UnaryNode;
import tornado.graal.compiler.nodes.spi.ArithmeticLIRLowerable;
import tornado.graal.compiler.nodes.spi.CanonicalizerTool;
import tornado.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalArithmeticTool;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalBuiltinTool;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalLIRStmt.AssignStmt;
import uk.ac.manchester.tornado.runtime.graal.nodes.interfaces.MarkFloatingPointIntrinsicsNode;

@NodeInfo(nameTemplate = "{p#operation/s}")
public class MetalFPUnaryIntrinsicNode extends UnaryNode implements ArithmeticLIRLowerable, MarkFloatingPointIntrinsicsNode {

    public static final NodeClass<MetalFPUnaryIntrinsicNode> TYPE = NodeClass.create(MetalFPUnaryIntrinsicNode.class);
    protected final Operation operation;

    protected MetalFPUnaryIntrinsicNode(ValueNode value, Operation op, JavaKind kind) {
        super(TYPE, StampFactory.forKind(kind), value);
        assert value.stamp(NodeView.DEFAULT) instanceof FloatStamp && PrimitiveStamp.getBits(value.stamp(NodeView.DEFAULT)) == kind.getBitCount();
        this.operation = op;
    }

    public static ValueNode create(ValueNode value, Operation op, JavaKind kind) {
        ValueNode c = tryConstantFold(value, op, kind);
        if (c != null) {
            return c;
        }
        return new MetalFPUnaryIntrinsicNode(value, op, kind);
    }

    protected static ValueNode tryConstantFold(ValueNode value, Operation op, JavaKind kind) {
        ConstantNode result = null;

        if (value.isConstant()) {
            if (kind == JavaKind.Double) {
                Double ret = fold(value.asJavaConstant().asDouble(), op);
                if (ret != null) {
                    result = ConstantNode.forDouble(ret);
                }
            } else if (kind == JavaKind.Float) {
                Double ret = fold(value.asJavaConstant().asFloat(), op);
                if (ret != null) {
                    result = ConstantNode.forFloat(ret.floatValue());
                }
            }
        }
        return result;
    }

    /**
     * The value of {@code op} at a constant, or null when the operation has no exact Java
     * equivalent; the node is then left for the device to compute. Float constants are folded in
     * double precision and rounded once.
     */
    private static Double fold(double v, Operation op) {
        return switch (op) {
            case ACOS -> Math.acos(v);
            case ACOSH -> Math.log(v + Math.sqrt(v * v - 1));
            case ASIN -> Math.asin(v);
            case ASINH -> Math.log(v + Math.sqrt(v * v + 1));
            case ATAN -> Math.atan(v);
            case ATANH -> 0.5 * Math.log((1 + v) / (1 - v));
            case CBRT -> Math.cbrt(v);
            case CEIL -> Math.ceil(v);
            case COS -> Math.cos(v);
            case COSH -> Math.cosh(v);
            case COSPI -> Math.cos(Math.PI * v);
            case EXP -> Math.exp(v);
            case EXP2 -> Math.pow(2, v);
            case EXP10 -> Math.pow(10, v);
            case EXPM1 -> Math.expm1(v);
            case FABS -> Math.abs(v);
            case FLOOR -> Math.floor(v);
            case LOG -> Math.log(v);
            case LOG2 -> Math.log(v) / Math.log(2);
            case LOG10 -> Math.log10(v);
            case LOG1P -> Math.log1p(v);
            case RADIANS -> Math.toRadians(v);
            case RINT -> Math.rint(v);
            case ROUND -> Math.signum(v) * Math.floor(Math.abs(v) + 0.5);
            case RSQRT -> 1 / Math.sqrt(v);
            case SIN -> Math.sin(v);
            case SINH -> Math.sinh(v);
            case SINPI -> Math.sin(Math.PI * v);
            case SQRT -> Math.sqrt(v);
            case TAN -> Math.tan(v);
            case TANH -> Math.tanh(v);
            case TANPI -> Math.tan(Math.PI * v);
            case TRUNC -> v < 0 ? Math.ceil(v) : Math.floor(v);
            default -> null;
        };
    }

    @Override
    public String getOperation() {
        return operation.toString();
    }

    public Operation getIntrinsicOperation() {
        return operation;
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
        MetalBuiltinTool gen = ((MetalArithmeticTool) lirGen).getGen().getMetalBuiltinTool();
        Value input = builder.operand(getValue());
        Value result = switch (operation()) {
            case ACOSH -> gen.genFloatACosh(input);
            case ASIN -> gen.genFloatASin(input);
            case ASINH -> gen.genFloatASinh(input);
            case ACOS -> gen.genFloatACos(input);
            case ATAN -> gen.genFloatATan(input);
            case CEIL -> gen.genFloatCeil(input);
            case COS -> gen.genFloatCos(input);
            case FABS -> gen.genFloatAbs(input);
            case EXP -> gen.genFloatExp(input);
            case SIN -> gen.genFloatSin(input);
            case SQRT -> gen.genFloatSqrt(input);
            case TAN -> gen.genFloatTan(input);
            case TANH -> gen.genFloatTanh(input);
            case FLOOR -> gen.genFloatFloor(input);
            case LOG -> gen.genFloatLog(input);
            case RADIANS -> gen.genFloatRadians(input);
            case COSPI -> gen.genFloatCosPI(input);
            case SINPI -> gen.genFloatSinPI(input);
            default -> throw shouldNotReachHere();
        };
        Variable x = builder.getLIRGeneratorTool().newVariable(result.getValueKind());
        builder.getLIRGeneratorTool().append(new AssignStmt(x, result));
        builder.setResult(this, x);

    }

    // @formatter:off
    public enum Operation {
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

}
