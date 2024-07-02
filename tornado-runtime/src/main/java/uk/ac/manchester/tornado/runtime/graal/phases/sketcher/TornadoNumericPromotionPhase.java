/*
 * Copyright (c) 2020, 2024 APT Group, Department of Computer Science,
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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.runtime.graal.phases.sketcher;

import java.util.Optional;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.FixedBinaryNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.ShiftNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.phases.BasePhase;

import uk.ac.manchester.tornado.runtime.graal.phases.TornadoSketchTierContext;

/**
 * The TornadoVM sketcher builds the IR for each reachable method from a given
 * task, and it passes a series of optimizations phases, starting with
 * Canonicalization. The first cannicalization phase differs from the Graal
 * execution workflow, and TornadoVM removes the link between a Narrow node and
 * the sign-Extend. This deletion triggers the elimination of this node when
 * passing to the deadcode-elimination.
 * 
 * This phase links the Narrow node with the Sign-Extend. This phase is intended
 * to be called after the first canonicalizer phase is passed. Currently this is
 * done in the TornadoVM Sketcher.
 * 
 * More detailed explanation: In this phase, we need to prevent that a Narrow
 * that is followed by a Sign-Extend node is removed from the IR. This is due to
 * a custom canonicalizer in TornadoVM, because we don't want to simplify vector
 * types, among other types. This path differs from vanilla GraalVM compiler.
 * 
 */
public class TornadoNumericPromotionPhase extends BasePhase<TornadoSketchTierContext> {

    private boolean isNodeElegibleForNumericPromotion(ValueNode node) {
        return (node instanceof BinaryArithmeticNode || node instanceof ShiftNode || node instanceof FixedBinaryNode);
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph, TornadoSketchTierContext context) {

        // Get Narrow Nodes
        NodeIterable<NarrowNode> narrowNodes = graph.getNodes().filter(NarrowNode.class);

        for (NarrowNode narrow : narrowNodes) {
            // We check if the usage is sign-extend and the predecessor is a logic operator
            ValueNode valueOfNarrow = narrow.getValue();

            if (!isNodeElegibleForNumericPromotion(valueOfNarrow)) {
                continue;
            }

            NodeIterable<Node> usages = narrow.usages();
            SignExtendNode signExtendNode = null;
            for (Node u : usages) {
                if (u instanceof SignExtendNode) {
                    signExtendNode = (SignExtendNode) u;
                }
            }
            if (signExtendNode == null) {
                continue;
            }

            NodeIterable<NarrowNode> filter = signExtendNode.usages().filter(NarrowNode.class);

            if (filter.isNotEmpty()) {
                // Do the link
                NarrowNode newNarrowNode = filter.first();
                signExtendNode.replaceAtMatchingUsages(newNarrowNode, node -> !node.equals(newNarrowNode));
                assert graph.verify();
            }
        }
    }
}
