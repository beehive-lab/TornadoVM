package tornado.drivers.opencl.graal.phases;

import com.oracle.graal.debug.Debug;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.util.GraphUtil;
import com.oracle.graal.phases.BasePhase;
import tornado.drivers.opencl.graal.nodes.GlobalThreadIdNode;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.graal.nodes.AbstractParallelNode;
import tornado.graal.nodes.ParallelOffsetNode;
import tornado.graal.nodes.ParallelRangeNode;
import tornado.graal.nodes.ParallelStrideNode;
import tornado.graal.phases.TornadoHighTierContext;

public class TornadoParallelScheduler extends BasePhase<TornadoHighTierContext> {

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {
        if (!context.hasDeviceMapping()) {
            return;
        }

        OCLDeviceMapping device = (OCLDeviceMapping) context.getDeviceMapping();
        long[] maxWorkItemSizes = device.getDevice().getMaxWorkItemSizes();

        graph.getNodes().filter(ParallelRangeNode.class).forEach(node -> {
            if (maxWorkItemSizes[node.index()] > 1) {
                paralleliseLoop(graph, node);
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

    private void paralleliseLoop(StructuredGraph graph, ParallelRangeNode range) {
        /*
         * take copies of any nodes found via range
         */
        ParallelOffsetNode offset = range.offset();
        ParallelStrideNode stride = range.stride();
        IfNode ifNode = (IfNode) range.usages().first().usages().first();

        /*
         * remove the range node from the graph - needs to be done before any
         * node which is used as an input to range: e.g. offset and stride
         */
        range.replaceAtUsages(range.value());
        killNode(range);

        /*
         * replace the offset node with thread id node
         */
        final ConstantNode index = graph.addOrUnique(ConstantNode.forInt(offset.index()));
        ValueNode threadId = graph.addOrUnique(new GlobalThreadIdNode(index));
        offset.replaceAtUsages(threadId);
        killNode(offset);

        /*
         * kill off the stride node - not needed
         */
        killNode(stride);

        /*
         * now update the cfg to remove the for loop
         */
        LoopExitNode loopExit = (LoopExitNode) ifNode.falseSuccessor();
        LoopBeginNode loopBegin = loopExit.loopBegin();

        BeginNode newFalseBegin = graph.addWithoutUnique(new BeginNode());
        EndNode newFalseEnd = graph.addWithoutUnique(new EndNode());
        newFalseBegin.setNext(newFalseEnd);

        MergeNode newMerge = graph.addWithoutUnique(new MergeNode());

        for (LoopEndNode loopEnd : loopBegin.loopEnds().snapshot()) {
            EndNode newEnd = graph.addWithoutUnique(new EndNode());
            loopEnd.replaceAndDelete(newEnd);
            newMerge.addForwardEnd(newEnd);
        }

        newMerge.addForwardEnd(newFalseEnd);

        FixedNode next = loopExit.next();

        loopExit.setNext(null);
        loopExit.replaceAndDelete(newFalseBegin);

        newMerge.setNext(next);

        /*
         * eliminate the phi nodes
         */
        for (PhiNode phi : loopBegin.phis()) {
            phi.replaceAtUsages(phi.firstValue());
        }

        /*
         * eliminate any unneccesary nodes/branches from the graph
         */
        graph.reduceDegenerateLoopBegin(loopBegin);
        GraphUtil.killWithUnusedFloatingInputs(ifNode.condition());

        AbstractBeginNode branch = ifNode.falseSuccessor();
        branch.predecessor().replaceFirstSuccessor(branch, null);
        GraphUtil.killCFG(branch);
        graph.removeSplitPropagate(ifNode, ifNode.trueSuccessor());

    }

}
