/*
 * MIT License
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package uk.ac.manchester.tornado.drivers.spirv.levelzero.utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandListDescriptor;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandListFlag;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandListHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandQueueDescriptor;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandQueueGroupProperties;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandQueueGroupPropertyFlags;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandQueueHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandQueueMode;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandQueuePriority;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeContextDescriptor;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDeviceProperties;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDevicesHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDriverHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeGroupDispatch;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeInitFlag;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeKernelDescriptor;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeKernelHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeModuleDescriptor;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeModuleFormat;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeModuleHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeResult;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.Ze_Structure_Type;

public class LevelZeroUtils {

     public static final boolean DEBUG = Boolean.parseBoolean(System.getProperties().getProperty("tornado.debug", "False"));
     public static final String YELLOW = "\u001B[33m";
     public static final String RESET = "\u001B[0m";

    /**
     * Utility for controlling error from a method invoked using the JNI Level Zero
     * library.
     *
     * @param method
     *            Method called.
     * @param result
     *            Value obtained from last call to JNI cade.
     */
    public static void errorLog(String method, int result) {
        if (result != ZeResult.ZE_RESULT_SUCCESS) {
            System.out.println("Error Code (hex): " + Integer.toHexString(result) + " Error-Decimal: " + result + " in method:" + method);
        }
    }

    /**
     * Utility for creating a Level Zero Context.
     *
     * @param driver
     *            {@link LevelZeroDriver}
     * @return {@link LevelZeroContext}
     */
    public static LevelZeroContext zeInitContext(LevelZeroDriver driver) {
        if (driver == null) {
            return null;
        }

        int result = driver.zeInit(ZeInitFlag.ZE_INIT_FLAG_GPU_ONLY);
        errorLog("zeInit", result);

        int[] numDrivers = new int[1];
        result = driver.zeDriverGet(numDrivers, null);
        errorLog("zeDriverGet", result);

        ZeDriverHandle driverHandler = new ZeDriverHandle(numDrivers[0]);
        result = driver.zeDriverGet(numDrivers, driverHandler);
        errorLog("zeDriverGet", result);

        ZeContextDescriptor contextDescription = new ZeContextDescriptor();
        contextDescription.setSType(Ze_Structure_Type.ZE_STRUCTURE_TYPE_CONTEXT_DESC);
        LevelZeroContext context = new LevelZeroContext(driverHandler, contextDescription);
        result = context.zeContextCreate(driverHandler.getZe_driver_handle_t_ptr()[0]);
        errorLog("zeContextCreate", result);
        return context;
    }

    /**
     * Utility for instantiating a {@link LevelZeroDevice}.
     *
     * @param context
     *            {@link LevelZeroContext}
     * @param driver
     *            {@link LevelZeroDriver}
     * @return {@link LevelZeroDevice}
     */
    public static LevelZeroDevice zeGetDevices(LevelZeroContext context, LevelZeroDriver driver) {

        ZeDriverHandle driverHandler = context.getDriver();

        // Get number of devices in a driver
        int[] deviceCount = new int[1];
        int result = driver.zeDeviceGet(driverHandler, 0, deviceCount, null);
        errorLog("zeDeviceGet", result);

        // Instantiate a device Handler
        ZeDevicesHandle deviceHandler = new ZeDevicesHandle(deviceCount[0]);
        result = driver.zeDeviceGet(driverHandler, 0, deviceCount, deviceHandler);
        errorLog("zeDeviceGet", result);

        // ============================================
        // Get the device
        // ============================================
        LevelZeroDevice device = driver.getDevice(driverHandler, 0);
        ZeDeviceProperties deviceProperties = new ZeDeviceProperties();
        result = device.zeDeviceGetProperties(device.getDeviceHandlerPtr(), deviceProperties);
        errorLog("zeDeviceGetProperties", result);
        return device;
    }

    /**
     * Utility for creating a Level Zero Command Queue.
     *
     * @param device
     *            {@link LevelZeroDevice}
     * @param context
     *            {@link LevelZeroContext}
     * @return {@link LevelZeroCommandQueue}
     */
    public static LevelZeroCommandQueue createCommandQueue(LevelZeroDevice device, LevelZeroContext context) {
        // A) Get the number of command queue groups
        int[] numQueueGroups = new int[1];
        int result = device.zeDeviceGetCommandQueueGroupProperties(device.getDeviceHandlerPtr(), numQueueGroups, null);
        errorLog("zeDeviceGetCommandQueueGroupProperties", result);

        if (numQueueGroups[0] == 0) {
            throw new RuntimeException("Number of Queue Groups is 0 for device: " + device.getDeviceProperties().getName());
        }

        ZeCommandQueueGroupProperties[] commandQueueGroupProperties = new ZeCommandQueueGroupProperties[numQueueGroups[0]];
        result = device.zeDeviceGetCommandQueueGroupProperties(device.getDeviceHandlerPtr(), numQueueGroups, commandQueueGroupProperties);
        errorLog("zeDeviceGetCommandQueueGroupProperties", result);

        ZeCommandQueueDescriptor commandQueueDescription = new ZeCommandQueueDescriptor();

        for (int i = 0; i < numQueueGroups[0]; i++) {
            if ((commandQueueGroupProperties[i].getFlags()
                    & ZeCommandQueueGroupPropertyFlags.ZE_COMMAND_QUEUE_GROUP_PROPERTY_FLAG_COMPUTE) == ZeCommandQueueGroupPropertyFlags.ZE_COMMAND_QUEUE_GROUP_PROPERTY_FLAG_COMPUTE) {
                commandQueueDescription.setOrdinal(i);
            }
        }

        // B) Create the command queue via the context
        ZeCommandQueueHandle cmdDescriptor = new ZeCommandQueueHandle();
        commandQueueDescription.setIndex(0);
        commandQueueDescription.setMode(ZeCommandQueueMode.ZE_COMMAND_QUEUE_MODE_ASYNCHRONOUS);
        commandQueueDescription.setPriority(ZeCommandQueuePriority.ZE_COMMAND_QUEUE_PRIORITY_NORMAL);
        commandQueueDescription.setFlags(0);
        result = context.zeCommandQueueCreate(context.getContextHandle().getContextPtr()[0], device.getDeviceHandlerPtr(), commandQueueDescription, cmdDescriptor);
        errorLog("zeCommandQueueCreate", result);
        LevelZeroCommandQueue commandQueue = new LevelZeroCommandQueue(context, cmdDescriptor, commandQueueDescription);
        return commandQueue;
    }

    /**
     * Utility for creating a Level Zero Command List.
     *
     * @param device
     *            {@link LevelZeroDevice}
     * @param context
     *            {@link LevelZeroContext}
     * @param ordinal
     *            Ordinal used for the command queue creation
     * @return {@link LevelZeroCommandList}
     */
    public static LevelZeroCommandList createCommandList(LevelZeroDevice device, LevelZeroContext context, long ordinal) {
        ZeCommandListHandle zeCommandListHandler = new ZeCommandListHandle();
        ZeCommandListDescriptor commandListDescription = new ZeCommandListDescriptor();
        commandListDescription.setFlags(ZeCommandListFlag.ZE_COMMAND_LIST_FLAG_RELAXED_ORDERING);
        commandListDescription.setCommandQueueGroupOrdinal(ordinal);
        int result = context.zeCommandListCreate(context.getContextHandle().getContextPtr()[0], device.getDeviceHandlerPtr(), commandListDescription, zeCommandListHandler);
        errorLog("zeCommandListCreate", result);
        return new LevelZeroCommandList(context, zeCommandListHandler);
    }

    public static LevelZeroKernel compileSPIRVKernel(LevelZeroDevice device, LevelZeroContext context, String kernelName, String pathToBinary) {
        ZeModuleHandle module = new ZeModuleHandle();
        ZeModuleDescriptor moduleDesc = new ZeModuleDescriptor();
        ZeBuildLogHandle buildLog = new ZeBuildLogHandle();
        moduleDesc.setFormat(ZeModuleFormat.ZE_MODULE_FORMAT_IL_SPIRV);
        moduleDesc.setBuildFlags("");

        int result = context.zeModuleCreate(context.getDefaultContextPtr(), device.getDeviceHandlerPtr(), moduleDesc, module, buildLog, pathToBinary);
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

        ZeKernelDescriptor kernelDesc = new ZeKernelDescriptor();
        ZeKernelHandle kernel = new ZeKernelHandle();
        kernelDesc.setKernelName(kernelName);
        result = levelZeroModule.zeKernelCreate(module.getPtrZeModuleHandle(), kernelDesc, kernel);
        LevelZeroUtils.errorLog("zeKernelCreate", result);

        return new LevelZeroKernel(kernelDesc, kernel, levelZeroModule);
    }

    /**
     * Dispatch the LookUpBufferKernel.
     *
     * @param commandList
     *            {@link LevelZeroCommandList}
     * @param commandQueue
     *            {@link LevelZeroCommandQueue}
     * @param levelZeroKernel
     *            {@link LevelZeroByteBuffer
     * @param deviceBuffer
     *            {@link LevelZeroByteBuffer}
     * @param output
     *            Long array with the results
     * @param bufferSize
     *
     * @return Long value with a valid address for the device (base address).
     */
    public static long dispatchLookUpBuffer(LevelZeroCommandList commandList, LevelZeroCommandQueue commandQueue, LevelZeroKernel levelZeroKernel, LevelZeroByteBuffer deviceBuffer, long[] output,
            int bufferSize) {

        int result = commandList.zeCommandListReset(commandList.getCommandListHandlerPtr());
        LevelZeroUtils.errorLog("zeCommandListReset", result);

        ZeKernelHandle kernel = levelZeroKernel.getKernelHandle();

        // Prepare kernel for launch
        // A) Suggest scheduling parameters to level-zero
        int[] groupSizeX = new int[] { 1 };
        int[] groupSizeY = new int[] { 1 };
        int[] groupSizeZ = new int[] { 1 };
        result = levelZeroKernel.zeKernelSuggestGroupSize(kernel.getPtrZeKernelHandle(), 1, 1, 1, groupSizeX, groupSizeY, groupSizeZ);
        LevelZeroUtils.errorLog("zeKernelSuggestGroupSize", result);

        result = levelZeroKernel.zeKernelSetGroupSize(kernel.getPtrZeKernelHandle(), groupSizeX, groupSizeY, groupSizeZ);
        LevelZeroUtils.errorLog("zeKernelSetGroupSize", result);

        result = levelZeroKernel.zeKernelSetArgumentValue(kernel.getPtrZeKernelHandle(), 0, Sizeof.POINTER.getNumBytes(), deviceBuffer.getPtrBuffer());
        LevelZeroUtils.errorLog("zeKernelSetArgumentValue", result);

        result = levelZeroKernel.zeKernelSetArgumentValue(kernel.getPtrZeKernelHandle(), 1, Sizeof.POINTER.getNumBytes(), 0);
        LevelZeroUtils.errorLog("zeKernelSetArgumentValue", result);

        result = levelZeroKernel.zeKernelSetArgumentValue(kernel.getPtrZeKernelHandle(), 2, Sizeof.POINTER.getNumBytes(), null);
        LevelZeroUtils.errorLog("zeKernelSetArgumentValue", result);

        // Dispatch SPIR-V Kernel
        ZeGroupDispatch dispatch = new ZeGroupDispatch();
        dispatch.setGroupCountX(1);
        dispatch.setGroupCountY(1);
        dispatch.setGroupCountZ(1);

        // Launch the kernel on the Intel Integrated GPU
        result = commandList.zeCommandListAppendLaunchKernel(commandList.getCommandListHandlerPtr(), kernel.getPtrZeKernelHandle(), dispatch, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendLaunchKernel", result);

        result = commandList.zeCommandListAppendBarrier(commandList.getCommandListHandlerPtr(), null, 0, null);
        errorLog("zeCommandListAppendBarrier", result);

        // Copy From Device-Allocated memory to host (data)
        ByteBuffer allocate = ByteBuffer.allocate(16);
        allocate.order(ByteOrder.LITTLE_ENDIAN);

        result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), allocate.array(), deviceBuffer, bufferSize, 0, 0, null, 0, null);
        errorLog("zeCommandListAppendMemoryCopy", result);

        result = commandList.zeCommandListAppendBarrier(commandList.getCommandListHandlerPtr(), null, 0, null);
        errorLog("zeCommandListAppendBarrier", result);

        // Close the command list
        result = commandList.zeCommandListClose(commandList.getCommandListHandlerPtr());
        errorLog("zeCommandListClose", result);
        result = commandQueue.zeCommandQueueExecuteCommandLists(commandQueue.getCommandQueueHandlerPtr(), 1, commandList.getCommandListHandler(), null);
        errorLog("zeCommandQueueExecuteCommandLists", result);
        result = commandQueue.zeCommandQueueSynchronize(commandQueue.getCommandQueueHandlerPtr(), Long.MAX_VALUE);
        errorLog("zeCommandQueueSynchronize", result);

        long baseAddress = allocate.getLong(0);
        output[0] = baseAddress;
        if (DEBUG) {
            System.out.printf(YELLOW + "[SPIRV-V-Runtime] Base Address " + baseAddress + RESET + "%%n");
        }

        commandList.zeCommandListReset(commandList.getCommandListHandlerPtr());
        errorLog("zeCommandListReset", result);
        return baseAddress;
    }
}
