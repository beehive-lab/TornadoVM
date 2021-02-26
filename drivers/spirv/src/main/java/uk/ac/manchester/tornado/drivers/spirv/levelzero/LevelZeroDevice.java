package uk.ac.manchester.tornado.drivers.spirv.levelzero;

import java.util.stream.IntStream;

public class LevelZeroDevice {

    private LevelZeroDriver driver;
    private ZeDriverHandle driverHandle;
    private int deviceIndex;
    private long deviceHandlerPtr;
    private ZeDeviceProperties deviceProperties;

    public LevelZeroDevice(LevelZeroDriver driver, ZeDriverHandle driverHandler, int deviceIndex, long deviceHandlerPointer) {
        this.driver = driver;
        this.driverHandle = driverHandler;
        this.deviceIndex = deviceIndex;
        this.deviceHandlerPtr = deviceHandlerPointer;
    }

    public int getDeviceIndex() {
        return deviceIndex;
    }

    public long getDeviceHandlerPtr() {
        return deviceHandlerPtr;
    }

    native int zeDeviceGetProperties_native(long deviceHandlerPtr, ZeDeviceProperties deviceProperties);

    public int zeDeviceGetProperties(long deviceHandlerPtr, ZeDeviceProperties deviceProperties) {
        int result = zeDeviceGetProperties_native(deviceHandlerPtr, deviceProperties);
        this.deviceProperties = deviceProperties;
        return result;
    }

    public ZeDeviceProperties getDeviceProperties() {
        return deviceProperties;
    }

    public native int zeDeviceGetComputeProperties(long deviceHandlerPtr, ZeComputeProperties computeProperties);

    public native int zeDeviceGetImageProperties(long deviceHandlerPtr, ZeDeviceImageProperties imageProperties);

    native int zeDeviceGetMemoryProperties_native(long deviceHandlerPtr, int[] memoryCount, ZeMemoryProperties[] memoryProperties);

    public int zeDeviceGetMemoryProperties(long deviceHandlerPtr, int[] memoryCount, ZeMemoryProperties[] memoryProperties) {
        if (memoryProperties != null) {
            // Initialize properties
            IntStream.range(0, memoryCount[0]).forEach(i -> memoryProperties[i] = new ZeMemoryProperties());
        }
        int result = zeDeviceGetMemoryProperties_native(deviceHandlerPtr, memoryCount, memoryProperties);
        return result;
    }

    public native int zeDeviceGetMemoryAccessProperties(long deviceHandlerPtr, ZeMemoryAccessProperties memoryAccessProperties);

    native int zeDeviceGetCacheProperties_native(long deviceHandlerPtr, int[] cacheCount, ZeDeviceCacheProperties[] cacheProperties);

    public int zeDeviceGetCacheProperties(long deviceHandlerPtr, int[] cacheCount, ZeDeviceCacheProperties[] cacheProperties) {
        if (cacheProperties != null) {
            // Initialize properties
            IntStream.range(0, cacheCount[0]).forEach(i -> cacheProperties[i] = new ZeDeviceCacheProperties());
        }
        return zeDeviceGetCacheProperties_native(deviceHandlerPtr, cacheCount, cacheProperties);
    }

    native int zeDeviceGetCommandQueueGroupProperties_native(long deviceHandlerPtr, int[] numQueueGroups, ZeCommandQueueGroupProperties[] commandQueueGroupProperties);

    public int zeDeviceGetCommandQueueGroupProperties(long deviceHandlerPtr, int[] numQueueGroups, ZeCommandQueueGroupProperties[] commandQueueGroupProperties) {
        if (commandQueueGroupProperties != null) {
            // Initialize properties
            IntStream.range(0, numQueueGroups[0]).forEach(i -> commandQueueGroupProperties[i] = new ZeCommandQueueGroupProperties());
        }
        return zeDeviceGetCommandQueueGroupProperties_native(deviceHandlerPtr, numQueueGroups, commandQueueGroupProperties);
    }

    public String getDeviceExtensions() {
        return null;
    }

    public LevelZeroDriver getDriver() {
        return this.driver;
    }

    public ZeDriverHandle getDriverHandler() {
        return this.driverHandle;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("SPIRV LEVELZERO Device");
        return sb.toString();
    }

}
