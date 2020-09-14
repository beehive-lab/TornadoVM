package uk.ac.manchester.tornado.drivers.common.graal.compiler;

import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getDebugContext;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.Phase;

public class DumpLowTierGraph extends Phase {

    @Override
    protected void run(StructuredGraph graph) {
        getDebugContext().dump(DebugContext.BASIC_LEVEL, graph, "After-OCLLowTier");
    }
}
