package uk.ac.manchester.tornado.drivers.spirv.graal.phases;

import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.BasePhase;

import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.LocalWorkGroupDimensionsNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.ThreadConfigurationNode;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoHighTierContext;

public class TornadoThreadScheduler extends BasePhase<TornadoHighTierContext> {

    /**
     * This value was chosen for Intel FPGAs due to experimental results.
     */
    private int oneD = 64;
    private int twoD = 1;
    private int threeD = 1;

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {
        if (graph.hasLoops() && (context.getDeviceMapping().getDeviceType() == TornadoDeviceType.ACCELERATOR)) {
            NodeIterable<EndNode> filter = graph.getNodes().filter(EndNode.class);
            EndNode end = filter.first();
            final LocalWorkGroupDimensionsNode localWorkGroupNode = graph.addOrUnique(new LocalWorkGroupDimensionsNode(oneD, twoD, threeD));
            ThreadConfigurationNode threadConfig = graph.addOrUnique(new ThreadConfigurationNode(localWorkGroupNode));
            graph.addBeforeFixed(end, threadConfig);
        }
    }
}