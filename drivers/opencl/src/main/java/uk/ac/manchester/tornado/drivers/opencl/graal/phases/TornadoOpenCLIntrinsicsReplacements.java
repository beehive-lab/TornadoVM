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
 * Authors: Juan Fumero
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.graal.phases;

import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.phases.BasePhase;

import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.GlobalThreadIdNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.GlobalThreadSizeNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.GroupIdNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.LocalGroupSizeNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.LocalThreadIDFixedNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLBarrierNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OpenCLPrintf;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoHighTierContext;

public class TornadoOpenCLIntrinsicsReplacements extends BasePhase<TornadoHighTierContext> {

    private ConstantNode getConstantNodeFromArguments(InvokeNode invoke, int index) {
        NodeInputList<ValueNode> arguments = invoke.callTarget().arguments();
        return (ConstantNode) arguments.get(index);
    }

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {

        NodeIterable<InvokeNode> invokeNodes = graph.getNodes().filter(InvokeNode.class);
        for (InvokeNode invoke : invokeNodes) {
            String methodName = invoke.callTarget().targetName();

            if (methodName.equals("Direct#OpenCLIntrinsics.localBarrier")) {
                OCLBarrierNode barrier = graph.addOrUnique(new OCLBarrierNode(OCLBarrierNode.OCLMemFenceFlags.LOCAL));
                graph.replaceFixed(invoke, barrier);
            } else if (methodName.equals("Direct#OpenCLIntrinsics.globalBarrier")) {
                OCLBarrierNode barrier = graph.addOrUnique(new OCLBarrierNode(OCLBarrierNode.OCLMemFenceFlags.GLOBAL));
                graph.replaceFixed(invoke, barrier);
            } else if (methodName.equals("Direct#OpenCLIntrinsics.get_local_id")) {
                ConstantNode dimension = getConstantNodeFromArguments(invoke, 0);
                LocalThreadIDFixedNode localIDNode = graph.addOrUnique(new LocalThreadIDFixedNode(dimension));
                graph.replaceFixed(invoke, localIDNode);
            } else if (methodName.equals("Direct#OpenCLIntrinsics.get_local_size")) {
                ConstantNode dimension = getConstantNodeFromArguments(invoke, 0);
                LocalGroupSizeNode groupSizeNode = graph.addOrUnique(new LocalGroupSizeNode(dimension));
                graph.replaceFixed(invoke, groupSizeNode);
            } else if (methodName.equals("Direct#OpenCLIntrinsics.get_global_id")) {
                ConstantNode dimension = getConstantNodeFromArguments(invoke, 0);
                GlobalThreadIdNode globalThreadId = graph.addOrUnique(new GlobalThreadIdNode(dimension));
                graph.replaceFixed(invoke, globalThreadId);
            } else if (methodName.equals("Direct#OpenCLIntrinsics.get_global_size")) {
                ConstantNode dimension = getConstantNodeFromArguments(invoke, 0);
                GlobalThreadSizeNode globalSize = graph.addOrUnique(new GlobalThreadSizeNode(dimension));
                graph.replaceFixed(invoke, globalSize);
            } else if (methodName.equals("Direct#OpenCLIntrinsics.get_group_id")) {
                ConstantNode dimension = getConstantNodeFromArguments(invoke, 0);
                GroupIdNode groupIdNode = graph.addOrUnique(new GroupIdNode(dimension));
                graph.replaceFixed(invoke, groupIdNode);
            } else if (methodName.equals("Direct#OpenCLIntrinsics.printEmpty")) {
                OpenCLPrintf printfNode = graph.addOrUnique(new OpenCLPrintf("\"\""));
                graph.replaceFixed(invoke, printfNode);
            }
        }
    }

}
