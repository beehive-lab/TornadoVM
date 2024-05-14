/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2009-2021, Oracle and/or its affiliates. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.spirv.graal.phases;

import java.util.Optional;

import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.phases.BasePhase;

import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.LocalWorkGroupDimensionsNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.ThreadConfigurationNode;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoHighTierContext;

public class TornadoThreadScheduler extends BasePhase<TornadoHighTierContext> {

    /**
     * This value was chosen for Intel FPGAs due to experimental results.
     */
    private int oneD = 64;
    private int twoD = 1;
    private int threeD = 1;

    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {
        if (graph.hasLoops() && (context.getDeviceMapping().getDeviceType() == TornadoDeviceType.ACCELERATOR)) {
            NodeIterable<EndNode> filter = graph.getNodes().filter(EndNode.class);
            EndNode end = filter.first();
            final LocalWorkGroupDimensionsNode localWorkGroupNode = graph.addOrUnique(new LocalWorkGroupDimensionsNode(oneD, twoD, threeD));
            ThreadConfigurationNode threadConfig = graph.addOrUnique(new ThreadConfigurationNode(localWorkGroupNode));
            graph.addBeforeFixed(end, threadConfig);
        }
    }
}
