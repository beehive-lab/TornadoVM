/*
 * Copyright (c) 2018-2024 APT Group, Department of Computer Science,
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
 * Authors: Michalis Papadimitriou
 *
 */
package uk.ac.manchester.tornado.drivers.common.compiler.phases.memalloc;

import java.util.Optional;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.phases.BasePhase;

import uk.ac.manchester.tornado.runtime.graal.nodes.interfaces.MarkLocalArray;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoHighTierContext;

/**
 * 
 * It filters the nodes for the length of the FixedArray node for the local
 * memory and calculates an optimal local memory size based on driver info.
 * Then, it replaces the length with the optimal size.
 *
 */
public class TornadoLocalMemoryAllocation extends BasePhase<TornadoHighTierContext> {
    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {

        if (!context.hasMeta()) {
            return;
        }

        if ((context.getMeta().getDomain() != null)) {
            if ((context.getMeta().getDomain().getDepth() != 0)) {
                NodeIterable<Node> sumNodes = graph.getNodes();

                for (Node n : sumNodes) {
                    if (n instanceof MarkLocalArray) {
                        ConstantNode newLengthNode = ConstantNode.forInt(calculateLocalMemAllocSize(context), graph);
                        if (newLengthNode != n.inputs().first()) {
                            n.inputs().first().replaceAndDelete(newLengthNode);
                        }
                    }
                }
            }
        }
    }

    private int calculateLocalMemAllocSize(TornadoHighTierContext context) {
        int maxBlockSize = (int) context.getDeviceMapping().getPhysicalDevice().getDeviceMaxWorkItemSizes()[0];

        if (context.getDeviceMapping().getPhysicalDevice().getDeviceMaxWorkItemSizes()[0] == context.getMeta().getDomain().get(0).cardinality()) {
            maxBlockSize /= 4;
        }

        int value = Math.min(Math.max(maxBlockSize, context.getMeta().getOpenCLGpuBlock2DX()), context.getMeta().getDomain().get(0).cardinality());

        if (value == 0) {
            return 0;
        } else {
            while (context.getMeta().getDomain().get(0).cardinality() % value != 0) {
                value--;
            }
            return value;
        }
    }
}
