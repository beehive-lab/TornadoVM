/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2009-2021, Oracle and/or its affiliates. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.spirv.graal.phases;

import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getDebugContext;

import java.util.Optional;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.phases.BasePhase;

import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.GlobalThreadIdNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.GlobalThreadSizeNode;
import uk.ac.manchester.tornado.drivers.spirv.runtime.SPIRVTornadoDevice;
import uk.ac.manchester.tornado.runtime.graal.nodes.AbstractParallelNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.ParallelOffsetNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.ParallelRangeNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.ParallelStrideNode;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoHighTierContext;

public class TornadoParallelScheduler extends BasePhase<TornadoHighTierContext> {

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    private void replaceOffsetNode(StructuredGraph graph, ParallelOffsetNode offset, ParallelRangeNode range) {
        final ConstantNode index = graph.addOrUnique(ConstantNode.forInt(offset.index()));
        final GlobalThreadIdNode threadId = graph.addOrUnique(new GlobalThreadIdNode(index));
        final AddNode addNode = graph.addOrUnique(new AddNode(threadId, offset.value()));
        final MulNode mulNode = graph.addOrUnique(new MulNode(addNode, range.stride().value()));
        offset.replaceAtUsages(mulNode);
        offset.safeDelete();
    }

    private void replaceStrideNode(StructuredGraph graph, ParallelStrideNode stride) {
        final ConstantNode index = graph.addOrUnique(ConstantNode.forInt(stride.index()));
        final GlobalThreadSizeNode threadCount = graph.addOrUnique(new GlobalThreadSizeNode(index));
        stride.replaceAtUsages(threadCount);
        stride.safeDelete();
    }

    private void replaceRangeNode(ParallelRangeNode range) {
        range.replaceAtUsages(range.value());
    }

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {
        if (context.getMeta() == null || context.getMeta().enableThreadCoarsener()) {
            return;
        }

        SPIRVTornadoDevice device = (SPIRVTornadoDevice) context.getDeviceMapping();
        long[] maxWorkItemSizes = device.getPhysicalDevice().getDeviceMaxWorkItemSizes();

        graph.getNodes().filter(ParallelRangeNode.class).forEach(parallelRange -> {
            if (context.getMeta().enableParallelization() && maxWorkItemSizes[parallelRange.index()] > 1) {
                ParallelOffsetNode offset = parallelRange.offset();
                ParallelStrideNode stride = parallelRange.stride();
                replaceRangeNode(parallelRange);
                replaceOffsetNode(graph, offset, parallelRange);
                replaceStrideNode(graph, stride);

            } else {
                serialiseLoop(parallelRange);
            }
            getDebugContext().dump(DebugContext.BASIC_LEVEL, graph, "after scheduling loop index=" + parallelRange.index());
        });
        graph.clearLastSchedule();
    }

    private void killNode(AbstractParallelNode node) {
        if (node.inputs().isNotEmpty()) {
            node.clearInputs();
        }
        if (!node.isDeleted()) {
            node.safeDelete();
        }
    }

    private void serialiseLoop(ParallelRangeNode range) {
        ParallelOffsetNode offset = range.offset();
        ParallelStrideNode stride = range.stride();
        range.replaceAtUsages(range.value());
        killNode(range);
        offset.replaceAtUsages(offset.value());
        stride.replaceAtUsages(stride.value());
        killNode(offset);
        killNode(stride);
    }
}
