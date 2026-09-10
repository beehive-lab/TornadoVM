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

import jdk.vm.ci.meta.JavaKind;
import tornado.graal.compiler.core.common.type.StampFactory;
import tornado.graal.compiler.graph.NodeClass;
import tornado.graal.compiler.graph.NodeInputList;
import tornado.graal.compiler.nodeinfo.NodeInfo;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.calc.FloatingNode;
import uk.ac.manchester.tornado.api.tile.DType;

/**
 * A tensor view: the buffer, its element type and its extents, carried from
 * {@code TileContext.view(...)} to the {@code partition(...)} that consumes it.
 *
 * <p>
 * This node is deliberately not lowerable. It is a parse time descriptor that
 * {@link CUDATilePartitionViewNode} reads and then discards, because a view only reaches the
 * generated code as part of the partition view that a load or store addresses through. If one
 * ever survives to LIR that is a bug in the plugin pairing, and it fails loudly rather than
 * emitting something meaningless.
 * </p>
 */
@NodeInfo
public class CUDATileViewNode extends FloatingNode {

    public static final NodeClass<CUDATileViewNode> TYPE = NodeClass.create(CUDATileViewNode.class);

    @Input
    protected ValueNode buffer;
    @Input
    protected NodeInputList<ValueNode> extents;

    private final DType dtype;

    public CUDATileViewNode(ValueNode buffer, ValueNode[] extents, DType dtype) {
        super(TYPE, StampFactory.forKind(JavaKind.Object));
        this.buffer = buffer;
        this.extents = new NodeInputList<>(this, extents);
        this.dtype = dtype;
    }

    public ValueNode getBuffer() {
        return buffer;
    }

    public ValueNode[] getExtents() {
        return extents.toArray(new ValueNode[0]);
    }

    public int getRank() {
        return extents.size();
    }

    public DType getDType() {
        return dtype;
    }
}
