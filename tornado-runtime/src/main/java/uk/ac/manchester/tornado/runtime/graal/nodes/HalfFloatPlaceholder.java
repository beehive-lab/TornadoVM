/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.runtime.graal.nodes;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ValueNode;

import jdk.vm.ci.meta.JavaKind;

@NodeInfo
public class HalfFloatPlaceholder extends ValueNode {

    public static final NodeClass<HalfFloatPlaceholder> TYPE = NodeClass.create(HalfFloatPlaceholder.class);

    @Input
    private ValueNode input;

    public HalfFloatPlaceholder(ValueNode input) {
        super(TYPE, StampFactory.forKind(JavaKind.Short));
        this.input = input;
    }

    public ValueNode getInput() {
        return this.input;
    }

    public void setInput(ValueNode input) {
        this.input = input;
    }

}
