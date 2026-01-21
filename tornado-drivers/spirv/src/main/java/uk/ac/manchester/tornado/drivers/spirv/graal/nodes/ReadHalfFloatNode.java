/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2024, 2025, APT Group, Department of Computer Science,
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

import jdk.graal.compiler.core.common.LIRKind;
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

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.opencl.graal.HalfFloatStamp;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVArchitecture;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIRStmt;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVUnary;

@NodeInfo
public class ReadHalfFloatNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<ReadHalfFloatNode> TYPE = NodeClass.create(ReadHalfFloatNode.class);

    @Input
    private AddressNode addressNode;
    @Input
    private ValueNode indexNode;

    public ReadHalfFloatNode(AddressNode addressNode) {
        super(TYPE, new HalfFloatStamp());
        this.addressNode = addressNode;
    }

    public ReadHalfFloatNode(AddressNode addressNode, ValueNode indexNode) {
        super(TYPE, new HalfFloatStamp());
        this.addressNode = addressNode;
        this.indexNode = indexNode;
    }

    public void generate(NodeLIRBuilderTool generator) {
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        Variable result = tool.newVariable(LIRKind.value(SPIRVKind.OP_TYPE_FLOAT_16));
        Value addressValue = generator.operand(addressNode);
        if (addressValue instanceof SPIRVUnary.MemoryAccess memoryAccess) {
            SPIRVArchitecture.SPIRVMemoryBase base = memoryAccess.getMemoryRegion();
            SPIRVUnary.SPIRVAddressCast cast = new SPIRVUnary.SPIRVAddressCast(memoryAccess, base, LIRKind.value(SPIRVKind.OP_TYPE_FLOAT_16));
            tool.append(new SPIRVLIRStmt.LoadStmt(result, cast, memoryAccess));
        } else if (addressValue instanceof SPIRVUnary.MemoryIndexedAccess indexedAccess) {
            Value index = generator.operand(indexNode);
            SPIRVUnary.MemoryIndexedAccess localAccessIndex = new SPIRVUnary.MemoryIndexedAccess(
                    indexedAccess.getMemoryRegion(),
                    indexedAccess.getValue(),  // base array
                    index
            );
            tool.append(new SPIRVLIRStmt.IndexedLoadMemAccess(localAccessIndex, result));
        }
        generator.setResult(this, result);
    }

}
