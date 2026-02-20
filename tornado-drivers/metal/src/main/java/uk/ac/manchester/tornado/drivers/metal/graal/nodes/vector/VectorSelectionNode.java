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
package uk.ac.manchester.tornado.drivers.metal.graal.nodes.vector;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalConstantValue;

@NodeInfo(nameTemplate = "{p#selection}")
public class VectorSelectionNode extends FloatingNode implements LIRLowerable {

    public static final NodeClass<VectorSelectionNode> TYPE = NodeClass.create(VectorSelectionNode.class);

    @Override
    public void generate(NodeLIRBuilderTool tool) {
        tool.setResult(this, new MetalConstantValue(selection.name().toLowerCase()));
    }

    public static enum VectorSelection {
        LO, Hi, ODD, EVEN;
    }

    private VectorSelection selection;

    public VectorSelectionNode(VectorSelection selection) {
        super(TYPE, StampFactory.forVoid());
        this.selection = selection;
    }

    public VectorSelection getSelection() {
        return selection;
    }

}
