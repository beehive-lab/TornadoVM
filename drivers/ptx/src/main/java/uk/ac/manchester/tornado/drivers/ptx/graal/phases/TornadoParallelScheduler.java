package uk.ac.manchester.tornado.drivers.ptx.graal.phases;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.phases.BasePhase;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.GlobalThreadIdNode;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.GlobalThreadSizeNode;
import uk.ac.manchester.tornado.drivers.ptx.runtime.PTXTornadoDevice;
import uk.ac.manchester.tornado.runtime.common.TornadoSchedulingStrategy;
import uk.ac.manchester.tornado.runtime.graal.nodes.AbstractParallelNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.ParallelOffsetNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.ParallelRangeNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.ParallelStrideNode;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoHighTierContext;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getDebugContext;
import static uk.ac.manchester.tornado.runtime.common.TornadoSchedulingStrategy.PER_BLOCK;
import static uk.ac.manchester.tornado.runtime.common.TornadoSchedulingStrategy.PER_ITERATION;

public class TornadoParallelScheduler extends BasePhase<TornadoHighTierContext> {
    private ValueNode blockSize;

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
    private void replacePerBlock(StructuredGraph graph, ParallelRangeNode range) {
        unimplemented();
        // buildBlockSize(graph, range);

        final GlobalThreadIdNode threadId = graph.addOrUnique(new GlobalThreadIdNode(ConstantNode.forInt(range.index(), graph)));
        final MulNode newOffset = graph.addOrUnique(new MulNode(threadId, blockSize));
        final AddNode newRange = graph.addOrUnique(new AddNode(newOffset, blockSize));

        // Stride of 2
        final MulNode stride = graph.addOrUnique(new MulNode(newRange, range.stride().value()));
        //final ValueNode adjustedRange = graph.addOrUnique(OCLIntBinaryIntrinsicNode.create(stride, range.value(), OCLIntBinaryIntrinsicNode.Operation.MIN, JavaKind.Int));

        //range.replaceAtUsages(adjustedRange);
        range.safeDelete();
    }

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {
        if (context.getMeta() == null || context.getMeta().enableThreadCoarsener()) {
            return;
        }

        PTXTornadoDevice device = (PTXTornadoDevice) context.getDeviceMapping();
        final TornadoSchedulingStrategy strategy = device.getPreferredSchedule();
        long[] maxWorkItemSizes = device.getDevice().getDeviceMaxWorkItemSizes();

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
