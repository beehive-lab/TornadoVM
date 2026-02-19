/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
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
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.lir.SPIRVArithmeticTool;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVBuiltinTool;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIRStmt;
import uk.ac.manchester.tornado.runtime.graal.nodes.interfaces.MarkIntIntrinsicNode;

@NodeInfo(nameTemplate = "{p#operation/s}")
public class SPIRVIntUnaryIntrinsicNode extends UnaryNode implements ArithmeticLIRLowerable, MarkIntIntrinsicNode {

    public static final NodeClass<SPIRVIntUnaryIntrinsicNode> TYPE = NodeClass.create(SPIRVIntUnaryIntrinsicNode.class);
    private SPIRVIntOperation operation;

    protected SPIRVIntUnaryIntrinsicNode(SPIRVIntOperation operation, ValueNode value, JavaKind kind) {
        super(TYPE, StampFactory.forKind(kind), value);
        this.operation = operation;
    }

    public static ValueNode create(ValueNode x, SPIRVIntOperation op, JavaKind kind) {
        ValueNode c = tryConstantFold(x, op, kind);
        if (c != null) {
            return c;
        }
        return new SPIRVIntUnaryIntrinsicNode(op, x, kind);
    }

    private static long doCompute(long value, SPIRVIntOperation op) {
        return switch (op) {
            case ABS -> Math.abs(value);
            case CLZ -> Long.numberOfLeadingZeros(value);
            case POPCOUNT -> Long.bitCount(value);
            default -> throw new TornadoInternalError("unknown op %s", op);
        };
    }

    private static int doCompute(int value, SPIRVIntOperation op) {
        return switch (op) {
            case ABS -> Math.abs(value);
            case CLZ -> Integer.numberOfLeadingZeros(value);
            case POPCOUNT -> Integer.bitCount(value);
            default -> throw new TornadoInternalError("unknown op %s", op);
        };
    }

    protected static ValueNode tryConstantFold(ValueNode x, SPIRVIntOperation op, JavaKind kind) {
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

    public SPIRVIntOperation operation() {
        return operation;
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forValue) {
        ValueNode c = tryConstantFold(value, operation(), getStackKind());
        if (c != null) {
            return c;
        }
        return this;
    }

    @Override
    public String getOperation() {
        return operation.toString();
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool lirGeneratorTool) {
        SPIRVBuiltinTool gen = ((SPIRVArithmeticTool) lirGeneratorTool).getGen().getSpirvBuiltinTool();
        Value x = builder.operand(getValue());
        Value computeIntrinsic = switch (operation) {
            case ABS -> gen.genIntAbs(x);
            case POPCOUNT -> gen.genIntPopcount(x);
            default -> throw new RuntimeException("Int binary intrinsic not supported yet");
        };
        Variable result = builder.getLIRGeneratorTool().newVariable(computeIntrinsic.getValueKind());
        builder.getLIRGeneratorTool().append(new SPIRVLIRStmt.AssignStmt(result, computeIntrinsic));
        builder.setResult(this, result);
    }

    public enum SPIRVIntOperation {
        ABS, CLZ, POPCOUNT;
    }

}
