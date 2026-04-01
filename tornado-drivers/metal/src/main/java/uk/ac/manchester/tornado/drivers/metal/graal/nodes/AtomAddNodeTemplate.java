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

import jdk.vm.ci.meta.JavaKind;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalLIRStmt;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalUnary;

@NodeInfo(shortName = "AtomAdd")
public class AtomAddNodeTemplate extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<AtomAddNodeTemplate> TYPE = NodeClass.create(AtomAddNodeTemplate.class);

    @Input
    AddressNode address;
    @Input
    ValueNode inc;

    public AtomAddNodeTemplate(AddressNode addressNode, ValueNode inc, JavaKind kind) {
        super(TYPE, StampFactory.forKind(kind));
        this.address = addressNode;
        this.inc = inc;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Variable result = tool.newVariable(tool.getLIRKind(stamp));
        tool.append(new MetalLIRStmt.AssignStmt(result, new MetalUnary.AtomOperation(MetalAssembler.MetalUnaryOp.CAST_TO_INT, tool.getLIRKind(stamp), gen.operand(address), (MetalUnary.MemoryAccess) gen.operand(
                address), MetalAssembler.MetalUnaryIntrinsic.ATOM_ADD, gen.operand(inc))));
        gen.setResult(this, result);
    }
}
