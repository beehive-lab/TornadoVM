/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.opencl.graal.phases;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.RawConstant;
import tornado.graal.compiler.core.common.type.StampFactory;
import tornado.graal.compiler.graph.Node;
import tornado.graal.compiler.nodes.ConstantNode;
import tornado.graal.compiler.nodes.FrameState;
import tornado.graal.compiler.nodes.GraphState;
import tornado.graal.compiler.nodes.StructuredGraph;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.ValuePhiNode;
import tornado.graal.compiler.nodes.calc.AddNode;
import tornado.graal.compiler.nodes.extended.JavaReadNode;
import tornado.graal.compiler.nodes.extended.JavaWriteNode;
import tornado.graal.compiler.nodes.java.LoadIndexedNode;
import tornado.graal.compiler.nodes.memory.address.OffsetAddressNode;
import tornado.graal.compiler.phases.BasePhase;
import uk.ac.manchester.tornado.runtime.common.BatchCompilationConfig;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoHighTierContext;

import java.util.ArrayList;
import java.util.Optional;

/**
 * This phase is only used for batch processing. If the loop index is written in the output
 * buffer, this phase will offset the value to be written based on the number of the batch.
 * <p> E.g.
 *     {@code output.set(i, i)} will be transformed to {@code output.set(i, i + batchNumber * batchSize)}
 * </p>
 */
public class TornadoBatchGlobalIndexOffset extends BasePhase<TornadoHighTierContext> {

    private long batchSize;
    private int batchNumber;

    @Override
    public Optional<BasePhase.NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    protected void run(StructuredGraph graph, TornadoHighTierContext context) {
        BatchCompilationConfig batchCompilationConfig = context.getBatchCompilationConfig();
        batchSize = batchCompilationConfig.getBatchSize();
        // This phase is only applied for batch processing.
        if (batchSize == 0) {
            return;
        }

        batchNumber = batchCompilationConfig.getBatchNumber();

        for (ValuePhiNode phiNode : graph.getNodes().filter(ValuePhiNode.class)) {
            ArrayList<ValueNode> indexUsages = new ArrayList<>();
            for (Node phiNodeUsage : phiNode.usages()) {
                if (isIndexUsedInJavaWrite(phiNodeUsage)) {
                    indexUsages.add((ValueNode) phiNodeUsage);
                }
            }
            for (ValueNode phiIndexUsage : indexUsages) {
                Constant batchNumberConstant = new RawConstant(batchNumber * batchSize);
                ConstantNode batchNumberNode = new ConstantNode(batchNumberConstant, StampFactory.forKind(JavaKind.Int));
                graph.addWithoutUnique(batchNumberNode);

                AddNode addOffsets = new AddNode(batchNumberNode, phiNode);
                graph.addWithoutUnique(addOffsets);
                phiIndexUsage.replaceFirstInput(phiNode, addOffsets);
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
