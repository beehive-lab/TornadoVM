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

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.ResolvedJavaMethod;
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
    public static HashMap<ResolvedJavaMethod, ArrayList<Integer>> globalAtomics = new HashMap<>();

    // Mapping between:
    // Java Method: -> { ParamIndex -> Position in the Atomic Buffer }
    public static HashMap<ResolvedJavaMethod, HashMap<Integer, Integer>> globalAtomicsParameters = new HashMap<>();

    private static final int DEFAULT_VALUE = -1;

    @Input
    ValueNode initialValue;

    private int indexFromGlobalMemory;

    private boolean atomicsByParameter = false;

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

    private void updateGlobalAtomicTable(HashMap positions, int paramIndex, int size) {
        positions.put(paramIndex, size);
        globalAtomicsParameters.put(this.graph().method(), positions);
    }

    /**
     * Method to reserve a position in the atomic-int global buffer and map the
     * parameter index with the assigned position. The mapping-table is obtained at
     * runtime for streaming in and out data in the right positions of the atomic
     * buffer.
     *
     * @param paramIndex
     *            Object parameter index taken from
     *            {@link jdk.graal.compiler.nodes.ParameterNode}.
     */
    public synchronized void assignIndexFromParameter(int paramIndex) {
        if (!globalAtomics.containsKey(this.graph().method())) {
            ArrayList<Integer> al = new ArrayList<>();
            al.add(DEFAULT_VALUE);
            // The position is reserved to be filled by TornadoVM. This position is then
            // used by the TornadoVM runtime to copy the initial value for the Atomic before
            // the kernel execution.
            globalAtomics.put(this.graph().method(), al);
            updateGlobalAtomicTable(new HashMap<>(), paramIndex, al.size() - 1);
            this.indexFromGlobalMemory = 0;
        } else {
            ArrayList<Integer> al = globalAtomics.get(this.graph().method());
            this.indexFromGlobalMemory = al.size();
            al.add(DEFAULT_VALUE);
            // A position for the atomic is reserved. This position is then used by the
            // TornadoVM runtime to copy the initial value for the Atomic before the kernel
            // execution.
            globalAtomics.put(this.graph().method(), al);

            HashMap positions = globalAtomicsParameters.get(this.graph().method());
            updateGlobalAtomicTable(positions, paramIndex, al.size() - 1);
        }
        atomicsByParameter = true;
    }

    public boolean isAtomicsByParameter() {
        return atomicsByParameter;
    }

    private synchronized void assignIndex() {
        if (!globalAtomics.containsKey(this.graph().method())) {
            ArrayList<Integer> al = new ArrayList<>();
            al.add(getIntFromValueNode());
            globalAtomics.put(this.graph().method(), al);
            this.indexFromGlobalMemory = 0;
        } else {
            ArrayList<Integer> al = globalAtomics.get(this.graph().method());
            this.indexFromGlobalMemory = al.size();
            al.add(getIntFromValueNode());
            globalAtomics.put(this.graph().method(), al);
        }
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        if (ATOMIC_2_0) {
            generateExpressionForOpenCL2_0(gen);
        } else {
            if (!atomicsByParameter) {
                // Only assign an index if the atomics is not a parameter to the function.
                assignIndex();
            }
            generateExpressionForOpenCL1_0(gen);
        }
    }
}
