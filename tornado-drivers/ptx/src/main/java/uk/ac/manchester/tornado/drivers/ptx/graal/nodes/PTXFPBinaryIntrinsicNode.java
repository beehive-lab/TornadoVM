/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.BinaryNode;
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
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXArithmeticTool;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXBinary;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXBuiltinTool;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt.AssignStmt;
import uk.ac.manchester.tornado.runtime.graal.nodes.interfaces.MarkFloatingPointIntrinsicsNode;

@NodeInfo(nameTemplate = "{p#operation/s}")
public class PTXFPBinaryIntrinsicNode extends BinaryNode implements ArithmeticLIRLowerable, MarkFloatingPointIntrinsicsNode {

    public static final NodeClass<PTXFPBinaryIntrinsicNode> TYPE = NodeClass.create(PTXFPBinaryIntrinsicNode.class);
    protected final Operation operation;

    protected PTXFPBinaryIntrinsicNode(ValueNode x, ValueNode y, Operation op, JavaKind kind) {
        super(TYPE, StampFactory.forKind(kind), x, y);
        this.operation = op;
    }

    public static ValueNode create(ValueNode x, ValueNode y, Operation op, JavaKind kind) {
        ValueNode c = tryConstantFold(x, y, op, kind);
        if (c != null) {
            return c;
        }
        return new PTXFPBinaryIntrinsicNode(x, y, op, kind);
    }

    protected static ValueNode tryConstantFold(ValueNode x, ValueNode y, Operation op, JavaKind kind) {
        ConstantNode result = null;

        if (x.isConstant() && y.isConstant()) {
            if (kind == JavaKind.Double) {
                double ret = doCompute(x.asJavaConstant().asDouble(), y.asJavaConstant().asDouble(), op);
                result = ConstantNode.forDouble(ret);
            } else if (kind == JavaKind.Float) {
                float ret = doCompute(x.asJavaConstant().asFloat(), y.asJavaConstant().asFloat(), op);
                result = ConstantNode.forFloat(ret);
            }
        }
        return result;
    }

    private static double doCompute(double x, double y, Operation op) {
        return switch (op) {
            case FMIN -> Math.min(x, y);
            case FMAX -> Math.max(x, y);
            case POW -> Math.pow(x, y);
            default -> throw new TornadoInternalError("unknown op %s", op);
        };
    }

    private static float doCompute(float x, float y, Operation op) {
        return switch (op) {
            case FMIN -> Math.min(x, y);
            case FMAX -> Math.max(x, y);
            case POW -> (float) Math.pow(x, y);
            default -> throw new TornadoInternalError("unknown op %s", op);
        };
    }

    @Override
    public String getOperation() {
        return operation.toString();
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        return canonical(tool, getX(), getY());
    }

    @Override
    public Stamp foldStamp(Stamp stampX, Stamp stampY) {
        return stamp(NodeView.DEFAULT);
    }

    @Override
    public void generate(NodeLIRBuilderTool builder) {
        generate(builder, builder.getLIRGeneratorTool().getArithmetic());
    }

    public Operation operation() {
        return operation;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool lirGen) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitPTXFPBinaryIntrinsic: op=%s, x=%s, y=%s", operation, x, y);
        PTXBuiltinTool gen = ((PTXArithmeticTool) lirGen).getGen().getPtxBuiltinTool();
        Value x = builder.operand(getX());
        Value y = builder.operand(getY());
        Value result;

        Variable auxVar;

        switch (operation()) {
            case FMIN:
                result = gen.genFloatMin(x, y);
                break;
            case FMAX:
                result = gen.genFloatMax(x, y);
                break;
            case POW:
                generatePow(builder, (PTXArithmeticTool) lirGen, gen, x, y);
                return;
            default:
                throw shouldNotReachHere();
        }
        auxVar = builder.getLIRGeneratorTool().newVariable(LIRKind.combine(x, y));
        builder.getLIRGeneratorTool().append(new AssignStmt(auxVar, result));

