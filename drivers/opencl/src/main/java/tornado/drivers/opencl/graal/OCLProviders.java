package tornado.drivers.opencl.graal;

import com.oracle.graal.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.hotspot.HotSpotGraalRuntimeProvider;
import com.oracle.graal.nodes.spi.LoweringProvider;
import com.oracle.graal.phases.tiers.SuitesProvider;
import com.oracle.graal.phases.util.Providers;

public class OCLProviders extends Providers {
	
	private final HotSpotGraalRuntimeProvider runtime;
	private final OCLSuitesProvider suites;
	private final Plugins graphBuilderPlugins;

	public OCLProviders(HotSpotGraalRuntimeProvider vmRuntime,
			Providers vmProviders, SuitesProvider suitesProvider, Plugins graphBuilderPlugins, LoweringProvider lowerer, OpenCLCodeCache codeCache) {
		super(vmProviders.getMetaAccess(),codeCache, vmProviders.getConstantReflection(), vmProviders.getForeignCalls(), lowerer,
				vmProviders.getReplacements(), vmProviders.getStampProvider());
		this.runtime = vmRuntime;
		this.suites = new OCLSuitesProvider(graphBuilderPlugins,vmProviders.getMetaAccess());
		
		this.graphBuilderPlugins = graphBuilderPlugins;
	}
	
	public OCLSuitesProvider getSuitesProvider() {
        return suites;
    }

    public Plugins getGraphBuilderPlugins() {
        return graphBuilderPlugins;
    }
    
    public HotSpotGraalRuntimeProvider getRuntime(){
    	return runtime;
    }
    
    


}
