package tornado.drivers.opencl.graal.phases;

import com.oracle.graal.debug.Debug;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.util.GraphUtil;
import com.oracle.graal.phases.BasePhase;
import tornado.drivers.opencl.graal.nodes.GlobalThreadIdNode;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.graal.nodes.ParallelOffsetNode;
import tornado.graal.nodes.ParallelRangeNode;
import tornado.graal.nodes.ParallelStrideNode;
import tornado.graal.phases.TornadoHighTierContext;

public class TornadoParallelScheduler extends BasePhase<TornadoHighTierContext> {

    private void replacePerIteration(StructuredGraph graph, ParallelRangeNode range) {
        range.replaceAtUsages(range.value());
    }

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

        graph.clearAllStateAfter();
        graph.clearLastSchedule();

    }

    private void serialiseLoop(ParallelRangeNode range) {
        ParallelOffsetNode offset = range.offset();
        ParallelStrideNode stride = range.stride();

        range.replaceAndDelete(range.value());
        offset.replaceAndDelete(offset.value());
        stride.replaceAndDelete(stride.value());
    }

    private void paralleliseLoop(StructuredGraph graph, ParallelRangeNode node) {
        ParallelOffsetNode offset = node.offset();
        ParallelStrideNode stride = node.stride();

        IfNode ifNode = (IfNode) node.usages().first().usages().first();
        LoopExitNode loopExit = (LoopExitNode) ifNode.falseSuccessor();
        LoopBeginNode loopBegin = loopExit.loopBegin();
        LoopEndNode loopEnd = loopBegin.loopEnds().first();

        BeginNode newFalseBegin = graph.addWithoutUnique(new BeginNode());
        EndNode newFalseEnd = graph.addWithoutUnique(new EndNode());

        EndNode newTrueEnd = graph.addWithoutUnique(new EndNode());

        loopEnd.replaceAndDelete(newTrueEnd);
        newFalseBegin.setNext(newFalseEnd);

        MergeNode newMerge = graph.addWithoutUnique(new MergeNode());

        newMerge.addForwardEnd(newTrueEnd);
        newMerge.addForwardEnd(newFalseEnd);
        FixedNode next = loopExit.next();

        loopExit.setNext(null);
        loopExit.replaceAndDelete(newFalseBegin);

        newMerge.setNext(next);

        final ConstantNode index = graph.addOrUnique(ConstantNode.forInt(offset.index()));

//        ValueNode offsetValue = offset.value();
        ValueNode threadId = graph.addOrUnique(new GlobalThreadIdNode(index));
        offset.replaceAndDelete(threadId);

//        final AddNode addNode = graph.addOrUnique(new AddNode(threadId, offsetValue));
//        offset.replaceAndDelete(addNode);
        for (PhiNode phi : loopBegin.phis()) {
            phi.replaceAtUsages(phi.firstValue());
        }

        graph.reduceDegenerateLoopBegin(loopBegin);
        GraphUtil.killWithUnusedFloatingInputs(ifNode.condition());
        graph.removeSplitPropagate(ifNode, ifNode.trueSuccessor());

        replacePerIteration(graph, node);

        stride.clearInputs();
        stride.safeDelete();

        node.clearInputs();
//        node.safeDelete();
    }

}
