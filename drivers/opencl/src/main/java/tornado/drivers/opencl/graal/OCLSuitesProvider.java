package tornado.drivers.opencl.graal;

import com.oracle.graal.java.GraphBuilderPhase;
import com.oracle.graal.lir.phases.PostAllocationOptimizationStage;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugins;
import com.oracle.graal.phases.PhaseSuite;
import com.oracle.graal.phases.common.AddressLoweringPhase;
import com.oracle.graal.phases.common.AddressLoweringPhase.AddressLowering;
import com.oracle.graal.phases.common.ExpandLogicPhase;
import com.oracle.graal.phases.tiers.HighTierContext;
import jdk.vm.ci.meta.MetaAccessProvider;
import tornado.drivers.opencl.graal.compiler.OCLCompilerConfiguration;
import tornado.drivers.opencl.graal.compiler.TornadoCanonicalizer;
import tornado.drivers.opencl.graal.compiler.plugins.OCLGraphBuilderPlugins;
import tornado.graal.TornadoLIRSuites;
import tornado.graal.TornadoSuites;

public class OCLSuitesProvider {

    private final PhaseSuite<HighTierContext> graphBuilderSuite;
    private TornadoSuites suites;
    private TornadoLIRSuites lirSuites;

    public OCLSuitesProvider(Plugins plugins, MetaAccessProvider metaAccessProvider, OCLCompilerConfiguration compilerConfig, AddressLowering addressLowering) {
        graphBuilderSuite = createGraphBuilderSuite(plugins);
        suites = new TornadoSuites(compilerConfig, new TornadoCanonicalizer());
        suites.getLowTier().findPhase(ExpandLogicPhase.class).add(new AddressLoweringPhase(addressLowering));
        lirSuites = createLIRSuites();
    }

    protected PhaseSuite<HighTierContext> createGraphBuilderSuite(Plugins plugins) {
        PhaseSuite<HighTierContext> suite = new PhaseSuite<>();

        InvocationPlugins invocationPlugins = plugins.getInvocationPlugins();
        OCLGraphBuilderPlugins.registerInvocationPlugins(invocationPlugins);
        OCLGraphBuilderPlugins.registerNewInstancePlugins(plugins);
        OCLGraphBuilderPlugins.registerParameterPlugins(plugins);

        GraphBuilderConfiguration config = GraphBuilderConfiguration.getSnippetDefault(plugins);
        config.withEagerResolving(true);
//        config.setUseProfiling(false);

        suite.appendPhase(new GraphBuilderPhase(config));

        return suite;
    }

    public TornadoLIRSuites createLIRSuites() {
        PostAllocationOptimizationStage.Options.LIROptRedundantMoveElimination.setValue(false);

        return new TornadoLIRSuites(suites.getPreAllocationOptimizationStage(),
                suites.getAllocationStage(), suites.getPostAllocationOptimizationStage());
    }

    public TornadoSuites createSuites() {
        return suites;
    }

    public PhaseSuite<HighTierContext> getDefaultGraphBuilderSuite() {
        return graphBuilderSuite;
    }

    public TornadoLIRSuites getLIRSuites() {
        return lirSuites;
    }

    public TornadoSuites getSuites() {
        return suites;
    }

}
