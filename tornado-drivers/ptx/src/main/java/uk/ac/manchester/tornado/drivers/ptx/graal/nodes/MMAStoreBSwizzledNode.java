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

import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

/**
 * Graal IR node for {@code KernelContext.mmaStoreBSwizzled(HalfFloat[], int, int, int, HalfFloat, int)}.
 *
 * <p>Cooperative-store partner of {@link MMALoadBSwizzledNode}. Writes one fp16
 * into a swizzled B-tile sub-region selected by {@code byteOffset}, applying the
 * same XOR permutation that {@code MMALoadBSwizzledNode}'s ldmatrix reads back.
 *
 * <p>Delegates to {@link PTXLIRStmt.SwizzledStoreFP16Stride32Stmt} (byteOffset
 * variant) — the PTX emission is identical to the general-purpose swizzle store,
 * but the typed-node identity makes the MMA coupling explicit at the graph level.
 */
@NodeInfo
public class MMAStoreBSwizzledNode extends FixedWithNextNode implements LIRLowerable {
    public static final NodeClass<MMAStoreBSwizzledNode> TYPE = NodeClass.create(MMAStoreBSwizzledNode.class);

    @Input private ValueNode tile;
    @Input private ValueNode row;
    @Input private ValueNode column;
    @Input private ValueNode stride;
    @Input private ValueNode value;
    @Input private ValueNode byteOffset;

    public MMAStoreBSwizzledNode(ValueNode tile, ValueNode row, ValueNode column,
                                 ValueNode stride, ValueNode value, ValueNode byteOffset) {
        super(TYPE, StampFactory.forVoid());
        this.tile = tile;
        this.row = row;
        this.column = column;
        this.stride = stride;
        this.value = value;
        this.byteOffset = byteOffset;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Value tileVal   = gen.operand(tile);
        Value rowVal    = gen.operand(row);
        Value colVal    = gen.operand(column);
        Value strideVal = gen.operand(stride);
        Value valueVal  = gen.operand(value);
        Value offVal    = gen.operand(byteOffset);

        Variable linIdx  = tool.newVariable(LIRKind.value(uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind.S32));
        Variable byteOff = tool.newVariable(LIRKind.value(uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind.S32));
        Variable shifted = tool.newVariable(LIRKind.value(uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind.S32));
        Variable masked  = tool.newVariable(LIRKind.value(uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind.S32));
        Variable xorTerm = tool.newVariable(LIRKind.value(uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind.S32));
        Variable swzByte = tool.newVariable(LIRKind.value(uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind.S32));

        tool.append(new uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt.SwizzledStoreFP16Stride32Stmt(
                tileVal, rowVal, colVal, strideVal, valueVal,
                linIdx, byteOff, shifted, masked, xorTerm, swzByte, offVal));
    }
}
