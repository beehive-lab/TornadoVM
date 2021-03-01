package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

import java.util.List;

public abstract class SPIRVContext extends TornadoLogger {

    SPIRVPlatform platform;
    List<SPIRVDevice> devices;

    public SPIRVContext(SPIRVPlatform platform, List<SPIRVDevice> devices) {
        this.platform = platform;
        this.devices = devices;

    }

    public abstract SPIRVDeviceContext getDeviceContext(int deviceIndex);

}
