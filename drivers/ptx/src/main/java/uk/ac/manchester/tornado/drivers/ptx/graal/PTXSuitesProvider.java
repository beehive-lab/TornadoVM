package uk.ac.manchester.tornado.drivers.ptx.graal;

import jdk.vm.ci.meta.MetaAccessProvider;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.AddressLoweringPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import uk.ac.manchester.tornado.drivers.ptx.PTXDeviceContext;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXCompilerConfiguration;
import uk.ac.manchester.tornado.runtime.graal.TornadoLIRSuites;
import uk.ac.manchester.tornado.runtime.graal.TornadoSuites;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSketchTier;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSuitesProvider;

public class PTXSuitesProvider implements TornadoSuitesProvider {
    private final PhaseSuite<HighTierContext> graphBuilderSuite;
    private final TornadoSuites suites;
    private final TornadoLIRSuites lirSuites;

    public PTXSuitesProvider(OptionValues options, PTXDeviceContext deviceContext, GraphBuilderConfiguration.Plugins plugins, MetaAccessProvider metaAccessProvider, PTXCompilerConfiguration compilerConfig, AddressLoweringPhase.AddressLowering addressLowering) {
        graphBuilderSuite = createGraphBuilderSuite(plugins);
        suites = new TornadoSuites(options, deviceContext, compilerConfig, metaAccessProvider, null, addressLowering);
        lirSuites = createLIRSuites();
    }

    private PhaseSuite<HighTierContext> createGraphBuilderSuite(GraphBuilderConfiguration.Plugins plugins) {
        PhaseSuite<HighTierContext> suite = new PhaseSuite<>();

        GraphBuilderConfiguration config = GraphBuilderConfiguration.getSnippetDefault(plugins);
        config.withEagerResolving(true);

        //config.setUseProfiling(false);
        suite.appendPhase(new GraphBuilderPhase(config));

        return suite;
    }

    public final TornadoLIRSuites createLIRSuites() {
        return new TornadoLIRSuites(suites.getPreAllocationOptimizationStage(), suites.getAllocationStage(), suites.getPostAllocationOptimizationStage());
    }

    public TornadoSuites createSuites() {
        return suites;
    }

    @Override
    public PhaseSuite<HighTierContext> getGraphBuilderSuite() {
        return graphBuilderSuite;
    }

    public TornadoLIRSuites getLIRSuites() {
        return lirSuites;
    }

    public TornadoSuites getSuites() {
        return suites;
    }

    @Override
    public TornadoSketchTier getSketchTier() {
        return suites.getSketchTier();
    }
}
