/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.runtime.kotlin;

import tornado.graal.compiler.graph.NodeClass;
import tornado.graal.compiler.nodeinfo.NodeInfo;
import tornado.graal.compiler.nodes.NodeView;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.calc.FloatingNode;

/**
 * Marks the induction variable of a loop written with the Kotlin API's {@code parallelFor}. It is created while
 * bytecode is parsed, from the call to {@code ParallelLoops.parallelIndex(index)} that {@code parallelFor} places on
 * its loop index, and it evaluates to that index. {@code TornadoApiReplacement} treats the marked loop as a
 * {@code @Parallel} loop and removes the marker.
 */
@NodeInfo(nameTemplate = "KotlinParallelIndex")
public class KotlinParallelIndexNode extends FloatingNode {

    public static final NodeClass<KotlinParallelIndexNode> TYPE = NodeClass.create(KotlinParallelIndexNode.class);

    @Input
    protected ValueNode index;

    public KotlinParallelIndexNode(ValueNode index) {
        super(TYPE, index.stamp(NodeView.DEFAULT));
        this.index = index;
    }

    public ValueNode index() {
        return index;
    }
}
