/*
 * MIT License
 *
 * Copyright (c) 2021-2022, APT Group, Department of Computer Science,
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

import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroByteBuffer;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroCommandList;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroCommandQueue;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroContext;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDevice;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDriver;
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
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDeviceMemAllocDescriptor;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDeviceProperties;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDevicesHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDriverHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeHostMemAllocDescriptor;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeInitFlag;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeRelaxedAllocationLimitsExpDescriptor;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeRelaxedAllocationLimitsFlags;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.utils.LevelZeroUtils;

public class TestLargeBuffer {

    /**
     * 6GB Allocation
     */
    private static final long SIZE = 6147483648l;


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

        ZeContextDescriptor contextDescription = new ZeContextDescriptor();
        LevelZeroContext context = new LevelZeroContext(driverHandler, contextDescription);
        result = context.zeContextCreate(driverHandler.getZe_driver_handle_t_ptr()[0]);
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
        ZeCommandQueueDescriptor cmdDescriptor = new ZeCommandQueueDescriptor();
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
        ZeCommandListDescriptor cmdListDescriptor = new ZeCommandListDescriptor();
        cmdListDescriptor.setFlags(ZeCommandListFlag.ZE_COMMAND_LIST_FLAG_RELAXED_ORDERING);
        cmdListDescriptor.setCommandQueueGroupOrdinal(getCommandQueueOrdinal(device));
        ZeCommandListHandle commandListHandler = new ZeCommandListHandle();
        int result = context.zeCommandListCreate(context.getContextHandle().getContextPtr()[0], device.getDeviceHandlerPtr(), cmdListDescriptor, commandListHandler);
        LevelZeroUtils.errorLog("zeCommandListCreate", result);
        return new LevelZeroCommandList(context, commandListHandler);
    }

    public static void testAppendMemoryCopyFromHeapToDeviceToHeap(LevelZeroContext context, LevelZeroDevice device) {

        final long allocSize = SIZE;

        LevelZeroByteBuffer sharedBuffer = new LevelZeroByteBuffer();

        LevelZeroCommandQueue commandQueue = createCommandQueue(context, device);
        LevelZeroCommandList commandList = createCommandList(context, device);

        ZeDeviceMemAllocDescriptor deviceMemAllocDesc = new ZeDeviceMemAllocDescriptor();
        deviceMemAllocDesc.setOrdinal(0);
        deviceMemAllocDesc.setFlags(0);
        final int alignment = 64;

        ZeHostMemAllocDescriptor hostMemAllocDesc = new ZeHostMemAllocDescriptor();
        hostMemAllocDesc.setFlags(0);

        ZeRelaxedAllocationLimitsExpDescriptor relaxedAllocationLimitsExpDescriptor = new ZeRelaxedAllocationLimitsExpDescriptor();
        relaxedAllocationLimitsExpDescriptor.setFlags(ZeRelaxedAllocationLimitsFlags.ZE_RELAXED_ALLOCATION_LIMITS_EXP_FLAG_MAX_SIZE);

        relaxedAllocationLimitsExpDescriptor.materialize();

        // These two lines allow us to use the extended memory allocation mode by appending the extended memory descriptor
        // to the device and host memory descriptors.
        deviceMemAllocDesc.setNext(relaxedAllocationLimitsExpDescriptor);
        hostMemAllocDesc.setNext(relaxedAllocationLimitsExpDescriptor);

        System.out.println("Allocating SHARED: " + (allocSize) + " (bytes) --> " + ((allocSize)*1e-9) + " (GBs)");
        int result = context.zeMemAllocShared(context.getContextHandle().getContextPtr()[0], deviceMemAllocDesc, hostMemAllocDesc, allocSize, alignment, device.getDeviceHandlerPtr(), sharedBuffer);
        LevelZeroUtils.errorLog("zeMemAllocShared", result);

        LevelZeroByteBuffer deviceBuffer = new LevelZeroByteBuffer();
        System.out.println("Allocating DEVICE: " + (allocSize) + " (bytes) --> " + ((allocSize)*1e-9) + " (GBs)");
        result = context.zeMemAllocDevice(context.getContextHandle().getContextPtr()[0], deviceMemAllocDesc, allocSize, alignment, device.getDeviceHandlerPtr(), deviceBuffer);
        LevelZeroUtils.errorLog("zeMemAllocDevice", result);

        LevelZeroByteBuffer hostBuffer = new LevelZeroByteBuffer();
        System.out.println("Allocating HOST: " + (allocSize) + " (bytes) --> " + ((allocSize)*1e-9) + " (GBs)");
        result = context.zeMemAllocHost(context.getContextHandle().getContextPtr()[0], hostMemAllocDesc, allocSize, alignment, hostBuffer);
        LevelZeroUtils.errorLog("zeMemAllocHost", result);

        // Free resources
        context.zeMemFree(context.getDefaultContextPtr(), sharedBuffer);
        context.zeMemFree(context.getDefaultContextPtr(), deviceBuffer);
        context.zeMemFree(context.getDefaultContextPtr(), hostBuffer);
        context.zeCommandListDestroy(commandList.getCommandListHandler());
        context.zeCommandQueueDestroy(commandQueue.getCommandQueueHandle());
        System.out.println("OK ");

     }

    public static void main(String[] args) {
        LevelZeroDriver driver = new LevelZeroDriver();
        LevelZeroContext context = zeInitContext(driver);
        LevelZeroDevice device = zeGetDevices(context, driver);

        ZeDeviceProperties deviceProperties = device.getDeviceProperties();
        System.out.println("Device: ");
        System.out.println("\tName     : " + deviceProperties.getName());
        System.out.println("\tVendor ID: " + Integer.toHexString(deviceProperties.getVendorId()));

        testAppendMemoryCopyFromHeapToDeviceToHeap(context, device);

        driver.zeContextDestroy(context);
    }
}
