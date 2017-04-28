/* 
 * Copyright 2012 James Clarkson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tornado.drivers.opencl.graal;

import com.oracle.graal.java.GraphBuilderPhase;
import com.oracle.graal.lir.phases.PostAllocationOptimizationStage;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugins;
import com.oracle.graal.phases.PhaseSuite;
import com.oracle.graal.phases.common.AddressLoweringPhase.AddressLowering;
import com.oracle.graal.phases.tiers.HighTierContext;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import tornado.drivers.opencl.graal.compiler.OCLCanonicalizer;
import tornado.drivers.opencl.graal.compiler.OCLCompilerConfiguration;
import tornado.drivers.opencl.graal.compiler.plugins.OCLGraphBuilderPlugins;
import tornado.graal.TornadoLIRSuites;
import tornado.graal.TornadoSuites;
import tornado.graal.compiler.TornadoSketchTier;
import tornado.graal.compiler.TornadoSuitesProvider;
import tornado.meta.Meta;

public class OCLSuitesProvider implements TornadoSuitesProvider {

    private final PhaseSuite<HighTierContext> graphBuilderSuite;
    private final TornadoSuites suites;
    private final TornadoLIRSuites lirSuites;
    private final OCLCanonicalizer canonicalizer;

    public OCLSuitesProvider(Plugins plugins, MetaAccessProvider metaAccessProvider, OCLCompilerConfiguration compilerConfig, AddressLowering addressLowering) {
        graphBuilderSuite = createGraphBuilderSuite(plugins);
        canonicalizer = new OCLCanonicalizer();
        suites = new TornadoSuites(compilerConfig, canonicalizer, addressLowering);
        lirSuites = createLIRSuites();
    }

    public void setContext(MetaAccessProvider metaAccess, ResolvedJavaMethod method, Object[] args, Meta meta) {
        canonicalizer.setContext(metaAccess, method, args, meta);
    }

    private PhaseSuite<HighTierContext> createGraphBuilderSuite(Plugins plugins) {
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

    private TornadoLIRSuites createLIRSuites() {
        PostAllocationOptimizationStage.Options.LIROptRedundantMoveElimination.setValue(false);

        return new TornadoLIRSuites(suites.getPreAllocationOptimizationStage(),
                suites.getAllocationStage(), suites.getPostAllocationOptimizationStage());
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
