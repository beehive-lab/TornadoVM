package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
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
        throw new UnsupportedOperationException("getName");
    }

    @Override
    public OCLTargetDevice getDevice() {
        return device;
    }

    @Override
    public long getDeviceGlobalMemorySize() {
        throw new UnsupportedOperationException("getDeviceGlobalMemorySize");
    }

    @Override
    public long getDeviceLocalMemorySize() {
        throw new UnsupportedOperationException("getDeviceLocalMemorySize");
    }

    @Override
    public long[] getDeviceMaxWorkgroupDimensions() {
        throw new UnsupportedOperationException("getDeviceMaxWorkgroupDimensions");
    }

    @Override
    public String getDeviceOpenCLCVersion() {
        throw new UnsupportedOperationException("getDeviceOpenCLCVersion");
    }

    @Override
    public long getMaxAllocMemory() {
        throw new UnsupportedOperationException("getMaxAllocMemory");
    }

    @Override
    public TornadoDeviceType getTornadoDeviceType() {
        throw new UnsupportedOperationException("getTornadoDeviceType");
    }
}
