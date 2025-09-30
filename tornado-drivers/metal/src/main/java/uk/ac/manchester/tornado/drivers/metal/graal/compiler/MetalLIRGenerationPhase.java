/*
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
package uk.ac.manchester.tornado.drivers.metal.graal.compiler;

import java.util.List;

import org.graalvm.compiler.core.common.cfg.BasicBlock;
import org.graalvm.compiler.core.common.cfg.BlockMap;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGenerator;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.lir.phases.LIRPhase;
import org.graalvm.compiler.lir.ssa.SSAUtil;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.ScheduleResult;
import org.graalvm.compiler.nodes.cfg.HIRBlock;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.code.TargetDescription;

public class MetalLIRGenerationPhase extends LIRPhase<MetalLIRGenerationPhase.LIRGenerationContext> {

    private static void emitBlock(final MetalNodeLIRBuilder nodeLirGen, final LIRGenerationResult lirGenRes, final HIRBlock b, final StructuredGraph graph, final BlockMap<List<Node>> blockMap,
            boolean isKernel) {
        if (lirGenRes.getLIR().getLIRforBlock(b) == null) {
            for (int i = 0; i < b.getPredecessorCount(); i++) {
                if (!b.isLoopHeader() || !b.getPredecessorAt(i).isLoopEnd()) {
                    emitBlock(nodeLirGen, lirGenRes, b.getPredecessorAt(i), graph, blockMap, isKernel);
                }
            }
            nodeLirGen.doBlock(b, graph, blockMap, isKernel);
        }
    }

    @Override
    protected final void run(final TargetDescription target, final LIRGenerationResult lirGenRes, final MetalLIRGenerationPhase.LIRGenerationContext context) {

        final NodeLIRBuilderTool nodeLirBuilder = context.nodeLirBuilder;
        final StructuredGraph graph = context.graph;
        final ScheduleResult schedule = context.schedule;
        final BlockMap<List<Node>> blockMap = schedule.getBlockToNodesMap();
        BasicBlock<?>[] blocks = lirGenRes.getLIR().getControlFlowGraph().getBlocks();

        for (BasicBlock<?> b : blocks) {
            emitBlock((MetalNodeLIRBuilder) nodeLirBuilder, lirGenRes, (HIRBlock) b, graph, blockMap, context.isKernel);
        }
        ((LIRGenerator) context.lirGen).beforeRegisterAllocation();

        assert SSAUtil.verifySSAForm(lirGenRes.getLIR());
    }

    public static final class LIRGenerationContext {

        private final StructuredGraph graph;
        private final LIRGeneratorTool lirGen;
        private final NodeLIRBuilderTool nodeLirBuilder;
        private final ScheduleResult schedule;
        private final boolean isKernel;

        public LIRGenerationContext(final LIRGeneratorTool lirGen, final NodeLIRBuilderTool nodeLirBuilder, final StructuredGraph graph, final ScheduleResult schedule, final boolean isKernel) {
            this.nodeLirBuilder = nodeLirBuilder;
            this.lirGen = lirGen;
            this.graph = graph;
            this.schedule = schedule;
            this.isKernel = isKernel;
        }
    }

}
