package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroCommandList;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroCommandQueue;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDevice;

public class SPIRVLevelZeroCommandQueue extends SPIRVCommandQueue {

    private LevelZeroCommandQueue commandQueue;
    private LevelZeroCommandList commandList;
    private LevelZeroDevice device;

    public SPIRVLevelZeroCommandQueue(LevelZeroCommandQueue commandQueue, LevelZeroCommandList commandList, LevelZeroDevice device) {
        this.commandQueue = commandQueue;
        this.commandList = commandList;
        this.device = device;
    }

    public LevelZeroCommandQueue getCommandQueue() {
        return commandQueue;
    }

    public LevelZeroCommandList getCommandList() {
        return commandList;
    }

    public LevelZeroDevice getDevice() {
        return device;
    }
}
