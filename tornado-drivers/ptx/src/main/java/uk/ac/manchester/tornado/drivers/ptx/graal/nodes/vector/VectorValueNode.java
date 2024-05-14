/*
 * Copyright (c) 2020, 2023, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.drivers.ptx.graal.nodes.vector;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

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
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXStampFactory;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXNodeLIRBuilder;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIROp;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXVectorAssign;
import uk.ac.manchester.tornado.runtime.graal.nodes.interfaces.MarkVectorValueNode;

@NodeInfo(nameTemplate = "{p#kind/s}")
public class VectorValueNode extends FloatingNode implements LIRLowerable, MarkVectorValueNode {

    public static final NodeClass<VectorValueNode> TYPE = NodeClass.create(VectorValueNode.class);
    private final PTXKind kind;
    @Input
    NodeInputList<ValueNode> values;
    @OptionalInput(InputType.Association)
    private ValueNode origin;

    public VectorValueNode(PTXKind kind) {
        super(TYPE, PTXStampFactory.getStampFor(kind));
        this.kind = kind;
        this.values = new NodeInputList<>(this, kind.getVectorLength());
    }

    public void initialiseToDefaultValues(StructuredGraph graph) {
        final ConstantNode defaultValue = ConstantNode.forPrimitive(kind.getElementKind().getDefaultValue(), graph);
        for (int i = 0; i < kind.getVectorLength(); i++) {
            setElement(i, defaultValue);
        }
    }

    public PTXKind getPTXKind() {
        return kind;
    }

    public ValueNode length() {
        return ConstantNode.forInt(kind.getVectorLength());
    }

    public ValueNode getElement(int index) {
        return values.get(index);
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

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        final LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitVectorValue: values=%s", values);

        if (origin instanceof InvokeNode) {
            gen.setResult(this, gen.operand(origin));
        } else if (origin instanceof ValuePhiNode) {

            final ValuePhiNode phi = (ValuePhiNode) origin;

            final Value phiOperand = ((PTXNodeLIRBuilder) gen).operandForPhi(phi);

            final AllocatableValue result = (gen.hasOperand(this)) ? (Variable) gen.operand(this) : tool.newVariable(LIRKind.value(getPTXKind()));
            tool.append(new PTXLIRStmt.AssignStmt(result, phiOperand));
            gen.setResult(this, result);

        } else if (origin instanceof ParameterNode) {
            gen.setResult(this, gen.operand(origin));
        } else if (origin == null) {

            final AllocatableValue result = tool.newVariable(LIRKind.value(getPTXKind()));

            /*
             * two cases: 1. when the state of the vector has elements assigned individually
             * 2. when this vector is assigned by a vector operation
             */
            final int numValues = values.count();
            final ValueNode firstValue = values.first();

            if (firstValue instanceof VectorValueNode || firstValue instanceof VectorOp) {
                tool.append(new PTXLIRStmt.AssignStmt(result, gen.operand(values.first())));
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
        if ((valueNode instanceof VectorLoadElementNode || valueNode instanceof VectorAddHalfNode) && kind.isHalf()) {
            return emitHalfFloatAssign(valueNode, tool, gen);
        }
        return (valueNode == null) ? new ConstantValue(LIRKind.value(kind), JavaConstant.defaultForKind(kind.getElementKind().asJavaKind())) : tool.emitMove(gen.operand(valueNode));
    }

    private Variable emitHalfFloatAssign(ValueNode vectorValue, LIRGeneratorTool tool, NodeLIRBuilderTool gen) {
        Variable result = tool.newVariable(LIRKind.value(PTXKind.B16));
        Value vectorField = gen.operand(vectorValue);
        tool.emitMove(result, vectorField);
        return result;
    }

    private void generateVectorAssign(NodeLIRBuilderTool gen, LIRGeneratorTool tool, AllocatableValue result) {

        PTXLIROp assignExpr = null;
        Value s0, s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15;

        // check if first parameter is a vector
        s0 = getParam(gen, tool, 0);
        if (kind.getVectorLength() >= 2) {
            if (((PTXKind) s0.getPlatformKind()).isVector()) {
                gen.setResult(this, s0);
                return;
            }
        }

        switch (kind.getVectorLength()) {
            case 2: {
                s1 = getParam(gen, tool, 1);
                assignExpr = new PTXVectorAssign.AssignVectorExpr(getPTXKind(), s0, s1);
                break;
            }
            case 3: {
                s1 = getParam(gen, tool, 1);
                s2 = getParam(gen, tool, 2);
                assignExpr = new PTXVectorAssign.AssignVectorExpr(getPTXKind(), s0, s1, s2);
                break;
            }
            case 4: {
                s1 = getParam(gen, tool, 1);
                s2 = getParam(gen, tool, 2);
                s3 = getParam(gen, tool, 3);
                assignExpr = new PTXVectorAssign.AssignVectorExpr(getPTXKind(), s0, s1, s2, s3);
                break;
            }
            case 8: {
                s1 = getParam(gen, tool, 1);
                s2 = getParam(gen, tool, 2);
                s3 = getParam(gen, tool, 3);
                s4 = getParam(gen, tool, 4);
                s5 = getParam(gen, tool, 5);
                s6 = getParam(gen, tool, 6);
                s7 = getParam(gen, tool, 7);
                assignExpr = new PTXVectorAssign.AssignVectorExpr(getPTXKind(), s0, s1, s2, s3, s4, s5, s6, s7);
                break;
            }
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
                assignExpr = new PTXVectorAssign.AssignVectorExpr(getPTXKind(), s0, s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15);
                break;
            }
            default:
                unimplemented("new vector length = " + kind.getVectorLength());
        }

        tool.append(new PTXLIRStmt.AssignStmt(result, assignExpr));

        gen.setResult(this, result);
    }
}
