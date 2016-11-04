package tornado.drivers.opencl.graal;

import com.oracle.graal.api.meta.MetaAccessProvider;
import com.oracle.graal.graphbuilderconf.GraphBuilderConfiguration;
import com.oracle.graal.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.graphbuilderconf.InvocationPlugins;
import com.oracle.graal.java.GraphBuilderPhase;
import com.oracle.graal.lir.phases.LIRSuites;
import com.oracle.graal.lir.phases.PostAllocationOptimizationStage;
import com.oracle.graal.phases.PhaseSuite;
import com.oracle.graal.phases.tiers.HighTierContext;
import com.oracle.graal.phases.tiers.Suites;
import tornado.drivers.opencl.graal.compiler.OCLCompilerConfiguration;
import tornado.drivers.opencl.graal.compiler.TornadoCanonicalizer;
import tornado.drivers.opencl.graal.compiler.plugins.OCLGraphBuilderPlugins;
import tornado.graal.TornadoLIRSuites;
import tornado.graal.TornadoSuites;
import tornado.graal.phases.lir.TornadoAllocationStage;

public class OCLSuitesProvider {

	private final PhaseSuite<HighTierContext>	graphBuilderSuite;
	private TornadoSuites							suites;
	private TornadoLIRSuites						lirSuites;
	

	public OCLSuitesProvider(Plugins plugins, MetaAccessProvider metaAccessProvider) {
		graphBuilderSuite = createGraphBuilderSuite(plugins);
		lirSuites = createLIRSuites();
		suites = new TornadoSuites(new OCLCompilerConfiguration(), new TornadoCanonicalizer());
	}

	protected PhaseSuite<HighTierContext> createGraphBuilderSuite(Plugins plugins) {
		PhaseSuite<HighTierContext> suite = new PhaseSuite<>();

		InvocationPlugins invocationPlugins = plugins.getInvocationPlugins();
		OCLGraphBuilderPlugins.registerInvocationPlugins(invocationPlugins);
		OCLGraphBuilderPlugins.registerNewInstancePlugins(plugins);
                OCLGraphBuilderPlugins.registerParameterPlugins(plugins);

		GraphBuilderConfiguration config = GraphBuilderConfiguration.getEagerDefault(plugins);
		config.setUseProfiling(false);

		suite.appendPhase(new GraphBuilderPhase(config));

		return suite;
	}

	public TornadoLIRSuites createLIRSuites() {
		PostAllocationOptimizationStage.Options.LIROptRedundantMoveElimination.setValue(false);

		LIRSuites defaultSuites = Suites.createDefaultLIRSuites();
		return new TornadoLIRSuites(defaultSuites.getPreAllocationOptimizationStage(),
				new TornadoAllocationStage(), defaultSuites.getPostAllocationOptimizationStage());
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

	public TornadoSuites	 getSuites() {
		return suites;
	}

}
