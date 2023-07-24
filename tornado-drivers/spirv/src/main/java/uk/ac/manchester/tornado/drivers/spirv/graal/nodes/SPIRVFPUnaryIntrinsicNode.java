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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
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

    public SPIRVUnaryOperation getIntrinsicOperation() {
        return operation;
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
            case ASIN:
                result = gen.genFloatASin(input);
                break;
            case ACOS:
                result = gen.genFloatACos(input);
                break;
            case FABS:
                result = gen.genFloatAbs(input);
                break;
            case EXP:
                result = gen.genFloatExp(input);
                break;
            case SIGN:
                result = gen.generateSign(input);
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
            case COS:
                result = gen.genFloatCos(input);
                break;
            case SIN:
                result = gen.genFloatSin(input);
                break;
            case ATAN:
                result = gen.genFloatATan(input);
                break;
            case TAN:
                result = gen.genFloatTan(input);
                break;
            case TANH:
                result = gen.genFloatTanh(input);
                break;
            case RADIANS:
                result = gen.genFloatRadians(input);
                break;
            case COSPI:
                result = gen.genFloatCospi(input);
                break;
            case SINPI:
                result = gen.genFloatSinpi(input);
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
            case ASIN:
                return Math.asin(value);
            case ACOS:
                return Math.acos(value);
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
            case COS:
                return Math.cos(value);
            case SIN:
                return Math.sin(value);
            case TAN:
                return Math.tan(value);
            default:
                throw new TornadoInternalError("unable to compute op %s", op);
        }
    }

    private static float doCompute(float value, SPIRVUnaryOperation op) {
        switch (op) {
            case ASIN:
                return (float) Math.asin(value);
            case ACOS:
                return (float) Math.acos(value);
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
            case COS:
                return (float) Math.cos(value);
            case SIN:
                return (float) Math.sin(value);
            case TAN:
                return (float) Math.tan(value);
            default:
                throw new TornadoInternalError("unable to compute op %s", op);
        }
    }
}
