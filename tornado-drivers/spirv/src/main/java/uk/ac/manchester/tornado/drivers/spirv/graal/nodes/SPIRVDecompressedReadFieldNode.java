/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2025, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2009-2021, Oracle and/or its affiliates. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.spirv.graal.nodes;

import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVLIRGenerator;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIRStmt;

/**
 * Node for reading and decompressing a 4-byte object reference from an object's field into a full 64-bit address.
 */
@NodeInfo
public class SPIRVDecompressedReadFieldNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<SPIRVDecompressedReadFieldNode> TYPE = NodeClass.create(SPIRVDecompressedReadFieldNode.class);
    @Input
    private ValueNode object;
    @Input
    private AddressNode address;

    public SPIRVDecompressedReadFieldNode(ValueNode object, AddressNode address, Stamp stamp) {
        super(TYPE, stamp);
        this.object = object;
        this.address = address;
    }

    public ValueNode getObject() {
        return object;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        SPIRVLIRGenerator tool = (SPIRVLIRGenerator) gen.getLIRGeneratorTool();
        Value addressBase = gen.operand(address.getBase());
        Value index = gen.operand(address.getIndex());
        Variable fieldAddress = tool.getArithmetic().emitAdd(addressBase, index, false);
        Variable compressedPointer = tool.newVariable(LIRKind.value(SPIRVKind.OP_TYPE_INT_32));
        tool.append(new SPIRVLIRStmt.CastCompressedStmt(compressedPointer, fieldAddress));
        Variable decompressedPointer = tool.newVariable(LIRKind.value(SPIRVKind.OP_TYPE_INT_64));
        tool.append(new SPIRVLIRStmt.DecompressPointerStmt(decompressedPointer, addressBase, compressedPointer));
        gen.setResult(this, decompressedPointer);
    }

}
