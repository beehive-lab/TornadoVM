package uk.ac.manchester.tornado.drivers.cuda.graal;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.AddressLoweringPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.PTXCanonicalizer;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.PTXCompilerConfiguration;
import uk.ac.manchester.tornado.runtime.graal.TornadoLIRSuites;
import uk.ac.manchester.tornado.runtime.graal.TornadoSuites;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSketchTier;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSuitesProvider;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class PTXSuitesProvider implements TornadoSuitesProvider {
    private final PhaseSuite<HighTierContext> graphBuilderSuite;
    private final TornadoSuites suites;
    private final TornadoLIRSuites lirSuites;
    private final PTXCanonicalizer canonicalizer;

    public PTXSuitesProvider(OptionValues options, GraphBuilderConfiguration.Plugins plugins, MetaAccessProvider metaAccessProvider, PTXCompilerConfiguration compilerConfig, AddressLoweringPhase.AddressLowering addressLowering) {
        graphBuilderSuite = createGraphBuilderSuite(plugins);
        canonicalizer = new PTXCanonicalizer();
        suites = new TornadoSuites(options, compilerConfig, metaAccessProvider, canonicalizer, addressLowering);
        lirSuites = createLIRSuites();
    }

    public void setContext(MetaAccessProvider metaAccess, ResolvedJavaMethod method, Object[] args, TaskMetaData meta) {
        //canonicalizer.setContext(metaAccess, method, args, meta);
    }

    private PhaseSuite<HighTierContext> createGraphBuilderSuite(GraphBuilderConfiguration.Plugins plugins) {
        PhaseSuite<HighTierContext> suite = new PhaseSuite<>();

        //InvocationPlugins invocationPlugins = plugins.getInvocationPlugins();
        //OCLGraphBuilderPlugins.registerInvocationPlugins(plugins, invocationPlugins);
        //OCLGraphBuilderPlugins.registerNewInstancePlugins(plugins);
        //OCLGraphBuilderPlugins.registerParameterPlugins(plugins);

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
