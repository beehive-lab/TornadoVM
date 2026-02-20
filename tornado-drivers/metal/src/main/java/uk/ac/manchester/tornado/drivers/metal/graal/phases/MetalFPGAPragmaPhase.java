/*
 * Copyright (c) 2020-2021, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2018, 2020, APT Group, Department of Computer Science,
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
 */
package uk.ac.manchester.tornado.drivers.metal.graal.phases;

import static org.graalvm.compiler.core.common.GraalOptions.MaximumDesiredSize;
import static org.graalvm.compiler.nodes.loop.DefaultLoopPolicies.Options.ExactFullUnrollMaxNodes;
import static org.graalvm.compiler.nodes.loop.DefaultLoopPolicies.Options.FullUnrollMaxNodes;

import java.util.List;
import java.util.Optional;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.debug.ControlFlowAnchorNode;
import org.graalvm.compiler.nodes.loop.CountedLoopInfo;
import org.graalvm.compiler.nodes.loop.LoopEx;
import org.graalvm.compiler.nodes.loop.LoopsData;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.Phase;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.IntelUnrollPragmaNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.XilinxPipeliningPragmaNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.TornadoLoopsData;

public class MetalFPGAPragmaPhase extends Phase {

    /**
     * The default factor number for loop unrolling is 4, as this number yielded the
     * same performance with full unrolling on an Intel Arria 10 FPGA.
     *
     * @see <a href= "https://arxiv.org/ftp/arxiv/papers/2010/2010.16304.pdf">
     *     Transparent Compiler and Runtime Specializations for Accelerating
     *     Managed Languages on FPGAs</a>.
     */
    private static final int UNROLL_FACTOR_NUMBER = 4;
    /**
     * The default initiation interval number for loop pipelining is 1, because this
     * number has been indicated as the default by Xilinx HLS for full pipelining.
     *
     * @see <a href=
     *     "https://www.xilinx.com/html_docs/xilinx2020_2/vitis_doc/metalattributes.html#sgo1504034359903__ad410982"
     *     * >Vitis 2020.2 Documentation</a>.
     */
    private static final int XILINX_PIPELINING_II_NUMBER = 1;
    private TornadoDeviceContext deviceContext;

    public MetalFPGAPragmaPhase(TornadoDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
    }

    private static boolean shouldFullUnrollOrPipeline(OptionValues options, LoopEx loop) {
        if (!loop.isCounted() || !loop.counted().isConstantMaxTripCount()) {
            return false;
        }
        CountedLoopInfo counted = loop.counted();
        long maxTrips = counted.constantMaxTripCount().asLong();
        int maxNodes = (counted.isExactTripCount() && counted.isConstantExactTripCount()) ? ExactFullUnrollMaxNodes.getValue(options) : FullUnrollMaxNodes.getValue(options);
        maxNodes = Math.min(maxNodes, MaximumDesiredSize.getValue(options) - loop.loopBegin().graph().getNodeCount());
        int size = Math.max(1, loop.size() - 1 - loop.loopBegin().phis().count());
        if (size * maxTrips <= maxNodes) {
            // check whether we're allowed to unroll or pipeline this loop
            int loops = 0;
            int ifs = 0;
            for (Node node : loop.inside().nodes()) {
                if (node instanceof ControlFlowAnchorNode) {
                    return false;
                } else if (node instanceof LoopBeginNode) {
                    loops++;
                } else if (node instanceof IfNode) {
                    ifs++;
                }
            }
            if (loops - ifs != 0) {
                return true;
            }

            return true;
        } else {
            return true;
        }
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph) {
        // Prevent Pragma Unroll for non-fpga devices
        if (graph.hasLoops() && (deviceContext.isPlatformFPGA())) {
            boolean peeled;
            do {
                peeled = false;
                final LoopsData dataCounted = new TornadoLoopsData(graph);
                dataCounted.detectCountedLoops();
                for (LoopEx loop : dataCounted.countedLoops()) {
                    if (shouldFullUnrollOrPipeline(graph.getOptions(), loop)) {
                        List<EndNode> snapshot = graph.getNodes().filter(EndNode.class).snapshot();
                        int idx = 0;
                        for (EndNode end : snapshot) {
                            idx++;
                            if (idx == 2) {
                                if (deviceContext.isPlatformXilinxFPGA()) {
                                    XilinxPipeliningPragmaNode pipeliningPragmaNode = graph.addOrUnique(new XilinxPipeliningPragmaNode(XILINX_PIPELINING_II_NUMBER));
                                    graph.addBeforeFixed(end, pipeliningPragmaNode);
                                } else {
                                    IntelUnrollPragmaNode unrollPragmaNode = graph.addOrUnique(new IntelUnrollPragmaNode(UNROLL_FACTOR_NUMBER));
                                    graph.addBeforeFixed(end, unrollPragmaNode);
                                }
                            }

                        }
                        peeled = false;
                        break;
                    }
                }
            } while (peeled);
        }
    }
}
