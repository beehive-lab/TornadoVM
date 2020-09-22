/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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
 */
package uk.ac.manchester.tornado.drivers.opencl.graal.nodes;

import java.util.ArrayList;
import java.util.HashMap;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLStampFactory;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLKind;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIRStmt;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLUnary;

@NodeInfo(shortName = "ATOMIC_INTEGER")
public class TornadoAtomicIntegerNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<TornadoAtomicIntegerNode> TYPE = NodeClass.create(TornadoAtomicIntegerNode.class);

    private final OCLKind kind;

    private boolean ATOMIC_2_0 = false;

    // How many atomics integers per graph
    public static HashMap<StructuredGraph, ArrayList<Integer>> globalAtomics = new HashMap<>();

    @Input
    ValueNode initialValue;

    private int indexFromGlobalMemory;

    public TornadoAtomicIntegerNode(OCLKind kind) {
        super(TYPE, OCLStampFactory.getStampFor(kind));
        this.kind = kind;
        this.initialValue = ConstantNode.forInt(0);
    }

    public void setInitialValue(ValueNode valueNode) {
        initialValue = valueNode;
    }

    public void setInitialValueAtUsages(ValueNode valueNode) {
        initialValue.replaceAtUsages(valueNode);
    }

    public ValueNode getInitialValue() {
        return this.initialValue;
    }

    private void generateExpressionForOpenCL2_0(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Variable result = tool.newVariable(tool.getLIRKind(StampFactory.intValue()));
        tool.append(new OCLLIRStmt.RelocatedExpressionStmt(new OCLUnary.IntrinsicAtomicDeclaration(OCLAssembler.OCLUnaryIntrinsic.ATOMIC_VAR_INIT, result, gen.operand(initialValue))));
        gen.setResult(this, result);
    }

    private void generateExpressionForOpenCL1_0(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Variable result = tool.newVariable(tool.getLIRKind(StampFactory.intValue()));
        gen.setResult(this, result);
    }

    public int getIndexFromGlobalMemory() {
        return this.indexFromGlobalMemory;
    }

    private int getIntFromValueNode() {
        if (initialValue instanceof ConstantNode) {
            ConstantNode c = (ConstantNode) initialValue;
            return Integer.parseInt(c.getValue().toValueString());
        } else {
            throw new TornadoRuntimeException("Value node not implemented for Atomics");
        }
    }

    private synchronized void assignIndex() {
        if (!globalAtomics.containsKey(this.graph())) {
            ArrayList<Integer> al = new ArrayList<>();
            al.add(getIntFromValueNode());
            globalAtomics.put(this.graph(), al);
            this.indexFromGlobalMemory = 0;
        } else {
            ArrayList<Integer> al = new ArrayList<>(globalAtomics.get(this.graph()));
            this.indexFromGlobalMemory = al.size();
            al.add(getIntFromValueNode());
            globalAtomics.put(this.graph(), al);
        }
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        if (ATOMIC_2_0) {
            generateExpressionForOpenCL2_0(gen);
        } else {
            assignIndex();
            generateExpressionForOpenCL1_0(gen);
        }
    }
}