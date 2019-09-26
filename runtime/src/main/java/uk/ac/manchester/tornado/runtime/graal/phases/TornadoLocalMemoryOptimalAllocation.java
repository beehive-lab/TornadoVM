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
 */
package uk.ac.manchester.tornado.runtime.graal.phases;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.BasePhase;

/**
 * 
 * It filters the nodes for the lenght of the FixedArray node for the local
 * memory and calucaltes the optimal local memory size based on driver info.
 * Then, it replaced the lenght with the optimal size.
 *
 */
public class TornadoLocalMemoryOptimalAllocation extends BasePhase<TornadoHighTierContext> {

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {

        if (!context.hasMeta()) {
            return;
        }

        if ((context.getMeta().getDomain() != null)) {
            if ((context.getMeta().getDomain().getDepth() != 0)) {
                System.out.println("---Optimal Size - - - " + calculateLocalMemAllocSize(context));

                int opt = calculateLocalMemAllocSize(context);
                // NodeIterable<ConstantNode> cNodes =
                // graph.getNodes().filter(ConstantNode.class);

                NodeIterable<Node> sumNodes = graph.getNodes();

                for (Node n : sumNodes) {
                    if (n instanceof MarkFixed && isPowerOfTwo(opt)) {
                        if (((MarkFixed) n).isMemLocal()) {
                            ConstantNode newLengthNode = ConstantNode.forInt(opt, graph);
                            n.inputs().first().replaceAndDelete(newLengthNode);
                            System.out.println("Mark access" + n.inputs().first().toString());
                            System.out.println("Is mem local" + ((MarkFixed) n).isMemLocal());
                        }
                    }
                }
                // for (ConstantNode cn : cNodes) {
                // int length = ((JavaConstant) cn.getValue()).asInt();
                // // System.out.println(cn.predecessor().toString());
                // // cn.//
                // System.out.println(cn.successors().first());
                // // System.out.println(cn.);
                // // if (length == 0) {
                // // ConstantNode newLengthNode = ConstantNode.forInt(opt, graph);
                // // cn.replaceAtUsages(newLengthNode);
                // // System.out.println(cn.getValue().toValueString());
                // // }
                // }
            }
        }

    }

    public static boolean isPowerOfTwo(int num) {
        return num > 0 && (num & (num - 1)) == 0;
    }

    private int calculateLocalMemAllocSize(TornadoHighTierContext context) {
        int maxBlockSize = (int) context.getDeviceMapping().getDevice().getDeviceMaxWorkItemSizes()[0];

        if (context.getDeviceMapping().getDevice().getDeviceMaxWorkItemSizes()[0] == context.getMeta().getDomain().get(0).cardinality()) {
            maxBlockSize /= 4;
        }

        int value = (int) Math.min(Math.max(maxBlockSize, context.getMeta().getOpenCLGpuBlock2DX()), context.getMeta().getDomain().get(0).cardinality());
        while (context.getMeta().getDomain().get(0).cardinality() % value != 0) {
            value--;
        }
        return value;
    }
}
