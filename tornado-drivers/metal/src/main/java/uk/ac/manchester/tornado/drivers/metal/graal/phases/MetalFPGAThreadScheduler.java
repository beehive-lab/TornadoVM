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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: Michalis Papadimitriou
 */
package uk.ac.manchester.tornado.drivers.metal.graal.phases;

import java.util.Optional;

import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.BasePhase;

import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.FPGAWorkGroupSizeNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.LocalWorkGroupDimensionsNode;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoLowTierContext;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

public class MetalFPGAThreadScheduler extends BasePhase<TornadoLowTierContext> {

    public static final int DEFAULT_FPGA_PARALLEL_1D = 64; // This value was chosen for Intel FPGAs due to experimental results
    public static final int DEFAULT_FPGA_PARALLEL_2D = 1;
    public static final int DEFAULT_FPGA_PARALLEL_3D = 1;
    public static final int DEFAULT_FPGA_SEQUENTIAL_1D = 1;
    public static final int DEFAULT_FPGA_SEQUENTIAL_2D = 1;
    public static final int DEFAULT_FPGA_SEQUENTIAL_3D = 1;

    private static int oneD = DEFAULT_FPGA_PARALLEL_1D;
    private static int twoD = DEFAULT_FPGA_PARALLEL_2D;
    private static int threeD = DEFAULT_FPGA_PARALLEL_3D;

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph, TornadoLowTierContext lowTierContext) {
        if (graph.hasLoops()) {
            NodeIterable<EndNode> filter = graph.getNodes().filter(EndNode.class);
            EndNode end = filter.first();
            TaskDataContext metaData;

            metaData = lowTierContext.getMeta();
            if (metaData != null) {
                if (metaData.isGridSchedulerEnabled()) {
                    if (metaData.isWorkerGridAvailable()) {
                        WorkerGrid workerGrid = metaData.getWorkerGrid(metaData.getId());
                        if (metaData.isGridSequential()) {
                            oneD = DEFAULT_FPGA_SEQUENTIAL_1D;
                            twoD = DEFAULT_FPGA_SEQUENTIAL_2D;
                            threeD = DEFAULT_FPGA_SEQUENTIAL_3D;
                        } else {
                            oneD = (int) workerGrid.getLocalWork()[0];
                            twoD = (int) workerGrid.getLocalWork()[1];
                            threeD = (int) workerGrid.getLocalWork()[2];
                        }
                    }
                } else {
                    if (!metaData.isParallel()) { // Sequential kernel
                        oneD = DEFAULT_FPGA_SEQUENTIAL_1D;
                        twoD = DEFAULT_FPGA_SEQUENTIAL_2D;
                        threeD = DEFAULT_FPGA_SEQUENTIAL_3D;
                    }
                }
            }

            ConstantNode xNode = graph.addOrUnique(ConstantNode.forInt(oneD));
            ConstantNode yNode = graph.addOrUnique(ConstantNode.forInt(twoD));
            ConstantNode zNode = graph.addOrUnique(ConstantNode.forInt(threeD));

            final LocalWorkGroupDimensionsNode localWorkGroupNode = graph.addOrUnique(new LocalWorkGroupDimensionsNode(xNode, yNode, zNode));
            FPGAWorkGroupSizeNode workGroupSizeNode = graph.addOrUnique(new FPGAWorkGroupSizeNode(localWorkGroupNode));
            graph.addBeforeFixed(end, workGroupSizeNode);
        }
    }
}
