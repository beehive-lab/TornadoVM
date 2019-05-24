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

import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.BasePhase;

import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.FixedArrayNode;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoHighTierContext;

public class TornadoLocalMemoryDefinitionScheduler extends BasePhase<TornadoHighTierContext> {
    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {

        graph.getNodes().filter(FixedArrayNode.class).forEach(node -> {
            boolean firstLoop = true;

            if (node.isMemLocal()) {
                graph.getNodes().filter(LoopBeginNode.class).forEach(loopBeginNode -> {
                    // loopBeginNode.
                    if (firstLoop) {
                        System.out.println(" BlockNodes " + loopBeginNode.getBlockNodes());
                        System.out.println(" toString " + loopBeginNode.toString());
                        System.out.println(" Predecessor " + loopBeginNode.predecessor());
                        System.out.println(" Predecessors " + loopBeginNode.cfgPredecessors());
                        System.out.println(" ++++++++++++++++++++++++++++++++++++ ");
                        // firstLoop = false;
                    }

                });
            }
            System.out.println(" Lenght " + node.getLength());
            System.out.println(" Type " + node.getElementType());
            System.out.println(" Is local " + node.isMemLocal());
            System.out.println(" Successors" + node.successors());
            System.out.println(" Predecessors " + node.predecessor());
            System.out.println(" ************************************* ");
        });

    }
}
