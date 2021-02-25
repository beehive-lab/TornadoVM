package uk.ac.manchester.tornado.drivers.spirv.graal;

import jdk.vm.ci.meta.MetaAccessProvider;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.AddressLoweringPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVContext;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDevice;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDeviceContext;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilerConfiguration;
import uk.ac.manchester.tornado.runtime.graal.TornadoLIRSuites;
import uk.ac.manchester.tornado.runtime.graal.TornadoSuites;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSketchTier;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSuitesProvider;

/**
 * TornadoVM Provider for all TIER compilation bases (HighTier, MidTier, LowTier
 * & SketchTier).
 */
public class SPIRVSuitesProvider implements TornadoSuitesProvider {

    private final PhaseSuite<HighTierContext> graphBuilderSuite;
    private TornadoSuites suites;
    private TornadoLIRSuites lirSuites;

    public SPIRVSuitesProvider(OptionValues options, SPIRVDeviceContext deviceContext, GraphBuilderConfiguration.Plugins plugins, MetaAccessProvider metaAccessProvider,
            SPIRVCompilerConfiguration compilerConfig, AddressLoweringPhase.AddressLowering addressLowering) {
        this.graphBuilderSuite = createGraphBuilderSuite(plugins);
        suites = new TornadoSuites(options, deviceContext, compilerConfig, metaAccessProvider, null, addressLowering);
        lirSuites = new TornadoLIRSuites(suites.getPreAllocationOptimizationStage(), suites.getAllocationStage(), suites.getPostAllocationOptimizationStage());
    }

    private PhaseSuite<HighTierContext> createGraphBuilderSuite(GraphBuilderConfiguration.Plugins plugins) {
        PhaseSuite<HighTierContext> suite = new PhaseSuite<>();
        GraphBuilderConfiguration config = GraphBuilderConfiguration.getSnippetDefault(plugins);
        config.withEagerResolving(true);
        // config.setUseProfiling(false);
        suite.appendPhase(new GraphBuilderPhase(config));
        return suite;
    }

    public TornadoLIRSuites getLIRSuites() {
        return lirSuites;
    }

    public TornadoSuites getSuites() {
        return suites;
    }

    @Override
    public PhaseSuite<HighTierContext> getGraphBuilderSuite() {
        return graphBuilderSuite;
    }

    @Override
    public TornadoSketchTier getSketchTier() {
        return suites.getSketchTier();
    }
}
