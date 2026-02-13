/*
 * Copyright (c) 2018, 2020, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.runtime.graal.nodes.logic;

import jdk.graal.compiler.graph.IterableNodeType;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.runtime.graal.nodes.interfaces.LogicalCompareNode;

@NodeInfo
public abstract class BinaryLogicalNode extends LogicNode implements IterableNodeType, Canonicalizable.Binary<LogicNode>, LogicalCompareNode {

    public static final NodeClass<BinaryLogicalNode> TYPE = NodeClass.create(BinaryLogicalNode.class);

    @Input(InputType.Condition)
    LogicNode x;
    @Input(InputType.Condition)
    LogicNode y;

    protected BinaryLogicalNode(NodeClass<? extends BinaryLogicalNode> type, LogicNode x, LogicNode y) {
        super(type);
        this.x = x;
        this.y = y;
    }

    @Override
    public final void generate(NodeLIRBuilderTool builder) {
        Value x = builder.operand(getX());
        Value y = builder.operand(getY());
        Value result = generate(builder.getLIRGeneratorTool(), x, y);
        builder.setResult(this, result);
    }

    public abstract Value generate(LIRGeneratorTool gen, Value x, Value y);

    @Override
    public LogicNode canonical(CanonicalizerTool tool) {
        return this;
    }

    @Override
    public Node canonical(CanonicalizerTool tool, LogicNode forX, LogicNode forY) {
        return this;
    }

    @Override
    public LogicNode getX() {
        return x;
    }

    @Override
    public LogicNode getY() {
        return y;
    }

}
