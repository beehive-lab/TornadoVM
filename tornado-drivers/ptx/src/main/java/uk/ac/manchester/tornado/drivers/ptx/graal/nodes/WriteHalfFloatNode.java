/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXLIRGenerationResult;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXUnary;
import uk.ac.manchester.tornado.runtime.graph.nodes.ConstantNode;

@NodeInfo
public class WriteHalfFloatNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<WriteHalfFloatNode> TYPE = NodeClass.create(WriteHalfFloatNode.class);

    @Input
    private AddressNode addressNode;

    @Input
    private ValueNode valueNode;

    public WriteHalfFloatNode(AddressNode addressNode, ValueNode valueNode) {
        super(TYPE, StampFactory.forKind(JavaKind.Short));
        this.addressNode = addressNode;
        this.valueNode = valueNode;
    }

    public void generate(NodeLIRBuilderTool generator) {
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        Value valueToStore;
        if (valueNode.stamp(NodeView.DEFAULT).isFloatStamp()) {
            // the value to be written is in float format, so the bytecodes to convert
            // to half float need to be generated
            Value value = generator.operand(valueNode);
            Variable intermediate = tool.newVariable(LIRKind.value(PTXKind.F32));
            Variable result = tool.newVariable(LIRKind.value(PTXKind.F16));
            tool.append(new PTXLIRStmt.ConvertHalfFloatStmt(result, value, intermediate));
            valueToStore = result;
        } else {
            valueToStore = generator.operand(valueNode);
        }
        Value addressValue = generator.operand(addressNode);
        PTXUnary.MemoryAccess access = (PTXUnary.MemoryAccess) addressValue;
        tool.append(new PTXLIRStmt.HalfFloatStoreStmt(access, valueToStore));
    }
}
