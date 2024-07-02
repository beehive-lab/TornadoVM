/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2018, 2020, APT Group, Department of Computer Science,
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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.graal.nodes;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.TernaryNode;
import jdk.graal.compiler.nodes.spi.ArithmeticLIRLowerable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLArithmeticTool;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLBuiltinTool;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIRStmt.AssignStmt;
import uk.ac.manchester.tornado.runtime.graal.nodes.interfaces.MarkIntIntrinsicNode;

@NodeInfo(nameTemplate = "{p#operation/s}")
public class OCLIntTernaryIntrinsicNode extends TernaryNode implements ArithmeticLIRLowerable, MarkIntIntrinsicNode {

    public static final NodeClass<OCLIntTernaryIntrinsicNode> TYPE = NodeClass.create(OCLIntTernaryIntrinsicNode.class);
    protected final Operation operation;
    protected OCLIntTernaryIntrinsicNode(ValueNode x, ValueNode y, ValueNode z, Operation op, JavaKind kind) {
        super(TYPE, StampFactory.forKind(kind), x, y, z);
        this.operation = op;
    }

    public static ValueNode create(ValueNode x, ValueNode y, ValueNode z, Operation op, JavaKind kind) {
        ValueNode c = tryConstantFold(x, y, z, op, kind);
        if (c != null) {
            return c;
        }
        return new OCLIntTernaryIntrinsicNode(x, y, z, op, kind);
    }

    protected static ValueNode tryConstantFold(ValueNode x, ValueNode y, ValueNode z, Operation op, JavaKind kind) {
        ConstantNode result = null;

        if (x.isConstant() && y.isConstant() && z.isConstant()) {
            if (kind == JavaKind.Int) {
                int ret = doCompute(x.asJavaConstant().asInt(), y.asJavaConstant().asInt(), z.asJavaConstant().asInt(), op);
                result = ConstantNode.forInt(ret);
            } else if (kind == JavaKind.Long) {
                long ret = doCompute(x.asJavaConstant().asLong(), y.asJavaConstant().asLong(), z.asJavaConstant().asInt(), op);
                result = ConstantNode.forLong(ret);
            }
        }
        return result;
    }

    private static long doCompute(long x, long y, long z, Operation op) {
        switch (op) {
            case CLAMP:
                return TornadoMath.clamp(x, y, z);
            default:
                throw new TornadoInternalError("unknown op %s", op);
        }
    }

    private static int doCompute(int x, int y, int z, Operation op) {
        switch (op) {
            case CLAMP:
                return TornadoMath.clamp(x, y, z);

            default:
                throw new TornadoInternalError("unknown op %s", op);
        }
    }

    @Override
    public Stamp foldStamp(Stamp stampX, Stamp stampY, Stamp stampZ) {
        return stamp(NodeView.DEFAULT);
    }

    @Override
    public String getOperation() {
        return operation.toString();
    }

    public Operation operation() {
        return operation;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool lirGen) {
        OCLBuiltinTool gen = ((OCLArithmeticTool) lirGen).getGen().getOCLBuiltinTool();

        Value x = builder.operand(getX());
        Value y = builder.operand(getY());
        Value z = builder.operand(getZ());
        LIRKind lirKind = builder.getLIRGeneratorTool().getLIRKind(stamp);
        Variable result = builder.getLIRGeneratorTool().newVariable(lirKind);
        Value expr;
        switch (operation()) {
            case CLAMP:
                expr = gen.genIntClamp(x, y, z);
                break;
            case MAD24:
                expr = gen.genIntMad24(x, y, z);
                break;
            case MAD_HI:
                expr = gen.genIntMadHi(x, y, z);
                break;
            case MAD_SAT:
                expr = gen.genIntMadSat(x, y, z);
                break;
            default:
                throw shouldNotReachHere();
        }
        builder.getLIRGeneratorTool().append(new AssignStmt(result, expr));
        builder.setResult(this, result);

    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode x, ValueNode y, ValueNode z) {
        ValueNode c = tryConstantFold(x, y, z, operation(), getStackKind());
        if (c != null) {
            return c;
        }
        return this;
    }

    public enum Operation {
        CLAMP, MAD_HI, MAD_SAT, MAD24
    }

}
