package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

import java.util.List;

public class SPIRVContext extends TornadoLogger {

    SPIRVPlatform platform;
    List<SPIRVDevice> devices;
    long id;

    public SPIRVContext(SPIRVPlatform platform, long id, List<SPIRVDevice> devices) {
        this.platform = platform;
        this.devices = devices;
        this.id = id;
    }

}
