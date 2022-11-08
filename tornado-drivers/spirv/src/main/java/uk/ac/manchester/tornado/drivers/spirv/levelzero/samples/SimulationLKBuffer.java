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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroByteBuffer;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroCommandList;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroCommandQueue;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroContext;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDevice;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDriver;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroKernel;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.Sizeof;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDeviceMemAllocDescriptor;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDeviceMemAllocFlags;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDeviceProperties;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeGroupDispatch;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeKernelHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.utils.LevelZeroUtils;

/**
 * How to run?
 *
 * <code>
 * __kernel void lookUp(__global uchar *heap) {
 *        __global ulong *_frame = (__global ulong *) &heap[0];
 *       *((__global long *) &heap[get_global_id(0)])  =  (ulong) _frame;
 * }
 *
 * </code>
 *
 * <code>
 *  __kernel void copyTest(__global uchar *_heap_base)
 * {
 *   int i_8, i_7, i_1, i_2;
 *   ulong ul_0, ul_6;
 *   long l_3, l_5, l_4;
 *
 *   __global ulong *_frame = (__global ulong *) &_heap_base[0];
 *
 *   ul_0  =  (ulong) _frame[3];
 *   i_1  =  get_global_id(0);
 *   i_2  =  i_1;
 *   l_3  =  (long) i_2;
 *   l_4  =  l_3 << 3;         // Long buffer
 *   l_5  =  l_4 + 16L;        // Randomly starting in position 16
 *   ul_6  =  ul_0 + l_5;
 *   *((__global int *) ul_6)  =  555;
 * }
 * </code>
 *
 *
 * <code>
 *     $ tornado uk.ac.manchester.tornado.drivers.spirv.levelzero.samples.SimulationLKBuffer
 * </code>
 */
public class SimulationLKBuffer {

    private static LevelZeroByteBuffer deviceHeapBuffer;
    // private static final int DEVICE_HEAP_SIZE = 128 * 8;
    private static final int DEVICE_HEAP_SIZE = 1000000000; // 1GB
    private static long[] stack;

    private static void dispatchCopyKernel(LevelZeroCommandList commandList, LevelZeroCommandQueue commandQueue, LevelZeroKernel levelZeroKernel, long[] output, int bufferSize, ByteBuffer stack) {
        ZeKernelHandle kernel = levelZeroKernel.getKernelHandle();

        // Prepare kernel for launch
        // A) Suggest scheduling parameters to level-zero
        int[] groupSizeX = new int[] { 1 };
        int[] groupSizeY = new int[] { 1 };
        int[] groupSizeZ = new int[] { 1 };
        int result = levelZeroKernel.zeKernelSuggestGroupSize(kernel.getPtrZeKernelHandle(), 128, 1, 1, groupSizeX, groupSizeY, groupSizeZ);
        LevelZeroUtils.errorLog("zeKernelSuggestGroupSize", result);

        result = levelZeroKernel.zeKernelSetGroupSize(kernel.getPtrZeKernelHandle(), groupSizeX, groupSizeY, groupSizeZ);
        LevelZeroUtils.errorLog("zeKernelSetGroupSize", result);

        // result =
        // levelZeroKernel.zeKernelSetArgumentValue(kernel.getPtrZeKernelHandle(), 0,
        // Sizeof.POINTER.getNumBytes(), deviceHeapBuffer.getPtrBuffer());
        result = levelZeroKernel.zeKernelSetArgumentValue(kernel.getPtrZeKernelHandle(), 0, Sizeof.POINTER.getNumBytes(), deviceHeapBuffer.getPtrBuffer());
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
        result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), output, deviceHeapBuffer, bufferSize, 0, 0, null, 0, null);
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

        System.out.println("Content: " + Arrays.toString(output));
    }

    private static void simulateLookUpBufferAddress(LevelZeroContext context, LevelZeroDevice device) {

        LevelZeroCommandQueue commandQueue = LevelZeroUtils.createCommandQueue(device, context);
        LevelZeroCommandList commandList = LevelZeroUtils.createCommandList(device, context, commandQueue.getCommandQueueDescription().getOrdinal());

        final int elements = 1;
        final int bufferSize = elements * 8;
        ZeDeviceMemAllocDescriptor deviceMemAllocDesc = new ZeDeviceMemAllocDescriptor();
        deviceMemAllocDesc.setFlags(ZeDeviceMemAllocFlags.ZE_DEVICE_MEM_ALLOC_FLAG_BIAS_CACHED);
        deviceMemAllocDesc.setOrdinal(0);

        long[] output = new long[elements];
        deviceHeapBuffer = new LevelZeroByteBuffer();
        int result = context.zeMemAllocDevice(context.getDefaultContextPtr(), deviceMemAllocDesc, DEVICE_HEAP_SIZE, 1, device.getDeviceHandlerPtr(), deviceHeapBuffer);
        LevelZeroUtils.errorLog("zeMemAllocDevice", result);

        LevelZeroKernel levelZeroKernel = LevelZeroUtils.compileSPIRVKernel(device, context, "lookUp", "/tmp/lookUpBufferAddress.spv");
        LevelZeroUtils.dispatchLookUpBuffer(commandList, commandQueue, levelZeroKernel, deviceHeapBuffer, output, bufferSize);

        result = commandList.zeCommandListReset(commandList.getCommandListHandlerPtr());
        errorLog("zeCommandListReset", result);

        // Run 2nd Kernel: Execute-Copy
        ByteBuffer stack = ByteBuffer.allocate(32);
        stack.order(ByteOrder.LITTLE_ENDIAN);
        stack.putLong(777);
        stack.putLong(888);
        stack.putLong(999);
        stack.putLong(output[0] + 80L);

        // Copy Host -> Device
        result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), deviceHeapBuffer, stack.array(), stack.position(), 0, 0, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendMemoryCopyWithOffset", result);
        result = commandList.zeCommandListAppendBarrier(commandList.getCommandListHandlerPtr(), null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendBarrier", result);

        LevelZeroKernel kernelCopy = LevelZeroUtils.compileSPIRVKernel(device, context, "copyTest", "/tmp/example.spv");
        long[] output2 = new long[128];
        dispatchCopyKernel(commandList, commandQueue, kernelCopy, output2, 128 * Sizeof.LONG.getNumBytes(), stack);

        // Free resources
        errorLog("zeMemFree", result);
        result = context.zeMemFree(context.getDefaultContextPtr(), deviceHeapBuffer);
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
     * $ tornado uk.ac.manchester.tornado.drivers.spirv.levelzero.samples.SimulationLKBuffer
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
        simulateLookUpBufferAddress(context, device);

        int result = driver.zeContextDestroy(context);
        errorLog("zeContextDestroy", result);
    }
}
