/*
 * Copyright (c) 2024, 2025, APT Group, Department of Computer Science,
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

import org.graalvm.compiler.core.common.LIRKind;
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

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.ptx.graal.HalfFloatStamp;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXUnary;

@NodeInfo
public class ReadHalfFloatNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<ReadHalfFloatNode> TYPE = NodeClass.create(ReadHalfFloatNode.class);

    @Input
    private AddressNode addressNode;
    @Input
    private ValueNode indexNode;
    @Input
    private ValueNode localMemoryArrayNode;

    public ReadHalfFloatNode(AddressNode addressNode) {
        super(TYPE, new HalfFloatStamp());
        this.addressNode = addressNode;
    }

    public ReadHalfFloatNode(AddressNode addressNode, ValueNode indexNode, ValueNode localMemoryArrayNode) {
        super(TYPE, new HalfFloatStamp());
        this.addressNode = addressNode;
        this.indexNode = indexNode;
        this.localMemoryArrayNode = localMemoryArrayNode;
    }

    public void generate(NodeLIRBuilderTool generator) {
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        Variable result = tool.newVariable(LIRKind.value(PTXKind.F16));
        Value addressValue = generator.operand(addressNode);
        if (indexNode == null) {
            // if the index is not passed, this is not a local/shared array access
            tool.append(new PTXLIRStmt.HalfFloatLoadStmt((PTXUnary.MemoryAccess) addressValue, result, PTXAssembler.PTXNullaryOp.LD));
        } else {
            Value index = generator.operand(indexNode);
            Value localArray = generator.operand(localMemoryArrayNode);
            tool.append(new PTXLIRStmt.HalfFloatLoadStmt((PTXUnary.MemoryAccess) addressValue, result, PTXAssembler.PTXNullaryOp.LD, index, localArray));
        }
        generator.setResult(this, result);
    }
}