        builder.setResult(this, auxVar);

    }

    /**
     * Generates the instructions to compute the power function (x ^ y).
     *
     * Because PTX cannot perform this computation directly, we use the functions
     * available to obtain the result. The pseudocode below illustrates what we do
     * to compute z = x ^ y : if (x < 0) x = -x; z = 2^(y * log2(x)); if (x < 0 && y
     * % 2 == 1) z = -z;
     *
     * Because the log function only operates on single precision FPU , we must
     * convert the inputs and output to and from double precision FPU, if necessary.
     *
     * This snipet generates the following code:
     *
     * <code>
     * cvt.rn.f32.f64 rfi1, rfd0;
     * mul.rn.f32 rfi2, rfi1, 0F3FB8AA3B;
     * ex2.approx.f32 rfi3, rfi2;
     * </code>
     *
     */
    private void generatePow(NodeLIRBuilderTool builder, PTXArithmeticTool lirGen, PTXBuiltinTool gen, Value x, Value y) {
        LIRGeneratorTool genTool = builder.getLIRGeneratorTool();
        Value a = x;
        Value b = y;
        Variable auxVar;
        // pow only operates on f32 values. We must convert
        if (!((PTXKind) a.getPlatformKind()).isF32()) {
            auxVar = genTool.newVariable(LIRKind.value(PTXKind.F32));
            a = genTool.append(new AssignStmt(auxVar, x)).getResult();
        }
        if (!((PTXKind) b.getPlatformKind()).isF32()) {
            auxVar = genTool.newVariable(LIRKind.value(PTXKind.F32));
            b = genTool.append(new AssignStmt(auxVar, y)).getResult();
        }

        Variable signPred = genTool.newVariable(LIRKind.value(PTXKind.PRED));
        Variable remPred = genTool.newVariable(LIRKind.value(PTXKind.PRED));
        Variable auxInt = genTool.newVariable(LIRKind.value(PTXKind.S32));
        auxVar = genTool.newVariable(LIRKind.value(PTXKind.F32));

        // log2 function is only defined for (0, +infinity). In case x < 0 then we need
        // to change the sign of x.
        genTool.append(new AssignStmt(signPred, new PTXBinary.Expr(PTXAssembler.PTXBinaryOp.SETP_LT, LIRKind.value(PTXKind.F32), a, new ConstantValue(LIRKind.value(PTXKind.F32),
                PrimitiveConstant.FLOAT_0))));
        genTool.append(new PTXLIRStmt.ConditionalStatement(new AssignStmt(auxVar, new PTXBinary.Expr(PTXAssembler.PTXBinaryOp.MUL, LIRKind.value(PTXKind.F32), a, new ConstantValue(LIRKind.value(
                PTXKind.F32), JavaConstant.forFloat(-1)))), signPred, false));
        genTool.append(new PTXLIRStmt.ConditionalStatement(new AssignStmt(auxVar, a), signPred, true));

        // we use x^y = 2^(y*log2(x))
        Value log2x = genTool.append(new AssignStmt(auxVar, gen.genFloatLog2(auxVar))).getResult();
        Value aMulLog2e = genTool.append(new AssignStmt(auxVar, lirGen.genBinaryExpr(PTXAssembler.PTXBinaryOp.MUL, LIRKind.value(PTXKind.F32), b, log2x))).getResult();
        genTool.append(new AssignStmt(auxVar, gen.genFloatExp2(aMulLog2e)));

        // if x < 0 && y % 2 == 1 then result = -result
        genTool.append(new AssignStmt(auxInt, b));
        genTool.append(new AssignStmt(auxInt, new PTXBinary.Expr(PTXAssembler.PTXBinaryOp.REM, LIRKind.value(PTXKind.S32), auxInt, new ConstantValue(LIRKind.value(PTXKind.S32),
                PrimitiveConstant.INT_2))));
        genTool.append(new AssignStmt(remPred, new PTXBinary.Expr(PTXAssembler.PTXBinaryOp.SETP_EQ, LIRKind.value(PTXKind.S32), auxInt, new ConstantValue(LIRKind.value(PTXKind.S32),
                PrimitiveConstant.INT_1))));
        genTool.append(new AssignStmt(signPred, new PTXBinary.Expr(PTXAssembler.PTXBinaryOp.BITWISE_AND, LIRKind.value(PTXKind.PRED), signPred, remPred)));

        genTool.append(new PTXLIRStmt.ConditionalStatement(new AssignStmt(auxVar, new PTXBinary.Expr(PTXAssembler.PTXBinaryOp.MUL, LIRKind.value(PTXKind.F32), auxVar, new ConstantValue(LIRKind.value(
                PTXKind.F32), JavaConstant.forFloat(-1)))), signPred, false));
        genTool.append(new PTXLIRStmt.ConditionalStatement(new AssignStmt(auxVar, auxVar), signPred, true));

        // pow only operates on f32 values. We must convert back
        if (!((PTXKind) LIRKind.combine(x, y).getPlatformKind()).isF32()) {
            Variable finalVar = genTool.newVariable(LIRKind.combine(x, y));
            genTool.append(new AssignStmt(finalVar, auxVar));
            auxVar = finalVar;
        }
        builder.setResult(this, auxVar);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode x, ValueNode y) {
        ValueNode c = tryConstantFold(x, y, operation(), getStackKind());
        if (c != null) {
            return c;
        }
        return this;
    }

    public enum Operation {
        FMAX, //
        FMIN, //
        POW //
    }

}
