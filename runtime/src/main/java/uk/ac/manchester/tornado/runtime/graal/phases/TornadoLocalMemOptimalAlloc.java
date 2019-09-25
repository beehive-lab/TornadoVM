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

import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.BasePhase;

import jdk.vm.ci.meta.JavaConstant;

/**
 * 
 * It filters the nodes for the lenght of the FixedArray node for the local
 * memory and calucaltes the optimal local memory size based on driver info.
 * Then, it replaced the lenght with the optimal size.
 *
 */
public class TornadoLocalMemOptimalAlloc extends BasePhase<TornadoHighTierContext> {

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {

        if (!context.hasMeta()) {
            return;
        }

        if (context.getMeta().getDomain().getDepth() != 0) {
            // System.out.println("CARDINALITY Run: " +
            // context.getMeta().getDomain().get(0).cardinality());
            // System.out.println("Max WorkItems Run: " +
            // context.getDeviceMapping().getDevice().getDeviceMaxWorkItemSizes()[0]);
            // System.out.println("OCLGPUblock2DX Run: " +
            // context.getMeta().getOpenCLGpuBlock2DX());
            // System.out.println("OCLGPUblock2DX Run: " +
            // context.getDeviceMapping().getDevice().getDeviceLocalMemorySize());
            System.out.println("---Optimal Size - -  - " + calculateLocalMemAllocSize(context));
            int opt = calculateLocalMemAllocSize(context);
            NodeIterable<ConstantNode> cNodes = graph.getNodes().filter(ConstantNode.class);

            for (ConstantNode cn : cNodes) {
                int length = ((JavaConstant) cn.getValue()).asInt();

                if (length == 512) {
                    ConstantNode newLengthNode = ConstantNode.forInt(opt, graph);
                    cn.replaceAtUsages(newLengthNode);
                    System.out.println(cn.getValue().toValueString());
                }
            }
        }

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
