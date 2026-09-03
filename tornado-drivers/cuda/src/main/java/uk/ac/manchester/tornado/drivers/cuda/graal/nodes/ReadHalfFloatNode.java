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
package uk.ac.manchester.tornado.drivers.cuda.graal.nodes;

import tornado.graal.compiler.core.common.LIRKind;
import tornado.graal.compiler.core.common.type.StampFactory;
import tornado.graal.compiler.graph.NodeClass;
import tornado.graal.compiler.lir.Variable;
import tornado.graal.compiler.lir.gen.LIRGeneratorTool;
import tornado.graal.compiler.nodeinfo.NodeInfo;
import tornado.graal.compiler.nodes.FixedWithNextNode;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.memory.address.AddressNode;
import tornado.graal.compiler.nodes.spi.LIRLowerable;
import tornado.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.cuda.graal.HalfFloatStamp;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDAArchitecture;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAUnary;

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
        Variable result = tool.newVariable(LIRKind.value(CUDAKind.HALF));
        Value addressValue = generator.operand(addressNode);
        CUDAArchitecture.CUDAMemoryBase base = ((CUDAUnary.MemoryAccess) addressValue).getBase();
        CUDAUnary.CUDAAddressCast cast = new CUDAUnary.CUDAAddressCast(base, LIRKind.value(CUDAKind.HALF));
        if (indexNode == null) {
            // if the index is not passed, this is not a local/shared array access
            tool.append(new CUDALIRStmt.LoadStmt(result, cast, (CUDAUnary.MemoryAccess) addressValue));
        } else {
            Value index = generator.operand(indexNode);
            tool.append(new CUDALIRStmt.LoadStmt(result, cast, (CUDAUnary.MemoryAccess) addressValue, index));
        }
        generator.setResult(this, result);
    }
}
