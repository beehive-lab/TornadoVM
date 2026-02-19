/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, 2023, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.spirv.graal.nodes.vector;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.vector.VectorValueNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVStampFactory;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVNodeLIRBuilder;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIROp;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIRStmt;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVVectorAssign;
import uk.ac.manchester.tornado.runtime.graal.nodes.interfaces.MarkVectorValueNode;

@NodeInfo(nameTemplate = "{p#spirvKind/s}")
public class SPIRVVectorValueNode extends FloatingNode implements LIRLowerable, MarkVectorValueNode {

    public static final NodeClass<SPIRVVectorValueNode> TYPE = NodeClass.create(SPIRVVectorValueNode.class);
    @Input
    NodeInputList<ValueNode> values;
    @OptionalInput(InputType.Association)
    private ValueNode origin;
    private SPIRVKind spirvKind;

    public SPIRVVectorValueNode(SPIRVKind spirvVectorKind) {
        super(TYPE, SPIRVStampFactory.getStampFor(spirvVectorKind));
        this.spirvKind = spirvVectorKind;
        this.values = new NodeInputList<>(this, spirvKind.getVectorLength());
    }

    public void initialiseToDefaultValues(StructuredGraph graph) {
        final ConstantNode defaultValue = ConstantNode.forPrimitive(spirvKind.getElementKind().getDefaultValue(), graph);
        for (int i = 0; i < spirvKind.getVectorLength(); i++) {
            setElement(i, defaultValue);
        }
    }

    /**
     * This method replaces the input of the current {@link VectorValueNode} that is
     * at a specific index with a replacement node.
     *
     * @param replacement
     * @param index
     */
    private void replaceInputAtIndex(Node replacement, int index) {
        int i = 0;
        for (Node input : this.inputs()) {
            if (i++ == index) {
                this.replaceFirstInput(input, replacement);
                break;
            }
        }
    }

    private boolean isInputValueAtIndexSet(int index) {
        return values.get(index) != null;
    }

    /**
     * This method sets a {@link ValueNode} as an input value of the current
     * {@link VectorValueNode} at a specific index. If the input value has been
     * already set (not null), the input that corresponds to the index is replaced
     * by the argument value. Otherwise, the input value is set by the argument
     * value.
     *
     * @param index
     * @param value
     */
    public void setElement(int index, ValueNode value) {
        if (isInputValueAtIndexSet(index)) {
            replaceInputAtIndex(value, index);
        } else {
            values.set(index, value);
        }
    }

    public ValueNode length() {
        return ConstantNode.forInt(spirvKind.getVectorLength());
    }

    public SPIRVKind getSPIRVKind() {
        return spirvKind;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        final LIRGeneratorTool tool = gen.getLIRGeneratorTool();

        if (origin instanceof InvokeNode) {
            gen.setResult(this, gen.operand(origin));
        } else if (origin instanceof ValuePhiNode) {

            final ValuePhiNode phi = (ValuePhiNode) origin;

            final Value phiOperand = ((SPIRVNodeLIRBuilder) gen).operandForPhi(phi);
            final AllocatableValue result = (gen.hasOperand(this)) ? (Variable) gen.operand(this) : tool.newVariable(LIRKind.value(getSPIRVKind()));
            tool.append(new SPIRVLIRStmt.AssignStmt(result, phiOperand));
            gen.setResult(this, result);
        } else if (origin instanceof ParameterNode) {
            gen.setResult(this, gen.operand(origin));
        } else if (origin == null) {
            final AllocatableValue result = tool.newVariable(LIRKind.value(getSPIRVKind()));

            /*
             * Two cases:
             *
             * 1. when this vector state has elements assigned individually.
             *
             * 2. when this vector is assigned by a vector operation
             *
             */
            final int numValues = values.count();
            final ValueNode firstValue = values.first();

            if (firstValue instanceof SPIRVVectorValueNode || firstValue instanceof VectorOp) {
                tool.append(new SPIRVLIRStmt.AssignStmt(result, gen.operand(values.first())));
                gen.setResult(this, result);
            } else if (numValues > 0 && gen.hasOperand(firstValue)) {
                generateVectorAssign(gen, tool, result);
            } else {
                gen.setResult(this, result);
            }
        }
    }

