package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.tornado.api.TornadoTargetDevice;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;

import java.nio.ByteOrder;

public abstract class SPIRVDevice implements TornadoTargetDevice {

    private int platformIndex;
    private int deviceIndex;
    private SPIRVDeviceContext context;

    public SPIRVDevice(int platformIndex, int deviceIndex) {
        this.platformIndex = platformIndex;
        this.deviceIndex = deviceIndex;
    }

    public SPIRVContext getSPIRVContext() {
        return this.context.getSpirvContext();
    }

    public abstract boolean isDeviceDoubleFPSupported();

    public abstract String getDeviceExtensions();

    public abstract ByteOrder getByteOrder();

    public int getDeviceIndex() {
        return deviceIndex;
    }

    public int getPlatformIndex() {
        return platformIndex;
    }

    public abstract String getName();

    public abstract Object getDevice();

    public abstract long getDeviceGlobalMemorySize();

    public abstract long getDeviceLocalMemorySize();

    public abstract long[] getDeviceMaxWorkgroupDimensions();

    public abstract String getDeviceOpenCLCVersion();

    public abstract long getMaxAllocMemory();

    public abstract TornadoDeviceType getTornadoDeviceType();

    public abstract String getPlatformName();

}
