package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDevice;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandListHandle;

public class SPIRVLevelZeroCommandQueue extends SPIRVCommandQueue {

    private ZeCommandListHandle commandQueueListHandle;
    private LevelZeroDevice device;

    public SPIRVLevelZeroCommandQueue(ZeCommandListHandle commandQueueListHandle, LevelZeroDevice device) {
        this.commandQueueListHandle = commandQueueListHandle;
        this.device = device;
    }

    public ZeCommandListHandle getCommandQueueListHandle() {
        return commandQueueListHandle;
    }

    public LevelZeroDevice getDevice() {
        return device;
    }
}
