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

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.AddressLoweringPhase.AddressLowering;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import tornado.api.meta.TaskMetaData;
import tornado.drivers.opencl.graal.compiler.OCLCanonicalizer;
import tornado.drivers.opencl.graal.compiler.OCLCompilerConfiguration;
import tornado.drivers.opencl.graal.compiler.plugins.OCLGraphBuilderPlugins;
import tornado.graal.TornadoLIRSuites;
import tornado.graal.TornadoSuites;
import tornado.graal.compiler.TornadoSketchTier;
import tornado.graal.compiler.TornadoSuitesProvider;

public class OCLSuitesProvider implements TornadoSuitesProvider {

    private final PhaseSuite<HighTierContext> graphBuilderSuite;
    private final TornadoSuites suites;
    private final TornadoLIRSuites lirSuites;
    private final OCLCanonicalizer canonicalizer;

    public OCLSuitesProvider(OptionValues options, Plugins plugins, MetaAccessProvider metaAccessProvider, OCLCompilerConfiguration compilerConfig, AddressLowering addressLowering) {
        graphBuilderSuite = createGraphBuilderSuite(plugins);
        canonicalizer = new OCLCanonicalizer();
        suites = new TornadoSuites(options, compilerConfig, canonicalizer, addressLowering);
        lirSuites = createLIRSuites();
    }

    public void setContext(MetaAccessProvider metaAccess, ResolvedJavaMethod method, Object[] args, TaskMetaData meta) {
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

    public final TornadoLIRSuites createLIRSuites() {
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
