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
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.UnaryNode;
import jdk.graal.compiler.nodes.spi.ArithmeticLIRLowerable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXArithmeticTool;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXBuiltinTool;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt.AssignStmt;
import uk.ac.manchester.tornado.runtime.graal.nodes.interfaces.MarkIntIntrinsicNode;

@NodeInfo(nameTemplate = "{p#operation/s}")
public class PTXIntUnaryIntrinsicNode extends UnaryNode implements ArithmeticLIRLowerable, MarkIntIntrinsicNode {

    public static final NodeClass<PTXIntUnaryIntrinsicNode> TYPE = NodeClass.create(PTXIntUnaryIntrinsicNode.class);
    protected final Operation operation;

    protected PTXIntUnaryIntrinsicNode(ValueNode x, Operation op, JavaKind kind) {
        super(TYPE, StampFactory.forKind(kind), x);
        this.operation = op;
    }

    public static ValueNode create(ValueNode x, Operation op, JavaKind kind) {
        ValueNode c = tryConstantFold(x, op, kind);
        if (c != null) {
            return c;
        }
        return new PTXIntUnaryIntrinsicNode(x, op, kind);
    }

    protected static ValueNode tryConstantFold(ValueNode x, Operation op, JavaKind kind) {
        ConstantNode result = null;

        if (x.isConstant()) {
            if (kind == JavaKind.Int) {
                int ret = doCompute(x.asJavaConstant().asInt(), op);
                result = ConstantNode.forInt(ret);
            } else if (kind == JavaKind.Long) {
                long ret = doCompute(x.asJavaConstant().asLong(), op);
                result = ConstantNode.forLong(ret);
            }
        }
        return result;
    }

    private static long doCompute(long value, Operation op) {
        return switch (op) {
            case ABS -> Math.abs(value);
            case POPCOUNT -> Long.bitCount(value);
            default -> throw new TornadoInternalError("unknown op %s", op);
        };
    }

    private static int doCompute(int value, Operation op) {
        return switch (op) {
            case ABS -> Math.abs(value);
            case POPCOUNT -> Integer.bitCount(value);
            default -> throw new TornadoInternalError("unknown op %s", op);
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
    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool lirGen) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitPTXIntUnaryIntrinsic: op=%s, x=%s", operation, getValue());
        PTXBuiltinTool gen = ((PTXArithmeticTool) lirGen).getGen().getPtxBuiltinTool();
        Value x = builder.operand(getValue());
        Value result;
        ValueKind valueKind = switch (operation()) {
            case ABS -> {
                result = gen.genIntAbs(x);
                yield result.getValueKind();
            }
            case POPCOUNT -> {
                result = gen.genIntPopcount(x);
                yield LIRKind.value(PTXKind.U32);
            }
            default -> throw shouldNotReachHere();
        };
        Variable var = builder.getLIRGeneratorTool().newVariable(valueKind);
        builder.getLIRGeneratorTool().append(new AssignStmt(var, result));
        builder.setResult(this, var);

    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode value) {
        ValueNode c = tryConstantFold(value, operation(), getStackKind());
        if (c != null) {
            return c;
        }
        return this;
    }

    public enum Operation {
        ABS, POPCOUNT
    }

}
