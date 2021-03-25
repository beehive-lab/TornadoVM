package uk.ac.manchester.tornado.drivers.spirv.levelzero.samples;

import java.io.IOException;
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
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandListHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandQueueDescription;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandQueueGroupProperties;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandQueueGroupPropertyFlags;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandQueueHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandQueueMode;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeContextDesc;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDeviceMemAllocDesc;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDeviceMemAllocFlags;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDeviceType;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDevicesHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDriverHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeGroupDispatch;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeHostMemAllocDesc;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeHostMemAllocFlags;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeInitFlag;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeKernelDesc;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeKernelHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeModuleDesc;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeModuleFormat;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeModuleHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeResult;

/**
 * Kernel to test:
 * 
 * <code>
 *    __kernel void copydata(__global int* input, __global int* output) {
 * 	         uint idx = get_global_id(0);
 * 	         output[idx] = input[idx];
 *    }      
 * </code>
 * 
 * 
 * To compile to SPIR-V:
 * 
 * <code>
 *     $ clang -cc1 -triple spir opencl-copy.cl -O0 -finclude-default-header -emit-llvm-bc -o opencl-copy.bc
 *     $ llvm-spirv opencl-copy.bc -o opencl-copy.spv
 *     $ cp opencl-copy.spv /tmp/copy.spv
 * </code>
 * 
 */
public class TestLevelZeroDedicatedMemory {

