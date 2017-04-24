package tornado.drivers.opencl.graal.phases;

import com.oracle.graal.debug.Debug;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.AddNode;
import com.oracle.graal.nodes.calc.DivNode;
import com.oracle.graal.nodes.calc.MulNode;
import com.oracle.graal.nodes.calc.SubNode;
import com.oracle.graal.phases.BasePhase;
import jdk.vm.ci.meta.JavaKind;
import tornado.api.enums.TornadoSchedulingStrategy;
import tornado.drivers.opencl.graal.nodes.GlobalThreadIdNode;
import tornado.drivers.opencl.graal.nodes.GlobalThreadSizeNode;
import tornado.drivers.opencl.graal.nodes.OCLIntBinaryIntrinsicNode;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.graal.nodes.AbstractParallelNode;
import tornado.graal.nodes.ParallelOffsetNode;
import tornado.graal.nodes.ParallelRangeNode;
import tornado.graal.nodes.ParallelStrideNode;
import tornado.graal.phases.TornadoHighTierContext;

import static tornado.api.enums.TornadoSchedulingStrategy.PER_BLOCK;
import static tornado.api.enums.TornadoSchedulingStrategy.PER_ITERATION;
import static tornado.common.Tornado.ENABLE_PARALLELIZATION;
import static tornado.common.Tornado.USE_THREAD_COARSENING;

public class TornadoParallelScheduler extends BasePhase<TornadoHighTierContext> {

    private ValueNode blockSize;

    private void replaceOffsetNode(TornadoSchedulingStrategy schedule, StructuredGraph graph,
            ParallelOffsetNode offset) {
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
        final GlobalThreadIdNode threadId = graph.addOrUnique(new GlobalThreadIdNode(ConstantNode.forInt(
                offset.index(), graph)));
        final MulNode newOffset = graph.addOrUnique(new MulNode(threadId, blockSize));
        offset.replaceAtUsages(newOffset);
        offset.safeDelete();
    }

    private void replaceStrideNode(TornadoSchedulingStrategy schedule, StructuredGraph graph,
            ParallelStrideNode stride) {
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

        // final MulNode newStride = graph.addOrUnique(new MulNode(stride.value(),)
        stride.replaceAtUsages(stride.value());
        stride.safeDelete();

    }

    private void replaceRangeNode(TornadoSchedulingStrategy schedule, StructuredGraph graph,
            ParallelRangeNode range) {
        if (schedule == PER_BLOCK) {
            replacePerBlock(graph, range);
        } else if (schedule == PER_ITERATION) {
            replacePerIteration(graph, range);
        }
    }

    private void replacePerIteration(StructuredGraph graph, ParallelRangeNode range) {

        range.replaceAtUsages(range.value());

    }

    private void buildBlockSize(StructuredGraph graph, ParallelRangeNode range) {

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

    private void replacePerBlock(StructuredGraph graph, ParallelRangeNode range) {
        buildBlockSize(graph, range);

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
        if (!context.hasDeviceMapping() || USE_THREAD_COARSENING) {
            return;
        }

        OCLDeviceMapping device = (OCLDeviceMapping) context.getDeviceMapping();
        final TornadoSchedulingStrategy strategy = device.getPreferedSchedule();
        long[] maxWorkItemSizes = device.getDevice().getMaxWorkItemSizes();

        graph.getNodes().filter(ParallelRangeNode.class).forEach(node -> {
            if (ENABLE_PARALLELIZATION && maxWorkItemSizes[node.index()] > 1) {

                ParallelOffsetNode offset = node.offset();
                ParallelStrideNode stride = node.stride();
                replaceRangeNode(strategy, graph, node);
                replaceOffsetNode(strategy, graph, offset);
                replaceStrideNode(strategy, graph, stride);

            } else {
                serialiseLoop(node);
            }

            Debug.dump(Debug.BASIC_LOG_LEVEL, graph, "after scheduling loop index=" + node.index());
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

//    private void paralleliseLoop(StructuredGraph graph, ParallelRangeNode range) {
//        /*
//         * take copies of any nodes found via range
//         */
//        ParallelOffsetNode offset = range.offset();
//        ParallelStrideNode stride = range.stride();
//        IfNode ifNode = (IfNode) range.usages().first().usages().first();
//
//        /*
//         * remove the range node from the graph - needs to be done before any
//         * node which is used as an input to range: e.g. offset and stride
//         */
//        range.replaceAtUsages(range.value());
//        killNode(range);
//
//        /*
//         * replace the offset node with thread id node
//         */
//        final ConstantNode index = graph.addOrUnique(ConstantNode.forInt(offset.index()));
//        ValueNode threadId = graph.addOrUnique(new GlobalThreadIdNode(index));
//        offset.replaceAtUsages(threadId);
//        killNode(offset);
//
//        /*
//         * kill off the stride node - not needed
//         */
//        killNode(stride);
//
//        /*
//         * now update the cfg to remove the for loop
//         */
//        LoopExitNode loopExit = (LoopExitNode) ifNode.falseSuccessor();
//        LoopBeginNode loopBegin = loopExit.loopBegin();
//
//        BeginNode newFalseBegin = graph.addWithoutUnique(new BeginNode());
//        EndNode newFalseEnd = graph.addWithoutUnique(new EndNode());
//        newFalseBegin.setNext(newFalseEnd);
//
//        MergeNode newMerge = graph.addWithoutUnique(new MergeNode());
//
//        for (LoopEndNode loopEnd : loopBegin.loopEnds().snapshot()) {
//            EndNode newEnd = graph.addWithoutUnique(new EndNode());
//            loopEnd.replaceAndDelete(newEnd);
//            newMerge.addForwardEnd(newEnd);
//        }
//
//        newMerge.addForwardEnd(newFalseEnd);
//
//        FixedNode next = loopExit.next();
//
//        loopExit.setNext(null);
//        loopExit.replaceAndDelete(newFalseBegin);
//
//        newMerge.setNext(next);
//
//        /*
//         * eliminate the phi nodes
//         */
//        for (PhiNode phi : loopBegin.phis()) {
//            phi.replaceAtUsages(phi.firstValue());
//        }
//
//        /*
//         * eliminate any unneccesary nodes/branches from the graph
//         */
//        graph.reduceDegenerateLoopBegin(loopBegin);
//
//        /*
//         * removes the if stmt (thread id check)
//         */
//        GraphUtil.killWithUnusedFloatingInputs(ifNode.condition());
//        AbstractBeginNode branch = ifNode.falseSuccessor();
//        branch.predecessor().replaceFirstSuccessor(branch, null);
//        GraphUtil.killCFG(branch);
//        graph.removeSplitPropagate(ifNode, ifNode.trueSuccessor());
//    }
}
