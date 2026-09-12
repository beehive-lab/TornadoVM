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
import jdk.vm.ci.meta.Value;
import tornado.graal.compiler.core.common.LIRKind;
import tornado.graal.compiler.core.common.type.StampFactory;
import tornado.graal.compiler.graph.NodeClass;
import tornado.graal.compiler.graph.NodeInputList;
import tornado.graal.compiler.lir.Variable;
import tornado.graal.compiler.lir.gen.LIRGeneratorTool;
import tornado.graal.compiler.nodeinfo.NodeInfo;
import tornado.graal.compiler.nodes.FixedWithNextNode;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.spi.LIRLowerable;
import tornado.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.api.tile.DType;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDATileStmt;

/**
 * A tile operation whose second argument is a shape: {@code ct::broadcast},
 * {@code ct::reshape}, or {@code ct::extract} (which adds sub-tile indices).
 */
@NodeInfo
public class CUDATileShapeOpNode extends FixedWithNextNode implements LIRLowerable, CUDATileNode {

    public static final NodeClass<CUDATileShapeOpNode> TYPE = NodeClass.create(CUDATileShapeOpNode.class);

    @Input
    protected ValueNode tile;
    @Input
    protected NodeInputList<ValueNode> blockIndices;

    private final String function;
    private final DType dtype;
    private final int[] shape;

    public CUDATileShapeOpNode(ValueNode tile, ValueNode[] blockIndices, String function, DType dtype, int[] shape) {
        super(TYPE, StampFactory.forKind(JavaKind.Object));
        this.tile = tile;
        this.blockIndices = new NodeInputList<>(this, blockIndices);
        this.function = function;
        this.dtype = dtype;
        this.shape = shape;
    }

    @Override
    public String tileOperationName() {
        return "tile " + function;
    }

    @Override
    public DType tileDType() {
        return dtype;
    }

    @Override
    public int[] tileShape() {
        return shape;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Variable result = tool.newVariable(LIRKind.value(CUDAKind.TILE));
        Value[] indices = new Value[blockIndices.size()];
        for (int i = 0; i < blockIndices.size(); i++) {
            indices[i] = gen.operand(blockIndices.get(i));
        }
        tool.append(new CUDATileStmt.TileShapeOpStmt(result, gen.operand(tile), indices, function, dtype, shape));
        gen.setResult(this, result);
    }
}
