/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.runtime.graal.nodes;

import jdk.vm.ci.meta.JavaKind;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ValueNode;

@NodeInfo(shortName = "FLOAT16(/)")
public class DivHalfFloatNode extends ValueNode {
    public static final NodeClass<DivHalfFloatNode> TYPE = NodeClass.create(DivHalfFloatNode.class);

    @Node.Input
    ValueNode input1;

    @Node.Input
    ValueNode input2;

    public DivHalfFloatNode(ValueNode input1, ValueNode input2) {
        super(TYPE, StampFactory.forKind(JavaKind.Object));
        this.input1 = input1;
        this.input2 = input2;
    }

    public ValueNode getX() {
        return input1;
    }

    public ValueNode getY() {
        return input2;
    }
}
