package uk.ac.manchester.tornado.drivers.spirv.levelzero.samples;

import uk.ac.manchester.tornado.drivers.spirv.levelzero.*;

public class TestCopies {

    public static LevelZeroContext zeInitContext(LevelZeroDriver driver) {
        if (driver == null) {
            return null;
        }

        int result = driver.zeInit(ZeInitFlag.ZE_INIT_FLAG_GPU_ONLY);
        LevelZeroUtils.errorLog("zeInit", result);

        int[] numDrivers = new int[1];
        result = driver.zeDriverGet(numDrivers, null);
        LevelZeroUtils.errorLog("zeDriverGet", result);

        ZeDriverHandle driverHandler = new ZeDriverHandle(numDrivers[0]);
        result = driver.zeDriverGet(numDrivers, driverHandler);
        LevelZeroUtils.errorLog("zeDriverGet", result);

        ZeContextDesc contextDescription = new ZeContextDesc();
        contextDescription.setSType(Ze_Structure_Type.ZE_STRUCTURE_TYPE_CONTEXT_DESC);
        LevelZeroContext context = new LevelZeroContext(driverHandler, contextDescription);
        result = context.zeContextCreate(driverHandler.getZe_driver_handle_t_ptr()[0], 0);
        LevelZeroUtils.errorLog("zeContextCreate", result);
        return context;
    }

    public static LevelZeroDevice zeGetDevices(LevelZeroContext context, LevelZeroDriver driver) {

        ZeDriverHandle driverHandler = context.getDriver();

        // Get number of devices in a driver
        int[] deviceCount = new int[1];
        int result = driver.zeDeviceGet(driverHandler, 0, deviceCount, null);
        LevelZeroUtils.errorLog("zeDeviceGet", result);

        // Instantiate a device Handler
        ZeDevicesHandle deviceHandler = new ZeDevicesHandle(deviceCount[0]);
        result = driver.zeDeviceGet(driverHandler, 0, deviceCount, deviceHandler);
        LevelZeroUtils.errorLog("zeDeviceGet", result);

        // ============================================
        // Get the device
        // ============================================
        LevelZeroDevice device = driver.getDevice(driverHandler, 0);
        ZeDeviceProperties deviceProperties = new ZeDeviceProperties();
        result = device.zeDeviceGetProperties(device.getDeviceHandlerPtr(), deviceProperties);
        LevelZeroUtils.errorLog("zeDeviceGetProperties", result);
        return device;
    }

    public static int getCommandQueueOrdinal(LevelZeroDevice device) {
        int[] numQueueGroups = new int[1];
        int result = device.zeDeviceGetCommandQueueGroupProperties(device.getDeviceHandlerPtr(), numQueueGroups, null);
        LevelZeroUtils.errorLog("zeDeviceGetCommandQueueGroupProperties", result);

        if (numQueueGroups[0] == 0) {
            throw new RuntimeException("Number of Queue Groups is 0 for device: " + device.getDeviceProperties().getName());
        }
        int ordinal = numQueueGroups[0];

        ZeCommandQueueGroupProperties[] commandQueueGroupProperties = new ZeCommandQueueGroupProperties[numQueueGroups[0]];
        result = device.zeDeviceGetCommandQueueGroupProperties(device.getDeviceHandlerPtr(), numQueueGroups, commandQueueGroupProperties);
        LevelZeroUtils.errorLog("zeDeviceGetCommandQueueGroupProperties", result);

        for (int i = 0; i < numQueueGroups[0]; i++) {
            if ((commandQueueGroupProperties[i].getFlags()
                    & ZeCommandQueueGroupPropertyFlags.ZE_COMMAND_QUEUE_GROUP_PROPERTY_FLAG_COMPUTE) == ZeCommandQueueGroupPropertyFlags.ZE_COMMAND_QUEUE_GROUP_PROPERTY_FLAG_COMPUTE) {
                ordinal = i;
                break;
            }
        }
        return ordinal;
    }

    public static ZeCommandQueueHandle createCommandQueue(LevelZeroContext context, LevelZeroDevice device) {
        // Create Command Queue
        ZeCommandQueueDescription cmdDescriptor = new ZeCommandQueueDescription();
        cmdDescriptor.setFlags(0);
        cmdDescriptor.setMode(ZeCommandQueueMode.ZE_COMMAND_QUEUE_MODE_DEFAULT);
        cmdDescriptor.setPriority(ZeCommandQueuePriority.ZE_COMMAND_QUEUE_PRIORITY_NORMAL);
        cmdDescriptor.setOrdinal(getCommandQueueOrdinal(device));
        cmdDescriptor.setIndex(0);

        ZeCommandQueueHandle commandQueue = new ZeCommandQueueHandle();
        int result = context.zeCommandQueueCreate(context.getContextHandle().getContextPtr()[0], device.getDeviceHandlerPtr(), cmdDescriptor, commandQueue);
        LevelZeroUtils.errorLog("zeCommandQueueCreate", result);

        return commandQueue;
    }

    public static ZeCommandQueueListHandle createCommandList(LevelZeroContext context, LevelZeroDevice device) {
        ZeCommandListDescription cmdListDescriptor = new ZeCommandListDescription();
        cmdListDescriptor.setFlags(0);
        cmdListDescriptor.setCommandQueueGroupOrdinal(getCommandQueueOrdinal(device));
        ZeCommandQueueListHandle commandList = new ZeCommandQueueListHandle();
        int result = context.zeCommandListCreate(context.getContextHandle().getContextPtr()[0], device.getDeviceHandlerPtr(), cmdListDescriptor, commandList);
        LevelZeroUtils.errorLog("zeCommandListCreate", result);
        return commandList;
    }

    public static boolean testAppendMemoryCopyFromHeapToDeviceToHeap(LevelZeroContext context, LevelZeroDevice device) {

        final int allocSize = 4096;
        char[] heapBuffer = new char[allocSize];

        LevelZeroByteBuffer deviceBuffer = new LevelZeroByteBuffer();
        char[] heapBuffer2 = new char[allocSize];

        ZeCommandQueueHandle commandQueue = createCommandQueue(context, device);
        ZeCommandQueueListHandle commandList = createCommandList(context, device);

        ZeDeviceMemAllocDesc deviceMemAllocDesc = new ZeDeviceMemAllocDesc();
        deviceMemAllocDesc.setOrdinal(0);
        deviceMemAllocDesc.setFlags(0);
        int alignment = 1;

        // This is the equivalent of a clCreateBuffer
        int result = context.zeMemAllocDevice(context.getContextHandle().getContextPtr()[0], deviceMemAllocDesc, allocSize, alignment, device.getDeviceHandlerPtr(), deviceBuffer);
        LevelZeroUtils.errorLog("zeMemAllocDevice", result);

        return false;
    }

    public static void main(String[] args) {

        LevelZeroDriver driver = new LevelZeroDriver();
        LevelZeroContext context = zeInitContext(driver);
        LevelZeroDevice device = zeGetDevices(context, driver);

        ZeDeviceProperties deviceProperties = device.getDeviceProperties();
        System.out.println("Device: ");
        System.out.println("\tName     : " + deviceProperties.getName());
        System.out.println("\tVendor ID: " + Integer.toHexString(deviceProperties.getVendorId()));

        boolean isValid = testAppendMemoryCopyFromHeapToDeviceToHeap(context, device);

    }
}
