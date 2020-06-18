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
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.BinaryNode;
import org.graalvm.compiler.nodes.spi.ArithmeticLIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXArithmeticTool;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXBuiltinTool;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXLIRStmt.AssignStmt;
import uk.ac.manchester.tornado.runtime.graal.phases.MarkOCLFPIntrinsicsNode;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;

@NodeInfo(nameTemplate = "{p#operation/s}")
public class PTXFPBinaryIntrinsicNode extends BinaryNode implements ArithmeticLIRLowerable, MarkOCLFPIntrinsicsNode {

    protected PTXFPBinaryIntrinsicNode(ValueNode x, ValueNode y, Operation op, JavaKind kind) {
        super(TYPE, StampFactory.forKind(kind), x, y);
        this.operation = op;
    }

    public static final NodeClass<PTXFPBinaryIntrinsicNode> TYPE = NodeClass.create(PTXFPBinaryIntrinsicNode.class);
    protected final Operation operation;

    // @formatter:off
    public enum Operation {
        ATAN2, 
        ATAN2PI, 
        COPYSIGN, 
        FDIM, 
        FMA, 
        FMAX, 
        FMIN, 
        FMOD, 
        FRACT, 
        FREXP, 
        HYPOT, 
        LDEXP, 
        MAD, 
        MAXMAG, 
        MINMAG, 
        MODF, 
        NEXTAFTER, 
        POW, 
        POWN, 
        POWR, 
        REMAINDER, 
        REMQUO, 
        ROOTN, 
        SINCOS
    }
    // @formatter:on

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

    @Override
    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool lirGen) {
        PTXBuiltinTool gen = ((PTXArithmeticTool) lirGen).getGen().getPtxBuiltinTool();
        Value x = builder.operand(getX());
        Value y = builder.operand(getY());
        Value result;

        Value a = x;
        Value b = y;
        Variable auxVar;
        Value auxValue;

        if (operation() == Operation.POW) {
            // pow only operates on f32 values. We must convert
            if (!((PTXKind)a.getPlatformKind()).isF32()) {
                auxVar = builder.getLIRGeneratorTool().newVariable(LIRKind.value(PTXKind.F32));
                a = builder.getLIRGeneratorTool().append(new AssignStmt(auxVar, x)).getResult();
            }
            if (!((PTXKind)b.getPlatformKind()).isF32()) {
                auxVar = builder.getLIRGeneratorTool().newVariable(LIRKind.value(PTXKind.F32));
                b = builder.getLIRGeneratorTool().append(new AssignStmt(auxVar, y)).getResult();
            }
        }

        switch (operation()) {
            case FMIN:
                result = gen.genFloatMin(x, y);
                break;
            case FMAX:
                result = gen.genFloatMax(x, y);
                break;
            case POW:
                // we use x^y = 2^(y*log2(x))
                auxVar = builder.getLIRGeneratorTool().newVariable(LIRKind.value(PTXKind.F32));
                Value log2x = builder.getLIRGeneratorTool().append(new AssignStmt(auxVar, gen.genFloatLog2(a))).getResult();
                Value aMulLog2e = builder.getLIRGeneratorTool().append(new AssignStmt(auxVar, ((PTXArithmeticTool) lirGen).genBinaryExpr(PTXAssembler.PTXBinaryOp.MUL, LIRKind.value(PTXKind.F32), b, log2x))).getResult();
                result = gen.genFloatExp2(aMulLog2e);
                break;
            default:
                throw shouldNotReachHere();
        }
        auxVar = builder.getLIRGeneratorTool().newVariable(LIRKind.combine(a, b));
        auxValue = builder.getLIRGeneratorTool().append(new AssignStmt(auxVar, result)).getResult();

        if (operation() == Operation.POW && !((PTXKind)LIRKind.combine(x, y).getPlatformKind()).isF32()) {
            // pow only operates on f32 values. We must convert back
            auxVar = builder.getLIRGeneratorTool().newVariable(LIRKind.combine(x, y));
            builder.getLIRGeneratorTool().append(new AssignStmt(auxVar, auxValue));
        }

        builder.setResult(this, auxVar);

    }

    private static double doCompute(double x, double y, Operation op) {
        switch (op) {
            case FMIN:
                return Math.min(x, y);
            case FMAX:
                return Math.max(x, y);
            case POW:
                return Math.pow(x, y);
            default:
                throw new TornadoInternalError("unknown op %s", op);
        }
    }

    private static float doCompute(float x, float y, Operation op) {
        switch (op) {
            case FMIN:
                return Math.min(x, y);
            case FMAX:
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
