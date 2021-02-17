package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class ZeCommandQueueHandle {

    private long commandQueueHandlerPointer;

    public static final int INIT = -1;

    public ZeCommandQueueHandle() {
        this.commandQueueHandlerPointer = INIT;
    }

    public long getCommandQueueHandlerPointer() {
        return commandQueueHandlerPointer;
    }

    public void initPtr() {
        this.commandQueueHandlerPointer = -1;
    }
}
