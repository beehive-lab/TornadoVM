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
package uk.ac.manchester.tornado.drivers.spirv.levelzero.samples;

import static uk.ac.manchester.tornado.drivers.spirv.levelzero.utils.LevelZeroUtils.errorLog;

import java.util.Arrays;

import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroByteBuffer;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroCommandList;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroCommandQueue;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroContext;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDevice;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDriver;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroKernel;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.Sizeof;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDeviceMemAllocDesc;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDeviceMemAllocFlags;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDeviceProperties;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeGroupDispatch;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeKernelHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.utils.LevelZeroUtils;

/**
 * How to run?
 *
 * <code>
 *     __kernel void lookUp(__global long *heap, __global long* output) {
 *           output[get_global_id(0)]  =  (ulong) heap;
 *      }
 * </code>
 * 
 * <code>
 * $ tornado uk.ac.manchester.tornado.drivers.spirv.levelzero.samples.TestLookUpBufferAddress
 * </code>
 */
public class TestLookUpBufferAddress {

    private static void dispatchLookUpBuffer(LevelZeroCommandList commandList, LevelZeroCommandQueue commandQueue, LevelZeroKernel levelZeroKernel, LevelZeroByteBuffer deviceBuffer,
            LevelZeroByteBuffer bufferB, long[] output, int bufferSize) {
        ZeKernelHandle kernel = levelZeroKernel.getKernelHandle();

        // Prepare kernel for launch
        // A) Suggest scheduling parameters to level-zero
        int[] groupSizeX = new int[] { 1 };
        int[] groupSizeY = new int[] { 1 };
        int[] groupSizeZ = new int[] { 1 };
        int result = levelZeroKernel.zeKernelSuggestGroupSize(kernel.getPtrZeKernelHandle(), 1, 1, 1, groupSizeX, groupSizeY, groupSizeZ);
        LevelZeroUtils.errorLog("zeKernelSuggestGroupSize", result);

        result = levelZeroKernel.zeKernelSetGroupSize(kernel.getPtrZeKernelHandle(), groupSizeX, groupSizeY, groupSizeZ);
        LevelZeroUtils.errorLog("zeKernelSetGroupSize", result);

        result = levelZeroKernel.zeKernelSetArgumentValue(kernel.getPtrZeKernelHandle(), 0, Sizeof.POINTER.getNumBytes(), deviceBuffer.getPtrBuffer());
        LevelZeroUtils.errorLog("zeKernelSetArgumentValue", result);
        result |= levelZeroKernel.zeKernelSetArgumentValue(kernel.getPtrZeKernelHandle(), 1, Sizeof.POINTER.getNumBytes(), bufferB.getPtrBuffer());
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
        result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), output, bufferB, bufferSize, 0, 0, null, 0, null);
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

        long baseAddress = output[0];
        System.out.println("Base Address: " + baseAddress);
    }

    private static void testLookUpBufferAddress(LevelZeroContext context, LevelZeroDevice device) {

        LevelZeroCommandQueue commandQueue = LevelZeroUtils.createCommandQueue(device, context);
        LevelZeroCommandList commandList = LevelZeroUtils.createCommandList(device, context, commandQueue.getCommandQueueDescription().getOrdinal());

        final int elements = 1;
        final int bufferSize = elements * 8;
        ZeDeviceMemAllocDesc deviceMemAllocDesc = new ZeDeviceMemAllocDesc();
        deviceMemAllocDesc.setFlags(ZeDeviceMemAllocFlags.ZE_DEVICE_MEM_ALLOC_FLAG_BIAS_UNCACHED);
        deviceMemAllocDesc.setOrdinal(0);

        // Fill heap buffer (Java side)
        long[] data = new long[elements];
        Arrays.fill(data, -1);
        long[] output = new long[elements];

        LevelZeroByteBuffer deviceBuffer = new LevelZeroByteBuffer();
        int result = context.zeMemAllocDevice(context.getDefaultContextPtr(), deviceMemAllocDesc, bufferSize, 1, device.getDeviceHandlerPtr(), deviceBuffer);
        LevelZeroUtils.errorLog("zeMemAllocDevice", result);

        LevelZeroByteBuffer bufferB = new LevelZeroByteBuffer();
        result = context.zeMemAllocDevice(context.getDefaultContextPtr(), deviceMemAllocDesc, bufferSize, 1, device.getDeviceHandlerPtr(), bufferB);
        LevelZeroUtils.errorLog("zeMemAllocDevice", result);

        // Copy from HEAP -> Device Allocated Memory
        result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), deviceBuffer, data, bufferSize, 0, 0, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendMemoryCopyWithOffset", result);
        result = commandList.zeCommandListAppendBarrier(commandList.getCommandListHandlerPtr(), null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendBarrier", result);

        LevelZeroKernel levelZeroKernel = LevelZeroUtils.compileSPIRVKernel(device, context, "lookUp", "/home/juan/manchester/tornado/tornado/assembly/src/bin/spirv/lookUpBufferAddress.spv");
        dispatchLookUpBuffer(commandList, commandQueue, levelZeroKernel, deviceBuffer, bufferB, output, bufferSize);

        result = commandList.zeCommandListReset(commandList.getCommandListHandlerPtr());
        errorLog("zeCommandListReset", result);

        // Free resources
        result = context.zeMemFree(context.getDefaultContextPtr(), deviceBuffer);
        errorLog("zeMemFree", result);
        result = context.zeCommandListDestroy(commandList.getCommandListHandler());
        errorLog("zeCommandListDestroy", result);
        result = context.zeCommandQueueDestroy(commandQueue.getCommandQueueHandle());
        errorLog("zeCommandQueueDestroy", result);
    }

    /**
     * Run as follows:
     *
     * <code>
     * $ tornado uk.ac.manchester.tornado.drivers.spirv.levelzero.samples.TestLookUpBufferAddress
     * </code>
     *
     * @param args
     */
    public static void main(String[] args) {
        LevelZeroDriver driver = new LevelZeroDriver();
        LevelZeroContext context = LevelZeroUtils.zeInitContext(driver);
        LevelZeroDevice device = LevelZeroUtils.zeGetDevices(context, driver);

        ZeDeviceProperties deviceProperties = device.getDeviceProperties();
        System.out.println("Device: ");
        System.out.println("\tName     : " + deviceProperties.getName());
        System.out.println("\tVendor ID: " + Integer.toHexString(deviceProperties.getVendorId()));
        testLookUpBufferAddress(context, device);

        int result = driver.zeContextDestroy(context);
        errorLog("zeContextDestroy", result);
    }
}
