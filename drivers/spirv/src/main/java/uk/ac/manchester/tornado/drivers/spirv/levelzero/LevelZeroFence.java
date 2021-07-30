package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class LevelZeroFence {

    private ZeFenceHandle fenceHandler;
    private ZeFenceDesc fenceDesc;

    public LevelZeroFence(ZeFenceDesc desc, ZeFenceHandle handler) {
        this.fenceDesc = desc;
        this.fenceHandler = handler;
    }

    private native int zeFenceCreate_native(long commandQueueHandlerPointer, ZeFenceDesc fenceDesc, ZeFenceHandle fenceHandler);

    public long getHandlerPointer() {
        return this.fenceHandler.getPtrZeFenceHandle();
    }

    public int zeFenceCreate(long commandQueueHandlerPointer, ZeFenceDesc fenceDesc, ZeFenceHandle fenceHandler) {
        return zeFenceCreate_native(commandQueueHandlerPointer, fenceDesc, fenceHandler);
    }

    private native int zeFenceHostSynchronize_native(long ptrZeFenceHandle, long maxValue);

    public int zeFenceHostSynchronize(long ptrZeFenceHandle, long maxValue) {
        return zeFenceHostSynchronize_native(ptrZeFenceHandle, maxValue);
    }

    private native int zeFenceReset_native(long ptrZeFenceHandle);

    public int zeFenceReset(long ptrZeFenceHandle) {
        return zeFenceReset_native(ptrZeFenceHandle);
    }
}