    // Test Program
    public static void main(String[] args) throws IOException {
        System.out.println("Level-ZERO JNI Library - Test");

        // Create the Level Zero Driver
        LevelZeroDriver driver = new LevelZeroDriver();
        int result = driver.zeInit(ZeInitFlag.ZE_INIT_FLAG_GPU_ONLY);
        LevelZeroUtils.errorLog("zeInit", result);

        int[] numDrivers = new int[1];
        result = driver.zeDriverGet(numDrivers, null);
        LevelZeroUtils.errorLog("zeDriverGet", result);

        ZeDriverHandle driverHandler = new ZeDriverHandle(numDrivers[0]);

        result = driver.zeDriverGet(numDrivers, driverHandler);
        LevelZeroUtils.errorLog("zeDriverGet", result);

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
        LevelZeroUtils.errorLog("zeContextCreate", result);

        // Get number of devices in a driver
        int[] deviceCount = new int[1];
        result = driver.zeDeviceGet(driverHandler, 0, deviceCount, null);
        LevelZeroUtils.errorLog("zeDeviceGet", result);

        // Instantiate a device Handler
        ZeDevicesHandle deviceHandler = new ZeDevicesHandle(deviceCount[0]);
        result = driver.zeDeviceGet(driverHandler, 0, deviceCount, deviceHandler);
        LevelZeroUtils.errorLog("zeDeviceGet", result);

        // ============================================
        // Query device properties
        // ============================================
        LevelZeroDevice device = driver.getDevice(driverHandler, 0);

        // ============================================
        // Create a command queue
        // ============================================
        // A) Get the number of command queue groups
        int[] numQueueGroups = new int[1];
        result = device.zeDeviceGetCommandQueueGroupProperties(device.getDeviceHandlerPtr(), numQueueGroups, null);
        LevelZeroUtils.errorLog("zeDeviceGetCommandQueueGroupProperties", result);

        if (numQueueGroups[0] == 0) {
            throw new RuntimeException("Number of Queue Groups is 0 for device: " + device.getDeviceProperties().getName());
        }

        ZeCommandQueueGroupProperties[] commandQueueGroupProperties = new ZeCommandQueueGroupProperties[numQueueGroups[0]];
        result = device.zeDeviceGetCommandQueueGroupProperties(device.getDeviceHandlerPtr(), numQueueGroups, commandQueueGroupProperties);
        LevelZeroUtils.errorLog("zeDeviceGetCommandQueueGroupProperties", result);

        ZeCommandQueueHandle commandQueueHandle = new ZeCommandQueueHandle();
        LevelZeroCommandQueue commandQueue = new LevelZeroCommandQueue(context, commandQueueHandle);
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
        result = context.zeCommandQueueCreate(context.getContextHandle().getContextPtr()[0], device.getDeviceHandlerPtr(), commandQueueDescription, commandQueueHandle);
        LevelZeroUtils.errorLog("zeCommandQueueCreate", result);

        // ============================================
        // Create a command list
        // ============================================
        ZeCommandListHandle zeCommandListHandler = new ZeCommandListHandle();
        LevelZeroCommandList commandList = new LevelZeroCommandList(context, zeCommandListHandler);
        ZeCommandListDescription commandListDescription = new ZeCommandListDescription();
        commandListDescription.setCommandQueueGroupOrdinal(commandQueueDescription.getOrdinal());
        result = context.zeCommandListCreate(context.getContextHandle().getContextPtr()[0], device.getDeviceHandlerPtr(), commandListDescription, zeCommandListHandler);
        LevelZeroUtils.errorLog("zeCommandListCreate", result);

        final int elements = 256;
        final int bufferSize = elements * 4;
        ZeDeviceMemAllocDesc deviceMemAllocDesc = new ZeDeviceMemAllocDesc();
        deviceMemAllocDesc.setFlags(ZeDeviceMemAllocFlags.ZE_DEVICE_MEM_ALLOC_FLAG_BIAS_UNCACHED);
        deviceMemAllocDesc.setOrdinal(0);

        ZeHostMemAllocDesc hostMemAllocDesc = new ZeHostMemAllocDesc();
        hostMemAllocDesc.setFlags(ZeHostMemAllocFlags.ZE_HOST_MEM_ALLOC_FLAG_BIAS_UNCACHED);

        // Fill heap buffer (Java side)
        int[] input = new int[elements];
        for (int i = 0; i < elements; i++) {
            input[i] = 150;
        }
        int[] output = new int[elements];

        LevelZeroByteBuffer bufferA = new LevelZeroByteBuffer();
        result = context.zeMemAllocDevice(context.getDefaultContextPtr(), deviceMemAllocDesc, bufferSize, 1, device.getDeviceHandlerPtr(), bufferA);
        LevelZeroUtils.errorLog("zeMemAllocDevice", result);

        LevelZeroByteBuffer bufferB = new LevelZeroByteBuffer();
        result = context.zeMemAllocDevice(context.getDefaultContextPtr(), deviceMemAllocDesc, bufferSize, 1, device.getDeviceHandlerPtr(), bufferB);
        LevelZeroUtils.errorLog("zeMemAllocDevice", result);

        // Copy from HEAP -> Device Allocated Memory
        result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), bufferA, input, bufferSize, 0, 0, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendMemoryCopyWithOffset", result);
        result = commandList.zeCommandListAppendBarrier(commandList.getCommandListHandlerPtr(), null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendBarrier", result);

        ZeModuleHandle module = new ZeModuleHandle();
        ZeModuleDesc moduleDesc = new ZeModuleDesc();
        ZeBuildLogHandle buildLog = new ZeBuildLogHandle();
        moduleDesc.setFormat(ZeModuleFormat.ZE_MODULE_FORMAT_IL_SPIRV);
        moduleDesc.setBuildFlags("");

        LevelZeroBinaryModule binaryModule = new LevelZeroBinaryModule("/tmp/copy.spv");
        result = binaryModule.readBinary();
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
            System.exit(0);
        }

        // Create Module Object
        LevelZeroModule levelZeroModule = new LevelZeroModule(module, moduleDesc, buildLog);

        // Destroy Log
        result = levelZeroModule.zeModuleBuildLogDestroy(buildLog);
        LevelZeroUtils.errorLog("zeModuleBuildLogDestroy", result);

