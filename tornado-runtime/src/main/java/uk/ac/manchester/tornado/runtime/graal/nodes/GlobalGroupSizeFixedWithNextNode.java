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
 */
package uk.ac.manchester.tornado.runtime.graal.nodes;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.LoweringTool;

import uk.ac.manchester.tornado.api.KernelContext;

/**
 * The {@link GlobalGroupSizeFixedWithNextNode} is used to replace the
 * FieldNodes that correspond to the {@link KernelContext}. In essence, these
 * fields are: globalGroupSizeX, globalGroupSizeY and globalGroupSizeZ.
 *
 * During lowering, this node is replaced with a FloatingNode that corresponds
 * to a TornadoVM backend (OpenCL, PTX). That replacement is performed in
 * OCLLoweringProvider, or PTXLoweringProvider, and drives the
 * {@link GlobalGroupSizeFixedWithNextNode} to extend FixedWithNextNode in order
 * to be replaced by a FloatingNode.
 */
@NodeInfo(shortName = "GlobalGroupSize")
public class GlobalGroupSizeFixedWithNextNode extends FixedWithNextNode implements Lowerable {

    public static final NodeClass<GlobalGroupSizeFixedWithNextNode> TYPE = NodeClass.create(GlobalGroupSizeFixedWithNextNode.class);
    private final int dimension;
    @Input
    ValueNode object;

    public GlobalGroupSizeFixedWithNextNode(ValueNode index, int dimension) {
        super(TYPE, StampFactory.forUnsignedInteger(32));
        this.object = index;
        this.dimension = dimension;
    }

    public ValueNode object() {
        return this.object;
    }

    public int getDimension() {
        return dimension;
    }

    @Override
    public void lower(LoweringTool loweringTool) {
        loweringTool.getLowerer().lower(this, loweringTool);
    }
}
