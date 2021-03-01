package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.drivers.opencl.OCLTargetDevice;
import uk.ac.manchester.tornado.drivers.opencl.OpenCL;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLDeviceType;

import java.nio.ByteOrder;

public class SPIRVOCLDevice extends SPIRVDevice {

    // Holds a reference to the OpenCL device implementation from the OpenCL
    // backend. It reuses the JNI low level code from the OpenCL Backend.
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
    public String getName() {
        return "SPIRV OCL - " + device.getDeviceName();
    }

    @Override
    public OCLTargetDevice getDevice() {
        return device;
    }

    @Override
    public String getDeviceName() {
        return device.getDeviceName();
    }

    @Override
    public long getDeviceGlobalMemorySize() {
        return device.getDeviceGlobalMemorySize();
    }

    @Override
    public long getDeviceLocalMemorySize() {
        return device.getDeviceLocalMemorySize();
    }

    @Override
    public int getDeviceMaxComputeUnits() {
        return device.getDeviceMaxComputeUnits();
    }

    @Override
    public long[] getDeviceMaxWorkItemSizes() {
        return device.getDeviceMaxWorkItemSizes();
    }

    @Override
    public long[] getDeviceMaxWorkGroupSize() {
        return device.getDeviceMaxWorkGroupSize();
    }

    @Override
    public int getDeviceMaxClockFrequency() {
        return device.getDeviceMaxClockFrequency();
    }

    @Override
    public long getDeviceMaxConstantBufferSize() {
        return device.getDeviceMaxConstantBufferSize();
    }

    @Override
    public long getDeviceMaxAllocationSize() {
        return device.getDeviceMaxAllocationSize();
    }

    @Override
    public String getDeviceInfo() {
        throw new UnsupportedOperationException("");
    }

    @Override
    public long[] getDeviceMaxWorkgroupDimensions() {
        return device.getDeviceMaxWorkItemSizes();
    }

    @Override
    public String getDeviceOpenCLCVersion() {
        return device.getDeviceOpenCLCVersion();
    }

    @Override
    public long getMaxAllocMemory() {
        return device.getDeviceMaxAllocationSize();
    }

    @Override
    public TornadoDeviceType getTornadoDeviceType() {
        OCLDeviceType type = device.getDeviceType();
        switch (type) {
            case CL_DEVICE_TYPE_CPU:
                return TornadoDeviceType.CPU;
            case CL_DEVICE_TYPE_GPU:
                return TornadoDeviceType.GPU;
            case CL_DEVICE_TYPE_ACCELERATOR:
                return TornadoDeviceType.FPGA;
            case CL_DEVICE_TYPE_ALL:
                return TornadoDeviceType.DEFAULT;
        }
        return null;
    }

    @Override
    public String getPlatformName() {
        return OpenCL.getPlatform(getPlatformIndex()).getName();
    }

}
