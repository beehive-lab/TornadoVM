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

import tornado.graal.compiler.core.common.type.StampFactory;
import tornado.graal.compiler.graph.NodeClass;
import tornado.graal.compiler.lir.gen.LIRGeneratorTool;
import tornado.graal.compiler.nodeinfo.NodeInfo;
import tornado.graal.compiler.nodes.FixedWithNextNode;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.spi.LIRLowerable;
import tornado.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt;
import uk.ac.manchester.tornado.runtime.graal.nodes.interfaces.MarkArrayParameterAccess;

@NodeInfo
public class CUDASwizzledStoreFP16Stride32Node extends FixedWithNextNode
        implements LIRLowerable, MarkArrayParameterAccess {

    public static final NodeClass<CUDASwizzledStoreFP16Stride32Node> TYPE =
            NodeClass.create(CUDASwizzledStoreFP16Stride32Node.class);

    @Input private ValueNode fp16LocalArray;
    @Input private ValueNode row;
    @Input private ValueNode column;
    @Input private ValueNode stride;
    @Input private ValueNode fp16Value;

    public CUDASwizzledStoreFP16Stride32Node(ValueNode fp16LocalArray, ValueNode row,
                                             ValueNode column, ValueNode stride,
                                             ValueNode fp16Value) {
        super(TYPE, StampFactory.forVoid());
        this.fp16LocalArray = fp16LocalArray;
        this.row = row;
        this.column = column;
        this.stride = stride;
        this.fp16Value = fp16Value;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        tool.append(new CUDALIRStmt.SwizzledStoreFP16Stride32Stmt(
                generator.operand(fp16LocalArray),
                generator.operand(row),
                generator.operand(column),
                generator.operand(stride),
                generator.operand(fp16Value)));   // no byteOffset — 5-arg constructor
    }

    @Override
    public Access getArrayParameterAccess(ValueNode parameter) {
        return Access.NONE;   // shared-memory tile, never a kernel parameter
    }
}
