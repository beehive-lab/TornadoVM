package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.tornado.drivers.spirv.levelzero.*;

import java.nio.ByteOrder;

public class SPIRVLevelZeroDevice extends SPIRVDevice {

    private LevelZeroDevice device;
    private String deviceName;
    private ZeMemoryProperties[] memoryProperties;
    private ZeDeviceProperties deviceProperties;
    private ZeComputeProperties computeProperties;
    ZeAPIVersion apiVersion;

    private long totalMemorySize;

    public SPIRVLevelZeroDevice(int platformIndex, int deviceIndex, LevelZeroDevice device) {
        super(platformIndex, deviceIndex);
        this.device = device;
        this.deviceName = getDeviceName();
        this.totalMemorySize = getTotalGlobalMemory();
        getDeviceComputeProperties();
        getDriverVersion();
    }

    private String getDeviceName() {
        deviceProperties = new ZeDeviceProperties();
        int result = device.zeDeviceGetProperties(device.getDeviceHandlerPtr(), deviceProperties);
        errorLog("zeDeviceGetProperties", result);
        return deviceProperties.getName();
    }

    private void getDeviceComputeProperties() {
        computeProperties = new ZeComputeProperties();
        int result = device.zeDeviceGetComputeProperties(device.getDeviceHandlerPtr(), computeProperties);
        errorLog("zeDeviceGetComputeProperties", result);
    }

    private long getTotalGlobalMemory() {
        // A) Count memories
        int[] memoryCount = new int[1];
        int result = device.zeDeviceGetMemoryProperties(device.getDeviceHandlerPtr(), memoryCount, null);
        errorLog("zeDeviceGetMemoryProperties", result);

        // B) Access the properties of each of the memories
        memoryProperties = new ZeMemoryProperties[memoryCount[0]];
        result = device.zeDeviceGetMemoryProperties(device.getDeviceHandlerPtr(), memoryCount, memoryProperties);
        errorLog("zeDeviceGetMemoryProperties", result);

        long memorySize = 0;
        for (ZeMemoryProperties m : memoryProperties) {
            memorySize += m.getTotalSize();
        }
        return memorySize;
    }

    private void getDriverVersion() {
        apiVersion = new ZeAPIVersion();
        LevelZeroDriver driver = device.getDriver();
        ZeDriverHandle driverHandler = device.getDriverHandler();
        int result = driver.zeDriverGetApiVersion(driverHandler, 0, apiVersion);
        errorLog("zeDriverGetApiVersion", result);
    }

    private static void errorLog(String method, int result) {
        if (result != ZeResult.ZE_RESULT_SUCCESS) {
            System.out.println("Error " + method);
        }
    }

    @Override
    public ByteOrder getByteOrder() {
        return ByteOrder.LITTLE_ENDIAN;
    }

    @Override
    public String getName() {
        return "SPIRV LevelZero - " + deviceName;
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

    @Override
    public long getDeviceGlobalMemorySize() {
        return totalMemorySize;
    }

    @Override
    public long getDeviceLocalMemorySize() {
        return computeProperties.getMaxSharedLocalMemory();
    }

    @Override
    public long[] getDeviceMaxWorkgroupDimensions() {
        long[] maxWorkGroup = new long[3];
        maxWorkGroup[0] = computeProperties.getMaxGroupSizeX();
        maxWorkGroup[1] = computeProperties.getMaxGroupSizeY();
        maxWorkGroup[2] = computeProperties.getMaxGroupSizeZ();
        return maxWorkGroup;
    }

    @Override
    public String getDeviceOpenCLCVersion() {
        return " (LEVEL ZERO) " + apiVersion.getAPIVersion();
    }
}
