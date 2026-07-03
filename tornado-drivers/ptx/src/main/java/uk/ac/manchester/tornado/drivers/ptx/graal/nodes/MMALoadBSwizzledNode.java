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
package uk.ac.manchester.tornado.drivers.ptx.graal.nodes;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt;

/**
 * Graal IR node for {@code KernelContext.mmaLoadBSwizzled(HalfFloat[], int)}.
 *
 * <p>Variant of {@link uk.ac.manchester.tornado.drivers.ptx.graal.nodes.MMALoadBNode} that reads from a tile populated using
 * the FP16 stride-32 swizzle layout. The address computation applies an XOR
 * permutation matching {@code SwizzledStoreFP16Stride32Stmt}, so the resulting
 * ldmatrix reads incur no shared-memory bank conflicts.
 *
 * <p>The tile must be allocated as a native fp16 array (not int-packed) and
 * populated using the {@code swizzleStoreFp16Stride32} accessor.
 *
 * <p>Lowers to {@code ldmatrix.sync.aligned.m8n8.x2.trans.shared.b16} with
 * the lane's address XOR-permuted by the stride-32 swizzle.
 */
@NodeInfo
public class MMALoadBSwizzledNode extends FixedWithNextNode implements LIRLowerable {
    public static final NodeClass<MMALoadBSwizzledNode> TYPE = NodeClass.create(MMALoadBSwizzledNode.class);

    @Input private ValueNode tile;
    @Input private ValueNode wmmaK;
    @OptionalInput private ValueNode byteOffset;

    public MMALoadBSwizzledNode(ValueNode tile, ValueNode wmmaK) {
          this(tile, wmmaK, null);
      }
      public MMALoadBSwizzledNode(ValueNode tile, ValueNode wmmaK, ValueNode byteOffset) {
          super(TYPE, StampFactory.forKind(JavaKind.Object));
          this.tile = tile;
          this.wmmaK = wmmaK;
          this.byteOffset = byteOffset;
      }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();

        Value tileVal = gen.operand(tile);
        Variable fragB = tool.newVariable(LIRKind.value(PTXKind.MMA_FRAG_B_F16));

        LIRKind u32 = LIRKind.value(PTXKind.U32);
        LIRKind u64 = LIRKind.value(PTXKind.U64);

        // Swizzled canonical stacked layout: 16 rows × 8 cols of fp16, row-major.
        // rowStride = 8 fp16 = 16 bytes. The XOR swizzle is applied to byte offsets
        // before computing lane addresses (see X2_TRANS_SWIZZLE_FP16_STRIDE32 in LdmatrixStmt).
        // The constructor's runtime check enforces rowStride == 16.
        int rowStride = 16;

        if (byteOffset == null) {
            tool.append(new PTXLIRStmt.LdmatrixStmt(
                    PTXLIRStmt.LdmatrixStmt.Variant.X2_TRANS_SWIZZLE_FP16_STRIDE32,
                    fragB, tileVal,
                    tool.newVariable(u32), tool.newVariable(u32), tool.newVariable(u32),
                    tool.newVariable(u32), tool.newVariable(u32), tool.newVariable(u32),
                    tool.newVariable(u32), tool.newVariable(u32), tool.newVariable(u64),
                    rowStride));
        } else {
            Value byteOffsetVal = gen.operand(byteOffset);
            tool.append(new PTXLIRStmt.LdmatrixStmt(
                    PTXLIRStmt.LdmatrixStmt.Variant.X2_TRANS_SWIZZLE_FP16_STRIDE32,
                    fragB, tileVal,
                    tool.newVariable(u32), tool.newVariable(u32), tool.newVariable(u32),
                    tool.newVariable(u32), tool.newVariable(u32), tool.newVariable(u32),
                    tool.newVariable(u32), tool.newVariable(u32), tool.newVariable(u64),
                    rowStride, byteOffsetVal));
        }

        gen.setResult(this, fragB);
    }
}
