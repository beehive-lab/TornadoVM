/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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

import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;

@NodeInfo(nameTemplate = "Range")
public class ParallelRangeNode extends AbstractParallelNode {

    public static final NodeClass<ParallelRangeNode> TYPE = NodeClass.create(ParallelRangeNode.class);

    @Input(InputType.Association)
    private FloatingNode offset;
    @Input(InputType.Association)
    private FloatingNode stride;

    public ParallelRangeNode(int index, ValueNode range, ParallelOffsetNode offset, ParallelStrideNode stride) {
        super(TYPE, index, range);
        this.offset = offset;
        this.stride = stride;
    }

    public ParallelOffsetNode offset() {
        assert offset instanceof ParallelOffsetNode;
        return (ParallelOffsetNode) offset;
    }

    public ParallelStrideNode stride() {
        assert stride instanceof ParallelStrideNode;
        return (ParallelStrideNode) stride;
    }

}
