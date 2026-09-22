/*
 * Copyright (c) 2018, 2020-2022, 2024, 2025, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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

import jdk.vm.ci.meta.Value;
import tornado.graal.compiler.core.common.type.StampFactory;
import tornado.graal.compiler.graph.NodeClass;
import tornado.graal.compiler.graph.NodeInputList;
import tornado.graal.compiler.lir.gen.LIRGeneratorTool;
import tornado.graal.compiler.nodeinfo.NodeInfo;
import tornado.graal.compiler.nodes.FixedWithNextNode;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.spi.LIRLowerable;
import tornado.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.tile.DType;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDATileStmt;
import uk.ac.manchester.tornado.runtime.graal.nodes.interfaces.MarkArrayParameterAccess;

/**
 * Applies a tile to a view atomically: add, sub, min, max, and, or, xor or exchange.
 *
 * <p>
 * Unlike a store, this cannot go through the {@code partition_view}: CUDA Tile gives a view
 * {@code atomic_load} and {@code atomic_store} but no read-modify-write, and
 * {@code ct::atomic_add} instead takes a tile of pointers. So this node carries the view's
 * buffer and extents rather than the view value, and the statement builds the pointer tile for
 * the addressed block itself.
 * </p>
 */
@NodeInfo
public class CUDATileAtomicNode extends FixedWithNextNode implements LIRLowerable, CUDATileNode, MarkArrayParameterAccess {

    public static final NodeClass<CUDATileAtomicNode> TYPE = NodeClass.create(CUDATileAtomicNode.class);

    @Input
    protected ValueNode buffer;
    @Input
    protected ValueNode tile;
    @Input
    protected NodeInputList<ValueNode> extents;
    @Input
    protected NodeInputList<ValueNode> blockIndices;

    private final DType dtype;
    private final int[] tileShape;
    private final int payloadOffset;

    private final String function;

    public CUDATileAtomicNode(ValueNode buffer, ValueNode tile, ValueNode[] extents, ValueNode[] blockIndices, String function, DType dtype, int[] tileShape, int payloadOffset) {
        super(TYPE, StampFactory.forVoid());
        this.function = function;
        this.buffer = buffer;
        this.tile = tile;
        this.extents = new NodeInputList<>(this, extents);
        this.blockIndices = new NodeInputList<>(this, blockIndices);
        this.dtype = dtype;
        this.tileShape = tileShape;
        this.payloadOffset = payloadOffset;
    }

    @Override
    public String tileOperationName() {
        return "tile " + function;
    }

    /**
     * Reports the accumulator as read and written.
     *
     * <p>
     * This node has to do its own marking rather than leaning on the view node, because it does
     * not consume the view: it takes the buffer and extents directly, which leaves the view node
     * with no usages at all, so the view is eliminated and marks nothing. Without this the
     * dataflow analysis calls the accumulator read-only, no device-to-host transfer is emitted,
     * and the kernel's atomics land in a buffer the host never reads back - the result looks
     * like an accumulator that stayed at zero.
     * </p>
     */
    @Override
    public Access getArrayParameterAccess(ValueNode parameter) {
        if (MarkArrayParameterAccess.unwrapPi(parameter) != MarkArrayParameterAccess.unwrapPi(buffer)) {
            return Access.NONE;
        }
        // An atomic add reads the old value and writes the new one.
        return Access.READ_WRITE;
    }

    @Override
    public DType tileDType() {
        return dtype;
    }

    @Override
    public int[] tileShape() {
        return tileShape;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Value[] extentValues = new Value[extents.size()];
        for (int i = 0; i < extents.size(); i++) {
            extentValues[i] = gen.operand(extents.get(i));
        }
        Value[] indexValues = new Value[blockIndices.size()];
        for (int i = 0; i < blockIndices.size(); i++) {
            indexValues[i] = gen.operand(blockIndices.get(i));
        }
        tool.append(new CUDATileStmt.TileAtomicStmt(gen.operand(buffer), gen.operand(tile), extentValues, indexValues, function, dtype, tileShape, payloadOffset));
    }
}
