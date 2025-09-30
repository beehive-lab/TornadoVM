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
package uk.ac.manchester.tornado.drivers.metal.graal.nodes;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.BinaryNode;
import org.graalvm.compiler.nodes.spi.ArithmeticLIRLowerable;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalArithmeticTool;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalBuiltinTool;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalLIRStmt.AssignStmt;
import uk.ac.manchester.tornado.runtime.graal.nodes.interfaces.MarkIntIntrinsicNode;

@NodeInfo(nameTemplate = "{p#operation/s}")
public class MetalIntBinaryIntrinsicNode extends BinaryNode implements ArithmeticLIRLowerable, MarkIntIntrinsicNode {

    public static final NodeClass<MetalIntBinaryIntrinsicNode> TYPE = NodeClass.create(MetalIntBinaryIntrinsicNode.class);
    protected final Operation operation;
    protected MetalIntBinaryIntrinsicNode(ValueNode x, ValueNode y, Operation op, JavaKind kind) {
        super(TYPE, StampFactory.forKind(kind), x, y);
        this.operation = op;
    }

    public static ValueNode create(ValueNode x, ValueNode y, Operation op, JavaKind kind) {
        ValueNode c = tryConstantFold(x, y, op, kind);
        if (c != null) {
            return c;
        }
        return new MetalIntBinaryIntrinsicNode(x, y, op, kind);
    }

    protected static ValueNode tryConstantFold(ValueNode x, ValueNode y, Operation op, JavaKind kind) {
        ConstantNode result = null;

        if (x.isConstant() && y.isConstant()) {
            if (kind == JavaKind.Int) {
                int ret = doCompute(x.asJavaConstant().asInt(), y.asJavaConstant().asInt(), op);
                result = ConstantNode.forInt(ret);
            } else if (kind == JavaKind.Long) {
                long ret = doCompute(x.asJavaConstant().asLong(), y.asJavaConstant().asLong(), op);
                result = ConstantNode.forLong(ret);
            }
        }
        return result;
    }
    //@formatter:on

    private static long doCompute(long x, long y, Operation op) {
        switch (op) {
            case MIN:
                return Math.min(x, y);
            case MAX:
                return Math.max(x, y);
            default:
                throw new TornadoInternalError("unknown op %s", op);
        }
    }

    private static int doCompute(int x, int y, Operation op) {
        switch (op) {
            case MIN:
                return Math.min(x, y);
            case MAX:
                return Math.max(x, y);
            default:
                throw new TornadoInternalError("unknown op %s", op);
        }
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
    public void generate(NodeLIRBuilderTool tool) {
        generate(tool, tool.getLIRGeneratorTool().getArithmetic());
    }

    public Operation operation() {
        return operation;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool lirGen) {
        MetalBuiltinTool gen = ((MetalArithmeticTool) lirGen).getGen().getMetalBuiltinTool();
        Value x = builder.operand(getX());
        Value y = builder.operand(getY());
        Value result;
        switch (operation()) {
            case MIN:
                result = gen.genIntMin(x, y);
                break;
            case MAX:
                result = gen.genIntMax(x, y);
                break;
            default:
                throw shouldNotReachHere();
        }
        Variable var = builder.getLIRGeneratorTool().newVariable(result.getValueKind());
        builder.getLIRGeneratorTool().append(new AssignStmt(var, result));
        builder.setResult(this, var);

    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode x, ValueNode y) {
        ValueNode c = tryConstantFold(x, y, operation(), getStackKind());
        if (c != null) {
            return c;
        }
        return this;
    }

    //@formatter:off
    public enum Operation {
        ABS_DIFF,
        ABS_SAT,
        HADD,
        RHADD,
        MAX,
        MIN,
        MUL_HI,
        ROTATE,
        SUB_SAT,
        UPSAMPLE,
        MUL24
    }

}
