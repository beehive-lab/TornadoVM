package tornado.runtime;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;

public interface TornadoDriverProvider {
    
    public String getName();
    public TornadoDriver createDriver(HotSpotJVMCIRuntime hostRuntime, TornadoVMConfig config);
    
}
