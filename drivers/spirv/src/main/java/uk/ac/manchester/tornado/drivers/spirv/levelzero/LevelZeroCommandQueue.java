package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class LevelZeroCommandQueue {

    private LevelZeroContext context;
    private ZeCommandQueueHandle zeCommandQueueHandle;

    public LevelZeroCommandQueue(LevelZeroContext context, ZeCommandQueueHandle zeCommandQueueHandle) {
        this.context = context;
        this.zeCommandQueueHandle = zeCommandQueueHandle;
    }

    public LevelZeroContext getContext() {
        return this.context;
    }

    public ZeCommandQueueHandle getCommandQueueHandle() {
        return this.zeCommandQueueHandle;
    }

    native int zeCommandQueueExecuteCommandLists_native(long commandQueueHandlerPointer, int numCommandLists, ZeCommandListHandle commandListHandler, Object hFence);

    public int zeCommandQueueExecuteCommandLists(long commandQueueHandlerPointer, int numCommandLists, ZeCommandListHandle commandListHandler, Object hFence) {
        return zeCommandQueueExecuteCommandLists_native(commandQueueHandlerPointer, numCommandLists, commandListHandler, hFence);
    }

    native int zeCommandQueueSynchronize_native(long commandQueueHandlerPointer, long timeOut);

    public int zeCommandQueueSynchronize(long commandQueueHandlerPointer, long timeOut) {
        return zeCommandQueueSynchronize_native(commandQueueHandlerPointer, timeOut);
    }

    public long getCommandQueueHandlerPtr() {
        return this.zeCommandQueueHandle.getCommandQueueHandlerPointer();
    }
}
