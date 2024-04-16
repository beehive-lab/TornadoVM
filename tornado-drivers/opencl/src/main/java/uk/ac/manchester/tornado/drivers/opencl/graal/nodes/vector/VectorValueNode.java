/*
 * Copyright (c) 2018, 2020, 2023, 2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.opencl.graal.nodes.vector;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

import java.util.List;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLStampFactory;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler.OCLOp2;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler.OCLOp3;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler.OCLOp4;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler.OCLOp8;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLNodeLIRBuilder;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLKind;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIROp;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIRStmt;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLVectorAssign;

@NodeInfo(nameTemplate = "{p#kind/s}")
public class VectorValueNode extends FloatingNode implements LIRLowerable {

    public static final NodeClass<VectorValueNode> TYPE = NodeClass.create(VectorValueNode.class);
    private final OCLKind kind;
    @Input
    NodeInputList<ValueNode> values;
    @OptionalInput(InputType.Association)
    private ValueNode origin;

    public VectorValueNode(OCLKind kind) {
        super(TYPE, OCLStampFactory.getStampFor(kind));
        this.kind = kind;
        this.values = new NodeInputList<>(this, kind.getVectorLength());
    }

    public void initialiseToDefaultValues(StructuredGraph graph) {
        final ConstantNode defaultValue = ConstantNode.forPrimitive(kind.getElementKind().getDefaultValue(), graph);
        for (int i = 0; i < kind.getVectorLength(); i++) {
            setElement(i, defaultValue);
        }
    }

    public OCLKind getOCLKind() {
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

        if (origin instanceof InvokeNode) {
            gen.setResult(this, gen.operand(origin));
        } else if (origin instanceof ValuePhiNode) {

            final ValuePhiNode phi = (ValuePhiNode) origin;

            final Value phiOperand = ((OCLNodeLIRBuilder) gen).operandForPhi(phi);

            final AllocatableValue result = (gen.hasOperand(this)) ? (Variable) gen.operand(this) : tool.newVariable(LIRKind.value(getOCLKind()));
            tool.append(new OCLLIRStmt.AssignStmt(result, phiOperand));
            gen.setResult(this, result);

        } else if (origin instanceof ParameterNode) {
            gen.setResult(this, gen.operand(origin));
        } else if (origin == null) {
            final AllocatableValue result = tool.newVariable(LIRKind.value(getOCLKind()));

            /*
             * Two cases:
             *
             * 1. when this vector state has elements assigned individually.
             *
             * 2.when this vector is assigned by a vector operation
             *
             */
            final int numValues = values.count();
            final ValueNode firstValue = values.first();

            if (firstValue instanceof VectorValueNode || firstValue instanceof VectorOp) {
                tool.append(new OCLLIRStmt.AssignStmt(result, gen.operand(values.first())));
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
        if (valueNode instanceof VectorLoadElementNode && kind.isHalf()) {
            return emitHalfFloatAssign(valueNode, tool, gen);
        }
        return (valueNode == null) ? new ConstantValue(LIRKind.value(kind), kind.getDefaultValue()) : tool.emitMove(gen.operand(valueNode));
    }

    private Variable emitHalfFloatAssign(ValueNode vectorValue, LIRGeneratorTool tool, NodeLIRBuilderTool gen) {
        Variable result = tool.newVariable(LIRKind.value(OCLKind.HALF));
        Value vectorField = gen.operand(vectorValue);
        tool.emitMove(result, vectorField);
        return result;
    }

    private void generateVectorAssign(NodeLIRBuilderTool gen, LIRGeneratorTool tool, AllocatableValue result) {

        OCLLIROp assignExpr = null;
        Value s0, s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15;

        // check if first parameter is a vector
        s0 = getParam(gen, tool, 0);
        if (kind.getVectorLength() >= 2) {
            if (((OCLKind) s0.getPlatformKind()).isVector()) {
                gen.setResult(this, s0);
                return;
            }
        }

        switch (kind.getVectorLength()) {
            case 2: {
                final OCLOp2 op2 = VectorUtil.resolveAssignOp2(getOCLKind());
                s1 = getParam(gen, tool, 1);
                assignExpr = new OCLVectorAssign.Assign2Expr(op2, getOCLKind(), s0, s1);
                break;
            }
            case 3: {
                final OCLOp3 op3 = VectorUtil.resolveAssignOp3(getOCLKind());
                s1 = getParam(gen, tool, 1);
                s2 = getParam(gen, tool, 2);
                assignExpr = new OCLVectorAssign.Assign3Expr(op3, getOCLKind(), s0, s1, s2);
                break;
            }
            case 4: {
                final OCLOp4 op4 = VectorUtil.resolveAssignOp4(getOCLKind());
                s1 = getParam(gen, tool, 1);
                s2 = getParam(gen, tool, 2);
                s3 = getParam(gen, tool, 3);
                assignExpr = new OCLVectorAssign.Assign4Expr(op4, getOCLKind(), s0, s1, s2, s3);
                break;
            }
            case 8: {
                final OCLOp8 op8 = VectorUtil.resolveAssignOp8(getOCLKind());
                s1 = getParam(gen, tool, 1);
                s2 = getParam(gen, tool, 2);
                s3 = getParam(gen, tool, 3);
                s4 = getParam(gen, tool, 4);
                s5 = getParam(gen, tool, 5);
                s6 = getParam(gen, tool, 6);
                s7 = getParam(gen, tool, 7);
                assignExpr = new OCLVectorAssign.Assign8Expr(op8, getOCLKind(), s0, s1, s2, s3, s4, s5, s6, s7);
                break;

            }
            case 16: {
                final OCLAssembler.OCLOp16 op16 = VectorUtil.resolveAssignOp16(getOCLKind());
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
                assignExpr = new OCLVectorAssign.Assign16Expr(op16, getOCLKind(), s0, s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15);
                break;
            }
            default:
                unimplemented("new vector length = " + kind.getVectorLength());
        }

        tool.append(new OCLLIRStmt.AssignStmt(result, assignExpr));

        gen.setResult(this, result);
    }

    public List<VectorStoreElementProxyNode> getLanesInputs() {
        return usages().filter(VectorStoreElementProxyNode.class).snapshot();
    }

    public boolean allLanesSet() {
        return (values.count() == 1 && values.first() instanceof VectorValueNode) || (values.filter(VectorLoadElementNode.class).count() == kind.getVectorLength());
    }

    public void set(ValueNode value) {
        values.clear();
        values.add(value);

    }

    public void deleteUnusedLoads() {
        usages().filter(VectorLoadElementNode.class).forEach(node -> {
            if (node.hasNoUsages()) {
                values.remove(node);
                node.safeDelete();
            }
        });

    }

    public boolean isLaneSet(int index) {
        if (values.count() < index) {
            return false;
        }

        return false;
    }

    public void clearOrigin() {
        this.replaceFirstInput(origin, null);
    }

    public ValueNode getOrigin() {
        return origin;
    }

    public void setOrigin(ValueNode value) {
        this.updateUsages(origin, value);
        origin = value;
    }

    public VectorValueNode duplicate() {
        return graph().addWithoutUnique(new VectorValueNode(kind));
    }

}
