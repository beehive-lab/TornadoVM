package uk.ac.manchester.tornado.drivers.spirv;

import java.util.List;

public abstract class SPIRVContext {

    SPIRVPlatform platform;
    List<SPIRVDevice> devices;

    public SPIRVContext(SPIRVPlatform platform, List<SPIRVDevice> devices) {
        this.platform = platform;
        this.devices = devices;

    }

    public abstract SPIRVDeviceContext getDeviceContext(int deviceIndex);

    public abstract long allocateMemory(long numBytes);
}
