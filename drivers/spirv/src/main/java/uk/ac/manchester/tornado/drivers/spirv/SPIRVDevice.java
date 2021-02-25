package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.tornado.drivers.opencl.OCLTargetDevice;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDevice;

import java.nio.ByteOrder;

public abstract class SPIRVDevice {

    private String name;
    private int platformIndex;
    private int deviceIndex;
    private SPIRVContext context;

    public SPIRVDevice(int platformIndex, int deviceIndex) {
        this.platformIndex = platformIndex;
        this.deviceIndex = deviceIndex;
    }

    public SPIRVContext getSPIRVContext() {
        return context;
    }

    public abstract boolean isDeviceDoubleFPSupported();

    public abstract String getDeviceExtensions();

    public abstract ByteOrder getByteOrder();

    private OCLTargetDevice oclDevice;

    public int getDeviceIndex() {
        return deviceIndex;
    }

    public int getPlatformIndex() {
        return platformIndex;
    }

    public String getName() {
        return name;
    }

    public abstract Object getDevice();
}
