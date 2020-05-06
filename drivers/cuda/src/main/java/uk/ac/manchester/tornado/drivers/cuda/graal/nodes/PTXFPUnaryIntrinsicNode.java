/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2018, 2019, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.drivers.cuda.graal.nodes;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.FloatStamp;
import org.graalvm.compiler.core.common.type.PrimitiveStamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.UnaryNode;
import org.graalvm.compiler.nodes.spi.ArithmeticLIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXArithmeticTool;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXBuiltinTool;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXLIRStmt.AssignStmt;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;

@NodeInfo(nameTemplate = "{p#operation/s}")
public class PTXFPUnaryIntrinsicNode extends UnaryNode implements ArithmeticLIRLowerable {

    protected PTXFPUnaryIntrinsicNode(ValueNode value, Operation op, JavaKind kind) {
        super(TYPE, StampFactory.forKind(kind), value);
        assert value.stamp(NodeView.DEFAULT) instanceof FloatStamp && PrimitiveStamp.getBits(value.stamp(NodeView.DEFAULT)) == kind.getBitCount();
        this.operation = op;
    }

    public static final NodeClass<PTXFPUnaryIntrinsicNode> TYPE = NodeClass.create(PTXFPUnaryIntrinsicNode.class);
    protected final Operation operation;

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

    public Operation operation() {
        return operation;
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
        PTXBuiltinTool gen = ((PTXArithmeticTool) lirGen).getGen().getPtxBuiltinTool();
        Value initialInput = builder.operand(getValue());
        Value result;

        Value auxValue = initialInput;
        Variable auxVar;

        if ((operation() == Operation.COS || operation() == Operation.SIN || operation == Operation.LOG || operation == Operation.EXP) && !((PTXKind)initialInput.getPlatformKind()).isF32()) {
            // sin, cos only operate on f32 values. We must convert
            auxVar = builder.getLIRGeneratorTool().newVariable(LIRKind.value(PTXKind.F32));
            auxValue = builder.getLIRGeneratorTool().append(new AssignStmt(auxVar, initialInput)).getResult();
        }

        switch (operation()) {
            case COS:
                result = gen.genFloatCos(auxValue);
                break;
            case FABS:
                result = gen.genFloatAbs(auxValue);
                break;
            case EXP:
                // we use e^a = 2^(a*log2(e))
                Value log2e = new ConstantValue(LIRKind.value(PTXKind.F32), JavaConstant.forFloat((float)(Math.log10(Math.exp(1)) / Math.log10(2))));
                Value aMulLog2e = lirGen.emitMul(auxValue, log2e, false);
                result = gen.genFloatExp2(aMulLog2e);
                break;
            case SIN:
                result = gen.genFloatSin(auxValue);
                break;
            case SQRT:
                result = gen.genFloatSqrt(auxValue);
                break;
            case FLOOR:
                result = gen.genFloatFloor(auxValue);
                break;
            case LOG:
                // we use  log_e(a) = log_2(a) / log_2(e)
                Variable var = builder.getLIRGeneratorTool().newVariable(LIRKind.value(PTXKind.F32));
                Value nominator = builder.getLIRGeneratorTool().append(new AssignStmt(var, gen.genFloatLog2(auxValue))).getResult();
                Value denominator = new ConstantValue(LIRKind.value(PTXKind.F32), JavaConstant.forFloat((float)(Math.log10(Math.exp(1)) / Math.log10(2))));
                result = lirGen.emitDiv(nominator, denominator, null);
                break;
            default:
                throw shouldNotReachHere();
        }

        auxVar = builder.getLIRGeneratorTool().newVariable(auxValue.getValueKind());
        auxValue = builder.getLIRGeneratorTool().append(new AssignStmt(auxVar, result)).getResult();

        if ((operation() == Operation.COS || operation() == Operation.SIN || operation == Operation.LOG || operation == Operation.EXP) && !((PTXKind)initialInput.getPlatformKind()).isF32()) {
            // sin, cos only operate on f32 values. We must convert back
            auxVar = builder.getLIRGeneratorTool().newVariable(LIRKind.value(initialInput.getPlatformKind()));
            builder.getLIRGeneratorTool().append(new AssignStmt(auxVar, auxValue));
        }

        builder.setResult(this, auxVar);
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
