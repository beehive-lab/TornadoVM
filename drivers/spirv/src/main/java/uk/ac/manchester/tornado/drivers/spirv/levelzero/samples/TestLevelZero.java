package uk.ac.manchester.tornado.drivers.spirv.levelzero.samples;

import java.io.IOException;

import uk.ac.manchester.tornado.drivers.spirv.levelzero.*;

public class TestLevelZero {

    private static void errorLog(String method, int result) {
        if (result != ZeResult.ZE_RESULT_SUCCESS) {
            System.out.println("Error " + method);
        }
    }

    // Test Program
    public static void main(String[] args) throws IOException {
        System.out.println("Level-ZERO JNI Library - Test");

        // Create the Level Zero Driver
        LevelZeroDriver driver = new LevelZeroDriver();
        int result = driver.zeInit(ZeInitFlag.ZE_INIT_FLAG_GPU_ONLY);
        errorLog("zeInit", result);

        int[] numDrivers = new int[1];
        result = driver.zeDriverGet(numDrivers, null);
        errorLog("zeDriverGet", result);

        ZeDriverHandle driverHandler = new ZeDriverHandle(numDrivers[0]);

        result = driver.zeDriverGet(numDrivers, driverHandler);
        errorLog("zeDriverGet", result);

        ZeDeviceType type = ZeDeviceType.ZE_DEVICE_TYPE_GPU;

        // ============================================
        // Create the Context
        // ============================================
        // Create context Description
        ZeContextDesc contextDescription = new ZeContextDesc();
        // Create context object
        LevelZeroContext context = new LevelZeroContext(driverHandler, contextDescription);
        // Call native method for creating the context
        result = context.zeContextCreate(driverHandler.getZe_driver_handle_t_ptr()[0], 0);
        errorLog("zeContextCreate", result);

        // Get number of devices in a driver
        int[] deviceCount = new int[1];
        result = driver.zeDeviceGet(driverHandler, 0, deviceCount, null);
        errorLog("zeDeviceGet", result);

        // Instantiate a device Handler
        ZeDevicesHandle deviceHandler = new ZeDevicesHandle(deviceCount[0]);
        result = driver.zeDeviceGet(driverHandler, 0, deviceCount, deviceHandler);
        errorLog("zeDeviceGet", result);

        // ============================================
        // Query driver properties
        // ============================================
        ZeDriverProperties driverProperties = new ZeDriverProperties(Ze_Structure_Type.ZE_STRUCTURE_TYPE_DRIVER_PROPERTIES);
        result = driver.zeDriverGetProperties(driverHandler, 0, driverProperties);
        errorLog("zeDriverGetProperties", result);

        System.out.println("Driver Version: " + driverProperties.getDriverVersion());

        ZeAPIVersion apiVersion = new ZeAPIVersion();
        result = driver.zeDriverGetApiVersion(driverHandler, 0, apiVersion);
        errorLog("zeDriverGetApiVersion", result);

        System.out.println("Level Zero API Version: " + apiVersion);

        // ============================================
        // Query device properties
        // ============================================
        LevelZeroDevice device = driver.getDevice(driverHandler, 0);
        ZeDeviceProperties deviceProperties = new ZeDeviceProperties();
        result = device.zeDeviceGetProperties(device.getDeviceHandlerPtr(), deviceProperties);
        errorLog("zeDeviceGetProperties", result);
        System.out.println(deviceProperties);

        // ============================================
        // Query device compute-properties
        // ============================================
        ZeComputeProperties computeProperties = new ZeComputeProperties();
        result = device.zeDeviceGetComputeProperties(device.getDeviceHandlerPtr(), computeProperties);
        errorLog("zeDeviceGetComputeProperties", result);
        System.out.println(computeProperties);

        // ============================================
        // Query device memory
        // ============================================
        // A) Count memories
        int[] memoryCount = new int[1];
        result = device.zeDeviceGetMemoryProperties(device.getDeviceHandlerPtr(), memoryCount, null);
        errorLog("zeDeviceGetMemoryProperties", result);

        // B) Access the properties of each of the memories
        ZeMemoryProperties[] memoryProperties = new ZeMemoryProperties[memoryCount[0]];
        result = device.zeDeviceGetMemoryProperties(device.getDeviceHandlerPtr(), memoryCount, memoryProperties);
        errorLog("zeDeviceGetMemoryProperties", result);
        for (ZeMemoryProperties m : memoryProperties) {
            System.out.println(m);
        }

        // ============================================
        // Query device memory access properties
        // ============================================
        ZeMemoryAccessProperties memoryAccessProperties = new ZeMemoryAccessProperties();
        result = device.zeDeviceGetMemoryAccessProperties(device.getDeviceHandlerPtr(), memoryAccessProperties);
        errorLog("zeDeviceGetMemoryAccessProperties", result);
        System.out.println(memoryAccessProperties);

        // ============================================
        // Query device cache properties
        // ============================================
        int[] cacheCount = new int[1];
        result = device.zeDeviceGetCacheProperties(device.getDeviceHandlerPtr(), cacheCount, null);
        errorLog("zeDeviceGetCacheProperties", result);

        ZeDeviceCacheProperties[] cacheProperties = new ZeDeviceCacheProperties[cacheCount[0]];
        result = device.zeDeviceGetCacheProperties(device.getDeviceHandlerPtr(), cacheCount, cacheProperties);
        errorLog("zeDeviceGetCacheProperties", result);

        for (ZeDeviceCacheProperties c : cacheProperties) {
            System.out.println(c);
        }

        // ============================================
        // Query device image properties
        // ============================================
        ZeDeviceImageProperties imageProperties = new ZeDeviceImageProperties();
        device.zeDeviceGetImageProperties(device.getDeviceHandlerPtr(), imageProperties);
        errorLog("zeDeviceGetImageProperties", result);
        System.out.println(imageProperties);

        // ============================================
        // Create a command queue
        // ============================================
        // A) Get the number of command queue groups
        int[] numQueueGroups = new int[1];
        result = device.zeDeviceGetCommandQueueGroupProperties(device.getDeviceHandlerPtr(), numQueueGroups, null);
        errorLog("zeDeviceGetCommandQueueGroupProperties", result);

        if (numQueueGroups[0] == 0) {
            throw new RuntimeException("Number of Queue Groups is 0 for device: " + device.getDeviceProperties().getName());
        }

        ZeCommandQueueGroupProperties[] commandQueueGroupProperties = new ZeCommandQueueGroupProperties[numQueueGroups[0]];
        result = device.zeDeviceGetCommandQueueGroupProperties(device.getDeviceHandlerPtr(), numQueueGroups, commandQueueGroupProperties);
        errorLog("zeDeviceGetCommandQueueGroupProperties", result);
        for (ZeCommandQueueGroupProperties p : commandQueueGroupProperties) {
            System.out.println(p);
        }

        ZeCommandQueueHandle commandQueue = new ZeCommandQueueHandle();
        ZeCommandQueueDescription commandQueueDescription = new ZeCommandQueueDescription();

        for (int i = 0; i < numQueueGroups[0]; i++) {
            if ((commandQueueGroupProperties[i].getFlags()
                    & ZeCommandQueueGroupPropertyFlags.ZE_COMMAND_QUEUE_GROUP_PROPERTY_FLAG_COMPUTE) == ZeCommandQueueGroupPropertyFlags.ZE_COMMAND_QUEUE_GROUP_PROPERTY_FLAG_COMPUTE) {
                commandQueueDescription.setOrdinal(i);
            }
        }

        // B) Create the command queue via the context
        commandQueueDescription.setIndex(0);
        commandQueueDescription.setMode(ZeCommandQueueMode.ZE_COMMAND_QUEUE_MODE_ASYNCHRONOUS);
        // zeCommandQueueCreate(context, device, &cmdQueueDesc, &cmdQueue);
        result = context.zeCommandQueueCreate(context.getContextHandle().getContextPtr()[0], device.getDeviceHandlerPtr(), commandQueueDescription, commandQueue);
        errorLog("zeCommandQueueCreate", result);

        // ============================================
        // Create a command list
        // ============================================
        ZeCommandQueueListHandle commandList = new ZeCommandQueueListHandle();
        ZeCommandListDescription commandListDescription = new ZeCommandListDescription();
        commandListDescription.setCommandQueueGroupOrdinal(commandQueueDescription.getOrdinal());
        result = context.zeCommandListCreate(context.getContextHandle().getContextPtr()[0], device.getDeviceHandlerPtr(), commandListDescription, commandList);
        errorLog("zeCommandListCreate", result);

        final int elements = 8192;
        final int bufferSize = elements * 4;
        ZeDeviceMemAllocDesc deviceMemAllocDesc = new ZeDeviceMemAllocDesc();
        deviceMemAllocDesc.setFlags(ZeDeviceMemAllocFlags.ZE_DEVICE_MEM_ALLOC_FLAG_BIAS_UNCACHED);
        deviceMemAllocDesc.setOrdinal(0);

        ZeHostMemAllocDesc hostMemAllocDesc = new ZeHostMemAllocDesc();
        hostMemAllocDesc.setFlags(ZeHostMemAllocFlags.ZE_HOST_MEM_ALLOC_FLAG_BIAS_UNCACHED);

        LevelZeroBufferInteger bufferA = new LevelZeroBufferInteger();
        result = context.zeMemAllocShared(context.getContextHandle().getContextPtr()[0], deviceMemAllocDesc, hostMemAllocDesc, bufferSize, 1, device.getDeviceHandlerPtr(), bufferA);
        errorLog("zeMemAllocShared", result);

        LevelZeroBufferInteger bufferB = new LevelZeroBufferInteger();
        result = context.zeMemAllocShared(context.getDefaultContextPtr(), deviceMemAllocDesc, hostMemAllocDesc, bufferSize, 1, device.getDeviceHandlerPtr(), bufferB);
        errorLog("zeMemAllocShared", result);

        bufferA.memset(100, elements);
        bufferB.memset(0, elements);

        ZeModuleHandle module = new ZeModuleHandle();
        ZeModuleDesc moduleDesc = new ZeModuleDesc();
        ZeBuildLogHandle buildLog = new ZeBuildLogHandle();
        moduleDesc.setFormat(ZeModuleFormat.ZE_MODULE_FORMAT_IL_SPIRV);
        moduleDesc.setBuildFlags("");

        LevelZeroBinaryModule binaryModule = new LevelZeroBinaryModule("/tmp/example.spv");
        result = binaryModule.readBinary();
        errorLog("readBinary", result);

        result = context.zeModuleCreate(context.getDefaultContextPtr(), device.getDeviceHandlerPtr(), binaryModule, moduleDesc, module, buildLog);
        errorLog("zeModuleCreate", result);

        if (result != ZeResult.ZE_RESULT_SUCCESS) {
            // Print Logs
            int[] sizeLog = new int[1];
            String errorMessage = new String();
            result = context.zeModuleBuildLogGetString(buildLog, sizeLog, errorMessage);
            System.out.println("LOGS::: " + sizeLog[0] + "  -- " + errorMessage);
            errorLog("zeModuleBuildLogGetString", result);
        }

        // Create Module Object
        LevelZeroModule levelZeroModule = new LevelZeroModule(module, moduleDesc, buildLog);

        // Destroy Log
        result = levelZeroModule.zeModuleBuildLogDestroy(buildLog);
        errorLog("zeModuleBuildLogDestroy", result);

        ZeKernelDesc kernelDesc = new ZeKernelDesc();
        ZeKernelHandle kernel = new ZeKernelHandle();
        kernelDesc.setKernelName("copydata");
        result = levelZeroModule.zeKernelCreate(module.getPtrZeModuleHandle(), kernelDesc, kernel);
        errorLog("zeKernelCreate", result);

        // We create a kernel Object
        LevelZeroKernel levelZeroKernel = new LevelZeroKernel(kernelDesc, kernel);

        // Prepare kernel for launch
        // A) Suggest scheduling parameters to level-zero
        int[] groupSizeX = new int[] { 32 };
        int[] groupSizeY = new int[] { 1 };
        int[] groupSizeZ = new int[] { 1 };
        result = levelZeroKernel.zeKernelSuggestGroupSize(kernel.getPtrZeKernelHandle(), elements, 1, 1, groupSizeX, groupSizeY, groupSizeZ);
        errorLog("zeKernelSuggestGroupSize", result);

        result = levelZeroKernel.zeKernelSetGroupSize(kernel.getPtrZeKernelHandle(), groupSizeX, groupSizeY, groupSizeZ);
        errorLog("zeKernelSetGroupSize", result);

        result = levelZeroKernel.zeKernelSetArgumentValue(kernel.getPtrZeKernelHandle(), 0, Sizeof.POINTER.getNumBytes(), bufferA);
        result |= levelZeroKernel.zeKernelSetArgumentValue(kernel.getPtrZeKernelHandle(), 1, Sizeof.POINTER.getNumBytes(), bufferB);
        errorLog("zeKernelSetArgumentValue", result);

        // Dispatch SPIR-V Kernel
        ZeGroupDispatch dispatch = new ZeGroupDispatch();
        dispatch.setGroupCountX(elements / groupSizeX[0]);
        dispatch.setGroupCountY(1);
        dispatch.setGroupCountZ(1);

        // Launch the kernel on the Intel Integrated GPU
        result = levelZeroKernel.zeCommandListAppendLaunchKernel(commandList.getPtrZeCommandListHandle(), kernel.getPtrZeKernelHandle(), dispatch, null, 0, null);
        errorLog("zeCommandListAppendLaunchKernel", result);

        result = levelZeroKernel.zeCommandListClose(commandList.getPtrZeCommandListHandle());
        errorLog("zeCommandListClose", result);

        result = levelZeroKernel.zeCommandQueueExecuteCommandLists(commandQueue.getCommandQueueHandlerPointer(), 1, commandList.getPtrZeCommandListHandle(), null);
        errorLog("zeCommandQueueExecuteCommandLists", result);

        result = levelZeroKernel.zeCommandQueueSynchronize(commandQueue.getCommandQueueHandlerPointer(), Long.MAX_VALUE);
        errorLog("zeCommandQueueSynchronize", result);

        boolean isEqual = bufferA.isEqual(bufferB, elements);
        if (isEqual) {
            System.out.println("Result is correct");
        } else {
            System.out.println("Result is wrong");
        }

        result = context.zeMemFree(context.getDefaultContextPtr(), bufferA);
        result |= context.zeMemFree(context.getDefaultContextPtr(), bufferB);
        errorLog("zeMemFree", result);

        result = context.zeCommandListDestroy(commandList);
        errorLog("zeCommandListDestroy", result);

        result = context.zeCommandQueueDestroy(commandQueue);
        errorLog("zeCommandQueueDestroy", result);

        result = driver.zeContextDestroy(context);
        errorLog("zeContextDestroy", result);

    }
}
