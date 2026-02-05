package uk.ac.manchester.tornado.runtime.graal.compiler;

import jdk.graal.compiler.java.BytecodeParser;
import jdk.graal.compiler.java.GraphBuilderPhase;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.IntrinsicContext;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class TornadoInternalGraphBuilder extends GraphBuilderPhase {

    public TornadoInternalGraphBuilder(GraphBuilderConfiguration config) {
        super(config);
    }

    @Override
    public GraphBuilderPhase copyWithConfig(GraphBuilderConfiguration config) {
        return new TornadoInternalGraphBuilder(config);
    }

    @Override
    protected Instance createInstance(CoreProviders providers, GraphBuilderConfiguration instanceGBConfig, OptimisticOptimizations optimisticOpts, IntrinsicContext initialIntrinsicContext) {
        return new Instance(providers, instanceGBConfig, optimisticOpts, initialIntrinsicContext);
    }

    public static class Instance extends GraphBuilderPhase.Instance {

        public Instance(CoreProviders providers, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts, IntrinsicContext initialIntrinsicContext) {
            super(providers, graphBuilderConfig, optimisticOpts, initialIntrinsicContext);
        }

        @Override
        protected BytecodeParser createBytecodeParser(StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI, IntrinsicContext intrinsicContext) {
            return new TestBytecodeParser(this, graph, parent, method, entryBCI, intrinsicContext);
        }
    }

    /**
     * A non-abstract subclass of {@link BytecodeParser} for testing purposes.
     */
    static class TestBytecodeParser extends BytecodeParser {
        protected TestBytecodeParser(GraphBuilderPhase.Instance graphBuilderInstance, StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI,
                IntrinsicContext intrinsicContext) {
            super(graphBuilderInstance, graph, parent, method, entryBCI, intrinsicContext);
        }
    }

}