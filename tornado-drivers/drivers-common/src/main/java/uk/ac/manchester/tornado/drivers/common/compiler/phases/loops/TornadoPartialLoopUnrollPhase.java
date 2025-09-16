/*
 * Copyright (c) 2023, 2024 APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.common.compiler.phases.loops;

import java.util.Optional;

import org.graalvm.compiler.loop.phases.LoopTransformations;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.loop.LoopFragmentInside;
import org.graalvm.compiler.nodes.loop.LoopsData;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.tiers.MidTierContext;

import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.graal.nodes.TornadoLoopsData;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoMidTierContext;

/**
 * Applies partial unroll on counted loops of more than 128 elements. By default,
 * the unroll factor is set to 2 except if the user explicitly passes a
 * different value power of two.
 *
 * @see org.graalvm.compiler.loop.phases.LoopTransformations
 */

public class TornadoPartialLoopUnrollPhase extends BasePhase<MidTierContext> {

    private static final int LOOP_UNROLL_FACTOR_DEFAULT = 2;
    private static final int LOOP_BOUND_UPPER_LIMIT = 16384;

    private static final int GRAPH_NODES_UPPER_LIMIT = 40000;

    private enum OptimizationStatus {
        SUCCESS, //
        ERROR;
    }

    private static OptimizationStatus partialUnroll(StructuredGraph graph, MidTierContext context) {
        LoopsData dataCounted;
        CanonicalizerPhase canonicalizer = CanonicalizerPhase.create();
        canonicalizer.apply(graph, context);
        try {
            dataCounted = new TornadoLoopsData(graph);
        } catch (NullPointerException nullPointerException) {
            return OptimizationStatus.ERROR;
        }
        dataCounted.detectCountedLoops();
        try {
            dataCounted.countedLoops().forEach(loop -> {
                if (LoopTransformations.isUnrollableLoop(loop)) {
                    int loopBound = loop.counted().getLimit().asJavaConstant().asInt();
                    if (isPowerOfTwo(loopBound) && (loopBound < LOOP_BOUND_UPPER_LIMIT)) {
                        LoopFragmentInside loopBody = loop.inside().duplicate();
                        loopBody.insertWithinAfter(loop, null);
                    }
                }
            });

            new DeadCodeEliminationPhase().apply(graph);
        } catch (NullPointerException runtimeException) {
            return OptimizationStatus.ERROR;
        }
        return OptimizationStatus.SUCCESS;
    }

    private static int getUnrollFactor() {
        return (isPowerOfTwo(TornadoOptions.UNROLL_FACTOR) && TornadoOptions.UNROLL_FACTOR <= 32) ? TornadoOptions.UNROLL_FACTOR : LOOP_UNROLL_FACTOR_DEFAULT;
    }

    private static int getUpperGraphLimit(int initialGraphNodeCount) {
        return (initialGraphNodeCount + (GRAPH_NODES_UPPER_LIMIT));
    }

    private static boolean isPowerOfTwo(int number) {
        return number > 0 && ((number & (number - 1)) == 0);
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    private StructuredGraph checkStatus(StructuredGraph graph, StructuredGraph snapshot, OptimizationStatus status) {
        return status != OptimizationStatus.SUCCESS ? snapshot : graph;
    }

    @Override
    protected void run(StructuredGraph graph, MidTierContext context) {

        TornadoMidTierContext tornadoMidTierContext = (TornadoMidTierContext) context;
        if (!tornadoMidTierContext.getMeta().applyPartialLoopUnroll()) {
            return;
        }

        if (!graph.hasLoops()) {
            return;
        }

        int initialNodeCount = graph.getNodeCount();
        int unrollFactor = getUnrollFactor();

        StructuredGraph snapshot = (StructuredGraph) graph.copy(TornadoCoreRuntime.getDebugContext());
        for (int i = 0; Math.pow(2, i) < unrollFactor; i++) {
            if (graph.getNodeCount() < getUpperGraphLimit(initialNodeCount)) {
                OptimizationStatus status = partialUnroll(graph, context);
                graph = checkStatus(graph, snapshot, status);
                if (status != OptimizationStatus.SUCCESS) {
                    return;
                }
            }
        }
    }
}
