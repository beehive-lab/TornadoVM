package uk.ac.manchester.tornado.drivers.spirv.graal.phases;

import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.loop.DefaultLoopPolicies;
import org.graalvm.compiler.nodes.loop.LoopEx;
import org.graalvm.compiler.nodes.loop.LoopFragmentInside;
import org.graalvm.compiler.nodes.loop.LoopPolicies;
import org.graalvm.compiler.nodes.loop.LoopsData;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.tiers.MidTierContext;

import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.graal.nodes.TornadoLoopsData;

public class TornadoPartialLoopUnroll extends BasePhase<MidTierContext> {

    private static final int LOOP_UNROLL_FACTOR_DEFAULT = 2;
    private static final int LOOP_BOUND_UPPER_LIMIT = 16384;

    @Override
    protected void run(StructuredGraph graph, MidTierContext context) {

        if (!graph.hasLoops()) {
            return;
        }

        int initialNodeCount = graph.getNodeCount();
        int unrollFactor = getUnrollFactor();

        for (int i = 0; Math.pow(2, i) < unrollFactor; i++) {
            if (graph.getNodeCount() < getUpperGraphLimit(initialNodeCount, graph)) {
                partialUnroll(graph, context);
            }
        }
    }

    private static void partialUnroll(StructuredGraph graph, MidTierContext context) {
        final LoopsData dataCounted = new TornadoLoopsData(graph);

        LoopPolicies loopPolicies = createLoopPolicies();
        CanonicalizerPhase canonicalizer = CanonicalizerPhase.create();

        canonicalizer.apply(graph, context);
        dataCounted.detectedCountedLoops();
        for (LoopEx loop : dataCounted.countedLoops()) {
            int loopBound = loop.counted().getLimit().asJavaConstant().asInt();
            if (isPowerOfTwo(loopBound) && (loopBound < LOOP_BOUND_UPPER_LIMIT)) {
                LoopFragmentInside newSegment = loop.inside().duplicate();
                newSegment.insertWithinAfter(loop, null);
            }
        }
        new DeadCodeEliminationPhase().apply(graph);
    }

    private static int getUnrollFactor() {
        return (isPowerOfTwo(Tornado.UNROLL_FACTOR) && Tornado.UNROLL_FACTOR <= 32) ? Tornado.UNROLL_FACTOR : LOOP_UNROLL_FACTOR_DEFAULT;
    }

    private static int getUpperGraphLimit(int initialGraphNodeCount, StructuredGraph graph) {
        return (initialGraphNodeCount + GraalOptions.MaximumDesiredSize.getValue(graph.getOptions()) * 2);
    }

    public static LoopPolicies createLoopPolicies() {
        return new DefaultLoopPolicies();
    }

    private static boolean isPowerOfTwo(int number) {
        return number > 0 && ((number & (number - 1)) == 0);
    }
}
