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
 * Creates a tile filled with a constant, the start of an accumulator chain.
 */
@NodeInfo
public class CUDATileCreateNode extends FixedWithNextNode implements LIRLowerable, CUDATileNode {

    public static final NodeClass<CUDATileCreateNode> TYPE = NodeClass.create(CUDATileCreateNode.class);

    private final DType dtype;
    private final int[] shape;
    private final String initializer;

    public CUDATileCreateNode(DType dtype, int[] shape, String initializer) {
        super(TYPE, StampFactory.forKind(JavaKind.Object));
        this.dtype = dtype;
        this.shape = shape;
        this.initializer = initializer;
    }

    public DType getDType() {
        return dtype;
    }

    public int[] getShape() {
        return shape;
    }

    @Override
    public String tileOperationName() {
        return "tile creation";
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
        tool.append(new CUDATileStmt.TileCreateStmt(result, dtype, shape, initializer));
        gen.setResult(this, result);
    }
}
