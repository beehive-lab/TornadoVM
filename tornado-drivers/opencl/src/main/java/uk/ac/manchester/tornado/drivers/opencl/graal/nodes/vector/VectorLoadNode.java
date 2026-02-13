/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.opencl.graal.nodes.vector;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.AccessIndexedNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLStamp;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLStampFactory;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLKind;

/**
 * The {@code VectorLoadNode} represents a vector-read from a set of contiguous
 * elements of an array.
 */
@NodeInfo(nameTemplate = "VectorLoad")
public class VectorLoadNode extends AccessIndexedNode {

    public static final NodeClass<VectorLoadNode> TYPE = NodeClass.create(VectorLoadNode.class);

    private final OCLKind kind;

    /**
     * Creates a new LoadIndexedNode.
     *
     * @param kind
     *     the element type
     * @param array
     *     the instruction producing the array
     * @param index
     *     the instruction producing the index
     */
    public VectorLoadNode(OCLKind kind, ValueNode array, ValueNode index) {
        super(TYPE, OCLStampFactory.getStampFor(kind), array, index, null, JavaKind.Illegal);
        this.kind = kind;
    }

    public Node canonical(CanonicalizerTool tool) {
        return this;
    }

    public int length() {
        return kind.getVectorLength();
    }

    public OCLKind elementType() {
        return kind.getElementKind();
    }

    public OCLKind vectorKind() {
        return kind;
    }

    @Override
    public JavaKind elementKind() {
        return ((OCLStamp) stamp(NodeView.DEFAULT)).getOCLKind().getElementKind().asJavaKind();
    }
}
