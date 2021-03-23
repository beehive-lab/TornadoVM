package uk.ac.manchester.tornado.drivers.spirv.graal;

import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.AddressLoweringPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;

import jdk.vm.ci.meta.MetaAccessProvider;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDeviceContext;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCanonicalizer;
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
    private final SPIRVCanonicalizer canonicalizer;

    public SPIRVSuitesProvider(OptionValues options, SPIRVDeviceContext deviceContext, GraphBuilderConfiguration.Plugins plugins, MetaAccessProvider metaAccessProvider,
            SPIRVCompilerConfiguration compilerConfig, AddressLoweringPhase.AddressLowering addressLowering) {
        this.graphBuilderSuite = createGraphBuilderSuite(plugins);
        this.canonicalizer = new SPIRVCanonicalizer();
        suites = new TornadoSuites(options, deviceContext, compilerConfig, metaAccessProvider, canonicalizer, addressLowering);
        lirSuites = new TornadoLIRSuites(suites.getPreAllocationOptimizationStage(), suites.getAllocationStage(), suites.getPostAllocationOptimizationStage());

    }

    private PhaseSuite<HighTierContext> createGraphBuilderSuite(GraphBuilderConfiguration.Plugins plugins) {
        PhaseSuite<HighTierContext> suite = new PhaseSuite<>();
        GraphBuilderConfiguration config = GraphBuilderConfiguration.getSnippetDefault(plugins);
        config.withEagerResolving(true);
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
