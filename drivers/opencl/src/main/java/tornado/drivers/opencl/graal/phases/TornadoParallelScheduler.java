package tornado.drivers.opencl.graal.phases;

import tornado.api.enums.TornadoSchedulingStrategy;
import tornado.common.DeviceMapping;
import tornado.drivers.opencl.graal.nodes.OCLIntBinaryIntrinsicNode;
import tornado.drivers.opencl.graal.nodes.ThreadCount;
import tornado.drivers.opencl.graal.nodes.ThreadId;
import static tornado.api.enums.TornadoSchedulingStrategy.*;
import tornado.graal.nodes.ParallelOffsetNode;
import tornado.graal.nodes.ParallelRangeNode;
import tornado.graal.nodes.ParallelStrideNode;
import tornado.graal.phases.TornadoHighTierContext;

import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.AddNode;
import com.oracle.graal.nodes.calc.DivNode;
import com.oracle.graal.nodes.calc.MulNode;
import com.oracle.graal.nodes.calc.SubNode;
import com.oracle.graal.phases.BasePhase;

public class TornadoParallelScheduler extends BasePhase<TornadoHighTierContext> {

	private ValueNode	blockSize;

	private void replaceOffsetNode(TornadoSchedulingStrategy schedule, StructuredGraph graph,
			ParallelOffsetNode offset) {
		if (schedule == PER_BLOCK) replacePerBlock(graph, offset);
		else if (schedule == PER_ITERATION) replacePerIteration(graph, offset);
	}

	private void replacePerIteration(StructuredGraph graph, ParallelOffsetNode offset) {

		final ConstantNode index = graph.addOrUnique(ConstantNode.forInt(offset.index()));

		final ThreadId threadId = graph.addOrUnique(new ThreadId(index));

		final AddNode addNode = graph.addOrUnique(new AddNode(threadId, offset.value()));

		offset.replaceAtUsages(addNode);
		offset.safeDelete();
	}

	private void replacePerBlock(StructuredGraph graph, ParallelOffsetNode offset) {

		final ThreadId threadId = graph.addOrUnique(new ThreadId(ConstantNode.forInt(
				offset.index(), graph)));
		final MulNode newOffset = graph.addOrUnique(new MulNode(threadId, blockSize));
		offset.replaceAtUsages(newOffset);
		offset.safeDelete();
	}

	private void replaceStrideNode(TornadoSchedulingStrategy schedule, StructuredGraph graph,
			ParallelStrideNode stride) {
		if (schedule == PER_BLOCK) replacePerBlock(graph, stride);
		else if (schedule == PER_ITERATION) replacePerIteration(graph, stride);
	}

	private void replacePerIteration(StructuredGraph graph, ParallelStrideNode stride) {

		final ConstantNode index = graph.addOrUnique(ConstantNode.forInt(stride.index()));

		final ThreadCount threadCount = graph.addOrUnique(new ThreadCount(index));

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
		if (schedule == PER_BLOCK) replacePerBlock(graph, range);
		else if (schedule == PER_ITERATION) replacePerIteration(graph, range);
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
		final ThreadCount threadCount = graph.addOrUnique(new ThreadCount(index));
		final SubNode threadCountM1 = graph.addOrUnique(new SubNode(threadCount, ConstantNode
				.forInt(1, graph)));
		final AddNode adjustedTrueRange = graph.addOrUnique(new AddNode(trueRange, threadCountM1));
		blockSize = graph.addOrUnique(new DivNode(adjustedTrueRange, threadCount));

	}

	private void replacePerBlock(StructuredGraph graph, ParallelRangeNode range) {
		buildBlockSize(graph, range);

		final ThreadId threadId = graph.addOrUnique(new ThreadId(ConstantNode.forInt(
				range.index(), graph)));
		final MulNode newOffset = graph.addOrUnique(new MulNode(threadId, blockSize));
		
		final AddNode newRange = graph.addOrUnique(new AddNode(newOffset, blockSize));

		final ValueNode adjustedRange = graph.addOrUnique(OCLIntBinaryIntrinsicNode.create(
				newRange, range.value(), OCLIntBinaryIntrinsicNode.Operation.MIN, Kind.Int));

		range.replaceAtUsages(adjustedRange);
		range.safeDelete();

	}

	@Override
	protected void run(StructuredGraph graph, TornadoHighTierContext context) {
		if (!context.hasDeviceMapping()) return;

		final DeviceMapping device = context.getDeviceMapping();
		final TornadoSchedulingStrategy strategy = device.getPreferedSchedule();

		graph.getNodes().filter(ParallelRangeNode.class).forEach(node -> {
			replaceRangeNode(strategy, graph, node);
			replaceOffsetNode(strategy, graph, node.offset());
			replaceStrideNode(strategy, graph, node.stride());

		});

	}

}
