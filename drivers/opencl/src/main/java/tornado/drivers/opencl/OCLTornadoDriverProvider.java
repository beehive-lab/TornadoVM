package tornado.drivers.opencl;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import tornado.runtime.TornadoDriver;
import tornado.runtime.TornadoDriverProvider;
import tornado.runtime.TornadoVMConfig;

public class OCLTornadoDriverProvider implements TornadoDriverProvider {

    @Override
    public String getName() {
        return "OpenCL Driver";
    }

    @Override
    public TornadoDriver createDriver(HotSpotJVMCIRuntime vmRuntime, TornadoVMConfig vmConfig) {
        return new OCLDriver(vmRuntime, vmConfig);
    }
    
}
