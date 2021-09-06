package uk.ac.manchester.tornado.drivers.spirv;

import java.nio.ByteOrder;

import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDevice;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDriver;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeAPIVersion;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeComputeProperties;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDeviceModuleFlags;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDeviceModuleProperties;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDeviceProperties;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDeviceType;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDriverHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeMemoryProperties;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeResult;

public class SPIRVLevelZeroDevice extends SPIRVDevice {

    private LevelZeroDevice device;
    private String deviceName;
    private ZeMemoryProperties[] memoryProperties;
    private ZeDeviceProperties deviceProperties;
    private ZeComputeProperties computeProperties;
    ZeAPIVersion apiVersion;

    private long totalMemorySize;

    private boolean queriedSupportFP64;
    private ZeDeviceModuleProperties moduleProperties;

    public SPIRVLevelZeroDevice(int platformIndex, int deviceIndex, LevelZeroDevice device) {
        super(platformIndex, deviceIndex);
        this.device = device;
        this.totalMemorySize = getTotalGlobalMemory();
        initDeviceProperties();
        initDeviceComputeProperties();
        initDriverVersion();
    }

    private void initDeviceProperties() {
        deviceProperties = new ZeDeviceProperties();
        int result = device.zeDeviceGetProperties(device.getDeviceHandlerPtr(), deviceProperties);
        errorLog("zeDeviceGetProperties", result);
        deviceName = deviceProperties.getName();
    }

    private void initDeviceComputeProperties() {
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

    private void initDriverVersion() {
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
        if (!queriedSupportFP64) {
            moduleProperties = new ZeDeviceModuleProperties();
            int result = device.zeDeviceGetModuleProperties(device.getDeviceHandlerPtr(), moduleProperties);
            errorLog("zeDeviceGetModuleProperties", result);
            queriedSupportFP64 = true;
        }
        int flags = moduleProperties.getFlags();
        return (ZeDeviceModuleFlags.ZE_DEVICE_MODULE_FLAG_FP64 & flags) == ZeDeviceModuleFlags.ZE_DEVICE_MODULE_FLAG_FP64;
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
    public String getDeviceName() {
        return deviceProperties.getName();
    }

    @Override
    public long getDeviceGlobalMemorySize() {
        return totalMemorySize;
    }

    @Override
    public long getDeviceLocalMemorySize() {
        return computeProperties.getMaxSharedLocalMemory();
    }

    // FIXME - Not sure this is the max of compute UNITS
    @Override
    public int getDeviceMaxComputeUnits() {
        return deviceProperties.getNumEUsPerSubslice();
    }

    /**
     * Return max thread for each dimension
     * 
     * @return
     */
    @Override
    public long[] getDeviceMaxWorkItemSizes() {
        return getDeviceMaxWorkgroupDimensions();
    }

    // FIXME
    @Override
    public long[] getDeviceMaxWorkGroupSize() {
        return new long[0];
    }

    @Override
    public int getDeviceMaxClockFrequency() {
        return deviceProperties.getCoreClockRate();
    }

    // FIXME
    @Override
    public long getDeviceMaxConstantBufferSize() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public long getDeviceMaxAllocationSize() {
        return deviceProperties.getMaxMemAllocSize();
    }

    @Override
    public String getDeviceInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(deviceProperties.toString() + "\n");
        sb.append(computeProperties.toString() + "\n");
        sb.append(memoryProperties.toString() + "\n");
        return sb.toString();
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

    @Override
    public long getMaxAllocMemory() {
        return deviceProperties.getMaxMemAllocSize();
    }

    @Override
    public TornadoDeviceType getTornadoDeviceType() {
        ZeDeviceType type = deviceProperties.getType();
        if (type == ZeDeviceType.ZE_DEVICE_TYPE_GPU) {
            return TornadoDeviceType.GPU;
        } else if (type == ZeDeviceType.ZE_DEVICE_TYPE_FPGA) {
            return TornadoDeviceType.FPGA;
        } else if (type == ZeDeviceType.ZE_DEVICE_TYPE_CPU) {
            return TornadoDeviceType.CPU;
        }
        return null;
    }

    @Override
    public String getPlatformName() {
        return "LevelZero";
    }

}
