/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2017 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
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
 *
 * Authors: James Clarkson
 *
 */
package tornado.drivers.opencl.graal.phases;

import static tornado.api.enums.TornadoSchedulingStrategy.PER_BLOCK;
import static tornado.api.enums.TornadoSchedulingStrategy.PER_ITERATION;

import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.DivNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.nodes.calc.SubNode;
import org.graalvm.compiler.phases.BasePhase;

import jdk.vm.ci.meta.JavaKind;
import tornado.api.enums.TornadoSchedulingStrategy;
import tornado.drivers.opencl.enums.OCLDeviceType;
import tornado.drivers.opencl.graal.nodes.GlobalThreadIdNode;
import tornado.drivers.opencl.graal.nodes.GlobalThreadSizeNode;
import tornado.drivers.opencl.graal.nodes.OCLIntBinaryIntrinsicNode;
import tornado.drivers.opencl.runtime.OCLTornadoDevice;
import tornado.graal.nodes.AbstractParallelNode;
import tornado.graal.nodes.ParallelOffsetNode;
import tornado.graal.nodes.ParallelRangeNode;
import tornado.graal.nodes.ParallelStrideNode;
import tornado.graal.phases.TornadoHighTierContext;

public class TornadoParallelScheduler extends BasePhase<TornadoHighTierContext> {

    private ValueNode blockSize;

    private void replaceOffsetNode(TornadoSchedulingStrategy schedule, StructuredGraph graph, ParallelOffsetNode offset) {
        if (schedule == PER_BLOCK) {
            replacePerBlock(graph, offset);
        } else if (schedule == PER_ITERATION) {
            replacePerIteration(graph, offset);
        }
    }

