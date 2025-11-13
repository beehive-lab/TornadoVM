/*
 * Copyright (c) 2025, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.ptx.graal.nodes;

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
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXLIRGenerator;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt;

/**
 * Node for reading and decompressing a 4-byte object reference from an object's field into a full 64-bit address.
 */
@NodeInfo
public class PTXDecompressedReadFieldNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<PTXDecompressedReadFieldNode> TYPE = NodeClass.create(PTXDecompressedReadFieldNode.class);
    @Input
    private ValueNode object;
    @Input
    private AddressNode address;

    public PTXDecompressedReadFieldNode(ValueNode object, AddressNode address, Stamp stamp) {
        super(TYPE, stamp);
        this.object = object;
        this.address = address;
    }

    public ValueNode getObject() {
        return object;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        PTXLIRGenerator tool = (PTXLIRGenerator) gen.getLIRGeneratorTool();
        Value addressBase = gen.operand(address.getBase());
        Value index = gen.operand(address.getIndex());
        Variable fieldAddress = tool.getArithmetic().emitAdd(addressBase, index, false);
        Variable compressedPointer = tool.newVariable(LIRKind.value(PTXKind.U32));
        tool.append(new PTXLIRStmt.CastCompressedStmt(compressedPointer, fieldAddress));
        Variable decompressedPointer = tool.newVariable(LIRKind.value(PTXKind.U64));
        // Temporary variable for cvt
        Variable temp64 = tool.newVariable(LIRKind.value(PTXKind.U64));
        // Temporary for shl
        Variable tempShifted = tool.newVariable(LIRKind.value(PTXKind.U64));
        tool.append(new PTXLIRStmt.DecompressPointerStmt(decompressedPointer, addressBase, compressedPointer, temp64, tempShifted));
        gen.setResult(this, decompressedPointer);
    }

}
