package uk.ac.manchester.tornado.drivers.spirv;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.options.OptionValues;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.runtime.TornadoAcceleratorDriver;
import uk.ac.manchester.tornado.runtime.TornadoVMConfig;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.graal.backend.TornadoBackend;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSuitesProvider;

public final class SPIRVDriver extends TornadoLogger implements TornadoAcceleratorDriver {

    public SPIRVDriver(OptionValues options, HotSpotJVMCIRuntime vmRuntime, TornadoVMConfig config) {
        int numSPIRVPlatforms = SPIRVProxy.getNumPlatforms();

    }

    @Override
    public TornadoBackend getDefaultBackend() {
        return null;
    }

    @Override
    public Providers getProviders() {
        return null;
    }

    @Override
    public TornadoSuitesProvider getSuitesProvider() {
        return null;
    }

    @Override
    public TornadoDevice getDefaultDevice() {
        return null;
    }

    @Override
    public void setDefaultDevice(int index) {

    }

    @Override
    public int getDeviceCount() {
        return 0;
    }

    @Override
    public TornadoDevice getDevice(int index) {
        return null;
    }

    @Override
    public TornadoDeviceType getTypeDefaultDevice() {
        return null;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public int getNumPlatforms() {
        return 0;
    }
}