    private void replacePerIteration(StructuredGraph graph, ParallelOffsetNode offset) {

        final ConstantNode index = graph.addOrUnique(ConstantNode.forInt(offset.index()));

        final GlobalThreadIdNode threadId = graph.addOrUnique(new GlobalThreadIdNode(index));

        final AddNode addNode = graph.addOrUnique(new AddNode(threadId, offset.value()));

        offset.replaceAtUsages(addNode);
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
            replacePerBlock(graph, stride);
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

    private void replacePerBlock(StructuredGraph graph, ParallelStrideNode stride) {
        // final MulNode newStride = graph.addOrUnique(new
        // MulNode(stride.value(),)
        stride.replaceAtUsages(stride.value());
        stride.safeDelete();
    }

    private void replaceRangeNode(TornadoSchedulingStrategy schedule, StructuredGraph graph, ParallelRangeNode range, OCLDeviceType deviceType) {
        if (schedule == PER_BLOCK) {
            if (deviceType == OCLDeviceType.CL_DEVICE_TYPE_CPU) {
                replacePerBlockCPU(graph, range);
            } else {
                replacePerBlockAccelerator(graph, range);
            }
        } else if (schedule == PER_ITERATION) {
            replacePerIteration(graph, range);
        }
    }

    private void replacePerIteration(StructuredGraph graph, ParallelRangeNode range) {
        range.replaceAtUsages(range.value());
    }

    // @SuppressWarnings("unused")
    // private ValueNode createCreateStrideNode(ValueNode threadCountM1,
    // ParallelRangeNode range, StructuredGraph graph) {
    // ValueNode newOffset = threadCountM1;
    // if (range.stride().value() instanceof ConstantNode) {
    // ConstantNode constant = (ConstantNode) range.stride().value();
    // if (constant.stamp().getStackKind() == JavaKind.Int) {
    // int value = constant.asJavaConstant().asInt();
    // if (value > 4) {
    // final AddNode add = graph.addOrUnique(new AddNode(threadCountM1,
    // ConstantNode.forInt(1, graph)));
    // newOffset = graph.addOrUnique(new MulNode(add, range.stride().value()));
    // }
    // }
    // }
    // return newOffset;
    // }

    // CPU-Scheduling with Stride
    private void buildBlockSizeCPU(StructuredGraph graph, ParallelRangeNode range) {
        final DivNode rangeByStride = graph.addOrUnique(new DivNode(range.value(), range.stride().value()));
        final SubNode trueRange = graph.addOrUnique(new SubNode(rangeByStride, range.offset().value()));
        final ConstantNode index = ConstantNode.forInt(range.index(), graph);
        final GlobalThreadSizeNode threadCount = graph.addOrUnique(new GlobalThreadSizeNode(index));
        final SubNode threadCountM1 = graph.addOrUnique(new SubNode(threadCount, ConstantNode.forInt(1, graph)));
        final AddNode adjustedTrueRange = graph.addOrUnique(new AddNode(trueRange, threadCountM1));
        final DivNode div = graph.addOrUnique(new DivNode(adjustedTrueRange, threadCount));
        blockSize = graph.addOrUnique(new MulNode(div, range.stride().value()));
    }

    // CPU-Scheduling with Stride
    private void replacePerBlockCPU(StructuredGraph graph, ParallelRangeNode range) {
        buildBlockSizeCPU(graph, range);

        final GlobalThreadIdNode threadId = graph.addOrUnique(new GlobalThreadIdNode(ConstantNode.forInt(range.index(), graph)));
        final MulNode newOffset = graph.addOrUnique(new MulNode(threadId, blockSize));
        final AddNode newRange = graph.addOrUnique(new AddNode(newOffset, blockSize));

        // Stride of 2
        final MulNode stride = graph.addOrUnique(new MulNode(newRange, range.stride().value()));
        final ValueNode adjustedRange = graph.addOrUnique(OCLIntBinaryIntrinsicNode.create(stride, range.value(), OCLIntBinaryIntrinsicNode.Operation.MIN, JavaKind.Int));

        range.replaceAtUsages(adjustedRange);
        range.safeDelete();
    }
    
    // GPU-Scheduling 
    private void buildBlockSizeAccelerator(StructuredGraph graph, ParallelRangeNode range) {
        final DivNode rangeByStride = graph.addOrUnique(new DivNode(range.value(), range.stride()
                .value()));
        final SubNode trueRange = graph.addOrUnique(new SubNode(rangeByStride, range.offset()
                .value()));
        final ConstantNode index = ConstantNode.forInt(range.index(), graph);
        final GlobalThreadSizeNode threadCount = graph.addOrUnique(new GlobalThreadSizeNode(index));
        final SubNode threadCountM1 = graph.addOrUnique(new SubNode(threadCount, ConstantNode
                .forInt(1, graph)));
        final AddNode adjustedTrueRange = graph.addOrUnique(new AddNode(trueRange, threadCountM1));
        blockSize = graph.addOrUnique(new DivNode(adjustedTrueRange, threadCount));
    }

    // GPU-Scheduling 
    private void replacePerBlockAccelerator(StructuredGraph graph, ParallelRangeNode range) {
        buildBlockSizeAccelerator(graph, range);

        final GlobalThreadIdNode threadId = graph.addOrUnique(new GlobalThreadIdNode(ConstantNode.forInt(
                range.index(), graph)));
        final MulNode newOffset = graph.addOrUnique(new MulNode(threadId, blockSize));

        final AddNode newRange = graph.addOrUnique(new AddNode(newOffset, blockSize));

        final ValueNode adjustedRange = graph.addOrUnique(OCLIntBinaryIntrinsicNode.create(
                newRange, range.value(), OCLIntBinaryIntrinsicNode.Operation.MIN, JavaKind.Int));

        range.replaceAtUsages(adjustedRange);
        range.safeDelete();
    }


    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {
        if (context.getMeta() == null || context.getMeta().enableThreadCoarsener()) {
            return;
        }

        OCLTornadoDevice device = (OCLTornadoDevice) context.getDeviceMapping();
        final TornadoSchedulingStrategy strategy = device.getPreferedSchedule();
        long[] maxWorkItemSizes = device.getDevice().getMaxWorkItemSizes();
        OCLDeviceType deviceType = device.getDevice().getDeviceType();

        graph.getNodes().filter(ParallelRangeNode.class).forEach(node -> {
            if (context.getMeta().enableParallelization() && maxWorkItemSizes[node.index()] > 1) {
                ParallelOffsetNode offset = node.offset();
                ParallelStrideNode stride = node.stride();
                replaceRangeNode(strategy, graph, node, deviceType);
                replaceOffsetNode(strategy, graph, offset);
                replaceStrideNode(strategy, graph, stride);

            } else {
                serialiseLoop(node);
            }
            Debug.dump(Debug.BASIC_LEVEL, graph, "after scheduling loop index=" + node.index());
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

    // private void paralleliseLoop(StructuredGraph graph, ParallelRangeNode
    // range) {
    // /*
    // * take copies of any nodes found via range
    // */
    // ParallelOffsetNode offset = range.offset();
    // ParallelStrideNode stride = range.stride();
    // IfNode ifNode = (IfNode) range.usages().first().usages().first();
    //
    // /*
    // * remove the range node from the graph - needs to be done before any
    // * node which is used as an input to range: e.g. offset and stride
    // */
    // range.replaceAtUsages(range.value());
    // killNode(range);
    //
    // /*
    // * replace the offset node with thread id node
    // */
    // final ConstantNode index =
    // graph.addOrUnique(ConstantNode.forInt(offset.index()));
    // ValueNode threadId = graph.addOrUnique(new GlobalThreadIdNode(index));
    // offset.replaceAtUsages(threadId);
    // killNode(offset);
    //
    // /*
    // * kill off the stride node - not needed
    // */
    // killNode(stride);
    //
    // /*
    // * now update the cfg to remove the for loop
    // */
    // LoopExitNode loopExit = (LoopExitNode) ifNode.falseSuccessor();
    // LoopBeginNode loopBegin = loopExit.loopBegin();
    //
    // BeginNode newFalseBegin = graph.addWithoutUnique(new BeginNode());
    // EndNode newFalseEnd = graph.addWithoutUnique(new EndNode());
    // newFalseBegin.setNext(newFalseEnd);
    //
    // MergeNode newMerge = graph.addWithoutUnique(new MergeNode());
    //
    // for (LoopEndNode loopEnd : loopBegin.loopEnds().snapshot()) {
    // EndNode newEnd = graph.addWithoutUnique(new EndNode());
    // loopEnd.replaceAndDelete(newEnd);
    // newMerge.addForwardEnd(newEnd);
    // }
    //
    // newMerge.addForwardEnd(newFalseEnd);
    //
    // FixedNode next = loopExit.next();
    //
    // loopExit.setNext(null);
    // loopExit.replaceAndDelete(newFalseBegin);
    //
    // newMerge.setNext(next);
    //
    // /*
    // * eliminate the phi nodes
    // */
    // for (PhiNode phi : loopBegin.phis()) {
    // phi.replaceAtUsages(phi.firstValue());
    // }
    //
    // /*
    // * eliminate any unneccesary nodes/branches from the graph
    // */
    // graph.reduceDegenerateLoopBegin(loopBegin);
    //
    // /*
    // * removes the if stmt (thread id check)
    // */
    // GraphUtil.killWithUnusedFloatingInputs(ifNode.condition());
    // AbstractBeginNode branch = ifNode.falseSuccessor();
    // branch.predecessor().replaceFirstSuccessor(branch, null);
    // GraphUtil.killCFG(branch);
    // graph.removeSplitPropagate(ifNode, ifNode.trueSuccessor());
    // }
}