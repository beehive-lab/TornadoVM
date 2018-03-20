/*
 * Copyright (c) 2018, APT Group, School of Computer Science,
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
 * Authors: Juan Fumero
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.graal.phases;

import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.BasePhase;

import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.GroupSizeNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.LocalThreadIDFixedNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLBarrierNode;
import uk.ac.manchester.tornado.graal.phases.TornadoHighTierContext;

public class TornadoOpenCLIntrinsicsReplacements extends BasePhase<TornadoHighTierContext> {

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {

        NodeIterable<InvokeNode> invokeNodes = graph.getNodes().filter(InvokeNode.class);
        for (InvokeNode invoke : invokeNodes) {
            String methodName = invoke.callTarget().targetName();

            if (methodName.equals("Direct#OpenCLIntrinsics.localBarrier")) {
                OCLBarrierNode barrier = graph.addOrUnique(new OCLBarrierNode(OCLBarrierNode.OCLMemFenceFlags.LOCAL));

                graph.replaceFixed(invoke, barrier);

                // barrier.setNext(invoke.next());
                // Node pred = invoke.predecessor();
                // pred.replaceFirstSuccessor(invoke, barrier);
                // invoke.replaceAtUsages(barrier);

            } else if (methodName.equals("Direct#OpenCLIntrinsics.globalBarrier")) {
                OCLBarrierNode barrier = graph.addOrUnique(new OCLBarrierNode(OCLBarrierNode.OCLMemFenceFlags.GLOBAL));
                graph.replaceFixed(invoke, barrier);

            } else if (methodName.equals("Direct#OpenCLIntrinsics.get_local_id")) {
                LocalThreadIDFixedNode localIDNode = graph.addOrUnique(new LocalThreadIDFixedNode(ConstantNode.forInt(0, graph)));
                graph.replaceFixed(invoke, localIDNode);
            } else if (methodName.equals("Direct#OpenCLIntrinsics.get_group_size")) {
                GroupSizeNode groupSizeNode = graph.addOrUnique(new GroupSizeNode(ConstantNode.forInt(0, graph)));
                graph.replaceFixed(invoke, groupSizeNode);

            }
        }
    }

}
