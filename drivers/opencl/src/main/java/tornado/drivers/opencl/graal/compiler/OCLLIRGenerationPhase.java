/* 
 * Copyright 2012 James Clarkson.
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
 */
package tornado.drivers.opencl.graal.compiler;

import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.compiler.common.cfg.BlockMap;
import com.oracle.graal.graph.Node;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.lir.phases.LIRPhase;
import com.oracle.graal.lir.ssa.SSAUtil;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.StructuredGraph.ScheduleResult;
import com.oracle.graal.nodes.cfg.Block;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import java.util.List;
import jdk.vm.ci.code.TargetDescription;

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
