/*
 * Copyright (c) 2025, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.ptx.graal.nodes;

import jdk.vm.ci.meta.JavaKind;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXUnary;

import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler.PTXNullaryOp.ATOM;

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
        Variable dest = tool.newVariable(tool.getLIRKind(stamp));
        tool.append(new PTXLIRStmt.AtomOperation((PTXUnary.MemoryAccess) gen.operand(address), dest, ATOM, PTXAssembler.PTXBinaryOp.ADD, gen.operand(inc)));
    }
}
