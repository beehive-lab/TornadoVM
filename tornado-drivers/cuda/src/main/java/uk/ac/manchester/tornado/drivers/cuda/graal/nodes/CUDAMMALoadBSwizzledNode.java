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

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import tornado.graal.compiler.core.common.LIRKind;
import tornado.graal.compiler.core.common.type.StampFactory;
import tornado.graal.compiler.graph.NodeClass;
import tornado.graal.compiler.lir.Variable;
import tornado.graal.compiler.lir.gen.LIRGeneratorTool;
import tornado.graal.compiler.nodeinfo.NodeInfo;
import tornado.graal.compiler.nodes.FixedWithNextNode;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.spi.LIRLowerable;
import tornado.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt;

@NodeInfo
public class CUDAMMALoadBSwizzledNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<CUDAMMALoadBSwizzledNode> TYPE = NodeClass.create(CUDAMMALoadBSwizzledNode.class);

    @Input private ValueNode tile;
    @Input private ValueNode wmmaK;
    @OptionalInput private ValueNode byteOffset;

    public CUDAMMALoadBSwizzledNode(ValueNode tile, ValueNode wmmaK) { this(tile, wmmaK, null); }
    public CUDAMMALoadBSwizzledNode(ValueNode tile, ValueNode wmmaK, ValueNode byteOffset) {
        super(TYPE, StampFactory.forKind(JavaKind.Object));
        this.tile = tile; this.wmmaK = wmmaK; this.byteOffset = byteOffset;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Value tileVal = gen.operand(tile);
        Variable fragA = tool.newVariable(LIRKind.value(CUDAKind.MMA_FRAG_B_F16));
        Value off = (byteOffset == null) ? null : gen.operand(byteOffset);
        int rowStride = 16;
        tool.append(new CUDALIRStmt.LdmatrixStmt(
                CUDALIRStmt.LdmatrixStmt.Variant.X2_TRANS_SWIZZLE_FP16_STRIDE32, fragA, tileVal, rowStride, off));
        gen.setResult(this, fragA);
    }
}
