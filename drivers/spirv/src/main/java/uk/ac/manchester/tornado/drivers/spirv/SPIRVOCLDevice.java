package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.tornado.drivers.opencl.OCLTargetDevice;

import java.nio.ByteOrder;

public class SPIRVOCLDevice extends SPIRVDevice {

    private OCLTargetDevice device;
    private long totalGlobalMemory;

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
    public String getName() {
        System.err.println("[SPIRVOCLDevice] OCL NAME UNIMPLEMENTED");
        return null;
    }

    @Override
    public OCLTargetDevice getDevice() {
        return device;
    }

    @Override
    public long getDeviceGlobalMemorySize() {
        System.err.println("[SPIRVOCLDevice] UNIMPLEMENTED");
        return -1;
    }

    @Override
    public long getDeviceLocalMemorySize() {
        System.err.println("[SPIRVOCLDevice] UNIMPLEMENTED getDeviceLocalMemorySize");
        return 0;
    }

    @Override
    public long[] getDeviceMaxWorkgroupDimensions() {
        System.err.println("[SPIRVOCLDevice] UNIMPLEMENTED getDeviceMaxWorkgroupDimensions");
        return new long[0];
    }

    @Override
    public String getDeviceOpenCLCVersion() {
        System.err.println("[SPIRVOCLDevice] UNIMPLEMENTED getDeviceOpenCLCVersion");
        return null;
    }
}
