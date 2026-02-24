/*
 * Copyright (c) 2018, 2020-2021, APT Group, Department of Computer Science,
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
 * Authors: Michalis Papadimitriou
 */
package uk.ac.manchester.tornado.drivers.opencl.graal.nodes;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

@NodeInfo
public class FPGAWorkGroupSizeNode extends FixedWithNextNode implements LIRLowerable {

    @Successor
    LoopBeginNode loopBegin;
    @Input
    LocalWorkGroupDimensionsNode localWorkNode;
    public static final NodeClass<FPGAWorkGroupSizeNode> TYPE = NodeClass.create(FPGAWorkGroupSizeNode.class);

    public static final String FPGA_THREAD_ATTRIBUTE_PREFIX = "__attribute__((reqd_work_group_size(";
    public static final String FPGA_THREAD_ATTRIBUTE_SUFFIX = ")))";

    public FPGAWorkGroupSizeNode(LocalWorkGroupDimensionsNode localWork) {
        super(TYPE, StampFactory.forVoid());
        this.localWorkNode = localWork;
    }

    /**
     * This an empty implementation for generating LIR for this node. This is due to
     * a fix template used during code generation for this node.
     */
    @Override
    public void generate(NodeLIRBuilderTool nodeLIRBuilderTool) {
    }

    public String createThreadAttribute() {
        String fpgaThreadAttribute;
        fpgaThreadAttribute = FPGA_THREAD_ATTRIBUTE_PREFIX + localWorkNode.getXToString() + ", " + localWorkNode.getYToString() + ", " + localWorkNode.getZToString() + FPGA_THREAD_ATTRIBUTE_SUFFIX;

        return fpgaThreadAttribute;
    }
}
