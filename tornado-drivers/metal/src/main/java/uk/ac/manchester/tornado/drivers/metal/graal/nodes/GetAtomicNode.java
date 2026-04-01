/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalLIRStmt;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalUnary;

@NodeInfo(shortName = "GET_ATOMIC_VALUE")
public class GetAtomicNode extends NodeAtomic implements LIRLowerable {

    public static final NodeClass<GetAtomicNode> TYPE = NodeClass.create(GetAtomicNode.class);

    @Input
    ValueNode atomicNode;

    public GetAtomicNode(ValueNode atomicValue) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        this.atomicNode = atomicValue;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        Variable result = tool.newVariable(tool.getLIRKind(stamp));

        if (atomicNode instanceof TornadoAtomicIntegerNode) {
            TornadoAtomicIntegerNode atomicIntegerNode = (TornadoAtomicIntegerNode) atomicNode;

            int indexFromGlobal = atomicIntegerNode.getIndexFromGlobalMemory();

            MetalUnary.IntrinsicAtomicGet atomicGet = new MetalUnary.IntrinsicAtomicGet( //
                    MetalAssembler.MetalUnaryIntrinsic.ATOMIC_GET, //
                    tool.getLIRKind(stamp), //
                    generator.operand(atomicNode), //
                    indexFromGlobal);

            MetalLIRStmt.AssignStmt assignStmt = new MetalLIRStmt.AssignStmt(result, atomicGet);
            tool.append(assignStmt);
            generator.setResult(this, result);
        }
    }

    @Override
    public ValueNode getAtomicNode() {
        return atomicNode;
    }
}
