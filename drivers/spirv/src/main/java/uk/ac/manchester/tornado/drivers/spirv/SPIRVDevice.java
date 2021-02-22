package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDevice;

public class SPIRVDevice {

    private String name;
    private int platformIndex;
    private int deviceIndex;
    private LevelZeroDevice l0Device;

    public enum SPIRV_DISPATCHER {
        OpenCL, LevelZero;
    }

    private SPIRV_DISPATCHER dispatcher;

    public SPIRVDevice(int platformIndex, int deviceIndex) {
        this.platformIndex = platformIndex;
        this.deviceIndex = deviceIndex;
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
