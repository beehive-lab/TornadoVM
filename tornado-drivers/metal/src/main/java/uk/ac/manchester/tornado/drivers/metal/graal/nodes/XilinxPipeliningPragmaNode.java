/*
 * Copyright (c) 2021, APT Group, Department of Computer Science,
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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * */

package uk.ac.manchester.tornado.drivers.metal.graal.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalLIRStmt;

@NodeInfo
public class XilinxPipeliningPragmaNode extends FixedWithNextNode implements LIRLowerable {

    @Successor
    LoopBeginNode loopBgNd;
    public static final NodeClass<XilinxPipeliningPragmaNode> TYPE = NodeClass.create(XilinxPipeliningPragmaNode.class);

    private int initiationIntervalValue;

    public XilinxPipeliningPragmaNode(int initiationIntervalValue) {
        super(TYPE, StampFactory.forVoid());
        this.initiationIntervalValue = initiationIntervalValue;
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeLIRBuilderTool) {
        nodeLIRBuilderTool.getLIRGeneratorTool().append(new MetalLIRStmt.PragmaExpr(new XclPipelineAttribute(initiationIntervalValue)));
    }
}
