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

import tornado.graal.compiler.core.common.LIRKind;
import tornado.graal.compiler.graph.NodeClass;
import tornado.graal.compiler.lir.gen.LIRGeneratorTool;
import tornado.graal.compiler.nodeinfo.NodeInfo;
import tornado.graal.compiler.nodes.FixedWithNextNode;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.memory.address.AddressNode;
import tornado.graal.compiler.nodes.spi.LIRLowerable;
import tornado.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.metal.graal.HalfFloatStamp;
import uk.ac.manchester.tornado.drivers.metal.graal.MetalArchitecture;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalKind;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalLIRStmt;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalUnary;
import uk.ac.manchester.tornado.runtime.graal.nodes.interfaces.MarkOCLWriteNode;

@NodeInfo
// MarkOCLWriteNode is the backend-neutral "this node writes an array parameter" marker the sketch-tier
// TornadoDataflowAnalysis scans for. Without it a HalfFloatArray written only through this node is classified
// read-only and never copied back to the host (result reads as 0). OpenCL/CUDA mark their write nodes the same way.
public class WriteHalfFloatNode extends FixedWithNextNode implements LIRLowerable, MarkOCLWriteNode {

    public static final NodeClass<WriteHalfFloatNode> TYPE = NodeClass.create(WriteHalfFloatNode.class);

    @Input
    private AddressNode addressNode;

    @Input
    private ValueNode valueNode;

    public WriteHalfFloatNode(AddressNode addressNode, ValueNode valueNode) {
        super(TYPE, new HalfFloatStamp());
        this.addressNode = addressNode;
        this.valueNode = valueNode;
    }

    public void generate(NodeLIRBuilderTool generator) {
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        Value addressValue = generator.operand(addressNode);
        MetalArchitecture.MetalMemoryBase base = ((MetalUnary.MemoryAccess) addressValue).getBase();
        MetalUnary.MetalAddressCast cast = new MetalUnary.MetalAddressCast(base, LIRKind.value(MetalKind.HALF));
        Value input = generator.operand(valueNode);
        tool.append(new MetalLIRStmt.StoreStmt(cast, (MetalUnary.MemoryAccess) addressValue, input));
    }
}
