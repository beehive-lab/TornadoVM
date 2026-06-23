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

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
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
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt;

/**
 * Graal IR node for {@code KernelContext.mmaLoadA(float[], int)}.
 *
 * <p>Loads the A fragment for {@code mma.sync.m16n8k16}: 8 × f16 elements per
 * lane, packed into 4 × b32 registers ({@code ra0..ra3}).
 *
 * <p>Lowers to 4 × {@code ld.shared.b32} with lane-specific offsets following
 * PTX ISA Table 108 A-operand row-major layout.
 */
@NodeInfo(shortName = "MMALoadA")
public class MMALoadANode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<MMALoadANode> TYPE = NodeClass.create(MMALoadANode.class);

    @Input private ValueNode tile;
    @Input private ValueNode wmmaK;
    @OptionalInput private ValueNode byteOffset;

    public MMALoadANode(ValueNode tile, ValueNode wmmaK) {
          this(tile, wmmaK, null);
      }
      public MMALoadANode(ValueNode tile, ValueNode wmmaK, ValueNode byteOffset) {
          super(TYPE, StampFactory.forKind(JavaKind.Object));
          this.tile = tile;
          this.wmmaK = wmmaK;
          this.byteOffset = byteOffset;
      }
    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Value tileVal = gen.operand(tile);

        Variable fragA = tool.newVariable(LIRKind.value(PTXKind.MMA_FRAG_A_F16));

        LIRKind u32 = LIRKind.value(PTXKind.U32);
        LIRKind u64 = LIRKind.value(PTXKind.U64);

        // rowStride = 16 b16 × 2 bytes = 32 bytes for 16-wide f16 row
        int rowStride = 32;

        if (byteOffset == null) {
            tool.append(new PTXLIRStmt.LdmatrixStmt(
                    PTXLIRStmt.LdmatrixStmt.Variant.X4,
                    fragA, tileVal,
                    tool.newVariable(u32), tool.newVariable(u32), tool.newVariable(u32),
                    tool.newVariable(u32), tool.newVariable(u32), tool.newVariable(u32),
                    tool.newVariable(u32), tool.newVariable(u32), tool.newVariable(u64),
                    rowStride));
        } else {
            Value byteOffsetVal = gen.operand(byteOffset);
            tool.append(new PTXLIRStmt.LdmatrixStmt(
                    PTXLIRStmt.LdmatrixStmt.Variant.X4,
                    fragA, tileVal,
                    tool.newVariable(u32), tool.newVariable(u32), tool.newVariable(u32),
                    tool.newVariable(u32), tool.newVariable(u32), tool.newVariable(u32),
                    tool.newVariable(u32), tool.newVariable(u32), tool.newVariable(u64),
                    rowStride, byteOffsetVal));
        }

        gen.setResult(this, fragA);
    }
}
