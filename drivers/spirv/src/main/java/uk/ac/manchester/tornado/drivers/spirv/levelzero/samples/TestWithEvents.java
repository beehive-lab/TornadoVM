package uk.ac.manchester.tornado.drivers.spirv.levelzero.samples;

import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroContext;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDevice;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDriver;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandListHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandQueueDescription;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeContextDesc;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDevicesHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDriverHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeEventDescription;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeEventHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeEventPoolDescription;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeEventPoolFlags;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeEventPoolHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeEventScopeFlags;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeInitFlag;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.utils.LevelZeroUtils;

public class TestWithEvents {

    public static void main(String[] args) {
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

        // Get number of devices in a driver
        int[] deviceCount = new int[1];
        result = driver.zeDeviceGet(driverHandler, 0, deviceCount, null);
        LevelZeroUtils.errorLog("zeDeviceGet", result);

        // Instantiate a device Handler
        ZeDevicesHandle deviceHandler = new ZeDevicesHandle(deviceCount[0]);
        result = driver.zeDeviceGet(driverHandler, 0, deviceCount, deviceHandler);
        LevelZeroUtils.errorLog("zeDeviceGet", result);

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

        // ============================================
        // Create device
        // ============================================
        LevelZeroDevice device = driver.getDevice(driverHandler, 0);

        ZeCommandQueueDescription commandQueueDescription = new ZeCommandQueueDescription();
        ZeCommandListHandle commandList = new ZeCommandListHandle();
        result = context.zeCommandListCreateImmediate(context.getContextHandle().getContextPtr()[0], device.getDeviceHandlerPtr(), commandQueueDescription, commandList);
        LevelZeroUtils.errorLog("zeCommandListCreateImmediate", result);

        ZeEventPoolDescription eventPoolDesc = new ZeEventPoolDescription();
        eventPoolDesc.setCount(1);
        eventPoolDesc.setFlags(ZeEventPoolFlags.ZE_EVENT_POOL_FLAG_HOST_VISIBLE);
        ZeEventDescription eventDescription = new ZeEventDescription();
        eventDescription.setSignal(ZeEventScopeFlags.ZE_EVENT_SCOPE_FLAG_HOST);
        eventDescription.setWait(ZeEventScopeFlags.ZE_EVENT_SCOPE_FLAG_HOST);

        ZeEventHandle event = new ZeEventHandle();
        ZeEventPoolHandle eventPool = new ZeEventPoolHandle();
        int numDevices = 1;
        result = context.zeEventPoolCreate(context.getDefaultContextPtr(), eventPoolDesc, numDevices, device.getDeviceHandlerPtr(), eventPool);
        LevelZeroUtils.errorLog("zeEventPoolCreate", result);

        // The following call cases a problem (SEG FAULT) in level-zero.
        // I suspect I need to pass the device pointer, rather than a copy of the
        // device.
        // result = context.zeEventCreate(eventPool, eventDescription, event);
        // errorLog("zeEventCreate", result);

        result = context.zeCommandListDestroy(commandList);
        LevelZeroUtils.errorLog("zeCommandListDestroy", result);

        // result = context.zeEventPoolDestroy(eventPool);
        // errorLog("zeEventPoolDestroy", result);

        result = context.zeEventDestroy(event);
        LevelZeroUtils.errorLog("zeEventDestroy", result);

        result = driver.zeContextDestroy(context);
        LevelZeroUtils.errorLog("zeContextDestroy", result);
    }
}
