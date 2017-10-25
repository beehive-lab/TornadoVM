/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2017 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: James Clarkson
 *
 */
package tornado.drivers.opencl.graal.compiler;

import java.util.List;
import jdk.vm.ci.code.TargetDescription;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.core.common.cfg.BlockMap;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.lir.phases.LIRPhase;
import org.graalvm.compiler.lir.ssa.SSAUtil;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.ScheduleResult;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

public class OCLLIRGenerationPhase extends LIRPhase<OCLLIRGenerationPhase.LIRGenerationContext> {

    public static final class LIRGenerationContext {

        private final StructuredGraph graph;
        private final LIRGeneratorTool lirGen;
        private final NodeLIRBuilderTool nodeLirBuilder;
        private final ScheduleResult schedule;
        private final boolean isKernel;

        public LIRGenerationContext(
                final LIRGeneratorTool lirGen,
                final NodeLIRBuilderTool nodeLirBuilder,
                final StructuredGraph graph,
                final ScheduleResult schedule,
                final boolean isKernel) {
            this.nodeLirBuilder = nodeLirBuilder;
            this.lirGen = lirGen;
            this.graph = graph;
            this.schedule = schedule;
            this.isKernel = isKernel;
        }
    }

    private static void emitBlock(final OCLNodeLIRBuilder nodeLirGen,
            final LIRGenerationResult lirGenRes, final Block b, final StructuredGraph graph,
            final BlockMap<List<Node>> blockMap, boolean isKernel) {

        if (lirGenRes.getLIR().getLIRforBlock(b) == null) {
            for (final Block pred : b.getPredecessors()) {
                if (!b.isLoopHeader() || !pred.isLoopEnd()) {
                    emitBlock(nodeLirGen, lirGenRes, pred, graph, blockMap, isKernel);
                }
            }

            //System.out.println("nodeLirGen: " + nodeLirGen.toString());
            // System.out.println("block: " + b.toString());
            //System.out.println("graph: " + graph.toString());
            //System.out.println("blockMap: " + blockMap.toString());
            nodeLirGen.doBlock(b, graph, blockMap, isKernel);

//			if(b.isLoopHeader()){
//				patchLoopHeader(lirGenRes.getLIR().getLIRforBlock(b));
//			}
        }
    }

    @Override
    protected final void run(final TargetDescription target,
            final LIRGenerationResult lirGenRes, final OCLLIRGenerationPhase.LIRGenerationContext context) {

        final NodeLIRBuilderTool nodeLirBuilder = context.nodeLirBuilder;
        final StructuredGraph graph = context.graph;
        final ScheduleResult schedule = context.schedule;
        final BlockMap<List<Node>> blockMap = schedule.getBlockToNodesMap();

        for (AbstractBlockBase<?> b : lirGenRes.getLIR().linearScanOrder()) {
            emitBlock((OCLNodeLIRBuilder) nodeLirBuilder, lirGenRes, (Block) b, graph, blockMap, context.isKernel);
        }
        context.lirGen.beforeRegisterAllocation();

        assert SSAUtil.verifySSAForm(lirGenRes.getLIR());
    }

}
