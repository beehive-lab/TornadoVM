/*
 * Copyright (c) 2020, 2025, APT Group, Department of Computer Science,
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

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIRStmt;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLUnary;

@NodeInfo(shortName = "INCREMENT_ATOMIC")
public class IncAtomicNode extends NodeAtomic implements LIRLowerable {

    public static final NodeClass<IncAtomicNode> TYPE = NodeClass.create(IncAtomicNode.class);

    private static boolean ATOMIC_2_0 = false;

    @Input
    ValueNode atomicNode;
    OCLUnary.AtomicOperator atomicOperator;

    public IncAtomicNode(ValueNode atomicValue, OCLUnary.AtomicOperator atomicOperator) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        this.atomicNode = atomicValue;
        this.atomicOperator = atomicOperator;
    }

    @Override
    public ValueNode getAtomicNode() {
        return this.atomicNode;
    }

    private void generateExpressionForOpenCL2_0(NodeLIRBuilderTool generator) {
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        Variable result = tool.newVariable(tool.getLIRKind(stamp));

        OCLUnary.IntrinsicAtomicFetch intrinsicAtomicFetch = new OCLUnary.IntrinsicAtomicFetch( //
                OCLAssembler.OCLUnaryIntrinsic.ATOMIC_FETCH_ADD_EXPLICIT, //
                tool.getLIRKind(stamp), //
                generator.operand(atomicNode));

        OCLLIRStmt.AssignStmt assignStmt = new OCLLIRStmt.AssignStmt(result, intrinsicAtomicFetch);
        tool.append(assignStmt);
        generator.setResult(this, result);
    }

    private void generateExpressionForOpenCL1_0(NodeLIRBuilderTool generator) {
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        Variable result = tool.newVariable(tool.getLIRKind(stamp));

        if (atomicNode instanceof TornadoAtomicIntegerNode) {
            TornadoAtomicIntegerNode atomicIntegerNode = (TornadoAtomicIntegerNode) atomicNode;

            int indexFromGlobal = atomicIntegerNode.getIndexFromGlobalMemory();

            OCLUnary.IntrinsicAtomicOperator intrinsicAtomicAdd = new OCLUnary.IntrinsicAtomicOperator( //
                    OCLAssembler.OCLUnaryIntrinsic.ATOMIC_INC, //
                    tool.getLIRKind(stamp), //
                    generator.operand(atomicNode), //
                    indexFromGlobal, //
                    atomicOperator);

            OCLLIRStmt.AssignStmt assignStmt = new OCLLIRStmt.AssignStmt(result, intrinsicAtomicAdd);
            tool.append(assignStmt);
            generator.setResult(this, result);
        }
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {

        if (ATOMIC_2_0) {
            generateExpressionForOpenCL2_0(generator);
        } else {
            generateExpressionForOpenCL1_0(generator);
        }
    }
}
