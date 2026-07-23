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
package uk.ac.manchester.tornado.drivers.cuda.graal.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt;

/**
 * Asynchronous 4-byte global-to-shared copy ({@code cp.async.ca.shared.global},
 * Ampere+/sm_80): moves one packed b32 slot from a global Tornado array into a
 * shared-memory int tile without staging through registers. Copies must be
 * closed with {@link CUDACpAsyncCommitGroupNode} and awaited with
 * {@link CUDACpAsyncWaitGroupNode} before the tile is read.
 */
@NodeInfo
public class CUDACpAsyncCopyNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<CUDACpAsyncCopyNode> TYPE = NodeClass.create(CUDACpAsyncCopyNode.class);

    @Input private ValueNode dstTile;
    @Input private ValueNode dstIndex;
    @Input private ValueNode srcArray;
    @Input private ValueNode srcIndex;
    private final int srcElemBytes;
    private final int headerBytes;

    public CUDACpAsyncCopyNode(ValueNode dstTile, ValueNode dstIndex, ValueNode srcArray,
                               ValueNode srcIndex, int srcElemBytes, int headerBytes) {
        super(TYPE, StampFactory.forVoid());
        this.dstTile = dstTile;
        this.dstIndex = dstIndex;
        this.srcArray = srcArray;
        this.srcIndex = srcIndex;
        this.srcElemBytes = srcElemBytes;
        this.headerBytes = headerBytes;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        gen.getLIRGeneratorTool().append(new CUDALIRStmt.CpAsyncCopyStmt(
                gen.operand(dstTile), gen.operand(dstIndex),
                gen.operand(srcArray), gen.operand(srcIndex),
                srcElemBytes, headerBytes));
    }
}
