package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.tornado.drivers.opencl.OCLTargetDevice;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDevice;

import java.nio.ByteOrder;

public class SPIRVDevice {

    private String name;
    private int platformIndex;
    private int deviceIndex;
    private LevelZeroDevice l0Device;
    private SPIRVContext context;

    public ByteOrder getByteOrder() {
        switch (dispatcher) {
            case OpenCL:
                return oclDevice.getByteOrder();
            case LevelZero:
                // SPIRV is always Little ENDIAN
                return ByteOrder.LITTLE_ENDIAN;
        }
        return null;
    }

    public SPIRVContext getSPIRVContext() {
        return context;
    }

    public boolean isDeviceDoubleFPSupported() {
        switch (dispatcher) {
            case OpenCL:
                return oclDevice.isDeviceDoubleFPSupported();
            case LevelZero:
                return true;
        }
        return false;
    }

    public String getDeviceExtensions() {
        switch (dispatcher) {
            case OpenCL:
                return oclDevice.getDeviceExtensions();
            case LevelZero:
                return l0Device.getDeviceExtensions();
        }
        return "";
    }

    public enum SPIRV_DISPATCHER {
        OpenCL, LevelZero;
    }

    private SPIRV_DISPATCHER dispatcher;

    private OCLTargetDevice oclDevice;

    public SPIRVDevice(int platformIndex, int deviceIndex, OCLTargetDevice device) {
        this.platformIndex = platformIndex;
        this.deviceIndex = deviceIndex;
        this.oclDevice = device;
        this.dispatcher = SPIRV_DISPATCHER.LevelZero;
    }

    public SPIRVDevice(int platformIndex, int deviceIndex, LevelZeroDevice device) {
        this.platformIndex = platformIndex;
        this.deviceIndex = deviceIndex;
        this.dispatcher = SPIRV_DISPATCHER.LevelZero;
        this.l0Device = device;
    }

    public SPIRV_DISPATCHER getDispatcher() {
        return dispatcher;
    }

    public LevelZeroDevice getL0Device() {
        return l0Device;
    }

    public int getDeviceIndex() {
        return deviceIndex;
    }

    public int getPlatformIndex() {
        return platformIndex;
    }

    public String getName() {
        return name;
    }
}
