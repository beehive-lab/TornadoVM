package uk.ac.manchester.tornado.drivers.spirv;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVHotSpotBackendFactory;
import uk.ac.manchester.tornado.runtime.TornadoAcceleratorDriver;
import uk.ac.manchester.tornado.runtime.TornadoVMConfig;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.graal.backend.TornadoBackend;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSuitesProvider;

public final class SPIRVDriver extends TornadoLogger implements TornadoAcceleratorDriver {

    private final SPIRVBackend[][] backends;

    public SPIRVDriver(OptionValues options, HotSpotJVMCIRuntime vmRuntime, TornadoVMConfig vmCon) {
        int numSPIRVPlatforms = SPIRVProxy.getNumPlatforms();
        info("[SPIRV] Found %d platforms", numSPIRVPlatforms);

        if (numSPIRVPlatforms < 1) {
            throw new TornadoBailoutRuntimeException("[Warning] No SPIRV platforms found. Deoptimizing to sequential execution");
        }

        backends = new SPIRVBackend[numSPIRVPlatforms][];
        discoverDevices(options, vmRuntime, vmCon, numSPIRVPlatforms);
    }

    private void discoverDevices(OptionValues options, HotSpotJVMCIRuntime vmRuntime, TornadoVMConfig vmCon, int numPlatforms) {
        for (int platformIndex = 0; platformIndex < numPlatforms; platformIndex++) {
            SPIRVPlatform platform = SPIRVProxy.getPlatform(platformIndex);
            int numDevices = platform.getNumDevices();
            backends[platformIndex] = new SPIRVBackend[numDevices];
            for (int deviceIndex = 0; deviceIndex < numDevices; deviceIndex++) {
                SPIRVDevice device = platform.getDevice(deviceIndex);
                backends[platformIndex][deviceIndex] = createSPIRVBackend(options, vmRuntime, vmCon, device);
            }
        }
    }

    private SPIRVBackend createSPIRVBackend(OptionValues options, HotSpotJVMCIRuntime vmRuntime, TornadoVMConfig vmConfig, SPIRVDevice device) {
        return SPIRVHotSpotBackendFactory.createBackend(options, vmRuntime, vmConfig, device);
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
        return "SPIRV";
    }

    @Override
    public int getNumPlatforms() {
        return 0;
    }
}
