/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
 */

package uk.ac.manchester.tornado.drivers.ptx.graal.compiler;

import java.util.List;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.gen.LIRGenerator;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.lir.phases.LIRPhase;
import jdk.graal.compiler.lir.ssa.SSAUtil;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.code.TargetDescription;

public class PTXLIRGenerationPhase extends LIRPhase<PTXLIRGenerationPhase.LIRGenerationContext> {

    private static void emitBlock(PTXNodeLIRBuilder nodeLirBuilder, LIRGenerationResult lirGenRes, HIRBlock b, StructuredGraph graph, BlockMap<List<Node>> blockMap, boolean isKernel) {
        if (lirGenRes.getLIR().getLIRforBlock(b) == null) {
            for (int i = 0; i < b.getPredecessorCount(); i++) {
                if (!b.isLoopHeader() || !b.getPredecessorAt(i).isLoopEnd()) {
                    emitBlock(nodeLirBuilder, lirGenRes, b.getPredecessorAt(i), graph, blockMap, isKernel);
                }
            }
            nodeLirBuilder.doBlock(b, graph, blockMap, isKernel);
        }
    }

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, LIRGenerationContext context) {
        final NodeLIRBuilderTool nodeLirBuilder = context.nodeLirBuilder;
        final StructuredGraph graph = context.graph;
        final StructuredGraph.ScheduleResult schedule = context.schedule;
        final BlockMap<List<Node>> blockMap = schedule.getBlockToNodesMap();
        BasicBlock<?>[] blocks = lirGenRes.getLIR().getControlFlowGraph().getBlocks();

        for (BasicBlock<?> b : blocks) {
            emitBlock((PTXNodeLIRBuilder) nodeLirBuilder, lirGenRes, (HIRBlock) b, graph, blockMap, context.isKernel);
        }
        ((LIRGenerator) context.lirGen).beforeRegisterAllocation();

        assert SSAUtil.verifySSAForm(lirGenRes.getLIR());
    }

    public static final class LIRGenerationContext {

        private final StructuredGraph graph;
        private final LIRGeneratorTool lirGen;
        private final NodeLIRBuilderTool nodeLirBuilder;
        private final StructuredGraph.ScheduleResult schedule;
        private final boolean isKernel;

        public LIRGenerationContext(final LIRGeneratorTool lirGen, final NodeLIRBuilderTool nodeLirBuilder, final StructuredGraph graph, final StructuredGraph.ScheduleResult schedule,
                final boolean isKernel) {
            this.nodeLirBuilder = nodeLirBuilder;
            this.lirGen = lirGen;
            this.graph = graph;
            this.schedule = schedule;
            this.isKernel = isKernel;
        }

    }
}