    private Value getParam(NodeLIRBuilderTool gen, LIRGeneratorTool tool, int index) {
        final ValueNode valueNode = values.get(index);
        return (valueNode == null) ? new ConstantValue(LIRKind.value(spirvKind), spirvKind.getDefaultValue()) : tool.emitMove(gen.operand(valueNode));
    }

    // THis construct generates the equivalent of the following OpenCL Code:
    // vtype = (a, b, c, d);
    private void generateVectorAssign(NodeLIRBuilderTool gen, LIRGeneratorTool tool, AllocatableValue result) {
        SPIRVLIROp assignExpr;
        Value s0;
        Value s1;
        Value s2;
        Value s3;
        Value s4;
        Value s5;
        Value s6;
        Value s7;
        Value s8;
        Value s9;
        Value s10;
        Value s11;
        Value s12;
        Value s13;
        Value s14;
        Value s15;
        LIRKind lirKind;

        // check if first parameter is a vector
        s0 = getParam(gen, tool, 0);
        if (spirvKind.getVectorLength() >= 2) {
            if (((SPIRVKind) s0.getPlatformKind()).isVector()) {
                gen.setResult(this, s0);
                return;
            }
        }

        switch (spirvKind.getVectorLength()) {
            case 2:
                s1 = getParam(gen, tool, 1);
                gen.getLIRGeneratorTool().getLIRKind(stamp);
                lirKind = gen.getLIRGeneratorTool().getLIRKind(stamp);
                assignExpr = new SPIRVVectorAssign.AssignVectorExpr(lirKind, s0, s1);
                break;
            case 3:
                s1 = getParam(gen, tool, 1);
                s2 = getParam(gen, tool, 2);
                lirKind = gen.getLIRGeneratorTool().getLIRKind(stamp);
                assignExpr = new SPIRVVectorAssign.AssignVectorExpr(lirKind, s0, s1, s2);
                break;
            case 4:
                s1 = getParam(gen, tool, 1);
                s2 = getParam(gen, tool, 2);
                s3 = getParam(gen, tool, 3);
                lirKind = gen.getLIRGeneratorTool().getLIRKind(stamp);
                assignExpr = new SPIRVVectorAssign.AssignVectorExpr(lirKind, s0, s1, s2, s3);
                break;
            case 8:
                s1 = getParam(gen, tool, 1);
                s2 = getParam(gen, tool, 2);
                s3 = getParam(gen, tool, 3);
                s4 = getParam(gen, tool, 4);
                s5 = getParam(gen, tool, 5);
                s6 = getParam(gen, tool, 6);
                s7 = getParam(gen, tool, 7);
                lirKind = gen.getLIRGeneratorTool().getLIRKind(stamp);
                assignExpr = new SPIRVVectorAssign.AssignVectorExpr(lirKind, s0, s1, s2, s3, s4, s5, s6, s7);
                break;
            case 16: {
                s1 = getParam(gen, tool, 1);
                s2 = getParam(gen, tool, 2);
                s3 = getParam(gen, tool, 3);
                s4 = getParam(gen, tool, 4);
                s5 = getParam(gen, tool, 5);
                s6 = getParam(gen, tool, 6);
                s7 = getParam(gen, tool, 7);
                s8 = getParam(gen, tool, 8);
                s9 = getParam(gen, tool, 9);
                s10 = getParam(gen, tool, 10);
                s11 = getParam(gen, tool, 11);
                s12 = getParam(gen, tool, 12);
                s13 = getParam(gen, tool, 13);
                s14 = getParam(gen, tool, 14);
                s15 = getParam(gen, tool, 15);
                lirKind = gen.getLIRGeneratorTool().getLIRKind(stamp);
                assignExpr = new SPIRVVectorAssign.AssignVectorExpr(lirKind, s0, s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15);
                break;
            }
            default:
                throw new RuntimeException("Unsupported vector width (up to size 16 currently supported)");
        }
        tool.append(new SPIRVLIRStmt.AssignStmt(result, assignExpr));
        gen.setResult(this, result);
    }
}
