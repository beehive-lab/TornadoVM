/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.AbstractNewArrayNode;

import jdk.vm.ci.meta.ResolvedJavaType;

@NodeInfo
public class NewArrayNonVirtualizableNode extends AbstractNewArrayNode {
    public static final NodeClass<NewArrayNonVirtualizableNode> TYPE = NodeClass.create(NewArrayNonVirtualizableNode.class);
    private final ResolvedJavaType elementType;

    public NewArrayNonVirtualizableNode(ResolvedJavaType elementType, ValueNode length, boolean fillContents) {
        this(elementType, length, fillContents, null);
    }

    public NewArrayNonVirtualizableNode(ResolvedJavaType elementType, ValueNode length, boolean fillContents, FrameState stateBefore) {
        this(TYPE, elementType, length, fillContents, stateBefore);
    }

    protected NewArrayNonVirtualizableNode(NodeClass<? extends NewArrayNonVirtualizableNode> c, ResolvedJavaType elementType, ValueNode length, boolean fillContents, FrameState stateBefore) {
        super(c, StampFactory.objectNonNull(TypeReference.createExactTrusted(elementType.getArrayClass())), length, fillContents, stateBefore);
        this.elementType = elementType;
    }

    @Node.NodeIntrinsic
    private static native Object NewArrayNonVirtualNode(@Node.ConstantNodeParameter Class<?> elementType, int length, @Node.ConstantNodeParameter boolean fillContents);

    public static Object newUninitializedArray(Class<?> elementType, int length) {
        return NewArrayNonVirtualNode(elementType, length, false);
    }

    public ResolvedJavaType elementType() {
        return elementType;
    }
}
