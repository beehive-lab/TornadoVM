package tornado.drivers.opencl.graal.phases;

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
import tornado.common.DeviceMapping;
import tornado.drivers.opencl.graal.nodes.GlobalThreadIdNode;
import tornado.drivers.opencl.graal.nodes.GlobalThreadSizeNode;
import tornado.drivers.opencl.graal.nodes.OCLIntBinaryIntrinsicNode;
import tornado.graal.nodes.ParallelOffsetNode;
import tornado.graal.nodes.ParallelRangeNode;
import tornado.graal.nodes.ParallelStrideNode;
import tornado.graal.phases.TornadoHighTierContext;

import static tornado.api.enums.TornadoSchedulingStrategy.PER_BLOCK;
import static tornado.api.enums.TornadoSchedulingStrategy.PER_ITERATION;

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
        if (!context.hasDeviceMapping()) {
            return;
        }

        final DeviceMapping device = context.getDeviceMapping();
        final TornadoSchedulingStrategy strategy = device.getPreferedSchedule();

        graph.getNodes().filter(ParallelRangeNode.class).forEach(node -> {
            ParallelOffsetNode offset = node.offset();
            ParallelStrideNode stride = node.stride();
            replaceRangeNode(strategy, graph, node);
            replaceOffsetNode(strategy, graph, offset);
            replaceStrideNode(strategy, graph, stride);

        });

    }

}
