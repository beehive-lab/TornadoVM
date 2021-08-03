/*
 * Copyright (c) 2018, 2020-2021, APT Group, Department of Computer Science,
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
 * Authors: Michalis Papadimitriou
 *
 *
 * */
package uk.ac.manchester.tornado.drivers.opencl.graal.phases;

import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.Phase;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.LocalWorkGroupDimensionsNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.ThreadConfigurationNode;

public class OCLFPGAThreadScheduler extends Phase {

    private int oneD = 64; // XXX: This value was chosen for Intel FPGAs due to experimental results
    private int twoD = 1;
    private int threeD = 1;

    TornadoDeviceContext context;

    public OCLFPGAThreadScheduler(TornadoDeviceContext context) {
        this.context = context;
    }

    @Override
    protected void run(StructuredGraph graph) {
        if (graph.hasLoops() && context.isPlatformFPGA()) {
            NodeIterable<EndNode> filter = graph.getNodes().filter(EndNode.class);
            EndNode end = filter.first();
            final LocalWorkGroupDimensionsNode localWorkGroupNode = graph.addOrUnique(new LocalWorkGroupDimensionsNode(oneD, twoD, threeD));
            ThreadConfigurationNode threadConfig = graph.addOrUnique(new ThreadConfigurationNode(localWorkGroupNode));
            graph.addBeforeFixed(end, threadConfig);
        }
    }
}