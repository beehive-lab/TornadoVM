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
package uk.ac.manchester.tornado.drivers.metal.graal.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalLIRStmt;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalUnary;

@NodeInfo(shortName = "INCREMENT_ATOMIC")
public class IncAtomicNode extends NodeAtomic implements LIRLowerable {

    public static final NodeClass<IncAtomicNode> TYPE = NodeClass.create(IncAtomicNode.class);

    private static boolean ATOMIC_2_0 = false;

    @Input
    ValueNode atomicNode;
    MetalUnary.AtomicOperator atomicOperator;

    public IncAtomicNode(ValueNode atomicValue, MetalUnary.AtomicOperator atomicOperator) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        this.atomicNode = atomicValue;
        this.atomicOperator = atomicOperator;
    }

    @Override
    public ValueNode getAtomicNode() {
        return this.atomicNode;
    }

    private void generateExpressionForMetal2_0(NodeLIRBuilderTool generator) {
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        Variable result = tool.newVariable(tool.getLIRKind(stamp));

        MetalUnary.IntrinsicAtomicFetch intrinsicAtomicFetch = new MetalUnary.IntrinsicAtomicFetch( //
                MetalAssembler.MetalUnaryIntrinsic.ATOMIC_FETCH_ADD_EXPLICIT, //
                tool.getLIRKind(stamp), //
                generator.operand(atomicNode));

        MetalLIRStmt.AssignStmt assignStmt = new MetalLIRStmt.AssignStmt(result, intrinsicAtomicFetch);
        tool.append(assignStmt);
        generator.setResult(this, result);
    }

    private void generateExpressionForMetal1_0(NodeLIRBuilderTool generator) {
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        Variable result = tool.newVariable(tool.getLIRKind(stamp));

        if (atomicNode instanceof TornadoAtomicIntegerNode) {
            TornadoAtomicIntegerNode atomicIntegerNode = (TornadoAtomicIntegerNode) atomicNode;

            int indexFromGlobal = atomicIntegerNode.getIndexFromGlobalMemory();

            MetalUnary.IntrinsicAtomicOperator intrinsicAtomicAdd = new MetalUnary.IntrinsicAtomicOperator( //
                    MetalAssembler.MetalUnaryIntrinsic.ATOMIC_INC, //
                    tool.getLIRKind(stamp), //
                    generator.operand(atomicNode), //
                    indexFromGlobal, //
                    atomicOperator);

            MetalLIRStmt.AssignStmt assignStmt = new MetalLIRStmt.AssignStmt(result, intrinsicAtomicAdd);
            tool.append(assignStmt);
            generator.setResult(this, result);
        }
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {

        if (ATOMIC_2_0) {
            generateExpressionForMetal2_0(generator);
        } else {
            generateExpressionForMetal1_0(generator);
        }
    }
}
