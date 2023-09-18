/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.graal.phases;

import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getDebugContext;
import static uk.ac.manchester.tornado.runtime.common.TornadoSchedulingStrategy.PER_BLOCK;
import static uk.ac.manchester.tornado.runtime.common.TornadoSchedulingStrategy.PER_ITERATION;

import java.util.Optional;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.nodes.calc.SubNode;
import org.graalvm.compiler.phases.BasePhase;

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.GlobalThreadIdNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.GlobalThreadSizeNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLIntBinaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.calc.DivNode;
import uk.ac.manchester.tornado.runtime.common.TornadoAcceleratorDevice;
import uk.ac.manchester.tornado.runtime.common.TornadoSchedulingStrategy;
import uk.ac.manchester.tornado.runtime.graal.nodes.AbstractParallelNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.ParallelOffsetNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.ParallelRangeNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.ParallelStrideNode;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoHighTierContext;

public class TornadoParallelScheduler extends BasePhase<TornadoHighTierContext> {
    private ValueNode blockSize;

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    private void replaceOffsetNode(TornadoSchedulingStrategy schedule, StructuredGraph graph, ParallelOffsetNode offset, ParallelRangeNode range) {
        if (schedule == PER_BLOCK) {
            replacePerBlock(graph, offset);
        } else if (schedule == PER_ITERATION) {
            replacePerIteration(graph, offset, range);
        }
    }

    private void replacePerIteration(StructuredGraph graph, ParallelOffsetNode offset, ParallelRangeNode range) {

        final ConstantNode index = graph.addOrUnique(ConstantNode.forInt(offset.index()));

        final GlobalThreadIdNode threadId = graph.addOrUnique(new GlobalThreadIdNode(index));

        final AddNode addNode = graph.addOrUnique(new AddNode(threadId, offset.value()));

        final MulNode mulNode = graph.addOrUnique(new MulNode(addNode, range.stride().value()));

        offset.replaceAtUsages(mulNode);
        offset.safeDelete();
    }

    private void replacePerBlock(StructuredGraph graph, ParallelOffsetNode offset) {
        final GlobalThreadIdNode threadId = graph.addOrUnique(new GlobalThreadIdNode(ConstantNode.forInt(offset.index(), graph)));
        final MulNode newOffset = graph.addOrUnique(new MulNode(threadId, blockSize));
        offset.replaceAtUsages(newOffset);
        offset.safeDelete();
    }

    private void replaceStrideNode(TornadoSchedulingStrategy schedule, StructuredGraph graph, ParallelStrideNode stride) {
        if (schedule == PER_BLOCK) {
            replacePerBlock(stride);
        } else if (schedule == PER_ITERATION) {
            replacePerIteration(graph, stride);
        }
    }

    private void replacePerIteration(StructuredGraph graph, ParallelStrideNode stride) {
        final ConstantNode index = graph.addOrUnique(ConstantNode.forInt(stride.index()));
        final GlobalThreadSizeNode threadCount = graph.addOrUnique(new GlobalThreadSizeNode(index));
        stride.replaceAtUsages(threadCount);
        stride.safeDelete();
    }

    private void replacePerBlock(ParallelStrideNode stride) {
        stride.replaceAtUsages(stride.value());
        stride.safeDelete();
    }

    private void replaceRangeNode(TornadoSchedulingStrategy schedule, StructuredGraph graph, ParallelRangeNode range) {
        if (schedule == PER_BLOCK) {
            replacePerBlock(graph, range);
        } else if (schedule == PER_ITERATION) {
            replacePerIteration(range);
        }
    }

    private void replacePerIteration(ParallelRangeNode range) {
        range.replaceAtUsages(range.value());
    }

    // CPU-Scheduling with Stride
    private void buildBlockSize(StructuredGraph graph, ParallelRangeNode range) {
        final ValueNode rangeByStride = graph.addOrUnique(DivNode.create(range.value(), range.stride().value()));
        final SubNode trueRange = graph.addOrUnique(new SubNode(rangeByStride, range.offset().value()));
        final ConstantNode index = ConstantNode.forInt(range.index(), graph);
        final GlobalThreadSizeNode threadCount = graph.addOrUnique(new GlobalThreadSizeNode(index));
        final SubNode threadCountM1 = graph.addOrUnique(new SubNode(threadCount, ConstantNode.forInt(1, graph)));
        final AddNode adjustedTrueRange = graph.addOrUnique(new AddNode(trueRange, threadCountM1));
        final ValueNode div = graph.addOrUnique(DivNode.create(adjustedTrueRange, threadCount));
        blockSize = graph.addOrUnique(new MulNode(div, range.stride().value()));
    }

