package uk.ac.manchester.tornado.drivers.spirv.levelzero.samples;

import static uk.ac.manchester.tornado.drivers.spirv.levelzero.utils.LevelZeroUtils.zeGetDevices;

import java.io.IOException;
import java.util.Arrays;

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
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDeviceMemAllocDesc;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDeviceMemAllocFlags;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeGroupDispatch;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeKernelDesc;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeKernelHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeModuleDesc;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeModuleFormat;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeModuleHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeResult;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.utils.LevelZeroUtils;

/**
 * Kernel to test:
 * 
 * <code>
 *    __kernel void copydata(__global long* input, __global long* output) {
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
 *     $ cp opencl-copy.spv /tmp/copyLong.spv
 * </code>
 * 
 * How to run?
 * 
 * <code>
 *     $ tornado uk.ac.manchester.tornado.drivers.spirv.levelzero.samples.TestLevelZeroDedicatedMemoryLong
 * </code>
 * 
 */
public class TestLevelZeroDedicatedMemoryLong {

    public static void main(String[] args) throws IOException {

        LevelZeroDriver driver = new LevelZeroDriver();
        LevelZeroContext context = LevelZeroUtils.zeInitContext(driver);
        LevelZeroDevice device = zeGetDevices(context, driver);

        LevelZeroCommandQueue commandQueue = LevelZeroUtils.createCommandQueue(device, context);
        LevelZeroCommandList commandList = LevelZeroUtils.createCommandList(device, context, commandQueue.getCommandQueueDescription().getOrdinal());

        final int elements = 256;
        final int bufferSize = elements * 8;
        ZeDeviceMemAllocDesc deviceMemAllocDesc = new ZeDeviceMemAllocDesc();
        deviceMemAllocDesc.setFlags(ZeDeviceMemAllocFlags.ZE_DEVICE_MEM_ALLOC_FLAG_BIAS_UNCACHED);
        deviceMemAllocDesc.setOrdinal(0);

        // Fill heap buffer (Java side)
        long[] input = new long[elements];
        for (int i = 0; i < elements; i++) {
            input[i] = Long.MAX_VALUE - 1;
        }
        long[] output = new long[elements];

        LevelZeroByteBuffer bufferA = new LevelZeroByteBuffer();
        int result = context.zeMemAllocDevice(context.getDefaultContextPtr(), deviceMemAllocDesc, bufferSize, 1, device.getDeviceHandlerPtr(), bufferA);
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

        result = context.zeModuleCreate(context.getDefaultContextPtr(), device.getDeviceHandlerPtr(), moduleDesc, module, buildLog, "/tmp/copyLong.spv");
        LevelZeroUtils.errorLog("zeModuleCreate", result);

        if (result != ZeResult.ZE_RESULT_SUCCESS) {
            // Print Logs
            int[] sizeLog = new int[1];
            String[] errorMessage = new String[1];
            result = context.zeModuleBuildLogGetString(buildLog, sizeLog, errorMessage);
            System.out.println("LOGS::: " + sizeLog[0] + "  -- " + errorMessage[1]);
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
        LevelZeroKernel levelZeroKernel = new LevelZeroKernel(kernelDesc, kernel, levelZeroModule);

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
        result = commandList.zeCommandListAppendLaunchKernel(commandList.getCommandListHandlerPtr(), kernel.getPtrZeKernelHandle(), dispatch, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendLaunchKernel", result);

        result = commandList.zeCommandListAppendBarrier(commandList.getCommandListHandlerPtr(), null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendBarrier", result);

        // Copy From Device-Allocated memory to host (heapBuffer2)
        result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), output, bufferB, bufferSize, 0, 0, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendMemoryCopy", result);

        result = commandList.zeCommandListClose(commandList.getCommandListHandlerPtr());
        LevelZeroUtils.errorLog("zeCommandListClose", result);

        result = commandQueue.zeCommandQueueExecuteCommandLists(commandQueue.getCommandQueueHandlerPtr(), 1, commandList.getCommandListHandler(), null);
        LevelZeroUtils.errorLog("zeCommandQueueExecuteCommandLists", result);

        result = commandQueue.zeCommandQueueSynchronize(commandQueue.getCommandQueueHandlerPtr(), Long.MAX_VALUE);
        LevelZeroUtils.errorLog("zeCommandQueueSynchronize", result);

        System.out.println("OUTPUT: " + Arrays.toString(output));

        // Clean-up
        result = context.zeMemFree(context.getDefaultContextPtr(), bufferA);
        result |= context.zeMemFree(context.getDefaultContextPtr(), bufferB);
        LevelZeroUtils.errorLog("zeMemFree", result);

        result = context.zeCommandListDestroy(commandList.getCommandListHandler());
        LevelZeroUtils.errorLog("zeCommandListDestroy", result);

        result = context.zeCommandQueueDestroy(commandQueue.getCommandQueueHandle());
        LevelZeroUtils.errorLog("zeCommandQueueDestroy", result);

        result = driver.zeContextDestroy(context);
        LevelZeroUtils.errorLog("zeContextDestroy", result);

    }
}
