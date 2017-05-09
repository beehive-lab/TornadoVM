package tornado.drivers.rmi;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import tornado.runtime.TornadoDriver;
import tornado.runtime.TornadoDriverProvider;
import tornado.runtime.TornadoVMConfig;

public class RMIDriverProvider implements TornadoDriverProvider {

    @Override
    public TornadoDriver createDriver(HotSpotJVMCIRuntime hostRuntime, TornadoVMConfig config) {
        return new RMIDriver();
    }

    @Override
    public String getName() {
        return "RMI Driver";
    }

}