    // CPU-Scheduling with Stride
    private void replacePerBlock(StructuredGraph graph, ParallelRangeNode range) {
        buildBlockSize(graph, range);

        final GlobalThreadIdNode threadId = graph.addOrUnique(new GlobalThreadIdNode(ConstantNode.forInt(range.index(), graph)));
        final MulNode newOffset = graph.addOrUnique(new MulNode(threadId, blockSize));
        final AddNode newRange = graph.addOrUnique(new AddNode(newOffset, blockSize));

        // Stride of 2
        final MulNode stride = graph.addOrUnique(new MulNode(newRange, range.stride().value()));
        final ValueNode adjustedRange = graph.addOrUnique(OCLIntBinaryIntrinsicNode.create(stride, range.value(), OCLIntBinaryIntrinsicNode.Operation.MIN, JavaKind.Int));

        range.replaceAtUsages(adjustedRange);
        range.safeDelete();
    }

    // ================================== DEPRECATED
    // ========================================
    // GPU-Scheduling
    private void buildBlockSizeAccelerator(StructuredGraph graph, ParallelRangeNode range) {
        final ValueNode rangeByStride = graph.addOrUnique(DivNode.create(range.value(), range.stride().value()));
        final SubNode trueRange = graph.addOrUnique(new SubNode(rangeByStride, range.offset().value()));
        final ConstantNode index = ConstantNode.forInt(range.index(), graph);
        final GlobalThreadSizeNode threadCount = graph.addOrUnique(new GlobalThreadSizeNode(index));
        final SubNode threadCountM1 = graph.addOrUnique(new SubNode(threadCount, ConstantNode.forInt(1, graph)));
        final AddNode adjustedTrueRange = graph.addOrUnique(new AddNode(trueRange, threadCountM1));
        blockSize = graph.addOrUnique(DivNode.create(adjustedTrueRange, threadCount));
    }

    // GPU-Scheduling
    @SuppressWarnings("unused")
    private void replacePerBlockAccelerator(StructuredGraph graph, ParallelRangeNode range) {
        buildBlockSizeAccelerator(graph, range);

        final GlobalThreadIdNode threadId = graph.addOrUnique(new GlobalThreadIdNode(ConstantNode.forInt(range.index(), graph)));
        final MulNode newOffset = graph.addOrUnique(new MulNode(threadId, blockSize));

        final AddNode newRange = graph.addOrUnique(new AddNode(newOffset, blockSize));

        final ValueNode adjustedRange = graph.addOrUnique(OCLIntBinaryIntrinsicNode.create(newRange, range.value(), OCLIntBinaryIntrinsicNode.Operation.MIN, JavaKind.Int));

        range.replaceAtUsages(adjustedRange);
        range.safeDelete();
    }
    // ================================== END-DEPRECATED
    // ========================================

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {

        if (context.getMeta() == null || context.getMeta().enableThreadCoarsener()) {
            return;
        }

        TornadoAcceleratorDevice device = context.getDeviceMapping();
        final TornadoSchedulingStrategy strategy = device.getPreferredSchedule();
        long[] maxWorkItemSizes = device.getPhysicalDevice().getDeviceMaxWorkItemSizes();

        graph.getNodes().filter(ParallelRangeNode.class).forEach(node -> {
            if (context.getMeta().enableParallelization() && maxWorkItemSizes[node.index()] > 1) {
                ParallelOffsetNode offset = node.offset();
                ParallelStrideNode stride = node.stride();
                replaceRangeNode(strategy, graph, node);
                replaceOffsetNode(strategy, graph, offset, node);
                replaceStrideNode(strategy, graph, stride);

            } else {
                serialiseLoop(node);
            }
            getDebugContext().dump(DebugContext.BASIC_LEVEL, graph, "after scheduling loop index=" + node.index());
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