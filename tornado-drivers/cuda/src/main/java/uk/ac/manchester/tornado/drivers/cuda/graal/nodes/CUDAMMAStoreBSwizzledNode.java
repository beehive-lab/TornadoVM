/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.cuda.graal.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt;

@NodeInfo
public class CUDAMMAStoreBSwizzledNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<CUDAMMAStoreBSwizzledNode> TYPE = NodeClass.create(CUDAMMAStoreBSwizzledNode.class);

    @Input private ValueNode tile;
    @Input private ValueNode row;
    @Input private ValueNode column;
    @Input private ValueNode stride;
    @Input private ValueNode value;
    @Input private ValueNode byteOffset;

    public CUDAMMAStoreBSwizzledNode(ValueNode tile, ValueNode row, ValueNode column,
                                     ValueNode stride, ValueNode value, ValueNode byteOffset) {
        super(TYPE, StampFactory.forVoid());
        this.tile = tile; this.row = row; this.column = column;
        this.stride = stride; this.value = value; this.byteOffset = byteOffset;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        tool.append(new CUDALIRStmt.SwizzledStoreFP16Stride32Stmt(
                gen.operand(tile), gen.operand(row), gen.operand(column),
                gen.operand(stride), gen.operand(value), gen.operand(byteOffset)));
    }
}
