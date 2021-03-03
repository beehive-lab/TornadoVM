package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDevice;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandQueueListHandle;

public class SPIRVLevelZeroCommandQueue extends SPIRVCommandQueue {

    private ZeCommandQueueListHandle commandQueueListHandle;
    private LevelZeroDevice device;

    public SPIRVLevelZeroCommandQueue(ZeCommandQueueListHandle commandQueueListHandle, LevelZeroDevice device) {
        this.commandQueueListHandle = commandQueueListHandle;
        this.device = device;
    }

    public ZeCommandQueueListHandle getCommandQueueListHandle() {
        return commandQueueListHandle;
    }

    public LevelZeroDevice getDevice() {
        return device;
    }
}
