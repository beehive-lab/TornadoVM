package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandQueueListHandle;

public class SPIRVLevelZeroCommandQueue extends SPIRVCommandQueue {

    ZeCommandQueueListHandle commandQueueListHandle;

    public SPIRVLevelZeroCommandQueue(ZeCommandQueueListHandle commandQueueListHandle) {
        this.commandQueueListHandle = commandQueueListHandle;
    }

}
