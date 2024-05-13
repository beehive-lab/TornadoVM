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

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.extended.JavaReadNode;
import org.graalvm.compiler.nodes.extended.JavaWriteNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.phases.BasePhase;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoSketchTierContext;

import java.util.Optional;

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
                if (isIndexUsedInJavaWrite(phiNodeUsage)) {
                    context.setBatchWriteThreadIndex();
                }
            }
        }
    }

    private static boolean isIndexUsedInJavaWrite(Node indexUsage) {
        if (indexUsage instanceof OffsetAddressNode || indexUsage instanceof FrameState || indexUsage instanceof LoadIndexedNode || indexUsage instanceof JavaReadNode) {
            return false;
        } else if (indexUsage instanceof JavaWriteNode) {
            return true;
        } else {
            for (Node usage : indexUsage.usages()) {
                return isIndexUsedInJavaWrite(usage);
            }
        }
        return false;
    }

}
