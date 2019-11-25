/*
 * Copyright (c) 2018, 2019, APT Group, School of Computer Science,
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

import java.util.List;

import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;

import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.LocalWorkGroupDimensionsNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.ThreadConfigurationNode;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoHighTierContext;

public class TornadoThreadScheduler extends BasePhase<TornadoHighTierContext> {
    private final CanonicalizerPhase canonicalizer;

    private int oneD = 64; // XXX: This value was chosen for Intel FPGAs due to experimental results
    private int twoD = 1;
    private int threeD = 1;

    public TornadoThreadScheduler(CanonicalizerPhase canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {
        if (graph.hasLoops() && (context.getDeviceMapping().getDeviceType() == TornadoDeviceType.ACCELERATOR)) {
            List<EndNode> snapshot = graph.getNodes().filter(EndNode.class).snapshot();
            int idx = 0;
            for (EndNode end : snapshot) {
                if (idx == 0) {
                    final LocalWorkGroupDimensionsNode lwg = graph.addOrUnique(new LocalWorkGroupDimensionsNode(oneD, twoD, threeD));
                    ThreadConfigurationNode threadConfig = graph.addOrUnique(new ThreadConfigurationNode(lwg));
                    graph.addBeforeFixed(end, threadConfig);
                    break;
                }
            }
        }
    }
}