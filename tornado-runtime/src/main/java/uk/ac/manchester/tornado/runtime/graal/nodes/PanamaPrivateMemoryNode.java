/*
 * Copyright (c) 2023, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.runtime.graal.nodes;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;

import jdk.vm.ci.meta.ResolvedJavaType;

@NodeInfo(nameTemplate = "TornadoPrivateMemoryNode")
public class PanamaPrivateMemoryNode extends FixedWithNextNode {

    public static final NodeClass<PanamaPrivateMemoryNode> TYPE = NodeClass.create(PanamaPrivateMemoryNode.class);
    private ResolvedJavaType elementType;
    @Input
    private ValueNode length;

    public PanamaPrivateMemoryNode(ResolvedJavaType elementType, ValueNode length) {
        super(TYPE, StampFactory.forKind(elementType.getJavaKind()));
        this.elementType = elementType;
        this.length = length;
    }

    public ResolvedJavaType getResolvedJavaType() {
        return elementType;
    }

    public ValueNode getLength() {
        return length;
    }
}