        ZeKernelDesc kernelDesc = new ZeKernelDesc();
        ZeKernelHandle kernel = new ZeKernelHandle();
        kernelDesc.setKernelName("copydata");
        result = levelZeroModule.zeKernelCreate(module.getPtrZeModuleHandle(), kernelDesc, kernel);
        LevelZeroUtils.errorLog("zeKernelCreate", result);

        // We create a kernel Object
        LevelZeroKernel levelZeroKernel = new LevelZeroKernel(kernelDesc, kernel);

        // Prepare kernel for launch
        // A) Suggest scheduling parameters to level-zero
        int[] groupSizeX = new int[] { 1 };
        int[] groupSizeY = new int[] { 1 };
        int[] groupSizeZ = new int[] { 1 };
        result = levelZeroKernel.zeKernelSuggestGroupSize(kernel.getPtrZeKernelHandle(), elements, 1, 1, groupSizeX, groupSizeY, groupSizeZ);
        LevelZeroUtils.errorLog("zeKernelSuggestGroupSize", result);

        result = levelZeroKernel.zeKernelSetGroupSize(kernel.getPtrZeKernelHandle(), groupSizeX, groupSizeY, groupSizeZ);
        LevelZeroUtils.errorLog("zeKernelSetGroupSize", result);

        result = levelZeroKernel.zeKernelSetArgumentValue(kernel.getPtrZeKernelHandle(), 0, Sizeof.POINTER.getNumBytes(), bufferA.getPtrBuffer());
        result |= levelZeroKernel.zeKernelSetArgumentValue(kernel.getPtrZeKernelHandle(), 1, Sizeof.POINTER.getNumBytes(), bufferB.getPtrBuffer());
        LevelZeroUtils.errorLog("zeKernelSetArgumentValue", result);

        // Dispatch SPIR-V Kernel
        ZeGroupDispatch dispatch = new ZeGroupDispatch();
        dispatch.setGroupCountX(elements);
        dispatch.setGroupCountY(1);
        dispatch.setGroupCountZ(1);

        // Launch the kernel on the Intel Integrated GPU
        result = commandList.zeCommandListAppendLaunchKernel(zeCommandListHandler.getPtrZeCommandListHandle(), kernel.getPtrZeKernelHandle(), dispatch, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendLaunchKernel", result);

        result = commandList.zeCommandListAppendBarrier(commandList.getCommandListHandlerPtr(), null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendBarrier", result);

        // Copy From Device-Allocated memory to host (heapBuffer2)
        result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), output, bufferB, bufferSize, 0, 0, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendMemoryCopy", result);

        result = commandList.zeCommandListClose(zeCommandListHandler.getPtrZeCommandListHandle());
        LevelZeroUtils.errorLog("zeCommandListClose", result);

        result = commandQueue.zeCommandQueueExecuteCommandLists(commandQueueHandle.getCommandQueueHandlerPointer(), 1, zeCommandListHandler, null);
        LevelZeroUtils.errorLog("zeCommandQueueExecuteCommandLists", result);

        result = commandQueue.zeCommandQueueSynchronize(commandQueueHandle.getCommandQueueHandlerPointer(), Long.MAX_VALUE);
        LevelZeroUtils.errorLog("zeCommandQueueSynchronize", result);

        System.out.println("OUTPUT: " + Arrays.toString(output));

        // Clean-up
        result = context.zeMemFree(context.getDefaultContextPtr(), bufferA);
        result |= context.zeMemFree(context.getDefaultContextPtr(), bufferB);
        LevelZeroUtils.errorLog("zeMemFree", result);

        result = context.zeCommandListDestroy(zeCommandListHandler);
        LevelZeroUtils.errorLog("zeCommandListDestroy", result);

        result = context.zeCommandQueueDestroy(commandQueueHandle);
        LevelZeroUtils.errorLog("zeCommandQueueDestroy", result);

        result = driver.zeContextDestroy(context);
        LevelZeroUtils.errorLog("zeContextDestroy", result);

    }
}
