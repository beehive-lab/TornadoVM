package uk.ac.manchester.tornado.drivers.spirv;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import org.graalvm.compiler.options.OptionValues;
import uk.ac.manchester.tornado.runtime.TornadoAcceleratorDriver;
import uk.ac.manchester.tornado.runtime.TornadoDriverProvider;
import uk.ac.manchester.tornado.runtime.TornadoVMConfig;
import uk.ac.manchester.tornado.runtime.common.enums.TornadoDrivers;

public class SPIRVTornadoDriver implements TornadoDriverProvider {

    private final TornadoDrivers priority = TornadoDrivers.SPIRV;

    @Override
    public String getName() {
        return "SPIRV Driver";
    }

    @Override
    public TornadoAcceleratorDriver createDriver(OptionValues options, HotSpotJVMCIRuntime hostRuntime, TornadoVMConfig config) {
        return new SPIRVDriver(options, hostRuntime, config);
    }

    @Override
    public TornadoDrivers getDevicePriority() {
        return priority;
    }

    @Override
    public int compareTo(TornadoDriverProvider o) {
        return o.getDevicePriority().value() - priority.value();
    }
}