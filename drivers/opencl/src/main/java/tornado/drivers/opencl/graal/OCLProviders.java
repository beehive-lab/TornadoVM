package tornado.drivers.opencl.graal;

import com.oracle.graal.hotspot.meta.HotSpotStampProvider;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.nodes.spi.LoweringProvider;
import com.oracle.graal.phases.util.Providers;
import jdk.vm.ci.runtime.JVMCIBackend;
import tornado.graal.compiler.TornadoCompilerConfiguration;
import tornado.graal.compiler.TornadoConstantFieldProvider;
import tornado.graal.compiler.TornadoForeignCallsProvider;
import tornado.graal.compiler.TornadoNodeCostProvider;

public class OCLProviders extends Providers {

    private final OCLSuitesProvider suites;
    private final Plugins graphBuilderPlugins;

    public OCLProviders(TornadoCompilerConfiguration compilerConfig, JVMCIBackend jvmciBackend,
            Plugins graphBuilderPlugins, LoweringProvider lowerer, OpenCLCodeCache codeCache) {
        super(jvmciBackend.getMetaAccess(), codeCache, jvmciBackend.getConstantReflection(), new TornadoConstantFieldProvider(), new TornadoForeignCallsProvider(), lowerer,
                null, new HotSpotStampProvider(), new TornadoNodeCostProvider());

        this.suites = new OCLSuitesProvider(graphBuilderPlugins, jvmciBackend.getMetaAccess());

        this.graphBuilderPlugins = graphBuilderPlugins;
    }

    public OCLSuitesProvider getSuitesProvider() {
        return suites;
    }

    public Plugins getGraphBuilderPlugins() {
        return graphBuilderPlugins;
    }
}
