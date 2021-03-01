package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroContext;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDevice;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandListDescription;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandQueueDescription;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandQueueGroupProperties;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandQueueGroupPropertyFlags;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandQueueHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandQueueListHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandQueueMode;

import java.util.ArrayList;
import java.util.List;

public class SPIRVLevelZeroContext extends SPIRVContext {

    private LevelZeroContext levelZeroContext;
    private List<SPIRVDeviceContext> spirvDeviceContext;
    private List<SPIRVCommandQueue> commandQueues;

    public SPIRVLevelZeroContext(SPIRVPlatform platform, List<SPIRVDevice> devices, LevelZeroContext levelZeroContext) {
        super(platform, devices);
        this.levelZeroContext = levelZeroContext;

        commandQueues = new ArrayList<>();
        for (SPIRVDevice device : devices) {
            ZeCommandQueueListHandle commandQueue = createCommandQueue(device);
            commandQueues.add(new SPIRVLevelZeroCommandQueue(commandQueue));
        }

        spirvDeviceContext = new ArrayList<>();

        // Create LevelZeroDeviceContext
        for (int deviceIndex = 0; deviceIndex < devices.size(); deviceIndex++) {
            SPIRVDeviceContext deviceContext = new SPIRVDeviceContext(devices.get(deviceIndex), commandQueues.get(deviceIndex), this);
            devices.get(deviceIndex).setDeviContext(deviceContext);
            spirvDeviceContext.add(deviceContext);
        }
    }

    private ZeCommandQueueListHandle createCommandQueue(SPIRVDevice spirvDevice) {
        // ============================================
        // Create a command queue
        // ============================================
        // A) Get the number of command queue groups
        LevelZeroDevice device = (LevelZeroDevice) spirvDevice.getDevice();

        int[] numQueueGroups = new int[1];
        device.zeDeviceGetCommandQueueGroupProperties(device.getDeviceHandlerPtr(), numQueueGroups, null);

        if (numQueueGroups[0] == 0) {
            throw new RuntimeException("Number of Queue Groups is 0 for device: " + device.getDeviceProperties().getName());
        }

        ZeCommandQueueGroupProperties[] commandQueueGroupProperties = new ZeCommandQueueGroupProperties[numQueueGroups[0]];
        device.zeDeviceGetCommandQueueGroupProperties(device.getDeviceHandlerPtr(), numQueueGroups, commandQueueGroupProperties);

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
        levelZeroContext.zeCommandQueueCreate(levelZeroContext.getContextHandle().getContextPtr()[0], device.getDeviceHandlerPtr(), commandQueueDescription, commandQueue);

        // ============================================
        // Create a command list
        // ============================================
        ZeCommandQueueListHandle commandList = new ZeCommandQueueListHandle();
        ZeCommandListDescription commandListDescription = new ZeCommandListDescription();
        commandListDescription.setCommandQueueGroupOrdinal(commandQueueDescription.getOrdinal());
        levelZeroContext.zeCommandListCreate(levelZeroContext.getContextHandle().getContextPtr()[0], device.getDeviceHandlerPtr(), commandListDescription, commandList);
        return commandList;
    }

    @Override
    public SPIRVDeviceContext getDeviceContext(int deviceIndex) {
        return spirvDeviceContext.get(deviceIndex);
    }

}
