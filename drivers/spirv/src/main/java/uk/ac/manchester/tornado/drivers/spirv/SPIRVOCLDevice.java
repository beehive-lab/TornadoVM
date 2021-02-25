package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.tornado.drivers.opencl.OCLTargetDevice;

import java.nio.ByteOrder;

public class SPIRVOCLDevice extends SPIRVDevice {

    private OCLTargetDevice device;

    public SPIRVOCLDevice(int platformIndex, int deviceIndex, OCLTargetDevice device) {
        super(platformIndex, deviceIndex);
        this.device = device;
    }

    @Override
    public boolean isDeviceDoubleFPSupported() {
        return device.isDeviceDoubleFPSupported();
    }

    @Override
    public String getDeviceExtensions() {
        return device.getDeviceExtensions();
    }

    @Override
    public ByteOrder getByteOrder() {
        return device.getByteOrder();
    }

    @Override
    public OCLTargetDevice getDevice() {
        return device;
    }
}
