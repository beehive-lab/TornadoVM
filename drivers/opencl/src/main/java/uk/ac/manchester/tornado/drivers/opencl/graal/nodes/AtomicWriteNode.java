/*
 * Copyright (c) 2018, APT Group, School of Computer Science,
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
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.graal.nodes;

import static uk.ac.manchester.tornado.common.exceptions.TornadoInternalError.unimplemented;

import org.graalvm.compiler.core.common.LocationIdentity;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.AbstractWriteNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler.OCLBinaryIntrinsic;

@NodeInfo(shortName = "Atomic Write")
public class AtomicWriteNode extends AbstractWriteNode implements LIRLowerable {

    public static final NodeClass<AtomicWriteNode> TYPE = NodeClass
            .create(AtomicWriteNode.class);

    OCLBinaryIntrinsic op;

    public AtomicWriteNode(
            OCLBinaryIntrinsic op,
            AddressNode address, LocationIdentity location, ValueNode value) {
        super(TYPE, address, location, value, BarrierType.NONE);
        this.op = op;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        final LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        unimplemented();
//        final LocationNode location = location();
//
//        final Value object = gen.operand(object());
//
//        final MemoryAccess addressOfObject = (MemoryAccess) location.generateAddress(gen, tool,
//                object);
////		addressOfObject.setKind(value().getKind());
//
//        final Value valueToStore = gen.operand(value());
//
//        tool.append(new OCLLIRInstruction.ExprStmt(new OCLBinary.Intrinsic(op, JavaKind.Illegal,
//                addressOfObject, valueToStore)));
//        trace("emitAtomicWrite: %s(%s, %s)", op.toString(),
//                addressOfObject, valueToStore);

    }

    @Override
    public boolean canNullCheck() {
        return false;
    }

}
