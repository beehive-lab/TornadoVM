package uk.ac.manchester.tornado.drivers.spirv.levelzero.samples;

import java.util.Arrays;

import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroBinaryModule;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroByteBuffer;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroCommandList;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroCommandQueue;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroContext;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDevice;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDriver;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroKernel;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroModule;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.Sizeof;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeBuildLogHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandListDescription;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandListFlag;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandListHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandQueueDescription;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandQueueGroupProperties;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandQueueGroupPropertyFlags;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandQueueHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandQueueMode;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandQueuePriority;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeContextDesc;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDeviceMemAllocDesc;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDeviceProperties;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDevicesHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDriverHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeGroupDispatch;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeInitFlag;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeKernelDesc;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeKernelHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeModuleDesc;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeModuleFormat;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeModuleHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeResult;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.Ze_Structure_Type;

/**
 * How to run?
 * 
 * <code>
 *     $ tornado uk.ac.manchester.tornado.drivers.spirv.levelzero.samples.TestLookUpBufferAddress
 * </code>
 */
public class TestLookUpBufferAddress {

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

    public static LevelZeroCommandQueue createCommandQueue(LevelZeroContext context, LevelZeroDevice device) {
        // Create Command Queue
        ZeCommandQueueDescription cmdDescriptor = new ZeCommandQueueDescription();
        cmdDescriptor.setFlags(0);
        cmdDescriptor.setMode(ZeCommandQueueMode.ZE_COMMAND_QUEUE_MODE_DEFAULT);
        cmdDescriptor.setPriority(ZeCommandQueuePriority.ZE_COMMAND_QUEUE_PRIORITY_NORMAL);
        cmdDescriptor.setOrdinal(getCommandQueueOrdinal(device));
        cmdDescriptor.setIndex(0);

        ZeCommandQueueHandle zeCommandQueueHandle = new ZeCommandQueueHandle();
        int result = context.zeCommandQueueCreate(context.getContextHandle().getContextPtr()[0], device.getDeviceHandlerPtr(), cmdDescriptor, zeCommandQueueHandle);
        LevelZeroUtils.errorLog("zeCommandQueueCreate", result);
        return new LevelZeroCommandQueue(context, zeCommandQueueHandle);
    }

    public static LevelZeroCommandList createCommandList(LevelZeroContext context, LevelZeroDevice device) {
        ZeCommandListDescription cmdListDescriptor = new ZeCommandListDescription();
        cmdListDescriptor.setFlags(ZeCommandListFlag.ZE_COMMAND_LIST_FLAG_RELAXED_ORDERING);
        cmdListDescriptor.setCommandQueueGroupOrdinal(getCommandQueueOrdinal(device));
        ZeCommandListHandle commandListHandler = new ZeCommandListHandle();
        int result = context.zeCommandListCreate(context.getContextHandle().getContextPtr()[0], device.getDeviceHandlerPtr(), cmdListDescriptor, commandListHandler);
        LevelZeroUtils.errorLog("zeCommandListCreate", result);
        return new LevelZeroCommandList(context, commandListHandler);
    }

