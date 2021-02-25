package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDevice;

import java.nio.ByteOrder;

public class SPIRVLevelZeroDevice extends SPIRVDevice {

    LevelZeroDevice device;

    public SPIRVLevelZeroDevice(int platformIndex, int deviceIndex, LevelZeroDevice device) {
        super(platformIndex, deviceIndex);
        this.device = device;
    }

    @Override
    public ByteOrder getByteOrder() {
        return ByteOrder.LITTLE_ENDIAN;
    }

    @Override
    public boolean isDeviceDoubleFPSupported() {
        return true;
    }

    @Override
    public String getDeviceExtensions() {
        return device.getDeviceExtensions();
    }

    @Override
    public LevelZeroDevice getDevice() {
        return device;
    }
}
