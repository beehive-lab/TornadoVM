/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.extended.JavaReadNode;
import jdk.graal.compiler.nodes.extended.JavaWriteNode;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.phases.BasePhase;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoSketchTierContext;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * This phase analyses the graph to deduct if the loop index is written in the output buffer.
 * This information is necessary for batch processing, because in that case the kernel
 * will need to be recompiled to offset the value written based on the number of the batch.
 */
public class TornadoBatchFunctionAnalysis extends BasePhase<TornadoSketchTierContext> {

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph, TornadoSketchTierContext context) {
        for (ValuePhiNode phiNode : graph.getNodes().filter(ValuePhiNode.class)) {
            for (Node phiNodeUsage : phiNode.usages()) {
                Set<Node> visited = new HashSet<>();
                if (isIndexUsedInJavaWrite(phiNodeUsage, visited)) {
                    context.setBatchWriteThreadIndex();
                }
            }
        }
    }

    private static boolean isIndexUsedInJavaWrite(Node indexUsage, Set<Node> visited) {
        visited.add(indexUsage);
        if (indexUsage instanceof OffsetAddressNode || indexUsage instanceof FrameState || indexUsage instanceof LoadIndexedNode || indexUsage instanceof JavaReadNode) {
            return false;
        } else if (indexUsage instanceof JavaWriteNode) {
            return true;
        } else {
            for (Node node : indexUsage.usages()) {
                if (!visited.contains(node)) {
                    if (isIndexUsedInJavaWrite(node, visited)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

}