    private static void testLookUpBufferAddress(LevelZeroContext context, LevelZeroDevice device) {

        LevelZeroCommandQueue commandQueue = createCommandQueue(context, device);
        LevelZeroCommandList commandList = createCommandList(context, device);
        int allocSize = 16;
        long[] heapBuffer2 = new long[2];

        Arrays.fill(heapBuffer2, -1);

        ZeModuleHandle module = new ZeModuleHandle();
        ZeModuleDesc moduleDesc = new ZeModuleDesc();
        ZeBuildLogHandle buildLog = new ZeBuildLogHandle();
        moduleDesc.setFormat(ZeModuleFormat.ZE_MODULE_FORMAT_IL_SPIRV);
        moduleDesc.setBuildFlags("");

        LevelZeroBinaryModule binaryModule = new LevelZeroBinaryModule("/home/juan/manchester/tornado/tornado/assembly/src/bin/spirv/lookUpBufferAddress.spv");
        int result = binaryModule.readBinary();
        LevelZeroUtils.errorLog("readBinary", result);

        result = context.zeModuleCreate(context.getDefaultContextPtr(), device.getDeviceHandlerPtr(), binaryModule, moduleDesc, module, buildLog);
        LevelZeroUtils.errorLog("zeModuleCreate", result);

        if (result != ZeResult.ZE_RESULT_SUCCESS) {
            // Print Logs
            int[] sizeLog = new int[1];
            String errorMessage = "";
            result = context.zeModuleBuildLogGetString(buildLog, sizeLog, errorMessage);
            System.out.println("LOGS::: " + sizeLog[0] + "  -- " + errorMessage);
            LevelZeroUtils.errorLog("zeModuleBuildLogGetString", result);
        }

        // Create Module Object
        LevelZeroModule levelZeroModule = new LevelZeroModule(module, moduleDesc, buildLog);

        // Destroy Log
        result = levelZeroModule.zeModuleBuildLogDestroy(buildLog);
        LevelZeroUtils.errorLog("zeModuleBuildLogDestroy", result);

        ZeKernelDesc kernelDesc = new ZeKernelDesc();
        ZeKernelHandle kernel = new ZeKernelHandle();
        kernelDesc.setKernelName("lookupBufferAddress");
        result = levelZeroModule.zeKernelCreate(module.getPtrZeModuleHandle(), kernelDesc, kernel);
        LevelZeroUtils.errorLog("zeKernelCreate", result);

        // We create a kernel Object
        LevelZeroKernel levelZeroKernel = new LevelZeroKernel(kernelDesc, kernel);

        // Prepare kernel for launch
        // A) Suggest scheduling parameters to level-zero
        int[] groupSizeX = new int[] { 1 };
        int[] groupSizeY = new int[] { 1 };
        int[] groupSizeZ = new int[] { 1 };
        result = levelZeroKernel.zeKernelSuggestGroupSize(kernel.getPtrZeKernelHandle(), 1, 1, 1, groupSizeX, groupSizeY, groupSizeZ);
        LevelZeroUtils.errorLog("zeKernelSuggestGroupSize", result);

        result = levelZeroKernel.zeKernelSetGroupSize(kernel.getPtrZeKernelHandle(), groupSizeX, groupSizeY, groupSizeZ);
        LevelZeroUtils.errorLog("zeKernelSetGroupSize", result);

        ZeDeviceMemAllocDesc deviceMemAllocDesc = new ZeDeviceMemAllocDesc();
        deviceMemAllocDesc.setOrdinal(0);
        deviceMemAllocDesc.setFlags(0);
        LevelZeroByteBuffer deviceBuffer = new LevelZeroByteBuffer();

        // This is the equivalent of a clCreateBuffer
        result = context.zeMemAllocDevice(context.getContextHandle().getContextPtr()[0], deviceMemAllocDesc, allocSize * 10, allocSize * 10, device.getDeviceHandlerPtr(), deviceBuffer);
        LevelZeroUtils.errorLog("zeMemAllocDevice", result);
        result = commandList.zeCommandListAppendBarrier(commandList.getCommandListHandlerPtr(), null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendBarrier", result);

        result = levelZeroKernel.zeKernelSetArgumentValue(kernel.getPtrZeKernelHandle(), 0, Sizeof.POINTER.getNumBytes(), deviceBuffer.getPtrBuffer());
        LevelZeroUtils.errorLog("zeKernelSetArgumentValue", result);

        // Dispatch SPIR-V Kernel
        ZeGroupDispatch dispatch = new ZeGroupDispatch();
        dispatch.setGroupCountX(10);
        dispatch.setGroupCountY(1);
        dispatch.setGroupCountZ(1);

        // Launch the kernel on the Intel Integrated GPU
        result = commandList.zeCommandListAppendLaunchKernel(commandList.getCommandListHandlerPtr(), kernel.getPtrZeKernelHandle(), dispatch, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendLaunchKernel", result);

        result = commandList.zeCommandListAppendBarrier(commandList.getCommandListHandlerPtr(), null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendBarrier", result);

        // Copy From Device-Allocated memory to host (heapBuffer2)
        result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), heapBuffer2, deviceBuffer, allocSize, 0, 0, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendMemoryCopy", result);

        result = commandList.zeCommandListAppendBarrier(commandList.getCommandListHandlerPtr(), null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendBarrier", result);

        // Close the command list
        result = commandList.zeCommandListClose(commandList.getCommandListHandlerPtr());
        LevelZeroUtils.errorLog("zeCommandListClose", result);
        result = commandQueue.zeCommandQueueExecuteCommandLists(commandQueue.getCommandQueueHandlerPtr(), 1, commandList.getCommandListHandler(), null);
        LevelZeroUtils.errorLog("zeCommandQueueExecuteCommandLists", result);
        result = commandQueue.zeCommandQueueSynchronize(commandQueue.getCommandQueueHandlerPtr(), Long.MAX_VALUE);
        LevelZeroUtils.errorLog("zeCommandQueueSynchronize", result);

        System.out.println("RESULT: " + Arrays.toString(heapBuffer2));

        // Free resources
        result = context.zeMemFree(context.getDefaultContextPtr(), deviceBuffer);
        LevelZeroUtils.errorLog("zeMemFree", result);
        result = context.zeCommandListDestroy(commandList.getCommandListHandler());
        LevelZeroUtils.errorLog("zeCommandListDestroy", result);
        result = context.zeCommandQueueDestroy(commandQueue.getCommandQueueHandle());
        LevelZeroUtils.errorLog("zeCommandQueueDestroy", result);
    }

    /**
     * Run as follows:
     * 
     * <code>
     *     $ tornado uk.ac.manchester.tornado.drivers.spirv.levelzero.samples.TestLookUpBufferAddress
     * </code>
     * 
     * @param args
     */
    public static void main(String[] args) {
        LevelZeroDriver driver = new LevelZeroDriver();
        LevelZeroContext context = zeInitContext(driver);
        LevelZeroDevice device = zeGetDevices(context, driver);

        ZeDeviceProperties deviceProperties = device.getDeviceProperties();
        System.out.println("Device: ");
        System.out.println("\tName     : " + deviceProperties.getName());
        System.out.println("\tVendor ID: " + Integer.toHexString(deviceProperties.getVendorId()));
        testLookUpBufferAddress(context, device);

        int result = driver.zeContextDestroy(context);
        LevelZeroUtils.errorLog("zeContextDestroy", result);
    }

}
